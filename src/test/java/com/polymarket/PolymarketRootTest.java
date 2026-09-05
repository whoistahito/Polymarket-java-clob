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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void shouldConstructOfflineWhenCredentialsAreAbsent() {
        try (Polymarket sdk = sdk()) {
            assertNotNull(sdk);
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldReturnTypedTimeWhenServerResponds() throws Exception {
        server.enqueue(new MockResponse().setBody("1773890758"));
        try (Polymarket sdk = sdk()) {
            ServerTime time = sdk.serverTime();
            assertEquals(Instant.ofEpochSecond(1773890758L), time.at());
        }
        assertEquals("/time", server.takeRequest().getPath());
    }

    @Test
    void shouldUseJdkUrisWhenConfiguringHosts() {
        PolymarketConfig config = PolymarketConfig.defaults();
        assertEquals(URI.create("https://clob.polymarket.com"), config.clobHost());
    }

    @Test
    void shouldReportServiceAvailabilityWhenHealthProbeRuns() {
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
    void shouldProbeDocumentedEndpointsWhenHealthRuns() throws Exception {
        for (int i = 0; i < 3; i++) server.enqueue(new MockResponse().setBody("{}"));

        try (Polymarket sdk = Polymarket.with(config(), noSleepRuntime(ReadRetryPolicy.none()))) {
            sdk.health();
        }

        // A deployment check must not depend on a path Polymarket never published: an undocumented
        // one can disappear without notice and report a healthy service as down.
        assertEquals(List.of("/time", "/tags?limit=1", "/trades?limit=1"),
                List.of(server.takeRequest().getPath(), server.takeRequest().getPath(),
                        server.takeRequest().getPath()));
    }

    @Test
    void shouldPreserveTypedGeoblockFieldsWhenResponseOmitsRegion() throws Exception {
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
    void shouldHonorRetryAfterWhenReadIsRateLimited() throws Exception {
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
    void shouldThrowWhenReadAttemptsExceedBudget() {
        for (int i = 0; i < 5; i++) server.enqueue(new MockResponse().setResponseCode(500));

        try (Polymarket sdk = Polymarket.with(config(),
                noSleepRuntime(new ReadRetryPolicy(3, Duration.ofMillis(1), Duration.ofSeconds(1))))) {
            assertThrows(IOException.class, sdk::serverTime);
        }
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void shouldSendWriteOnceWhenRetriesAreConfigured() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        try (HttpRuntime runtime = noSleepRuntime(ReadRetryPolicy.defaults())) {
            runtime.post(server.url("/").uri(), "/order", Map.of(), "{}");
        }
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void shouldThrowWhenReadingAfterRootIsClosed() {
        Polymarket sdk = sdk();
        sdk.close();
        sdk.close();
        assertThrows(IllegalStateException.class, sdk::serverTime);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldCloseRtdsWhenRootIsClosed() {
        Polymarket sdk = sdk();
        com.polymarket.streaming.Rtds rtds = sdk.rtds();
        assertSame(rtds, sdk.rtds(), "the root must own one RTDS capability, not hand out new ones");
        assertFalse(rtds.isClosed());

        sdk.close();

        assertTrue(rtds.isClosed(), "closing the root must close the RTDS capability");
        assertThrows(IllegalStateException.class, sdk::rtds);
    }

    @Test
    void shouldReuseBuildersWhenRootOwnsCapability() {
        Polymarket sdk = sdk();
        com.polymarket.builders.Builders builders = sdk.builders();
        assertSame(builders, sdk.builders(), "the root must own one Builders capability");

        sdk.close();

        assertThrows(IllegalStateException.class, sdk::builders);
    }

    @Test
    void shouldReuseSocialWhenRootOwnsCapability() {
        Polymarket sdk = sdk();
        com.polymarket.social.Social social = sdk.social();
        assertSame(social, sdk.social(), "the root must own one Social capability");

        sdk.close();

        assertThrows(IllegalStateException.class, sdk::social);
    }

    @Test
    void shouldReuseRfqWhenGatewayHostIsSame() {
        Polymarket sdk = sdk();
        URI gateway = URI.create("https://gateway.example");

        com.polymarket.rfq.Rfq rfq = sdk.rfq(gateway);

        assertSame(rfq, sdk.rfq(gateway), "the root must own one RFQ capability per gateway host");
        assertNotNull(sdk.rfq(URI.create("https://other.example")));

        sdk.close();

        assertThrows(IllegalStateException.class, () -> sdk.rfq(gateway));
    }

    @Test
    void shouldUseConfiguredComboHostWhenBuildingConfig() {
        assertEquals(URI.create("https://combos-rfq-api.polymarket.com"),
                PolymarketConfig.defaults().comboMarketsHost());
        assertEquals(URI.create("https://elsewhere.example"),
                PolymarketConfig.defaults()
                        .comboMarketsHost(URI.create("https://elsewhere.example"))
                        .comboMarketsHost());
    }

    @Test
    void shouldThrowWhenCapabilityReadsAfterRootCloses() {
        Polymarket sdk = sdk();
        com.polymarket.markets.Markets markets = sdk.markets();

        sdk.close();

        assertThrows(IllegalStateException.class,
                () -> markets.market("0x1"),
                "a capability captured before close must not outlive the root's transport");
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldHonorRetryAfterDateWhenReadIsRateLimited() throws Exception {
        String farFuture = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(60));
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", farFuture));
        server.enqueue(new MockResponse().setBody("1773890758"));

        List<Duration> waits = new ArrayList<>();
        try (Polymarket sdk = Polymarket.with(config(),
                new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                        ReadRetryPolicy.defaults(), waits::add))) {
            assertEquals(Instant.ofEpochSecond(1773890758L), sdk.serverTime().at());
        }

        assertEquals(List.of(Duration.ofSeconds(5)), waits,
                "a date 60 s out must be read as a wait and clamped to the max backoff");
    }

    @Test
    void shouldThrowWhenConfigMutatorReceivesNull() {
        PolymarketConfig config = PolymarketConfig.defaults();

        assertThrows(NullPointerException.class, () -> config.clobHost(null));
        assertThrows(NullPointerException.class, () -> config.gammaHost(null));
        assertThrows(NullPointerException.class, () -> config.dataHost(null));
        assertThrows(NullPointerException.class, () -> config.geoblockHost(null));
        assertThrows(NullPointerException.class, () -> config.comboMarketsHost(null));
        assertThrows(NullPointerException.class, () -> config.connectTimeout(null));
        assertThrows(NullPointerException.class, () -> config.requestTimeout(null));
        assertThrows(NullPointerException.class, () -> config.readRetryPolicy(null));
    }

    @Test
    void shouldUseConfiguredStreamHostsWhenBuildingConfig() {
        PolymarketConfig defaults = PolymarketConfig.defaults();
        assertEquals(URI.create("wss://ws-subscriptions-clob.polymarket.com"), defaults.streamHost());
        assertEquals(URI.create("wss://ws-live-data.polymarket.com"), defaults.rtdsHost());

        PolymarketConfig custom = defaults
                .streamHost(URI.create("ws://localhost:1/clob"))
                .rtdsHost(URI.create("ws://localhost:2/rtds"));
        assertEquals(URI.create("ws://localhost:1/clob"), custom.streamHost());
        assertEquals(URI.create("ws://localhost:2/rtds"), custom.rtdsHost());
        assertThrows(NullPointerException.class, () -> defaults.streamHost(null));
        assertThrows(NullPointerException.class, () -> defaults.rtdsHost(null));
    }

    @Test
    void shouldCloseRtdsTransportWhenRootCloses() {
        Polymarket sdk = sdk();
        com.polymarket.streaming.Rtds rtds = sdk.rtds();

        sdk.close();

        assertTrue(rtds.isClosed());
        assertThrows(IllegalStateException.class,
                () -> rtds.subscribeBinancePrices(List.of("btcusdt")),
                "a closed RTDS capability never reopens");
    }

    @Test
    void shouldCancelHttpCallWhenRootCloses() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        HttpRuntime runtime = new HttpRuntime(
                Duration.ofSeconds(2), Duration.ofMinutes(1), ReadRetryPolicy.none());
        Polymarket sdk = Polymarket.with(config(), runtime);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> read = executor.submit(() -> {
            try {
                sdk.serverTime();
            } catch (IOException expected) {
                return;
            }
            throw new AssertionError("the in-flight call completed successfully after close");
        });

        try {
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
            sdk.close();
            read.get(5, TimeUnit.SECONDS);
        } finally {
            sdk.close();
            executor.shutdownNow();
        }
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
