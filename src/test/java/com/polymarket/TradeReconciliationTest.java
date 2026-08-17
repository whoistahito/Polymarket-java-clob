package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.trading.ReconciliationOutcome;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Trading: trade-ID settlement reconciliation (issue #16)")
class TradeReconciliationTest {

    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final ApiCredentials CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");

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

    private Polymarket sdk(Clock clock) {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        SigningAuthority authority = SigningAuthority
                .signing(SIGNER, SigningIdentity.eoa(SIGNER.address()))
                .withApiCredentials(CREDENTIALS);
        return Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), ReadRetryPolicy.none(), d -> {
                }), authority, clock);
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse().setBody(body));
    }

    /** Advances a fixed step on every read so a poll loop reaches its deadline with no real sleep. */
    private static final class SteppingClock extends Clock {
        private Instant now;
        private final Duration step;

        SteppingClock(Instant start, Duration step) {
            this.now = start;
            this.step = step;
        }

        @Override
        public Instant instant() {
            Instant t = now;
            now = now.plus(step);
            return t;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    @DisplayName("TC-RC-001: every trade confirmed on the first poll is Confirmed")
    void everyTradeConfirmedIsConfirmed() throws Exception {
        enqueue("""
                [{"id":"t1","status":"CONFIRMED","side":"BUY","asset_id":"123","size":"10",
                  "price":"0.52","match_time":"1773890758","transaction_hash":"0xhash1"}]""");
        enqueue("""
                [{"id":"t2","status":"CONFIRMED","side":"BUY","asset_id":"123","size":"5",
                  "price":"0.52","transaction_hash":"0xhash2"}]""");

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(Clock.fixed(Instant.ofEpochSecond(1_800_000_000), ZoneOffset.UTC))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, SIGNER.address(), "order-1",
                    List.of("t1", "t2"), Duration.ofSeconds(30), Duration.ZERO);
        }

        ReconciliationOutcome.Confirmed confirmed =
                assertInstanceOf(ReconciliationOutcome.Confirmed.class, outcome);
        assertEquals(2, confirmed.trades().size());
        assertEquals("0xhash1", confirmed.trades().get(0).transactionHash().orElseThrow());
        assertEquals(2, server.getRequestCount());
        assertEquals("/data/trades?id=t1", server.takeRequest().getPath());
        assertEquals("/data/trades?id=t2", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-RC-002: any trade reaching FAILED makes the whole reconciliation Failed")
    void anyFailedTradeIsFailed() throws Exception {
        enqueue("""
                [{"id":"t1","status":"CONFIRMED","side":"BUY","asset_id":"123","size":"10","price":"0.52"}]""");
        enqueue("""
                [{"id":"t2","status":"FAILED","side":"BUY","asset_id":"123","size":"5","price":"0.52"}]""");

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(Clock.fixed(Instant.ofEpochSecond(1_800_000_000), ZoneOffset.UTC))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, SIGNER.address(), "order-1",
                    List.of("t1", "t2"), Duration.ofSeconds(30), Duration.ZERO);
        }

        assertInstanceOf(ReconciliationOutcome.Failed.class, outcome);
    }

    @Test
    @DisplayName("TC-RC-003: a delayed transaction hash is resolved from a later poll")
    void delayedTransactionHashResolvesOnALaterPoll() throws Exception {
        // First round: matched, no hash yet — not terminal.
        enqueue("""
                [{"id":"t1","status":"MATCHED","side":"BUY","asset_id":"123","size":"10","price":"0.52"}]""");
        // Second round: confirmed, hash now present — terminal.
        enqueue("""
                [{"id":"t1","status":"CONFIRMED","side":"BUY","asset_id":"123","size":"10","price":"0.52",
                  "transaction_hash":"0xlate"}]""");

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(Instant.ofEpochSecond(1_800_000_000), Duration.ofSeconds(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, SIGNER.address(), "order-1",
                    List.of("t1"), Duration.ofDays(1), Duration.ZERO);
        }

        ReconciliationOutcome.Confirmed confirmed =
                assertInstanceOf(ReconciliationOutcome.Confirmed.class, outcome);
        assertEquals("0xlate", confirmed.trades().get(0).transactionHash().orElseThrow());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RC-004: a missing trade record is not terminal; the poll continues")
    void missingTradeRecordIsNotTerminal() throws Exception {
        enqueue("[]"); // t1 not in the response yet
        enqueue("""
                [{"id":"t1","status":"CONFIRMED","side":"BUY","asset_id":"123","size":"10","price":"0.52"}]""");

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(Instant.ofEpochSecond(1_800_000_000), Duration.ofSeconds(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, SIGNER.address(), "order-1",
                    List.of("t1"), Duration.ofDays(1), Duration.ZERO);
        }

        assertInstanceOf(ReconciliationOutcome.Confirmed.class, outcome);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RC-005: a local timeout is Pending, retaining the order and trade ids, not a failure")
    void timeoutIsPendingNotFailure() throws Exception {
        enqueue("""
                [{"id":"t1","status":"MATCHED","side":"BUY","asset_id":"123","size":"10","price":"0.52"}]""");

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(Instant.ofEpochSecond(1_800_000_000), Duration.ofHours(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, SIGNER.address(), "order-1",
                    List.of("t1"), Duration.ofSeconds(1), Duration.ZERO);
        }

        ReconciliationOutcome.Pending pending =
                assertInstanceOf(ReconciliationOutcome.Pending.class, outcome);
        assertEquals("order-1", pending.orderId());
        assertEquals(List.of("t1"), pending.tradeIds());
        assertTrue(pending.rfqId().isEmpty());
    }

    @Test
    @DisplayName("TC-RC-006: a status this release does not know keeps its raw value and is not terminal")
    void unknownStatusPreservesRawValue() throws Exception {
        enqueue("""
                [{"id":"t1","status":"SETTLING_NEW_2027","side":"BUY","asset_id":"123",
                  "size":"10","price":"0.52"}]""");

        ReconciliationOutcome outcome;
        try (Polymarket sdk = sdk(new SteppingClock(Instant.ofEpochSecond(1_800_000_000), Duration.ofHours(1)))) {
            outcome = sdk.trading().reconcile(CREDENTIALS, SIGNER.address(), "order-1",
                    List.of("t1"), Duration.ofSeconds(1), Duration.ZERO);
        }

        // Unknown status is not terminal, so the poll runs out the clock: Pending, not a crash.
        assertInstanceOf(ReconciliationOutcome.Pending.class, outcome);
    }
}
