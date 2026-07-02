package com.polymarket.examples.bot;

import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles concurrent order execution while preventing duplicate overlapping batches.
 *
 * <p>Production flow: build all legs first, submit once via POST /orders (max 15), then inspect
 * each leg response for mixed outcomes. Orders with {@code status=delayed} are followed up via
 * polling to confirm whether they were actually filled.
 *
 * <p>Partial fill handling: When only some legs fill, the engine immediately sells the filled
 * positions at current market bid prices to close the one-sided risk, then resumes evaluation.
 */
public class ExecutionEngine {

  private static final boolean DEBUG_EXECUTION =
      Boolean.parseBoolean(System.getProperty("bot.debug.execution", "true"));

  /** Number of times to poll a delayed order before giving up. */
  private static final int DELAYED_POLL_MAX_ATTEMPTS = 3;

  /** Milliseconds to wait between delayed-order poll attempts. */
  private static final long DELAYED_POLL_INTERVAL_MS = 500;

  /** Status string indicating the order was matched/filled. */
  private static final String STATUS_MATCHED = "matched";

  /** Status string indicating the order is live (resting on the book). */
  private static final String STATUS_LIVE = "live";

  /** Status string indicating the order processing was deferred. */
  private static final String STATUS_DELAYED = "delayed";

  private final AsyncPolymarketClient asyncClient;
  private final WalletInventory walletInventory;
  private final AtomicBoolean isExecuting = new AtomicBoolean(false);
  private final AtomicLong executionCounter = new AtomicLong(0);

  @FunctionalInterface
  public interface PartialFillCallback {
    void onPartialFill(
        List<String> filledTokenIds, List<Double> currentBidPrices, List<Double> sizes);
  }

  private PartialFillCallback partialFillCallback;

  public ExecutionEngine(AsyncPolymarketClient asyncClient) {
    this(asyncClient, null);
  }

  public ExecutionEngine(AsyncPolymarketClient asyncClient, WalletInventory walletInventory) {
    this.asyncClient = asyncClient;
    this.walletInventory = walletInventory;
  }

  public void setPartialFillCallback(PartialFillCallback callback) {
    this.partialFillCallback = callback;
  }

  /**
   * Closes a one-sided position by selling the token at the current market bid price. Called when a
   * partial fill leaves the bot holding a single outcome.
   *
   * @param marketId the market this position belongs to
   * @param tokenId the token to sell
   * @param size how many shares to sell
   * @param currentBidPrice the current best bid price to sell at
   */
  public void sellPosition(String marketId, String tokenId, double size, double currentBidPrice) {
    if (tokenId == null || tokenId.isBlank() || size <= 0 || currentBidPrice <= 0) {
      return;
    }

    String executionId = "sell-" + executionCounter.incrementAndGet();

    if (DEBUG_EXECUTION) {
      System.out.printf(
          "[BOT][%s] Closing one-sided position: token=%s size=%.4f bid=%.6f%n",
          executionId, tokenId, size, currentBidPrice);
    }

    UserOrder sellOrder =
        UserOrder.builder()
            .tokenID(tokenId)
            .side(Side.SELL)
            .price(BigDecimal.valueOf(currentBidPrice))
            .size(BigDecimal.valueOf(size))
            .build();

    asyncClient
        .createAndPostOrders(List.of(sellOrder), OrderType.FOK)
        .whenComplete(
            (responses, error) -> {
              if (error != null) {
                System.err.printf(
                    "[BOT][%s] Failed to close position for token=%s: %s%n",
                    executionId, tokenId, error.getMessage());
                return;
              }

              if (responses != null && !responses.isEmpty()) {
                OrderResponse response = responses.get(0);
                String status = response.status() != null ? response.status().toLowerCase() : "";
                if (STATUS_MATCHED.equals(status) || STATUS_LIVE.equals(status)) {
                  BigDecimal proceeds = BigDecimal.valueOf(currentBidPrice * size);
                  System.out.printf(
                      "[BOT][%s] ✅ Position closed: token=%s size=%.4f bid=%.6f proceeds=$%.4f%n",
                      executionId, tokenId, size, currentBidPrice, proceeds);
                } else if (STATUS_DELAYED.equals(status)) {
                  System.out.printf(
                      "[BOT][%s] ⚠️ Sell order delayed for token=%s, monitoring...%n",
                      executionId, tokenId);
                } else {
                  System.err.printf(
                      "[BOT][%s] ❌ Failed to close position: token=%s status=%s error=%s%n",
                      executionId, tokenId, response.status(), response.errorMsg());
                }
              }
            });
  }

  /**
   * Executes an arbitrage trade by creating all legs and submitting them as a single FOK batch.
   *
   * @param instructions The list of trade legs to execute simultaneously.
   */
  public void executeArbitrage(List<TradeInstruction> instructions) {
    executeArbitrage(null, instructions);
  }

  /**
   * Executes an arbitrage trade for a specific market, enforcing its allocated budget.
   *
   * @param marketId market identifier used for budget accounting
   * @param instructions The list of trade legs to execute simultaneously.
   */
  public void executeArbitrage(String marketId, List<TradeInstruction> instructions) {
    if (instructions == null || instructions.isEmpty()) {
      return;
    }

    String executionId = "arb-" + executionCounter.incrementAndGet();

    // Prevent duplicate overlapping executions
    if (!isExecuting.compareAndSet(false, true)) {
      if (DEBUG_EXECUTION) {
        System.out.printf(
            "[BOT][%s] Skipping arbitrage trigger because another batch is still executing.%n",
            executionId);
      }
      return;
    }

    try {
      Set<String> uniqueTokens = new HashSet<>();
      for (TradeInstruction instruction : instructions) {
        if (instruction == null
            || instruction.tokenId() == null
            || instruction.tokenId().isBlank()) {
          System.err.printf(
              "[BOT][%s] Invalid instruction detected. Aborting batch.%n", executionId);
          isExecuting.set(false);
          return;
        }
        if (!uniqueTokens.add(instruction.tokenId())) {
          System.err.printf(
              "[BOT][%s] Duplicate token leg detected for token=%s. Aborting batch to avoid one-sided execution.%n",
              executionId, instruction.tokenId());
          isExecuting.set(false);
          return;
        }
      }

      if (uniqueTokens.size() < 2) {
        System.err.printf(
            "[BOT][%s] Arbitrage requires at least 2 unique outcomes. Aborting batch.%n",
            executionId);
        isExecuting.set(false);
        return;
      }

      // Scale down trade size to fit within the remaining budget for this market.
      List<TradeInstruction> effectiveInstructions = instructions;
      if (walletInventory != null && marketId != null && !marketId.isBlank()) {
        BigDecimal remaining = walletInventory.remainingBudget(marketId);
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
          // sum of prices across all legs (cost per 1 share of the full basket)
          double priceSum =
                  instructions.stream().mapToDouble(TradeInstruction::price).sum();
          if (priceSum > 0) {
            double maxAffordableSize = remaining.doubleValue() / priceSum;
            double currentSize = instructions.get(0).size();
            if (maxAffordableSize < currentSize) {
              // Floor to 2 decimal places to avoid submitting fractional micro-sizes
              double cappedSize = Math.floor(maxAffordableSize * 100.0) / 100.0;
              if (DEBUG_EXECUTION) {
                System.out.printf(
                        "[BOT][%s] Capping trade size from %.4f to %.4f to fit budget=%s (priceSum=%.6f)%n",
                        executionId, currentSize, cappedSize, remaining, priceSum);
              }
              effectiveInstructions =
                      instructions.stream()
                              .map(inst -> new TradeInstruction(inst.tokenId(), inst.price(), cappedSize))
                              .toList();
            }
          }
        }
      }
      final List<TradeInstruction> finalInstructions = effectiveInstructions;

      BigDecimal requestedUsd = calculateRequestedUsd(finalInstructions);
      boolean budgetReserved = false;
      if (walletInventory != null && marketId != null && !marketId.isBlank()) {
        budgetReserved = walletInventory.tryConsumeBudget(marketId, requestedUsd);
        if (!budgetReserved) {
          System.err.printf(
              "[BOT][%s] Skipping batch for market=%s due to budget cap. requested=%s remaining=%s%n",
              executionId, marketId, requestedUsd, walletInventory.remainingBudget(marketId));
          isExecuting.set(false);
          return;
        }
      }

      if (DEBUG_EXECUTION) {
        System.out.printf(
                "[BOT][%s] Submitting FOK batch with %d legs.%n", executionId, finalInstructions.size());
      }

      List<UserOrder> orders =
              finalInstructions.stream()
              .map(
                  inst ->
                      UserOrder.builder()
                          .tokenID(inst.tokenId())
                          .side(Side.BUY)
                          .price(BigDecimal.valueOf(inst.price()))
                          .size(BigDecimal.valueOf(inst.size()))
                          .build())
              .toList();

      if (DEBUG_EXECUTION) {
        for (int i = 0; i < finalInstructions.size(); i++) {
          TradeInstruction leg = finalInstructions.get(i);
          System.out.printf(
              "[BOT][%s] Leg %d -> token=%s side=BUY price=%.6f size=%.4f%n",
              executionId, i, leg.tokenId(), leg.price(), leg.size());
        }
      }

      final boolean reservedForBatch = budgetReserved;
      asyncClient
          .createAndPostOrders(orders, OrderType.FOK)
          .whenComplete(
              (responses, error) -> {
                try {
                  if (error != null) {
                    System.err.printf(
                        "[BOT][%s] Fatal error in execution batch: %s%n",
                        executionId, error.getMessage());
                    if (reservedForBatch) {
                      walletInventory.refundBudget(marketId, requestedUsd);
                    }
                    return;
                  }

                  BatchResult result = evaluateBatchResults(executionId, finalInstructions, responses);

                  if (reservedForBatch) {
                    BigDecimal spentUsd =
                            calculateConfirmedSpentUsd(finalInstructions, result.confirmedFillIndices);
                    BigDecimal refund = requestedUsd.subtract(spentUsd);
                    if (refund.compareTo(BigDecimal.ZERO) > 0) {
                      walletInventory.refundBudget(marketId, refund);
                    }
                  }

                  if (result.partialFill() && partialFillCallback != null) {
                    List<String> filledTokenIds = new ArrayList<>();
                    List<Double> sizes = new ArrayList<>();
                    for (int idx : result.confirmedFillIndices()) {
                      TradeInstruction inst = finalInstructions.get(idx);
                      filledTokenIds.add(inst.tokenId());
                      sizes.add(inst.size());
                    }
                    System.out.printf(
                        "[BOT][%s] Triggering partial fill close for %d tokens%n",
                        executionId, filledTokenIds.size());
                    partialFillCallback.onPartialFill(filledTokenIds, null, sizes);
                  }
                } finally {
                  isExecuting.set(false);
                  if (DEBUG_EXECUTION) {
                    System.out.printf("[BOT][%s] Batch execution finished.%n", executionId);
                  }
                }
              });
    } catch (Exception e) {
      System.err.printf(
          "[BOT][%s] Failed to build batch orders: %s%n", executionId, e.getMessage());
      isExecuting.set(false); // Release lock for synchronous failures
    }
  }

  /**
   * Evaluates batch results, properly interpreting the Polymarket API response statuses. For
   * {@code delayed} orders, polls the API to determine the actual fill status.
   *
   * @return a {@link BatchResult} with confirmed fill indices and counts
   */
  private BatchResult evaluateBatchResults(
      String executionId, List<TradeInstruction> instructions, List<OrderResponse> responses) {
    if (responses == null) {
      System.err.printf("[BOT][%s] Batch submission returned null responses.%n", executionId);
      return BatchResult.of(List.of(), 0, 0, 0, instructions.size());
    }

    if (responses.size() != instructions.size()) {
      System.err.printf(
          "[BOT][%s] Response leg count mismatch. requested=%d returned=%d%n",
          executionId, instructions.size(), responses.size());
    }

    int matchedCount = 0;
    int failedCount = 0;
    int delayedCount = 0;
    List<Integer> confirmedFillIndices = new ArrayList<>();
    List<DelayedLeg> delayedLegs = new ArrayList<>();

    int maxLegs = Math.max(instructions.size(), responses.size());
    for (int i = 0; i < maxLegs; i++) {
      TradeInstruction instruction = i < instructions.size() ? instructions.get(i) : null;
      OrderResponse response = i < responses.size() ? responses.get(i) : null;

      String token = instruction != null ? instruction.tokenId() : "<missing-instruction>";

      if (response == null) {
        failedCount++;
        System.err.printf("[BOT][%s] Leg %d missing response | token=%s%n", executionId, i, token);
        continue;
      }

      if (!response.success()) {
        failedCount++;
        System.err.printf(
            "[BOT][%s] ❌ Leg %d REJECTED | token=%s | orderId=%s | status=%s | error=%s%n",
            executionId, i, token, response.orderID(), response.status(), response.errorMsg());
        continue;
      }

      // success=true — now distinguish by status
      String status = response.status() != null ? response.status().toLowerCase() : "";

      switch (status) {
        case STATUS_MATCHED:
          matchedCount++;
          confirmedFillIndices.add(i);
          if (DEBUG_EXECUTION) {
            System.out.printf(
                "[BOT][%s] ✅ Leg %d MATCHED | token=%s | orderId=%s%n",
                executionId, i, token, response.orderID());
          }
          break;
        case STATUS_LIVE:
          // For FOK this shouldn't happen, but log it distinctly
          matchedCount++;
          confirmedFillIndices.add(i);
          if (DEBUG_EXECUTION) {
            System.out.printf(
                "[BOT][%s] ⏳ Leg %d LIVE (resting) | token=%s | orderId=%s%n",
                executionId, i, token, response.orderID());
          }
          break;
        case STATUS_DELAYED:
          delayedCount++;
          System.out.printf(
              "[BOT][%s] ⚠️ Leg %d DELAYED (unconfirmed) | token=%s | orderId=%s%n",
              executionId, i, token, response.orderID());
          delayedLegs.add(new DelayedLeg(i, token, response.orderID()));
          break;
        default:
          // Blank or unrecognised status. If no orderId was assigned the exchange rejected
          // the order before queuing it (e.g. minimum size violation, bad tick rounding).
          // Polling is pointless without an orderId, so treat it as a hard failure.
          if (response.orderID() == null || response.orderID().isBlank()) {
            failedCount++;
            System.err.printf(
                "[BOT][%s] ❌ Leg %d REJECTED (no orderId) | token=%s | status='%s' | error=%s%n",
                executionId, i, token, status, response.errorMsg());
          } else {
            delayedCount++;
            System.err.printf(
                "[BOT][%s] ❓ Leg %d UNKNOWN status='%s' | token=%s | orderId=%s | error=%s%n",
                executionId, i, status, token, response.orderID(), response.errorMsg());
            delayedLegs.add(new DelayedLeg(i, token, response.orderID()));
          }
          break;
      }
    }

    if (!delayedLegs.isEmpty()) {
      System.out.printf(
          "[BOT][%s] Polling %d delayed leg(s) to confirm fill status...%n",
          executionId, delayedLegs.size());

      for (DelayedLeg delayed : delayedLegs) {
        OrderStatusType finalStatus = pollOrderStatus(executionId, delayed);
        delayedCount--;

        if (finalStatus == OrderStatusType.MATCHED) {
          matchedCount++;
          confirmedFillIndices.add(delayed.legIndex);
          System.out.printf(
              "[BOT][%s] ✅ Leg %d confirmed MATCHED after polling | token=%s | orderId=%s%n",
              executionId, delayed.legIndex, delayed.token, delayed.orderId);
        } else {
          failedCount++;
          System.err.printf(
              "[BOT][%s] ❌ Leg %d resolved to %s after polling | token=%s | orderId=%s%n",
              executionId, delayed.legIndex, finalStatus, delayed.token, delayed.orderId);
        }
      }
    }

    // Summary
    int totalLegs = instructions.size();
    if (matchedCount == totalLegs) {
      System.out.printf(
          "[BOT][%s] ✅ All %d batch legs confirmed filled.%n", executionId, totalLegs);
    } else if (matchedCount > 0 && matchedCount < totalLegs) {
      System.err.printf(
          "[BOT][%s] ⚠️ PARTIAL FILL: %d/%d legs filled, %d failed. ONE-SIDED POSITION RISK!%n",
          executionId, matchedCount, totalLegs, failedCount);
    } else {
      System.err.printf(
          "[BOT][%s] ❌ No legs filled (%d failed). No position taken.%n",
          executionId, failedCount);
    }

    return BatchResult.of(confirmedFillIndices, matchedCount, failedCount, delayedCount, totalLegs);
  }

  /**
   * Polls the Polymarket API to determine the final status of a delayed order. Retries up to
   * {@link #DELAYED_POLL_MAX_ATTEMPTS} times with {@link #DELAYED_POLL_INTERVAL_MS} between
   * attempts.
   *
   * @return the resolved {@link OrderStatusType}, or {@link OrderStatusType#UNKNOWN} if polling
   *     fails
   */
  private OrderStatusType pollOrderStatus(String executionId, DelayedLeg delayed) {
    if (delayed.orderId == null || delayed.orderId.isBlank()) {
      return OrderStatusType.UNKNOWN;
    }

    for (int attempt = 1; attempt <= DELAYED_POLL_MAX_ATTEMPTS; attempt++) {
      try {
        Thread.sleep(DELAYED_POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return OrderStatusType.UNKNOWN;
      }

      try {
        OpenOrder order = asyncClient.getOrder(delayed.orderId).join();
        OrderStatusType status = order.getStatus();

        if (DEBUG_EXECUTION) {
          System.out.printf(
              "[BOT][%s] Poll attempt %d/%d for orderId=%s | status=%s | sizeMatched=%s%n",
              executionId,
              attempt,
              DELAYED_POLL_MAX_ATTEMPTS,
              delayed.orderId,
              status,
              order.getSizeMatched());
        }

        // Terminal statuses — return immediately
        if (status == OrderStatusType.MATCHED
            || status == OrderStatusType.CANCELED
            || status == OrderStatusType.UNMATCHED) {
          return status;
        }

        // DELAYED or LIVE — keep polling
      } catch (Exception e) {
        System.err.printf(
            "[BOT][%s] Poll attempt %d/%d failed for orderId=%s: %s%n",
            executionId, attempt, DELAYED_POLL_MAX_ATTEMPTS, delayed.orderId, e.getMessage());
      }
    }

    // Exhausted retries — for FOK orders, if still delayed it almost certainly didn't fill
    System.err.printf(
        "[BOT][%s] Polling exhausted for orderId=%s. Treating as UNMATCHED for FOK safety.%n",
        executionId, delayed.orderId);
    return OrderStatusType.UNMATCHED;
  }

  private BigDecimal calculateUsd(List<TradeInstruction> instructions, List<Integer> indices) {
    BigDecimal total = BigDecimal.ZERO;
    for (int idx : indices) {
      if (idx >= instructions.size()) continue;
      TradeInstruction inst = instructions.get(idx);
      total = total.add(BigDecimal.valueOf(inst.price()).multiply(BigDecimal.valueOf(inst.size())));
    }
    return total.setScale(6, RoundingMode.HALF_UP);
  }

  private BigDecimal calculateRequestedUsd(List<TradeInstruction> instructions) {
    List<Integer> allIndices = new ArrayList<>();
    for (int i = 0; i < instructions.size(); i++) allIndices.add(i);
    return calculateUsd(instructions, allIndices);
  }

  private BigDecimal calculateConfirmedSpentUsd(
          List<TradeInstruction> instructions, List<Integer> confirmedFillIndices) {
    return calculateUsd(instructions, confirmedFillIndices);
  }

  /** DTO for representing a single leg of the arbitrage execution. */
  public record TradeInstruction(String tokenId, double price, double size) {}

  /** Tracks a delayed leg pending follow-up polling. */
  private record DelayedLeg(int legIndex, String token, String orderId) {}

  /** Result of a batch evaluation with indices of confirmed fills. */
  private record BatchResult(
      List<Integer> confirmedFillIndices,
      int matchedCount,
      int failedCount,
      int delayedCount,
      boolean partialFill) {
    static BatchResult of(List<Integer> indices, int matched, int failed, int delayed, int total) {
      return new BatchResult(indices, matched, failed, delayed, matched > 0 && matched < total);
    }
  }
}
