package com.polymarket.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The official taker fee is nonlinear in price. Every expected value here is read from the pinned
 * {@code fees.json}, whose examples carry Polymarket's own published figures.
 */
@DisplayName("Official taker fee formula (issue #11)")
class FeeRateTest {

    private static final JsonNode FEES = load();

    private static JsonNode load() {
        try (InputStream in = FeeRateTest.class.getResourceAsStream("/protocol/fees.json")) {
            return new ObjectMapper().readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("missing protocol fixture", e);
        }
    }

    static List<String> exampleIds() {
        List<String> ids = new ArrayList<>();
        FEES.get("derivedExamples").forEach(e -> ids.add(e.get("id").asText()));
        return ids;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exampleIds")
    @DisplayName("TC-FE-001: the fee matches the pinned example for its category, size and price")
    void feeMatchesThePinnedExample(String id) {
        JsonNode example = example(id);
        FeeRate rate = FeeRate.of(example.get("feeRate").asText());

        PusdAmount fee = rate.feeOn(ShareQuantity.of(example.get("shares").asText()),
                Price.of(example.get("price").asText()));

        assertEquals(new BigDecimal(example.get("feeAtFivePlaces").asText()).stripTrailingZeros(),
                fee.value().stripTrailingZeros(), id);
    }

    @Test
    @DisplayName("TC-FE-002: the fee is symmetric around one half, as published")
    void theFeeIsSymmetricAroundOneHalf() {
        FeeRate crypto = FeeRate.of("0.07");
        ShareQuantity shares = ShareQuantity.of("100");

        assertEquals(crypto.feeOn(shares, Price.of("0.30")),
                crypto.feeOn(shares, Price.of("0.70")));
    }

    @Test
    @DisplayName("TC-FE-003: a CLOB base_fee in basis points is never read as a coefficient")
    void basisPointsAreConvertedNotSubstituted() {
        // GET /fee-rate publishes base_fee as an integer in basis points; the Gamma fee schedule
        // publishes the same coefficient as a decimal. 700 bps is 0.07, not 700.
        assertEquals(FeeRate.of("0.07"), FeeRate.ofBasisPoints(700));
        assertThrows(IllegalArgumentException.class, () -> FeeRate.of("-0.01"));
    }

    private static JsonNode example(String id) {
        for (JsonNode e : FEES.get("derivedExamples")) {
            if (id.equals(e.get("id").asText())) {
                return e;
            }
        }
        throw new IllegalStateException("no example " + id);
    }
}
