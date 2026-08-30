package com.polymarket.live;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.polymarket.Polymarket;
import com.polymarket.markets.DiscoveredMarket;
import com.polymarket.markets.MarketOutcome;
import com.polymarket.markets.MarketQuery;
import com.polymarket.markets.OrderBookSnapshot;
import com.polymarket.markets.TokenId;
import com.polymarket.operations.GeoblockStatus;
import com.polymarket.operations.ServerTime;
import com.polymarket.operations.ServiceHealth;
import com.polymarket.streaming.BookEvent;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Opt-in smoke checks against the real production API: {@code mvn -Plive test -Dtest=LiveReadOnlyTest}.
 * Credential-free and read-only by construction — nothing here signs, places, accepts or cancels.
 */
@Tag("live")
@EnabledIfSystemProperty(named = "polymarket.live", matches = "true")
@DisplayName("Live read-only smoke checks against production Polymarket")
class LiveReadOnlyTest {

    private static final Duration STREAM_WAIT = Duration.ofSeconds(30);

    @Test
    @DisplayName("TC-LV-001: GET /time returns a clock within a day of ours")
    void serverTimeIsSane() throws IOException {
        try (Polymarket polymarket = Polymarket.withDefaults()) {
            ServerTime time = polymarket.serverTime();

            Duration skew = Duration.between(time.at(), Instant.now()).abs();
            assertTrue(skew.compareTo(Duration.ofDays(1)) < 0, "implausible server time: " + time.at());
        }
    }

    @Test
    @DisplayName("TC-LV-002: every probed service reports itself available")
    void everyServiceIsHealthy() {
        try (Polymarket polymarket = Polymarket.withDefaults()) {
            List<ServiceHealth> health = polymarket.health();

            assertFalse(health.isEmpty(), "health() probed nothing");
            health.forEach(service -> assertTrue(service.available(),
                    service.service() + " is down: " + service.detail().orElse("no detail")));
        }
    }

    @Test
    @DisplayName("TC-LV-003: the geoblock endpoint answers with a decodable status")
    void geoblockAnswers() throws IOException {
        try (Polymarket polymarket = Polymarket.withDefaults()) {
            // Blocked or not depends on where this runs, so only the shape is asserted.
            GeoblockStatus status = polymarket.geoblock();

            assertNotNull(status);
        }
    }

    @Test
    @DisplayName("TC-LV-004: Gamma returns open markets carrying CLOB token ids")
    void marketDiscoveryReturnsTradeableMarkets() throws IOException {
        try (Polymarket polymarket = Polymarket.withDefaults()) {
            List<DiscoveredMarket> markets =
                    polymarket.markets().markets(MarketQuery.create().limit(20).closed(false));

            assertFalse(markets.isEmpty(), "Gamma returned no open markets");
            assertTrue(markets.stream().anyMatch(m -> !m.outcomes().isEmpty()),
                    "no discovered market carried an outcome");
        }
    }

    @Test
    @DisplayName("TC-LV-005: GET /book supplies the full signing rule set for a live token")
    void orderBookCarriesSigningRules() throws IOException {
        try (Polymarket polymarket = Polymarket.withDefaults()) {
            TokenId token = anyLiveToken(polymarket);

            OrderBookSnapshot book = polymarket.orderBooks().book(token)
                    .orElseThrow(() -> new AssertionError("no book for live token " + token.value()));

            assertNotNull(book.rules().tickSize());
            assertNotNull(book.rules().minimumShares());
            assertNotNull(book.observedAt());
        }
    }

    @Test
    @DisplayName("TC-LV-006: the market stream connects and delivers a book snapshot")
    void marketStreamDeliversASnapshot() throws Exception {
        try (Polymarket polymarket = Polymarket.withDefaults()) {
            TokenId token = anyLiveToken(polymarket);
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<BookEvent> first = new AtomicReference<>();

            polymarket.streaming().onBookUpdate(List.of(token.value()), event -> {
                first.compareAndSet(null, event);
                received.countDown();
            });
            polymarket.streaming().subscribeMarket(List.of(token.value()));

            assertTrue(received.await(STREAM_WAIT.toSeconds(), TimeUnit.SECONDS),
                    "no book event within " + STREAM_WAIT);
            assertNotNull(first.get().assetId());
        }
    }

    /** First CLOB token id an open market publishes; skips rather than fails if Gamma has none. */
    private static TokenId anyLiveToken(Polymarket polymarket) throws IOException {
        Optional<String> tokenId =
                polymarket.markets().markets(MarketQuery.create().limit(50).closed(false)).stream()
                        .flatMap(market -> market.outcomes().stream())
                        .map(MarketOutcome::tokenId)
                        .flatMap(Optional::stream)
                        .filter(id -> !id.isBlank())
                        .findFirst();
        assumeTrue(tokenId.isPresent(), "no open market published a CLOB token id");
        return new TokenId(tokenId.get());
    }
}
