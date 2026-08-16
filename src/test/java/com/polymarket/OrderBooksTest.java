package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.OrderBookSnapshot;
import com.polymarket.markets.OrderBooks;
import com.polymarket.markets.Price;
import com.polymarket.markets.PriceLevel;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Order books")
class OrderBooksTest {

    private static final TokenId TOKEN = new TokenId(
            "87782427474688337437235992432444831633336196607441214985368623008271938507042");

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    /** Credential-free: a book read must work with no signing authority at all. */
    private Polymarket sdk() {
        return sdk(ReadRetryPolicy.none());
    }

    private Polymarket sdk(ReadRetryPolicy retries) {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        return Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), retries, d -> {
                }));
    }

    /** Body captured from the official CLOB API on 2026-08-16. */
    private void enqueueFixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/clob/" + name)) {
            server.enqueue(new MockResponse().setBody(new String(in.readAllBytes(),
                    StandardCharsets.UTF_8)));
        }
    }

    @Test
    @DisplayName("TC-OB-001: one book response carries levels, tick, minimum, neg risk, hash and time")
    void oneResponseCarriesEverythingSigningNeeds() throws Exception {
        enqueueFixture("book.json");

        OrderBookSnapshot book;
        try (Polymarket sdk = sdk()) {
            book = sdk.orderBooks().book(TOKEN).orElseThrow();
        }

        assertEquals("0x091cf26c853682dbdc91ee38d63b1838801842992bdd4dc761b39cffc531e106",
                book.conditionId());
        assertEquals(TOKEN, book.asset());
        assertEquals("e2675c6d51cc8c120fc63f8750eac2d046809fca", book.hash());
        assertEquals(Instant.parse("2026-08-16T18:17:23.902Z"), book.observedAt());

        assertEquals(TickSize.of("0.01"), book.rules().tickSize());
        assertEquals(ShareQuantity.of("5"), book.rules().minimumShares());
        assertTrue(book.rules().negativeRisk());
        assertEquals(Price.of("0.060"), book.lastTradePrice());

        assertEquals(49, book.bids().size());
        assertEquals(5, book.asks().size());
        assertEquals(new PriceLevel(Price.of("0.94"), ShareQuantity.of("5388.04")),
                book.bestBid().orElseThrow());
        assertEquals(new PriceLevel(Price.of("0.95"), ShareQuantity.of("11191.12")),
                book.bestAsk().orElseThrow());

        assertEquals("/book?token_id=" + TOKEN.value(), server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-OB-002: shuffled wire levels are sorted numerically before use")
    void levelsAreSortedNumericallyWhateverTheWireOrder() throws Exception {
        // The OpenAPI claims bids descend and asks ascend; the live API sends the exact reverse.
        // Neither ordering may be trusted, and "0.09" sorts before "0.5" only numerically.
        server.enqueue(new MockResponse().setBody(shuffledBook()));

        OrderBookSnapshot book;
        try (Polymarket sdk = sdk()) {
            book = sdk.orderBooks().book(TOKEN).orElseThrow();
        }

        assertEquals(List.of(Price.of("0.9"), Price.of("0.5"), Price.of("0.09"), Price.of("0.05")),
                book.bids().stream().map(PriceLevel::price).toList());
        assertEquals(List.of(Price.of("0.91"), Price.of("0.95"), Price.of("0.99")),
                book.asks().stream().map(PriceLevel::price).toList());
        assertEquals(new PriceLevel(Price.of("0.9"), ShareQuantity.of("2")),
                book.bestBid().orElseThrow());
        assertEquals(new PriceLevel(Price.of("0.91"), ShareQuantity.of("6")),
                book.bestAsk().orElseThrow());
    }

    @Test
    @DisplayName("TC-OB-003: the live fixture itself arrives out of order and is corrected")
    void theLiveWireOrderIsNotTrusted() throws Exception {
        enqueueFixture("book.json");

        OrderBookSnapshot book;
        try (Polymarket sdk = sdk()) {
            book = sdk.orderBooks().book(TOKEN).orElseThrow();
        }

        assertEquals(List.of(Price.of("0.99"), Price.of("0.98"), Price.of("0.97"),
                        Price.of("0.96"), Price.of("0.95")), wireAskPrices());
        assertEquals(List.of(Price.of("0.95"), Price.of("0.96"), Price.of("0.97"),
                        Price.of("0.98"), Price.of("0.99")),
                book.asks().stream().map(PriceLevel::price).toList());
    }

    @Test
    @DisplayName("TC-OB-004: rules are re-read every time; nothing is remembered between reads")
    void rulesAreNeverCached() throws Exception {
        // A market's tick can be widened and neg risk can be switched on. A cached rule would
        // sign the next order against a grid the exchange no longer uses.
        server.enqueue(new MockResponse().setBody(bookWithRules("0.001", "5", false)));
        server.enqueue(new MockResponse().setBody(bookWithRules("0.01", "100", true)));

        MarketRules first;
        MarketRules second;
        try (Polymarket sdk = sdk()) {
            first = sdk.orderBooks().book(TOKEN).orElseThrow().rules();
            second = sdk.orderBooks().book(TOKEN).orElseThrow().rules();
        }

        assertEquals(new MarketRules(TickSize.of("0.001"), ShareQuantity.of("5"), false), first);
        assertEquals(new MarketRules(TickSize.of("0.01"), ShareQuantity.of("100"), true), second);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-OB-005: the book path holds no map to remember a rule in")
    void theBookPathHoldsNoRuleStore() throws Exception {
        for (Class<?> type : List.of(OrderBooks.class,
                Class.forName("com.polymarket.internal.markets.OrderBookGateway"))) {
            for (Field field : type.getDeclaredFields()) {
                assertFalse(Map.class.isAssignableFrom(field.getType()) && !isStatic(field),
                        type.getSimpleName() + "." + field.getName() + " can outlive one read");
            }
        }
    }

    @Test
    @DisplayName("TC-OB-006: the CLOB minimum is shares, and Gamma is never asked")
    void theMinimumComesFromTheBookInShares() throws Exception {
        enqueueFixture("book.json");

        MarketRules rules;
        try (Polymarket sdk = sdk()) {
            rules = sdk.orderBooks().book(TOKEN).orElseThrow().rules();
        }

        // The book says 5 SHARES. Gamma's orderMinSize for the same market is a USDC notional and
        // must never stand in for it, so exactly one request goes out and it is the book read.
        assertEquals(ShareQuantity.of("5"), rules.minimumShares());
        assertEquals(1, server.getRequestCount());
        assertEquals("/book?token_id=" + TOKEN.value(), server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-OB-007: a token the exchange keeps no book for is empty, not an error")
    void anUnknownTokenIsEmpty() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody(
                "{\"error\":\"No orderbook exists for the requested token id\"}"));

        try (Polymarket sdk = sdk()) {
            assertEquals(Optional.empty(), sdk.orderBooks().book(TOKEN));
        }
    }

    @Test
    @DisplayName("TC-OB-008: an empty last trade price stays absent instead of becoming zero")
    void anAbsentLastTradePriceIsNotFabricated() throws Exception {
        // The live API really does send "" on a market that has never traded.
        server.enqueue(new MockResponse().setBody(bookWithRules("0.01", "5", false)
                .replace("\"last_trade_price\":\"0.9\"", "\"last_trade_price\":\"\"")));

        try (Polymarket sdk = sdk()) {
            assertNull(sdk.orderBooks().book(TOKEN).orElseThrow().lastTradePrice());
        }
    }

    @Test
    @DisplayName("TC-OB-009: a batch read is one idempotent GET that retries a transient failure")
    void aBatchReadKeepsReadRetrySemantics() throws Exception {
        // /books also exists as a POST. Routing it through the write path would make a pure read
        // non-retryable, and making writes retryable would let a read budget replay an order.
        TokenId other = new TokenId("112713157523689199379181329313787575968439519861163279"
                + "671688438035881529091971");
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("[" + shuffledBook() + "]"));

        List<OrderBookSnapshot> books;
        try (Polymarket sdk = sdk(ReadRetryPolicy.defaults())) {
            books = sdk.orderBooks().books(List.of(TOKEN, other));
        }

        assertEquals(1, books.size());
        assertEquals(Price.of("0.9"), books.get(0).bestBid().orElseThrow().price());
        assertEquals(2, server.getRequestCount());
        for (int attempt = 0; attempt < 2; attempt++) {
            RecordedRequest request = server.takeRequest();
            assertEquals("GET", request.getMethod());
            assertEquals("/books?token_ids=" + TOKEN.value() + "," + other.value(),
                    request.getPath());
        }
    }

    @Test
    @DisplayName("TC-OB-010: an empty batch asks nothing of the exchange")
    void anEmptyBatchMakesNoRequest() throws Exception {
        try (Polymarket sdk = sdk()) {
            assertEquals(List.of(), sdk.orderBooks().books(List.of()));
        }

        assertEquals(0, server.getRequestCount());
    }

    private static boolean isStatic(Field field) {
        return java.lang.reflect.Modifier.isStatic(field.getModifiers());
    }

    private static String bookWithRules(String tick, String minimum, boolean negativeRisk) {
        return shuffledBook()
                .replace("\"tick_size\":\"0.01\"", "\"tick_size\":\"" + tick + "\"")
                .replace("\"min_order_size\":\"5\"", "\"min_order_size\":\"" + minimum + "\"")
                .replace("\"neg_risk\":false", "\"neg_risk\":" + negativeRisk);
    }

    /** Reads the captured body directly, so the wire order is evidence and not an assumption. */
    private List<Price> wireAskPrices() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/clob/book.json")) {
            List<Price> prices = new ArrayList<>();
            new ObjectMapper().readTree(in).path("asks")
                    .forEach(level -> prices.add(Price.of(level.path("price").asText())));
            return prices;
        }
    }

    /** Deliberately jumbled, with decimal text that a string sort would order wrongly. */
    private static String shuffledBook() {
        return """
                {"market":"0xfeed","asset_id":"%s","timestamp":"1786904243902","hash":"h",
                 "bids":[{"price":"0.09","size":"1"},{"price":"0.9","size":"2"},
                         {"price":"0.05","size":"3"},{"price":"0.5","size":"4"}],
                 "asks":[{"price":"0.95","size":"5"},{"price":"0.91","size":"6"},
                         {"price":"0.99","size":"7"}],
                 "min_order_size":"5","tick_size":"0.01","neg_risk":false,"last_trade_price":"0.9"}
                """.formatted(TOKEN.value());
    }
}
