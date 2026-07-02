package com.polymarket.examples.bot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

import com.polymarket.client.AsyncPolymarketClient;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.BalanceAllowanceResponse;
import com.polymarket.model.GammaMarket;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArbitrageOrchestratorTest {

  @Mock private PolymarketClient syncClient;

  @Mock private AsyncPolymarketClient asyncClient;

  private ArbitrageOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator = new ArbitrageOrchestrator(syncClient, asyncClient);
  }

  @AfterEach
  void tearDown() {
    orchestrator.stop();
  }

  private GammaMarket createMarket(
      String id, String volume, Boolean enableOrderBook, List<String> tokens) {
    return new GammaMarket(
        id,
        "Question for " + id,
        "2025-01-01",
        volume != null ? new BigDecimal(volume) : null,
        true, // acceptingOrders
        true, // active
        false, // closed
        enableOrderBook,
        tokens,
        Arrays.asList("Yes", "No"),
        Arrays.asList("0.5", "0.5"));
  }

  @Test
  void testMarketFiltering() throws Exception {
    // Create a mix of valid and invalid markets
    GammaMarket validMarket =
        createMarket("valid-market", "60000", true, Arrays.asList("tokenA", "tokenB"));
    GammaMarket lowVolume =
        createMarket("low-vol-market", "1000", true, Arrays.asList("tokenC", "tokenD"));
    GammaMarket noOrderBook =
        createMarket("no-ob-market", "100000", false, Arrays.asList("tokenE", "tokenF"));
    GammaMarket noTokens =
        createMarket("no-tokens-market", "100000", true, Collections.emptyList());

    when(syncClient.getGammaMarkets(anyMap()))
        .thenReturn(Arrays.asList(validMarket, lowVolume, noOrderBook, noTokens));
    when(syncClient.getBalanceAllowance(any()))
        .thenReturn(
            BalanceAllowanceResponse.builder().balance("1000000000").allowance("0").build());

    // Start orchestrator, which triggers the first poll immediately on a separate thread
    orchestrator.start();

    // Give the scheduled thread a moment to execute the first poll
    Thread.sleep(500);

    // Verify that only the valid market resulted in a spawned bot
    assertEquals(1, orchestrator.getActiveBots().size(), "Only one valid market should be tracked");
    assertTrue(
        orchestrator.getActiveBots().containsKey("valid-market"),
        "The valid market bot should exist");
  }

  @Test
  void testBotCleanup() throws Exception {
    GammaMarket validMarket =
        createMarket("valid-cleanup", "80000", true, Arrays.asList("t1", "t2"));

    when(syncClient.getGammaMarkets(anyMap())).thenReturn(Collections.singletonList(validMarket));
    when(syncClient.getBalanceAllowance(any()))
        .thenReturn(
            BalanceAllowanceResponse.builder().balance("1000000000").allowance("0").build());

    // Start orchestrator and let it discover the market
    orchestrator.start();
    Thread.sleep(500);

    assertEquals(1, orchestrator.getActiveBots().size());

    // Simulate the market completion callback (e.g. from the bot detecting prices hitting 1.0)
    orchestrator.onMarketCompleted("valid-cleanup");

    // Verify the bot is successfully cleaned up from the tracking map
    assertEquals(0, orchestrator.getActiveBots().size(), "Bot should be removed after completion");
  }

  @Test
  void testStopsSpawningWhenBalanceInsufficient() throws Exception {
    GammaMarket m1 = createMarket("m1", "90000", true, Arrays.asList("a1", "a2"));
    GammaMarket m2 = createMarket("m2", "88000", true, Arrays.asList("b1", "b2"));
    GammaMarket m3 = createMarket("m3", "87000", true, Arrays.asList("c1", "c2"));

    when(syncClient.getGammaMarkets(anyMap())).thenReturn(Arrays.asList(m1, m2, m3));
    // 6 USDC total => one 5 USDC allocation succeeds, next allocation fails and scan stops.
    when(syncClient.getBalanceAllowance(any()))
        .thenReturn(BalanceAllowanceResponse.builder().balance("6000000").allowance("0").build());

    orchestrator.start();
    Thread.sleep(500);

    assertEquals(1, orchestrator.getActiveBots().size());
    assertTrue(orchestrator.getActiveBots().containsKey("m1"));
    assertFalse(orchestrator.getActiveBots().containsKey("m2"));
    assertFalse(orchestrator.getActiveBots().containsKey("m3"));
  }
}
