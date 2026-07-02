package com.polymarket.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GammaMarket DTO helpers")
class GammaMarketTest {

  @Test
  @DisplayName("TC-GM-001: YES/NO token ids are resolved by matching outcomes to token ids")
  void resolvesYesNoTokenIds() {
    GammaMarket market =
        new GammaMarket(
            "m1",
            "Will it rain?",
            "2026-01-01T00:00:00Z",
            null,
            true,
            true,
            false,
            true,
            List.of("token-yes", "token-no"),
            List.of("Yes", "No"),
            List.of("0.55", "0.45"));

    assertEquals("token-yes", market.getYesTokenId());
    assertEquals("token-no", market.getNoTokenId());
  }

  @Test
  @DisplayName("TC-GM-002: YES/NO matching is case-insensitive and trims whitespace")
  void resolvesYesNoTokenIdsCaseInsensitive() {
    GammaMarket market =
        new GammaMarket(
            "m2",
            "Question",
            null,
            null,
            null,
            null,
            null,
            null,
            List.of("id-a", "id-b"),
            List.of(" yes ", "NO"),
            null);

    assertEquals("id-a", market.getYesTokenId());
    assertEquals("id-b", market.getNoTokenId());
    assertEquals("id-a", market.getTokenIdByOutcome(BinaryOutcome.YES).orElseThrow());
    assertEquals("id-b", market.getTokenIdByOutcome(BinaryOutcome.NO).orElseThrow());
  }

  @Test
  @DisplayName("TC-GM-003: returns null YES/NO token ids when outcomes are missing or not mapped")
  void returnsNullWhenOutcomesUnavailable() {
    GammaMarket market =
        new GammaMarket(
            "m3",
            "Question",
            null,
            null,
            null,
            null,
            null,
            null,
            List.of("token-only"),
            List.of("Maybe"),
            null);

    assertNull(market.getYesTokenId());
    assertNull(market.getNoTokenId());
    assertTrue(market.getTokenIdByOutcome(BinaryOutcome.YES).isEmpty());
    assertTrue(market.getTokenIdByOutcome(BinaryOutcome.NO).isEmpty());
  }

  @Test
  @DisplayName("TC-GM-004: JavaBean get accessors expose non-null list views")
  void javaBeanListAccessorsAreNonNull() {
    GammaMarket market =
        new GammaMarket("m4", "Question", null, null, null, null, null, null, null, null, null);

    assertNotNull(market.getTokenIds());
    assertNotNull(market.getOutcomes());
    assertNotNull(market.getOutcomePrices());
    assertTrue(market.getTokenIds().isEmpty());
    assertTrue(market.getOutcomes().isEmpty());
    assertTrue(market.getOutcomePrices().isEmpty());
  }
}
