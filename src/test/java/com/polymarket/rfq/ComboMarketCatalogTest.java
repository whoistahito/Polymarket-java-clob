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
import com.polymarket.internal.trading.Eip712OrderSigner;
import com.polymarket.markets.PositionId;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ground truth: src/test/resources/protocol/combo-markets.json. */
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

    private Rfq rfq() {
        URI host = server.url("/").uri();
        HttpRuntime runtime = new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                ReadRetryPolicy.none(), d -> {
                });
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1773890758L), ZoneOffset.UTC);
        return new Rfq(new RfqGateway(host, runtime, clock),
                new ComboMarketGateway(host, runtime), new Eip712OrderSigner(), clock);
    }

    private void enqueuePinnedPage() {
        server.enqueue(new MockResponse()
                .setBody(FIXTURE.get("observedResponse").get("body").toString()));
    }

    @Test
    void shouldMapLegPositionIdsByIndexWhenCatalogIsRead() throws Exception {
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
    void shouldEncodeCursorAndExcludeAsQueryParametersWhenReadingCatalog() throws Exception {
        enqueuePinnedPage();

        rfq().comboMarkets(ComboMarketQuery.pageSize(50)
                .cursor("NA")
                .exclude(List.of("0xaaa", "0xbbb")));

        assertEquals("/v1/rfq/combo-markets?limit=50&cursor=NA&exclude=0xaaa%2C0xbbb",
                server.takeRequest().getPath());
    }

    @Test
    void shouldEndCatalogWalkWhenNextCursorIsNull() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"markets":[],"next_cursor":null}"""));

        ComboMarketPage page = rfq().comboMarkets(ComboMarketQuery.pageSize(50));

        assertEquals(Optional.empty(), page.nextCursor());
        assertEquals(List.of(), page.markets());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenPageSizeIsOutsideAcceptedRange() {
        assertThrows(IllegalArgumentException.class, () -> ComboMarketQuery.pageSize(0));
        assertThrows(IllegalArgumentException.class, () -> ComboMarketQuery.pageSize(101));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldSkipMarketWhenOutcomePairIsMalformed() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"markets":[{"id":"1","condition_id":"0xa","position_ids":["7"],
                             "slug":"s","title":"t","outcomes":["Yes"],"outcome_prices":["0.5"]}],
                 "next_cursor":null}"""));

        ComboMarketPage page = rfq().comboMarkets(ComboMarketQuery.pageSize(50));

        assertEquals(List.of(), page.markets());
    }

    @Test
    void shouldKeepReadableMarketsWhenAnotherMarketIsUnusable() throws Exception {
        // The method's contract is "anything else is skipped, never guessed". A position id that is
        // not a uint256, or a price that is not a number, must not take the whole page with it.
        server.enqueue(new MockResponse().setBody("""
                {"markets":[
                   {"id":"1","condition_id":"0xa","position_ids":["not-a-uint256","8"],
                    "slug":"s","title":"t","outcomes":["Yes","No"],"outcome_prices":["0.5","0.5"]},
                   {"id":"2","condition_id":"0xb","position_ids":["9","10"],
                    "slug":"s2","title":"t2","outcomes":["Yes","No"],"outcome_prices":["oops","0.5"]},
                   {"id":"3","condition_id":"0xc","position_ids":["11","12"],
                    "slug":"s3","title":"t3","outcomes":["Yes","No"],"outcome_prices":["0.5","0.5"]}],
                 "next_cursor":null}"""));

        ComboMarketPage page = rfq().comboMarkets(ComboMarketQuery.pageSize(50));

        assertEquals(List.of("3"), page.markets().stream().map(ComboMarket::id).toList(),
                "the readable market survives its unreadable neighbours");
    }

    @Test
    void shouldThrowIOExceptionWhenErrorBodyIsNotJson() {
        // A gateway or proxy failure is exactly when the status matters, and exactly when the body
        // is least likely to be JSON.
        server.enqueue(new MockResponse().setResponseCode(502)
                .setBody("<html><body>502 Bad Gateway</body></html>"));

        IOException failure = assertThrows(IOException.class,
                () -> rfq().comboMarkets(ComboMarketQuery.pageSize(50)));

        assertTrue(failure.getMessage().contains("502"), failure.getMessage());
    }

    @Test
    void shouldAcceptOnlyOfficialSourcesWhenCatalogFixtureIsLoaded() {
        List<String> sources = new java.util.ArrayList<>();
        FIXTURE.get("sources").forEach(s -> sources.add(s.asText()));

        assertEquals(2, sources.size());
        for (String source : sources) {
            assertTrue(source.startsWith("https://docs.polymarket.com/")
                    || source.startsWith("https://combos-rfq-api.polymarket.com"), source);
        }
    }
}
