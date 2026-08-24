package com.polymarket.rfq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.ReadRetryPolicy;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.internal.rfq.ComboMarketGateway;
import com.polymarket.internal.rfq.RfqGateway;
import com.polymarket.markets.PositionId;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ground truth: src/test/resources/protocol/combo-markets.json. */
@DisplayName("Combo market discovery: eligible legs without local CTF calculation (issue #25)")
class ComboMarketCatalogTest {

    private static final JsonNode FIXTURE = load();

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

    private static JsonNode load() {
        try (InputStream in = ComboMarketCatalogTest.class
                .getResourceAsStream("/protocol/combo-markets.json")) {
            return new ObjectMapper().readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("could not read combo-markets.json", e);
        }
    }

    /** Driven through the capability, over the real adapter, so discovery is reachable as shipped. */
    private Rfq rfq() {
        URI host = server.url("/").uri();
        HttpRuntime runtime = new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                ReadRetryPolicy.none(), d -> {
                });
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1773890758L), ZoneOffset.UTC);
        return new Rfq(new RfqGateway(host, runtime, clock),
                new ComboMarketGateway(host, runtime), clock);
    }

    private void enqueuePinnedPage() {
        server.enqueue(new MockResponse()
                .setBody(FIXTURE.get("observedResponse").get("body").toString()));
    }

    @Test
    @DisplayName("TC-CM-001: a catalog read is an unauthenticated GET that maps YES and NO leg Position IDs by index")
    void catalogReadMapsLegPositionIdsByIndex() throws Exception {
        enqueuePinnedPage();

        ComboMarketPage page = rfq().comboMarkets(ComboMarketQuery.pageSize(2));

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/v1/rfq/combo-markets?limit=2", request.getPath());
        assertEquals(null, request.getHeader("POLY_ADDRESS"));
        assertEquals(null, request.getHeader("POLY_API_KEY"));

        assertEquals(2, page.markets().size());
        ComboMarket iran = page.markets().get(0);
        assertEquals("665374", iran.id());
        assertEquals("0x5db999fad322cea2914535aae5517060c3f80ad6d8c0231cde2124a434d16846",
                iran.conditionId());
        assertEquals("will-the-us-invade-iran-before-2027", iran.slug());
        assertEquals("Will the U.S. invade Iran before 2027?", iran.title());
        assertEquals(new PositionId(
                        "798559951534518479645224261511384773234863312866932338530531601041078616064"),
                iran.yes().positionId());
        assertEquals(new PositionId(
                        "798559951534518479645224261511384773234863312866932338530531601041078616065"),
                iran.no().positionId());
        assertEquals("Yes", iran.yes().label());
        assertEquals("No", iran.no().label());
        assertEquals(Optional.of(new BigDecimal("0.165")), iran.yes().price());
        assertEquals(Optional.of(new BigDecimal("0.835")), iran.no().price());
        assertEquals(Optional.of(new BigDecimal("59479547.79032897")), iran.volume());
        assertTrue(iran.tags().contains("geopolitics"), iran.tags().toString());
        assertEquals(Optional.of("NA"), page.nextCursor());
    }

    @Test
    @DisplayName("TC-CM-002: cursor and exclude travel as the documented query parameters")
    void cursorAndExcludeTravelAsQueryParameters() throws Exception {
        enqueuePinnedPage();

        rfq().comboMarkets(ComboMarketQuery.pageSize(50)
                .cursor("NA")
                .exclude(List.of("0xaaa", "0xbbb")));

        assertEquals("/v1/rfq/combo-markets?limit=50&cursor=NA&exclude=0xaaa%2C0xbbb",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-CM-003: a null next_cursor ends the walk rather than repeating the last page")
    void nullNextCursorEndsTheWalk() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"markets":[],"next_cursor":null}"""));

        ComboMarketPage page = rfq().comboMarkets(ComboMarketQuery.pageSize(50));

        assertEquals(Optional.empty(), page.nextCursor());
        assertEquals(List.of(), page.markets());
    }

    @Test
    @DisplayName("TC-CM-004: a page size outside the gateway's accepted 1-100 range is rejected before any request")
    void pageSizeOutsideAcceptedRangeIsRejectedLocally() {
        assertThrows(IllegalArgumentException.class, () -> ComboMarketQuery.pageSize(0));
        assertThrows(IllegalArgumentException.class, () -> ComboMarketQuery.pageSize(101));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-CM-005: a market whose outcomes are not the documented YES/NO pair is skipped, not guessed at")
    void malformedOutcomePairIsSkipped() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"markets":[{"id":"1","condition_id":"0xa","position_ids":["7"],
                             "slug":"s","title":"t","outcomes":["Yes"],"outcome_prices":["0.5"]}],
                 "next_cursor":null}"""));

        ComboMarketPage page = rfq().comboMarkets(ComboMarketQuery.pageSize(50));

        assertEquals(List.of(), page.markets());
    }

    @Test
    @DisplayName("TC-CM-006: the pinned Combo catalog fixture cites official Polymarket sources only")
    void fixtureCitesOfficialSourcesOnly() {
        List<String> sources = new java.util.ArrayList<>();
        FIXTURE.get("sources").forEach(s -> sources.add(s.asText()));

        assertEquals(2, sources.size());
        for (String source : sources) {
            assertTrue(source.startsWith("https://docs.polymarket.com/")
                    || source.startsWith("https://combos-rfq-api.polymarket.com"), source);
        }
    }
}
