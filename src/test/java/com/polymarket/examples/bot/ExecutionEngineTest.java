package com.polymarket.examples.bot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.BalanceAllowanceResponse;
import com.polymarket.model.OpenOrder;
import com.polymarket.model.OrderResponse;
import com.polymarket.model.OrderStatusType;
import com.polymarket.model.OrderType;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionEngineTest {

  @Mock private AsyncPolymarketClient asyncClient;
  @Mock private PolymarketClient syncClient;

  private ExecutionEngine engine;

  @BeforeEach
  void setUp() {
    engine = new ExecutionEngine(asyncClient);
  }

  @Test
  void testConcurrentFokOrders() {
    doReturn(CompletableFuture.completedFuture(List.of()))
        .when(asyncClient)
        .createAndPostOrders(anyList(), eq(OrderType.FOK));

    List<ExecutionEngine.TradeInstruction> instructions =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenA", 0.40, 100),
            new ExecutionEngine.TradeInstruction("tokenB", 0.50, 100));

    engine.executeArbitrage(instructions);

    verify(asyncClient, times(1)).createAndPostOrders(anyList(), eq(OrderType.FOK));
  }

  @Test
  void testExecutionLocking() {
    CompletableFuture<List<com.polymarket.model.OrderResponse>> delayedFuture =
        new CompletableFuture<>();

    // Keep first execution in-flight to assert locking behavior.
    doReturn(delayedFuture).when(asyncClient).createAndPostOrders(anyList(), eq(OrderType.FOK));

    List<ExecutionEngine.TradeInstruction> instructions1 =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenA", 0.40, 100),
            new ExecutionEngine.TradeInstruction("tokenB", 0.50, 100));

    List<ExecutionEngine.TradeInstruction> instructions2 =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenC", 0.30, 50),
            new ExecutionEngine.TradeInstruction("tokenD", 0.60, 50));

    // First trigger should execute
    engine.executeArbitrage(instructions1);
    verify(asyncClient, times(1)).createAndPostOrders(anyList(), eq(OrderType.FOK));

    // Second trigger should hit the lock and be ignored
    engine.executeArbitrage(instructions2);
    verify(asyncClient, times(1)).createAndPostOrders(anyList(), eq(OrderType.FOK));

    // Complete the first execution batch
    delayedFuture.complete(List.of());

    // The lock should now be released. Third trigger should execute
    engine.executeArbitrage(instructions2);
    verify(asyncClient, times(2)).createAndPostOrders(anyList(), eq(OrderType.FOK));
  }

  @Test
  void testBudgetCapBlocksOverspendForSingleBot() throws Exception {
    WalletInventory inventory = new WalletInventory(syncClient, new BigDecimal("5"));
    doReturn(BalanceAllowanceResponse.builder().balance("100000000").allowance("0").build())
        .when(syncClient)
        .getBalanceAllowance(any());
    inventory.allocate("m1");

    ExecutionEngine budgetedEngine = new ExecutionEngine(asyncClient, inventory);

    List<ExecutionEngine.TradeInstruction> instructions =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenA", 0.40, 10),
            new ExecutionEngine.TradeInstruction("tokenB", 0.20, 10));

    // First batch costs 6 USDC total => above per-bot budget of 5, should be skipped.
    budgetedEngine.executeArbitrage("m1", instructions);

    verify(asyncClient, never()).createAndPostOrders(anyList(), eq(OrderType.FOK));
  }

  @Test
  void testRefundFromFailedLegsAllowsNextExecutionWithinBudget() throws Exception {
    WalletInventory inventory = new WalletInventory(syncClient, new BigDecimal("5"));
    doReturn(BalanceAllowanceResponse.builder().balance("100000000").allowance("0").build())
        .when(syncClient)
        .getBalanceAllowance(any());
    inventory.allocate("m1");

    ExecutionEngine budgetedEngine = new ExecutionEngine(asyncClient, inventory);

    List<ExecutionEngine.TradeInstruction> firstBatch =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenA", 0.30, 5), // 1.5
            new ExecutionEngine.TradeInstruction("tokenB", 0.50, 5)); // 2.5 => requested 4.0

    List<OrderResponse> mixedResponses =
        Arrays.asList(
            OrderResponse.builder().success(true).orderID("o1").status("matched").build(),
            OrderResponse.builder().success(false).orderID("o2").status("rejected").build());

    doReturn(CompletableFuture.completedFuture(mixedResponses))
        .doReturn(CompletableFuture.completedFuture(List.of()))
        .when(asyncClient)
        .createAndPostOrders(anyList(), eq(OrderType.FOK));

    budgetedEngine.executeArbitrage("m1", firstBatch);

    // Spent should be 1.5 after refund from failed leg; remaining should be 3.5.
    List<ExecutionEngine.TradeInstruction> secondBatch =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenC", 0.35, 5), // 1.75
            new ExecutionEngine.TradeInstruction("tokenD", 0.35, 5)); // 1.75 => 3.5

    budgetedEngine.executeArbitrage("m1", secondBatch);

    verify(asyncClient, times(2)).createAndPostOrders(anyList(), eq(OrderType.FOK));
  }

  @Test
  @DisplayName("TC-EE-005: Delayed status should NOT be treated as confirmed fill")
  void testDelayedStatusNotTreatedAsConfirmedFill() throws Exception {
    WalletInventory inventory = new WalletInventory(syncClient, new BigDecimal("10"));
    doReturn(BalanceAllowanceResponse.builder().balance("100000000").allowance("0").build())
        .when(syncClient)
        .getBalanceAllowance(any());
    inventory.allocate("m1");

    ExecutionEngine budgetedEngine = new ExecutionEngine(asyncClient, inventory);

    List<ExecutionEngine.TradeInstruction> batch =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenA", 0.54, 5), // 2.7
            new ExecutionEngine.TradeInstruction("tokenB", 0.45, 5)); // 2.25 => requested 4.95

    // Both legs return success=true but status=delayed (the bug scenario)
    List<OrderResponse> delayedResponses =
        Arrays.asList(
            OrderResponse.builder().success(true).orderID("o1").status("delayed").build(),
            OrderResponse.builder().success(true).orderID("o2").status("delayed").build());

    doReturn(CompletableFuture.completedFuture(delayedResponses))
        .when(asyncClient)
        .createAndPostOrders(anyList(), eq(OrderType.FOK));

    // Polling should resolve both as CANCELED (simulating FOK order that couldn't fill)
    OpenOrder cancelledOrder =
        OpenOrder.builder()
            .id("o1")
            .status(OrderStatusType.CANCELED)
            .sizeMatched("0")
            .build();

    doReturn(CompletableFuture.completedFuture(cancelledOrder))
        .when(asyncClient)
        .getOrder(any());

    budgetedEngine.executeArbitrage("m1", batch);

    // Both delayed legs should have been polled
    verify(asyncClient, times(2)).getOrder(any());

    // Budget should be fully refunded since no legs were confirmed filled.
    // A second batch with the full budget should be possible.
    List<ExecutionEngine.TradeInstruction> secondBatch =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenC", 0.40, 5),
            new ExecutionEngine.TradeInstruction("tokenD", 0.40, 5)); // 4.0

    List<OrderResponse> matchedResponses =
        Arrays.asList(
            OrderResponse.builder().success(true).orderID("o3").status("matched").build(),
            OrderResponse.builder().success(true).orderID("o4").status("matched").build());

    doReturn(CompletableFuture.completedFuture(matchedResponses))
        .when(asyncClient)
        .createAndPostOrders(anyList(), eq(OrderType.FOK));

    budgetedEngine.executeArbitrage("m1", secondBatch);

    verify(asyncClient, times(2)).createAndPostOrders(anyList(), eq(OrderType.FOK));
  }

  @Test
  @DisplayName("TC-EE-006: Mixed matched/delayed with one confirmed and one cancelled")
  void testMixedMatchedDelayedBatch() throws Exception {
    List<ExecutionEngine.TradeInstruction> batch =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenA", 0.54, 5),
            new ExecutionEngine.TradeInstruction("tokenB", 0.45, 5));

    // Leg 0 matched immediately, Leg 1 delayed
    List<OrderResponse> mixedResponses =
        Arrays.asList(
            OrderResponse.builder().success(true).orderID("o1").status("matched").build(),
            OrderResponse.builder().success(true).orderID("o2").status("delayed").build());

    doReturn(CompletableFuture.completedFuture(mixedResponses))
        .when(asyncClient)
        .createAndPostOrders(anyList(), eq(OrderType.FOK));

    // Polling resolves delayed leg as CANCELED
    OpenOrder cancelledOrder =
        OpenOrder.builder()
            .id("o2")
            .status(OrderStatusType.CANCELED)
            .sizeMatched("0")
            .build();

    doReturn(CompletableFuture.completedFuture(cancelledOrder))
        .when(asyncClient)
        .getOrder("o2");

    engine.executeArbitrage(batch);

    // Should poll the delayed leg
    verify(asyncClient, times(1)).getOrder("o2");
    // Should NOT poll the matched leg
    verify(asyncClient, never()).getOrder("o1");
  }

  @Test
  @DisplayName("TC-EE-007: Delayed order confirmed as MATCHED after polling")
  void testDelayedOrderConfirmedAfterPolling() throws Exception {
    List<ExecutionEngine.TradeInstruction> batch =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenA", 0.54, 5),
            new ExecutionEngine.TradeInstruction("tokenB", 0.45, 5));

    // Both delayed initially
    List<OrderResponse> delayedResponses =
        Arrays.asList(
            OrderResponse.builder().success(true).orderID("o1").status("delayed").build(),
            OrderResponse.builder().success(true).orderID("o2").status("delayed").build());

    doReturn(CompletableFuture.completedFuture(delayedResponses))
        .when(asyncClient)
        .createAndPostOrders(anyList(), eq(OrderType.FOK));

    // Polling confirms both as MATCHED
    OpenOrder matchedOrder1 =
        OpenOrder.builder()
            .id("o1")
            .status(OrderStatusType.MATCHED)
            .sizeMatched("5000000")
            .build();
    OpenOrder matchedOrder2 =
        OpenOrder.builder()
            .id("o2")
            .status(OrderStatusType.MATCHED)
            .sizeMatched("5000000")
            .build();

    doReturn(CompletableFuture.completedFuture(matchedOrder1))
        .when(asyncClient)
        .getOrder("o1");
    doReturn(CompletableFuture.completedFuture(matchedOrder2))
        .when(asyncClient)
        .getOrder("o2");

    engine.executeArbitrage(batch);

    // Both should be polled
    verify(asyncClient, times(1)).getOrder("o1");
    verify(asyncClient, times(1)).getOrder("o2");
  }

  @Test
  @DisplayName("TC-EE-008: Partial fill triggers callback with correct filled tokens")
  void testPartialFillTriggersCallback() {
    List<ExecutionEngine.TradeInstruction> batch =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenA", 0.40, 10),
            new ExecutionEngine.TradeInstruction("tokenB", 0.50, 10));

    List<OrderResponse> partialResponses =
        Arrays.asList(
            OrderResponse.builder().success(true).orderID("o1").status("matched").build(),
            OrderResponse.builder().success(false).orderID("o2").status("rejected").build());

    doReturn(CompletableFuture.completedFuture(partialResponses))
        .when(asyncClient)
        .createAndPostOrders(anyList(), eq(OrderType.FOK));

    ExecutionEngine.PartialFillCallback callback = mock(ExecutionEngine.PartialFillCallback.class);
    engine.setPartialFillCallback(callback);

    engine.executeArbitrage(batch);

    verify(callback, times(1)).onPartialFill(eq(List.of("tokenA")), eq(null), eq(List.of(10.0)));
  }

  @Test
  @DisplayName("TC-EE-009: Sell position sends SELL order at current bid price")
  void testSellPositionSendsSellOrder() {
    doReturn(CompletableFuture.completedFuture(List.of()))
        .when(asyncClient)
        .createAndPostOrders(anyList(), eq(OrderType.FOK));

    engine.sellPosition("market-1", "token-yes", 10.0, 0.42);

    verify(asyncClient, times(1))
        .createAndPostOrders(
            argThat(
                orders ->
                    orders.size() == 1
                        && orders.get(0).side() == com.polymarket.model.Side.SELL
                        && orders.get(0).tokenID().equals("token-yes")
                        && orders.get(0).price().doubleValue() == 0.42
                        && orders.get(0).size().doubleValue() == 10.0),
            eq(OrderType.FOK));
  }

  @Test
  @DisplayName("TC-EE-010: No partial fill callback on full match")
  void testNoPartialFillCallbackOnFullMatch() {
    List<ExecutionEngine.TradeInstruction> batch =
        Arrays.asList(
            new ExecutionEngine.TradeInstruction("tokenA", 0.40, 10),
            new ExecutionEngine.TradeInstruction("tokenB", 0.50, 10));

    List<OrderResponse> fullMatchResponses =
        Arrays.asList(
            OrderResponse.builder().success(true).orderID("o1").status("matched").build(),
            OrderResponse.builder().success(true).orderID("o2").status("matched").build());

    doReturn(CompletableFuture.completedFuture(fullMatchResponses))
        .when(asyncClient)
        .createAndPostOrders(anyList(), eq(OrderType.FOK));

    ExecutionEngine.PartialFillCallback callback = mock(ExecutionEngine.PartialFillCallback.class);
    engine.setPartialFillCallback(callback);

    engine.executeArbitrage(batch);

    verify(callback, never()).onPartialFill(any(), any(), any());
  }
}
