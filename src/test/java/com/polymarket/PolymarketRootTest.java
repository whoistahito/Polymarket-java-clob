package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.operations.GeoblockStatus;
import com.polymarket.operations.PolymarketService;
import com.polymarket.operations.ServerTime;
import com.polymarket.operations.ServiceHealth;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Polymarket root")
class PolymarketRootTest {

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

    private Polymarket sdk() {
        return Polymarket.with(config());
    }

    @Test
    @DisplayName("TC-PR-001: construction needs no credentials and makes no request")
    void constructionIsCredentialFreeAndOffline() {
        try (Polymarket sdk = sdk()) {
            assertNotNull(sdk);
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-PR-002: server time is returned as a typed instant")
    void serverTimeIsTyped() throws Exception {
        server.enqueue(new MockResponse().setBody("1773890758"));
        try (Polymarket sdk = sdk()) {
            ServerTime time = sdk.serverTime();
            assertEquals(Instant.ofEpochSecond(1773890758L), time.at());
        }
        assertEquals("/time", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-PR-003: hosts are plain JDK URIs")
    void configUsesJdkTypes() {
        PolymarketConfig config = PolymarketConfig.defaults();
        assertEquals(URI.create("https://clob.polymarket.com"), config.clobHost());
    }

    @Test
    @DisplayName("TC-PR-004: every service is probed and an unreachable one is reported, not thrown")
    void healthReportsEveryService() {
        server.enqueue(new MockResponse().setBody("1773890758"));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("{\"status\":\"ok\"}"));

        try (Polymarket sdk = Polymarket.with(config(), noSleepRuntime(ReadRetryPolicy.none()))) {
            List<ServiceHealth> health = sdk.health();

            assertEquals(List.of(PolymarketService.CLOB, PolymarketService.GAMMA, PolymarketService.DATA),
                    health.stream().map(ServiceHealth::service).toList());
            assertEquals(List.of(true, false, true),
                    health.stream().map(ServiceHealth::available).toList());
            assertEquals(Optional.of("HTTP 503"), health.get(1).detail());
        }
    }

    @Test
    @DisplayName("TC-PR-005: geoblock is typed and missing fields stay absent")
    void geoblockIsTyped() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"blocked\":true,\"ip\":\"203.0.113.42\",\"country\":\"US\"}"));

        try (Polymarket sdk = sdk()) {
            GeoblockStatus status = sdk.geoblock();

            assertTrue(status.blocked());
            assertEquals(Optional.of("US"), status.country());
            assertEquals(Optional.empty(), status.region());
        }
        assertEquals("/api/geoblock", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-PR-006: a read retries within its budget and honours Retry-After")
    void readsRetryAndHonourRetryAfter() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "2"));
        server.enqueue(new MockResponse().setBody("1773890758"));

        List<Duration> waits = new ArrayList<>();
        try (Polymarket sdk = Polymarket.with(config(),
                new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                        ReadRetryPolicy.defaults(), waits::add))) {
            assertEquals(Instant.ofEpochSecond(1773890758L), sdk.serverTime().at());
        }

        assertEquals(2, server.getRequestCount());
        assertEquals(List.of(Duration.ofSeconds(2)), waits, "Retry-After must win over backoff");
    }

    @Test
    @DisplayName("TC-PR-007: a read stops at its attempt budget")
    void readsAreBounded() {
        for (int i = 0; i < 5; i++) server.enqueue(new MockResponse().setResponseCode(500));

        try (Polymarket sdk = Polymarket.with(config(),
                noSleepRuntime(new ReadRetryPolicy(3, Duration.ofMillis(1), Duration.ofSeconds(1))))) {
            assertThrows(IOException.class, sdk::serverTime);
        }
        assertEquals(3, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-PR-008: a write is executed once even when reads may retry three times")
    void writesAreNeverReplayed() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        try (HttpRuntime runtime = noSleepRuntime(ReadRetryPolicy.defaults())) {
            runtime.post(server.url("/").uri(), "/order", Map.of(), "{}");
        }
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-PR-009: closing twice releases resources once and blocks later reads")
    void closeIsIdempotent() {
        Polymarket sdk = sdk();
        sdk.close();
        sdk.close();
        assertThrows(IllegalStateException.class, sdk::serverTime);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-PR-010: RTDS is reached and closed through the root, never constructed by hand")
    void rtdsIsOwnedByTheRoot() {
        Polymarket sdk = sdk();
        com.polymarket.streaming.Rtds rtds = sdk.rtds();
        assertSame(rtds, sdk.rtds(), "the root must own one RTDS capability, not hand out new ones");
        assertFalse(rtds.isClosed());

        sdk.close();

        assertTrue(rtds.isClosed(), "closing the root must close the RTDS capability");
        assertThrows(IllegalStateException.class, sdk::rtds);
    }

    @Test
    @DisplayName("TC-PR-011: Builders is reached through the root, never by constructing a gateway")
    void buildersAreOwnedByTheRoot() {
        Polymarket sdk = sdk();
        com.polymarket.builders.Builders builders = sdk.builders();
        assertSame(builders, sdk.builders(), "the root must own one Builders capability");

        sdk.close();

        assertThrows(IllegalStateException.class, sdk::builders);
    }

    @Test
    @DisplayName("TC-PR-012: Social is reached through the root, never by constructing a gateway")
    void socialIsOwnedByTheRoot() {
        Polymarket sdk = sdk();
        com.polymarket.social.Social social = sdk.social();
        assertSame(social, sdk.social(), "the root must own one Social capability");

        sdk.close();

        assertThrows(IllegalStateException.class, sdk::social);
    }

    @Test
    @DisplayName("TC-PR-013: RFQ is reached through the root at a caller-supplied gateway host")
    void rfqIsOwnedByTheRoot() {
        Polymarket sdk = sdk();
        URI gateway = URI.create("https://gateway.example");

        com.polymarket.rfq.Rfq rfq = sdk.rfq(gateway);

        assertSame(rfq, sdk.rfq(gateway), "the root must own one RFQ capability per gateway host");
        assertNotNull(sdk.rfq(URI.create("https://other.example")));

        sdk.close();

        assertThrows(IllegalStateException.class, () -> sdk.rfq(gateway));
    }

    @Test
    @DisplayName("TC-PR-014: the Combo markets catalog host is configured, never hardcoded internally")
    void comboMarketsHostIsConfigurable() {
        assertEquals(URI.create("https://combos-rfq-api.polymarket.com"),
                PolymarketConfig.defaults().comboMarketsHost());
        assertEquals(URI.create("https://elsewhere.example"),
                PolymarketConfig.defaults()
                        .comboMarketsHost(URI.create("https://elsewhere.example"))
                        .comboMarketsHost());
    }

    private PolymarketConfig config() {
        URI host = server.url("/").uri();
        return PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
    }

    private HttpRuntime noSleepRuntime(ReadRetryPolicy policy) {
        return new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5), policy, d -> {
        });
    }
}
