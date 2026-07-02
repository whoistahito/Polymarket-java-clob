package com.polymarket.rtds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.client.ApiKeyCreds;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Mirrors the Rust SDK {@code rtds::types::request} serialization tests. */
class SubscriptionTest {

    private static String subscribe(Subscription s) {
        return Subscription.requestJson("subscribe", List.of(s));
    }

    @Test
    @DisplayName("TC-RTDS-001 crypto_prices filters serialize as a raw JSON array")
    void cryptoPricesFiltersAreJsonArray() {
        String json = subscribe(Subscription.cryptoPrices(List.of("btcusdt", "ethusdt")));
        assertTrue(json.contains("\"action\":\"subscribe\""));
        assertTrue(json.contains("\"topic\":\"crypto_prices\""));
        assertTrue(json.contains("\"type\":\"update\""));
        assertTrue(json.contains("\"filters\":[\"btcusdt\",\"ethusdt\"]"), json);
    }

    @Test
    @DisplayName("TC-RTDS-002 crypto_prices without symbols omits filters")
    void cryptoPricesWithoutSymbolsOmitsFilters() {
        assertFalse(subscribe(Subscription.cryptoPrices(null)).contains("\"filters\""));
        assertFalse(subscribe(Subscription.cryptoPrices(List.of())).contains("\"filters\""));
    }

    @Test
    @DisplayName("TC-RTDS-003 chainlink filters serialize as an escaped JSON string")
    void chainlinkFiltersAreEscapedString() {
        String json = subscribe(Subscription.chainlinkPrices("eth/usd"));
        assertTrue(json.contains("\"topic\":\"crypto_prices_chainlink\""));
        assertTrue(json.contains("\"type\":\"*\""));
        assertTrue(json.contains("\"filters\":\"{\\\"symbol\\\":\\\"eth/usd\\\"}\""), json);
    }

    @Test
    @DisplayName("TC-RTDS-004 chainlink without symbol omits filters")
    void chainlinkWithoutSymbolOmitsFilters() {
        String json = subscribe(Subscription.chainlinkPrices(null));
        assertTrue(json.contains("\"topic\":\"crypto_prices_chainlink\""));
        assertFalse(json.contains("\"filters\""));
    }

    @Test
    @DisplayName("TC-RTDS-005 comments use snake_case type")
    void commentsSnakeCaseType() {
        String json = subscribe(Subscription.comments(CommentType.COMMENT_CREATED));
        assertTrue(json.contains("\"topic\":\"comments\""));
        assertTrue(json.contains("\"type\":\"comment_created\""));
    }

    @Test
    @DisplayName("TC-RTDS-006 comments with null type wildcard")
    void commentsWildcard() {
        assertTrue(subscribe(Subscription.comments(null)).contains("\"type\":\"*\""));
    }

    @Test
    @DisplayName("TC-RTDS-007 mixed chainlink + binance serialize differently in one request")
    void mixedSubscriptions() {
        String json = Subscription.requestJson("subscribe", List.of(
            Subscription.chainlinkPrices("btc/usd"),
            Subscription.cryptoPrices(List.of("btcusdt", "ethusdt"))
        ));
        assertTrue(json.contains("\"filters\":\"{\\\"symbol\\\":\\\"btc/usd\\\"}\""), json);
        assertTrue(json.contains("\"filters\":[\"btcusdt\",\"ethusdt\"]"), json);
    }

    @Test
    @DisplayName("TC-RTDS-008 unsubscribe action")
    void unsubscribeAction() {
        String json = Subscription.requestJson("unsubscribe",
            List.of(Subscription.cryptoPrices(List.of("btcusdt"))));
        assertTrue(json.contains("\"action\":\"unsubscribe\""));
        assertTrue(json.contains("\"topic\":\"crypto_prices\""));
    }

    @Test
    @DisplayName("TC-RTDS-009 clob_auth embedded for authenticated comments")
    void clobAuthEmbedded() {
        Subscription s = Subscription.comments(CommentType.COMMENT_CREATED)
            .withClobAuth(new ApiKeyCreds("k", "s", "p"));
        String json = subscribe(s);
        assertTrue(json.contains("\"clob_auth\""), json);
        assertTrue(json.contains("\"key\":\"k\""));
        assertTrue(json.contains("\"secret\":\"s\""));
        assertTrue(json.contains("\"passphrase\":\"p\""));
    }

    @Test
    @DisplayName("TC-RTDS-010 no clob_auth field when unauthenticated")
    void noClobAuthWhenUnauthenticated() {
        assertFalse(subscribe(Subscription.comments(null)).contains("clob_auth"));
    }

    @Test
    @DisplayName("TC-RTDS-011 comment type wire values")
    void commentTypeWireValues() {
        assertEquals("comment_created", CommentType.COMMENT_CREATED.wireValue());
        assertEquals("reaction_removed", CommentType.REACTION_REMOVED.wireValue());
    }
}
