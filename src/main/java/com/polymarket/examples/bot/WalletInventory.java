package com.polymarket.examples.bot;

import com.polymarket.client.PolymarketClient;
import com.polymarket.model.AssetType;
import com.polymarket.model.BalanceAllowanceParams;
import com.polymarket.model.BalanceAllowanceResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Tracks wallet inventory and reserves a fixed USD budget per active bot. */
public final class WalletInventory {

  enum AllocationDecision {
    ALLOCATED,
    ALREADY_ALLOCATED,
    INSUFFICIENT_BALANCE,
    BALANCE_UNAVAILABLE
  }

  private final PolymarketClient client;
  private final BigDecimal perBotUsdAllocation;
  private final Map<String, BigDecimal> reservedByMarket = new HashMap<>();
  private final Map<String, BigDecimal> spentByMarket = new HashMap<>();

  WalletInventory(PolymarketClient client, BigDecimal perBotUsdAllocation) {
    this.client = client;
    this.perBotUsdAllocation = perBotUsdAllocation;
  }

  synchronized AllocationDecision allocate(String marketId) {
    if (reservedByMarket.containsKey(marketId)) {
      return AllocationDecision.ALREADY_ALLOCATED;
    }

    BigDecimal balance;
    try {
      balance = fetchWalletBalanceUsd();
    } catch (Exception e) {
      return AllocationDecision.BALANCE_UNAVAILABLE;
    }

    BigDecimal reserved =
        reservedByMarket.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal available = balance.subtract(reserved);

    if (available.compareTo(perBotUsdAllocation) < 0) {
      return AllocationDecision.INSUFFICIENT_BALANCE;
    }

    reservedByMarket.put(marketId, perBotUsdAllocation);
    return AllocationDecision.ALLOCATED;
  }

  synchronized void release(String marketId) {
    reservedByMarket.remove(marketId);
    spentByMarket.remove(marketId);
  }

  synchronized void releaseAll() {
    reservedByMarket.clear();
    spentByMarket.clear();
  }

  synchronized boolean tryConsumeBudget(String marketId, BigDecimal amountUsd) {
    if (marketId == null || marketId.isBlank()) {
      return false;
    }
    if (amountUsd == null || amountUsd.compareTo(BigDecimal.ZERO) <= 0) {
      return true;
    }

    BigDecimal reserved = reservedByMarket.get(marketId);
    if (reserved == null) {
      return false;
    }

    BigDecimal spent = spentByMarket.getOrDefault(marketId, BigDecimal.ZERO);
    BigDecimal remaining = reserved.subtract(spent);
    if (remaining.compareTo(amountUsd) < 0) {
      return false;
    }

    spentByMarket.put(marketId, spent.add(amountUsd));
    return true;
  }

  synchronized void refundBudget(String marketId, BigDecimal amountUsd) {
    if (marketId == null
        || marketId.isBlank()
        || amountUsd == null
        || amountUsd.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }

    BigDecimal spent = spentByMarket.get(marketId);
    if (spent == null) {
      return;
    }

    BigDecimal updatedSpent = spent.subtract(amountUsd);
    if (updatedSpent.compareTo(BigDecimal.ZERO) <= 0) {
      spentByMarket.remove(marketId);
    } else {
      spentByMarket.put(marketId, updatedSpent);
    }
  }

  synchronized BigDecimal remainingBudget(String marketId) {
    BigDecimal reserved = reservedByMarket.get(marketId);
    if (reserved == null) {
      return BigDecimal.ZERO;
    }
    BigDecimal spent = spentByMarket.getOrDefault(marketId, BigDecimal.ZERO);
    BigDecimal remaining = reserved.subtract(spent);
    return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
  }

  private BigDecimal fetchWalletBalanceUsd() throws IOException {
    BalanceAllowanceResponse response =
        client.getBalanceAllowance(
            BalanceAllowanceParams.builder().assetType(AssetType.COLLATERAL).build());
    return parseAmount(response.getBalance());
  }

  private BigDecimal parseAmount(String amount) {
    if (amount == null || amount.isBlank()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(amount).scaleByPowerOfTen(-6);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Invalid numeric amount from balance endpoint: '" + amount + "'", e);
    }
  }
}
