package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.DiscoveredEvent;
import com.polymarket.markets.DiscoveredMarket;
import com.polymarket.markets.EventQuery;
import com.polymarket.markets.MarketMetadata;
import com.polymarket.markets.MarketOutcome;
import com.polymarket.markets.MarketQuery;
import com.polymarket.markets.MarketSeries;
import com.polymarket.markets.SearchResults;
import com.polymarket.markets.Sport;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Markets")
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
    @DisplayName("TC-MK-001: a discovery market carries its identity and zipped outcomes")
    void marketsMapToOneSemanticModel() throws Exception {
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
    @DisplayName("TC-MK-002: state, times, prices and metadata come through typed")
    void marketCarriesStateTimesPricesAndMetadata() throws Exception {
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
    @DisplayName("TC-MK-003: Gamma's minimum order notional stays discovery metadata")
    void minimumOrderNotionalIsNotASigningRule() throws Exception {
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
    @DisplayName("TC-MK-004: an omitted or null field stays absent instead of becoming a value")
    void absentValuesAreNotFabricated() throws Exception {
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
    @DisplayName("TC-MK-005: unknown response fields are tolerated and never surface as raw maps")
    void unknownFieldsAreToleratedWithoutRawMaps() throws Exception {
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
    @DisplayName("TC-MK-006: a single market read is empty when Gamma does not know the id")
    void marketByIdIsOptional() throws Exception {
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
    @DisplayName("TC-MK-007: an event carries its own markets and sends every filter it was given")
    void eventsCarryTheirMarkets() throws Exception {
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
    @DisplayName("TC-MK-008: an event slug resolves, and an unknown slug is empty")
    void eventBySlugIsOptional() throws Exception {
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
    @DisplayName("TC-MK-009: tags, series and sports come back typed and bounded")
    void referenceDataIsTyped() throws Exception {
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
    @DisplayName("TC-MK-010: search returns events and tags for an encoded query")
    void searchReturnsEventsAndTags() throws Exception {
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
