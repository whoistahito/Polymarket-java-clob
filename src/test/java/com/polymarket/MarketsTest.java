package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.DiscoveredEvent;
import com.polymarket.markets.DiscoveredMarket;
import com.polymarket.markets.EventQuery;
import com.polymarket.markets.MarketMetadata;
import com.polymarket.markets.MarketOutcome;
import com.polymarket.markets.MarketQuery;
import com.polymarket.markets.MarketSeries;
import com.polymarket.markets.MarketTag;
import com.polymarket.markets.SearchResults;
import com.polymarket.markets.Sport;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketsTest {

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

    /** Credential-free: discovery must work with no signing authority at all. */
    private Polymarket sdk() {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        return Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), ReadRetryPolicy.none(), d -> {
                }));
    }

    /** Body captured from the official Gamma API on 2026-08-16. */
    private void enqueueFixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/gamma/" + name)) {
            server.enqueue(new MockResponse().setBody(new String(in.readAllBytes(),
                    StandardCharsets.UTF_8)));
        }
    }

    @Test
    void shouldMapMarketsToOneSemanticModelWhenDiscoveryReturnsMarkets() throws Exception {
        enqueueFixture("markets.json");

        List<DiscoveredMarket> markets;
        try (Polymarket sdk = sdk()) {
            markets = sdk.markets().markets(MarketQuery.create().limit(2).closed(false));
        }

        DiscoveredMarket first = markets.get(0);
        assertEquals("559651", first.id());
        assertEquals("0xa467b14d51f01b957109d9cbb1d6c124fab2a089d52ed8f471d23c2812e743b7",
                first.conditionId().orElseThrow());
        assertEquals("xi-jinping-out-before-2027", first.slug().orElseThrow());
        assertEquals("Xi Jinping out before 2027?", first.question().orElseThrow());

        assertEquals(List.of("Yes", "No"), first.outcomes().stream()
                .map(MarketOutcome::name).toList());
        MarketOutcome yes = first.outcomes().get(0);
        assertEquals(new BigDecimal("0.0445"), yes.price().orElseThrow());
        assertEquals("32338220190071351435772801779725302244575775216413325951443816017994629993401",
                yes.tokenId().orElseThrow());

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/markets?limit=2&closed=false", request.getPath());
    }

    @Test
    void shouldMapStateTimesPricesAndMetadataWhenReadingMarket() throws Exception {
        enqueueFixture("markets.json");

        DiscoveredMarket market;
        try (Polymarket sdk = sdk()) {
            market = sdk.markets().markets(MarketQuery.create()).get(1);
        }

        assertEquals(Optional.of(true), market.state().active());
        assertEquals(Optional.of(false), market.state().closed());
        assertEquals(Optional.of(false), market.state().archived());
        assertEquals(Optional.of(true), market.state().acceptingOrders());
        assertEquals(Optional.of(true), market.state().orderBookEnabled());

        assertEquals(Instant.parse("2025-07-11T18:35:56.805Z"), market.startsAt().orElseThrow());
        assertEquals(Instant.parse("2028-11-07T00:00:00Z"), market.endsAt().orElseThrow());

        assertEquals(new BigDecimal("0.172"), market.pricing().bestBid().orElseThrow());
        assertEquals(new BigDecimal("0.173"), market.pricing().bestAsk().orElseThrow());
        assertEquals(new BigDecimal("0.175"), market.pricing().lastTradePrice().orElseThrow());
        assertEquals(new BigDecimal("0.001"), market.pricing().spread().orElseThrow());

        assertEquals(new BigDecimal("352407.94977"), market.metadata().liquidity().orElseThrow());
        assertEquals(new BigDecimal("26769607.417030994"), market.metadata().volume().orElseThrow());
        assertEquals(List.of("primaries", "united-states", "politics", "elections",
                        "us-presidential-election", "world-elections", "earn-4"),
                market.metadata().tags().stream().map(t -> t.slug().orElseThrow()).toList());
    }

    @Test
    void shouldKeepMinimumOrderNotionalOutOfSigningRulesWhenReadingMarket() throws Exception {
        enqueueFixture("markets.json");

        DiscoveredMarket market;
        try (Polymarket sdk = sdk()) {
            market = sdk.markets().markets(MarketQuery.create()).get(0);
        }

        assertEquals(new BigDecimal("5"),
                market.metadata().minimumOrderNotional().orElseThrow());
        assertFalse(officialConstraint("gammaDiscoveryMetadata"),
                "Gamma's orderMinSize is not authoritative for signing");

        for (Class<?> type : List.of(DiscoveredMarket.class, MarketMetadata.class)) {
            for (Method method : type.getMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                assertFalse(name.contains("minsize") || name.contains("minordersize")
                                || name.contains("minimumshares") || name.contains("rules"),
                        type.getSimpleName() + "." + method.getName()
                                + " reads like a CLOB signing rule");
            }
        }
    }

    @Test
    void shouldPreserveAbsentValuesWhenGammaFieldsAreNull() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"7\",\"conditionId\":\"\",\"question\":null,\"outcomes\":null,"
                        + "\"active\":null,\"bestBid\":null,\"orderMinSize\":null}]"));

        DiscoveredMarket market;
        try (Polymarket sdk = sdk()) {
            market = sdk.markets().markets(MarketQuery.create()).get(0);
        }

        assertEquals("7", market.id());
        assertEquals(Optional.empty(), market.conditionId());
        assertEquals(Optional.empty(), market.question());
        assertEquals(Optional.empty(), market.slug());
        assertEquals(List.of(), market.outcomes());
        assertEquals(Optional.empty(), market.state().active());
        assertEquals(Optional.empty(), market.state().closed());
        assertEquals(Optional.empty(), market.startsAt());
        assertEquals(Optional.empty(), market.endsAt());
        assertEquals(Optional.empty(), market.pricing().bestBid());
        assertEquals(Optional.empty(), market.metadata().volume());
        assertEquals(Optional.empty(), market.metadata().minimumOrderNotional());
        assertEquals(List.of(), market.metadata().tags());
    }

    @Test
    void shouldTolerateUnknownFieldsWithoutRawMapsWhenReadingMarket() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"7\",\"question\":\"Will it?\","
                        + "\"aFieldPolymarketAddedYesterday\":{\"nested\":[1,2]}}]"));

        try (Polymarket sdk = sdk()) {
            assertEquals("Will it?",
                    sdk.markets().markets(MarketQuery.create()).get(0).question().orElseThrow());
        }

        Set<Class<?>> visited = new LinkedHashSet<>();
        assertNoRawContainers(DiscoveredMarket.class, visited);
    }

    @Test
    void shouldReturnEmptyWhenMarketIdIsUnknown() throws Exception {
        enqueueFixture("market.json");
        server.enqueue(new MockResponse().setResponseCode(404)
                .setBody("{\"type\":\"not found error\",\"error\":\"id not found\"}"));

        try (Polymarket sdk = sdk()) {
            assertEquals("xi-jinping-out-before-2027",
                    sdk.markets().market("559651").orElseThrow().slug().orElseThrow());
            assertEquals(Optional.empty(), sdk.markets().market("1"));
        }

        assertEquals("/markets/559651", server.takeRequest().getPath());
        assertEquals("/markets/1", server.takeRequest().getPath());
    }

    @Test
    void shouldCarryMarketsWhenReadingEvents() throws Exception {
        enqueueFixture("events.json");

        List<DiscoveredEvent> events;
        try (Polymarket sdk = sdk()) {
            events = sdk.markets().events(EventQuery.create()
                    .limit(1).offset(0).order("volume24hr").ascending(false)
                    .active(true).closed(false).tagSlug("crypto"));
        }

        DiscoveredEvent event = events.get(0);
        assertEquals("16183", event.id());
        assertEquals("kraken-ipo-in-2025", event.slug().orElseThrow());
        assertEquals("Kraken IPO by ___ ?", event.title().orElseThrow());
        assertEquals(Optional.of(false), event.negRisk());
        assertEquals(Instant.parse("2027-01-01T05:00:00Z"), event.endsAt().orElseThrow());
        assertEquals(List.of("kraken-ipo-in-2025", "kraken-ipo-by-march-31-2026",
                        "kraken-ipo-by-december-31-2026-513", "kraken-ipo-by-june-30-2026"),
                event.markets().stream().map(m -> m.slug().orElseThrow()).toList());

        assertEquals("/events?limit=1&offset=0&order=volume24hr&ascending=false"
                + "&active=true&closed=false&tag_slug=crypto", server.takeRequest().getPath());
    }

    @Test
    void shouldReturnEmptyWhenEventSlugIsUnknown() throws Exception {
        enqueueFixture("event.json");
        server.enqueue(new MockResponse().setResponseCode(404));

        try (Polymarket sdk = sdk()) {
            assertEquals("16183",
                    sdk.markets().eventBySlug("kraken-ipo-in-2025").orElseThrow().id());
            assertEquals(Optional.empty(), sdk.markets().eventBySlug("no such event"));
        }

        assertEquals("/events/slug/kraken-ipo-in-2025", server.takeRequest().getPath());
        assertEquals("/events/slug/no+such+event", server.takeRequest().getPath());
    }

    @Test
    void shouldReturnTypedReferenceDataWhenReadingTagsSeriesAndSports() throws Exception {
        enqueueFixture("tags.json");
        enqueueFixture("series.json");
        enqueueFixture("sports.json");

        try (Polymarket sdk = sdk()) {
            assertEquals(List.of("product-marekt-fit", "caitlin-clark", "virgins"),
                    sdk.markets().tags(3).stream().map(t -> t.slug().orElseThrow()).toList());

            MarketSeries nfl = sdk.markets().series(2).get(0);
            assertEquals("1", nfl.id());
            assertEquals("NFL", nfl.title().orElseThrow());
            assertEquals("daily", nfl.recurrence().orElseThrow());

            Sport ufl = sdk.markets().sports().get(0);
            assertEquals("ufl", ufl.id());
            assertEquals("https://www.theufl.com/", ufl.resolutionSource().orElseThrow());
        }

        assertEquals("/tags?limit=3", server.takeRequest().getPath());
        assertEquals("/series?limit=2", server.takeRequest().getPath());
        assertEquals("/sports", server.takeRequest().getPath());
    }

    @Test
    void shouldReturnEventsAndTagsWhenSearching() throws Exception {
        enqueueFixture("public-search.json");

        SearchResults results;
        try (Polymarket sdk = sdk()) {
            results = sdk.markets().search("bitcoin above");
        }

        assertEquals(List.of("bitcoin-above-on-february-23"),
                results.events().stream().map(e -> e.slug().orElseThrow()).toList());
        assertEquals(List.of("Bitcoin", "Bitcoin Volatility"),
                results.tags().stream().map(t -> t.label().orElseThrow()).toList());
        assertEquals(List.of("bitcoin-above-58k-on-february-23"), results.events().get(0).markets()
                .stream().map(m -> m.slug().orElseThrow()).toList());

        assertEquals("/public-search?q=bitcoin+above", server.takeRequest().getPath());
    }

    @Test
    void shouldThrowIOExceptionWhenMappingMarketWithoutId() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "[{\"question\":\"Will it?\",\"slug\":\"will-it\"}]"));

        try (Polymarket sdk = sdk()) {
            IOException failure = assertThrows(IOException.class,
                    () -> sdk.markets().markets(MarketQuery.create()));
            assertTrue(failure.getMessage().contains("market id"), failure.getMessage());
        }
    }

    @Test
    void shouldThrowIOExceptionWhenMappingEventWithBlankId() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"\",\"slug\":\"kraken-ipo-in-2025\",\"markets\":[]}]"));

        try (Polymarket sdk = sdk()) {
            IOException failure = assertThrows(IOException.class,
                    () -> sdk.markets().events(EventQuery.create()));
            assertTrue(failure.getMessage().contains("event id"), failure.getMessage());
        }
    }

    @Test
    void shouldThrowIOExceptionWhenMappingTagWithoutId() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "[{\"label\":\"Bitcoin\",\"slug\":\"bitcoin\"}]"));

        try (Polymarket sdk = sdk()) {
            IOException failure = assertThrows(IOException.class,
                    () -> sdk.markets().tags(1));
            assertTrue(failure.getMessage().contains("tag id"), failure.getMessage());
        }
    }

    @Test
    void shouldThrowIOExceptionWhenMappingSeriesWithBlankId() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"   \",\"ticker\":\"nfl\",\"title\":\"NFL\"}]"));

        try (Polymarket sdk = sdk()) {
            IOException failure = assertThrows(IOException.class,
                    () -> sdk.markets().series(1));
            assertTrue(failure.getMessage().contains("series id"), failure.getMessage());
        }
    }

    @Test
    void shouldPreserveAbsentOptionalOutcomesWhenValuesAreBlank() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"7\",\"outcomes\":\"[\\\"Yes\\\", \\\"No\\\"]\","
                        + "\"outcomePrices\":\"[\\\"\\\", \\\"0.5\\\"]\","
                        + "\"clobTokenIds\":\"[\\\"\\\", \\\"123\\\"]\"}]"));

        DiscoveredMarket market;
        try (Polymarket sdk = sdk()) {
            market = sdk.markets().markets(MarketQuery.create()).get(0);
        }

        MarketOutcome yes = market.outcomes().get(0);
        assertEquals("Yes", yes.name());
        assertEquals(Optional.empty(), yes.price());
        assertEquals(Optional.empty(), yes.tokenId());

        MarketOutcome no = market.outcomes().get(1);
        assertEquals(new BigDecimal("0.5"), no.price().orElseThrow());
        assertEquals("123", no.tokenId().orElseThrow());
    }

    @Test
    void shouldTolerateUnknownAdditiveFieldsWhenReadingReferenceData() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"16183\",\"slug\":\"kraken-ipo-in-2025\","
                        + "\"quantumResolutionOracleV9\":{\"nested\":[1,2]},"
                        + "\"markets\":[{\"id\":\"516950\",\"whatIsThisEven\":true}]}]"));
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"101867\",\"slug\":\"bitcoin\",\"tagCarouselWeightV2\":7}]"));
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"1\",\"title\":\"NFL\",\"seriesFutureFlag\":[\"x\"]}]"));

        try (Polymarket sdk = sdk()) {
            DiscoveredEvent event = sdk.markets().events(EventQuery.create()).get(0);
            assertEquals("16183", event.id());
            assertEquals("516950", event.markets().get(0).id());
            assertEquals("101867", sdk.markets().tags(1).get(0).id());
            assertEquals("1", sdk.markets().series(1).get(0).id());
        }
    }

    @Test
    void shouldKeepOptionalValuesAbsentWhenGammaFieldsAreBlank() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"16183\",\"ticker\":\"\",\"slug\":null,\"title\":\"  \"}]"));
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"101867\",\"label\":\"\",\"slug\":null}]"));
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"1\",\"ticker\":null,\"title\":\"\",\"recurrence\":\"  \"}]"));

        try (Polymarket sdk = sdk()) {
            DiscoveredEvent event = sdk.markets().events(EventQuery.create()).get(0);
            assertEquals("16183", event.id());
            assertEquals(Optional.empty(), event.ticker());
            assertEquals(Optional.empty(), event.slug());
            assertEquals(Optional.empty(), event.title());

            MarketTag tag = sdk.markets().tags(1).get(0);
            assertEquals("101867", tag.id());
            assertEquals(Optional.empty(), tag.label());
            assertEquals(Optional.empty(), tag.slug());

            MarketSeries series = sdk.markets().series(1).get(0);
            assertEquals("1", series.id());
            assertEquals(Optional.empty(), series.ticker());
            assertEquals(Optional.empty(), series.title());
            assertEquals(Optional.empty(), series.recurrence());
        }
    }

    /** Walks every type reachable from the model and rejects escape hatches. */
    private static void assertNoRawContainers(Class<?> type, Set<Class<?>> visited) {
        if (!visited.add(type)) return;
        for (Method method : type.getMethods()) {
            Class<?> returned = method.getReturnType();
            assertFalse(Map.class.isAssignableFrom(returned),
                    type.getSimpleName() + "." + method.getName() + " exposes a raw map");
            assertFalse(returned.getName().startsWith("com.fasterxml"),
                    type.getSimpleName() + "." + method.getName() + " exposes a transport type");
            if (returned.getPackageName().equals(DiscoveredMarket.class.getPackageName())) {
                assertNoRawContainers(returned, visited);
            }
        }
    }

    /** Reads the conflict pinned from official documentation by issue #3. */
    private boolean officialConstraint(String section) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/protocol/constraints.json")) {
            return new ObjectMapper().readTree(in).path("minimumSize").path(section)
                    .path("authoritativeForSigning").asBoolean();
        }
    }
}
