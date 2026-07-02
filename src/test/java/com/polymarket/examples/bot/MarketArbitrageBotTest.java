package com.polymarket.examples.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.polymarket.ws.model.BookUpdate;
import com.polymarket.ws.model.OrderBookLevel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketArbitrageBotTest {

  @Mock private ExecutionEngine executionEngine;

  @Mock private Consumer<String> onMarketCompleted;

  private MarketArbitrageBot bot;

  private final String marketId = "market-123";
  private final String tokenYes = "token-yes";
  private final String tokenNo = "token-no";

  @BeforeEach
  void setUp() {
    bot =
        new MarketArbitrageBot(
            marketId, Arrays.asList(tokenYes, tokenNo), executionEngine, onMarketCompleted);
    // We don't call bot.start() to avoid actual WebSocket connections.
    // Instead, we push messages directly to the listener interface.
  }

  private BookUpdate createBookUpdate(
      String assetId, String askPrice, String askSize, String bidPrice, String bidSize) {
    BookUpdate update = new BookUpdate();
    update.setAssetId(assetId);

    if (askPrice != null) {
      OrderBookLevel ask = new OrderBookLevel();
      ask.setPrice(askPrice);
      ask.setSize(askSize);
      update.setAsks(Collections.singletonList(ask));
    }

    if (bidPrice != null) {
      OrderBookLevel bid = new OrderBookLevel();
      bid.setPrice(bidPrice);
      bid.setSize(bidSize);
      update.setBids(Collections.singletonList(bid));
    }

    return update;
  }

  @Test
  @SuppressWarnings("unchecked")
  void testArbitrageConditionMet() {
    // YES token ask at 0.45, size 10
    bot.onMessage(createBookUpdate(tokenYes, "0.45", "10.0", "0.40", "100.0"));
    // NO token ask at 0.50, size 20
    bot.onMessage(createBookUpdate(tokenNo, "0.50", "20.0", "0.45", "100.0"));

    // Sum = 0.95 (< 0.99 threshold), Min Size = 10.0 (>= 5.0 threshold)
    ArgumentCaptor<List<ExecutionEngine.TradeInstruction>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(executionEngine, times(1)).executeArbitrage(eq(marketId), captor.capture());

    List<ExecutionEngine.TradeInstruction> instructions = captor.getValue();
    assertEquals(2, instructions.size());

    // Assert instructions contain correct sizes capping at min available size = 10.0
    boolean hasYes =
        instructions.stream()
            .anyMatch(i -> i.tokenId().equals(tokenYes) && i.price() == 0.45 && i.size() == 10.0);
    boolean hasNo =
        instructions.stream()
            .anyMatch(i -> i.tokenId().equals(tokenNo) && i.price() == 0.50 && i.size() == 10.0);

    assertTrue(hasYes, "Missing or incorrect YES token instruction");
    assertTrue(hasNo, "Missing or incorrect NO token instruction");
  }

  @Test
  void testArbitrageConditionNotMet_PriceTooHigh() {
    // YES token ask at 0.51
    bot.onMessage(createBookUpdate(tokenYes, "0.51", "10.0", "0.40", "100.0"));
    // NO token ask at 0.50
    bot.onMessage(createBookUpdate(tokenNo, "0.50", "20.0", "0.45", "100.0"));

    // Sum = 1.01 (>= 0.99 threshold)
    verify(executionEngine, never()).executeArbitrage(eq(marketId), any());
  }

  @Test
  void testArbitrageConditionNotMet_SizeTooSmall() {
    // YES token ask at 0.45, size 4.0 (below 5.0 threshold)
    bot.onMessage(createBookUpdate(tokenYes, "0.45", "4.0", "0.40", "100.0"));
    // NO token ask at 0.50, size 20.0
    bot.onMessage(createBookUpdate(tokenNo, "0.50", "20.0", "0.45", "100.0"));

    // Sum = 0.95, but min size = 4.0 (< 5.0 threshold)
    verify(executionEngine, never()).executeArbitrage(eq(marketId), any());
  }

  @Test
  void testMarketResolvedDetection() {
    // YES token bid hits 0.99
    bot.onMessage(createBookUpdate(tokenYes, "1.00", "10.0", "0.99", "100.0"));

    // Should trigger termination and callback
    verify(onMarketCompleted, times(1)).accept(marketId);
    verify(executionEngine, never()).executeArbitrage(eq(marketId), any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testDynamicOutcomes() {
    // Create a new bot with 3 tokens
    String token3 = "token-maybe";
    MarketArbitrageBot bot3 =
        new MarketArbitrageBot(
            marketId, Arrays.asList(tokenYes, tokenNo, token3), executionEngine, onMarketCompleted);

    // tokenYes ask at 0.30, size 10
    bot3.onMessage(createBookUpdate(tokenYes, "0.30", "10.0", "0.20", "100.0"));
    // tokenNo ask at 0.30, size 20
    bot3.onMessage(createBookUpdate(tokenNo, "0.30", "20.0", "0.25", "100.0"));
    // token3 ask at 0.35, size 15
    bot3.onMessage(createBookUpdate(token3, "0.35", "15.0", "0.30", "100.0"));

    // Sum = 0.95 (< 0.99 threshold), Min Size = 10.0 (>= 5.0 threshold)
    ArgumentCaptor<List<ExecutionEngine.TradeInstruction>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(executionEngine, times(1)).executeArbitrage(eq(marketId), captor.capture());

    List<ExecutionEngine.TradeInstruction> instructions = captor.getValue();
    assertEquals(3, instructions.size());

    boolean hasYes =
        instructions.stream()
            .anyMatch(i -> i.tokenId().equals(tokenYes) && i.price() == 0.30 && i.size() == 10.0);
    boolean hasNo =
        instructions.stream()
            .anyMatch(i -> i.tokenId().equals(tokenNo) && i.price() == 0.30 && i.size() == 10.0);
    boolean hasMaybe =
        instructions.stream()
            .anyMatch(i -> i.tokenId().equals(token3) && i.price() == 0.35 && i.size() == 10.0);

    assertTrue(hasYes, "Missing or incorrect YES token instruction");
    assertTrue(hasNo, "Missing or incorrect NO token instruction");
    assertTrue(hasMaybe, "Missing or incorrect MAYBE token instruction");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testDuplicateTokenIdsAreSkipped() {
    MarketArbitrageBot botWithDuplicateTokens =
        new MarketArbitrageBot(
            marketId,
            Arrays.asList(tokenYes, tokenNo, tokenNo),
            executionEngine,
            onMarketCompleted);

    botWithDuplicateTokens.onMessage(createBookUpdate(tokenYes, "0.45", "10.0", "0.40", "100.0"));
    botWithDuplicateTokens.onMessage(createBookUpdate(tokenNo, "0.50", "10.0", "0.45", "100.0"));

    ArgumentCaptor<List<ExecutionEngine.TradeInstruction>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(executionEngine, times(1)).executeArbitrage(eq(marketId), captor.capture());

    List<ExecutionEngine.TradeInstruction> instructions = captor.getValue();
    assertEquals(2, instructions.size(), "Bot should submit only unique outcome legs");
    assertEquals(
        2,
        instructions.stream().map(ExecutionEngine.TradeInstruction::tokenId).distinct().count(),
        "Instruction token IDs must be unique");
  }
}
