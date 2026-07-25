package com.polymarket.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.model.gamma.GammaMarketDetail;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ticket 024 — typed, exact market rules.
 *
 * <p>The tick and minimum are money rules: routing them through {@code double} loses the exact
 * decimal the exchange published, and reading them out of an untyped map forces every caller to
 * re-derive the field names. These tests pin exactness and nullability.
 */
@DisplayName("TC-MRU — typed market rules (Ticket 024)")
class MarketRulesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("TC-MRU-001 a minimum of 5.0000000000000001 survives deserialization exactly")
    void exactMinimumPreserved() throws Exception {
        MarketRules rules = MAPPER.readValue(
            "{\"minimum_tick_size\":\"0.01\",\"minimum_order_size\":5.0000000000000001}",
            MarketRules.class);

        assertEquals(new BigDecimal("5.0000000000000001"), rules.orderMinSize());
        // The value is beyond double precision — a double round trip would collapse it to 5.
        assertTrue(rules.orderMinSize().compareTo(new BigDecimal("5")) > 0);
    }

    @Test
    @DisplayName("TC-MRU-002 tick 0.0025 survives deserialization exactly")
    void exactQuarterCentTick() throws Exception {
        MarketRules rules = MAPPER.readValue(
            "{\"minimum_tick_size\":0.0025,\"minimum_order_size\":5}", MarketRules.class);

        assertEquals(0, new BigDecimal("0.0025").compareTo(rules.orderPriceMinTickSize()));
        assertEquals("0.0025", rules.orderPriceMinTickSize().toPlainString());
    }

    @Test
    @DisplayName("TC-MRU-003 string-encoded rules deserialize exactly too")
    void stringEncodedRules() throws Exception {
        MarketRules rules = MAPPER.readValue(
            "{\"minimum_tick_size\":\"0.005\",\"minimum_order_size\":\"10.005\"}", MarketRules.class);

        assertEquals(new BigDecimal("0.005"), rules.orderPriceMinTickSize());
        assertEquals(new BigDecimal("10.005"), rules.orderMinSize());
        assertTrue(rules.isComplete());
    }

    @Test
    @DisplayName("TC-MRU-004 missing rules stay null so callers can fail closed")
    void missingRulesRemainNull() throws Exception {
        MarketRules rules = MAPPER.readValue("{\"condition_id\":\"0xabc\"}", MarketRules.class);

        assertNull(rules.orderPriceMinTickSize());
        assertNull(rules.orderMinSize());
        assertFalse(rules.isComplete());
    }

    @Test
    @DisplayName("TC-MRU-005 explicitly null rules stay null")
    void explicitNullRulesRemainNull() throws Exception {
        MarketRules rules = MAPPER.readValue(
            "{\"minimum_tick_size\":null,\"minimum_order_size\":null}", MarketRules.class);

        assertNull(rules.orderPriceMinTickSize());
        assertNull(rules.orderMinSize());
        assertFalse(rules.isComplete());
    }

    @Test
    @DisplayName("TC-MRU-006 one present rule is not a complete rule set")
    void partialRulesAreIncomplete() throws Exception {
        assertFalse(MAPPER.readValue("{\"minimum_tick_size\":\"0.01\"}", MarketRules.class)
            .isComplete());
        assertFalse(MAPPER.readValue("{\"minimum_order_size\":\"5\"}", MarketRules.class)
            .isComplete());
    }

    @Test
    @DisplayName("TC-MRU-007 the abbreviated clob-markets field names resolve to the same rules")
    void abbreviatedClobFieldNames() throws Exception {
        MarketRules rules = MAPPER.readValue("{\"mts\":0.0025,\"mos\":5}", MarketRules.class);

        assertEquals(0, new BigDecimal("0.0025").compareTo(rules.orderPriceMinTickSize()));
        assertEquals(0, new BigDecimal("5").compareTo(rules.orderMinSize()));
    }

    @Test
    @DisplayName("TC-MRU-008 the Gamma camelCase field names resolve to the same rules")
    void gammaFieldNames() throws Exception {
        MarketRules rules = MAPPER.readValue(
            "{\"orderPriceMinTickSize\":0.001,\"orderMinSize\":5}", MarketRules.class);

        assertEquals(0, new BigDecimal("0.001").compareTo(rules.orderPriceMinTickSize()));
        assertEquals(0, new BigDecimal("5").compareTo(rules.orderMinSize()));
    }

    @Test
    @DisplayName("TC-MRU-009 out-of-range rules are reported as invalid, not silently accepted")
    void outOfRangeRulesInvalid() throws Exception {
        assertFalse(MAPPER.readValue("{\"minimum_tick_size\":0,\"minimum_order_size\":5}",
            MarketRules.class).isValid());
        assertFalse(MAPPER.readValue("{\"minimum_tick_size\":1,\"minimum_order_size\":5}",
            MarketRules.class).isValid());
        assertFalse(MAPPER.readValue("{\"minimum_tick_size\":0.01,\"minimum_order_size\":0}",
            MarketRules.class).isValid());
        assertTrue(MAPPER.readValue("{\"minimum_tick_size\":0.01,\"minimum_order_size\":5}",
            MarketRules.class).isValid());
    }

    @Test
    @DisplayName("TC-MRU-010 GammaMarketDetail exposes the rules as exact BigDecimals")
    void gammaMarketDetailCarriesRules() throws Exception {
        GammaMarketDetail detail = MAPPER.readValue(
            "{\"id\":\"1\",\"orderPriceMinTickSize\":0.0025,\"orderMinSize\":5.0000000000000001}",
            GammaMarketDetail.class);

        assertEquals("0.0025", detail.orderPriceMinTickSize().toPlainString());
        assertEquals(new BigDecimal("5.0000000000000001"), detail.orderMinSize());
        assertEquals(new BigDecimal("5.0000000000000001"), detail.marketRules().orderMinSize());
    }

    @Test
    @DisplayName("TC-MRU-011 GammaMarket exposes the rules as exact BigDecimals")
    void gammaMarketCarriesRules() throws Exception {
        GammaMarket market = MAPPER.readValue(
            "{\"id\":\"1\",\"orderPriceMinTickSize\":0.005,\"orderMinSize\":\"10.005\"}",
            GammaMarket.class);

        assertEquals("0.005", market.orderPriceMinTickSize().toPlainString());
        assertEquals(new BigDecimal("10.005"), market.getOrderMinSize());
        assertTrue(market.marketRules().isComplete());
    }

    @Test
    @DisplayName("TC-MRU-012 a market without rules yields an incomplete rule set, not defaults")
    void gammaMarketWithoutRules() throws Exception {
        GammaMarket market = MAPPER.readValue("{\"id\":\"1\"}", GammaMarket.class);

        assertNull(market.orderPriceMinTickSize());
        assertNull(market.orderMinSize());
        assertFalse(market.marketRules().isComplete());
    }
}
