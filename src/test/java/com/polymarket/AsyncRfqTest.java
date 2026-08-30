package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentials;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.internal.rfq.ComboMarketGateway;
import com.polymarket.internal.rfq.RfqGateway;
import com.polymarket.internal.trading.Eip712OrderSigner;
import com.polymarket.markets.PositionId;
import com.polymarket.markets.PusdAmount;
import com.polymarket.rfq.AsyncRfq;
import com.polymarket.rfq.Rfq;
import com.polymarket.rfq.RfqOutcome;
import com.polymarket.rfq.RfqRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AsyncRfq: narrow async decorator (issue #27)")
class AsyncRfqTest {

    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final ApiCredentials ACCOUNT_CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final BuilderCredentials BUILDER_CREDENTIALS = new BuilderCredentials(
            "builder-key", "YnVpbGRlci1zZWNyZXQtYnVpbGRlci1zZWNyZXQ=", "builder-passphrase");
    private static final Clock FIXED =
            Clock.fixed(Instant.ofEpochSecond(1773890758L), ZoneOffset.UTC);

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

    private Rfq rfq() {
        URI host = server.url("/").uri();
        HttpRuntime runtime = new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                ReadRetryPolicy.none(), d -> {
                });
        return new Rfq(new RfqGateway(host, runtime, FIXED),
                new ComboMarketGateway(host, runtime), new Eip712OrderSigner(), FIXED);
    }

    private static final class CountingExecutor implements Executor {
        private final AtomicInteger runs = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            runs.incrementAndGet();
            command.run();
        }
    }

    @Test
    @DisplayName("TC-AR-001: a caller-supplied executor is honored")
    void supplierExecutorIsHonored() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}"""));
        CountingExecutor executor = new CountingExecutor();
        RfqRequest.Buy request = new RfqRequest.Buy(
                List.of(new PositionId("111"), new PositionId("222")), PusdAmount.of("1.0"));

        AsyncRfq.wrap(rfq(), executor).request(request, SigningIdentity.eoa(SIGNER.address()),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS).get();

        assertEquals(1, executor.runs.get());
    }

    @Test
    @DisplayName("TC-AR-002: async status preserves the same typed RfqOutcome as sync")
    void asyncStatusPreservesOutcomeType() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"EXPIRED"}"""));

        RfqOutcome outcome =
                AsyncRfq.wrap(rfq()).status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address()).get();

        assertInstanceOf(RfqOutcome.Expired.class, outcome);
    }

    @Test
    @DisplayName("TC-AR-003: no synchronous Rfq or Executor accessor is exposed")
    void noSyncAccessorsExposed() {
        for (var method : AsyncRfq.class.getMethods()) {
            assertTrue(!Executor.class.isAssignableFrom(method.getReturnType())
                    && !Rfq.class.isAssignableFrom(method.getReturnType()),
                    "unexpected sync escape hatch: " + method);
        }
    }
}
