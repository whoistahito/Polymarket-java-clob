package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import com.polymarket.trading.AsyncTrading;
import com.polymarket.trading.OrderPlacement;
import com.polymarket.trading.OrderType;
import com.polymarket.trading.Side;
import com.polymarket.trading.SignedOrder;
import com.polymarket.trading.SigningContext;
import com.polymarket.trading.SubmissionOutcome;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import com.polymarket.trading.Trading;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AsyncTradingTest {

    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final ApiCredentials CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final Clock FIXED =
            Clock.fixed(Instant.ofEpochSecond(1773890758L), ZoneOffset.UTC);
    private static final MarketRules RULES =
            new MarketRules(TickSize.of("0.01"), ShareQuantity.of("1"), false);

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
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        SigningAuthority authority = SigningAuthority
                .signing(SIGNER, SigningIdentity.eoa(SIGNER.address()))
                .withApiCredentials(CREDENTIALS);
        return Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), ReadRetryPolicy.none(), d -> {
                }), authority, FIXED);
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
    void shouldHonorCallerExecutorWhenSigning() throws Exception {
        CountingExecutor executor = new CountingExecutor();
        SigningContext context = SigningContext.of(
                SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant());

        try (Polymarket sdk = sdk()) {
            AsyncTrading async = AsyncTrading.wrap(sdk.trading(), executor);
            async.sign(new TokenId("123"), Side.BUY, Price.of("0.52"), ShareQuantity.of("10"),
                    RULES, context).get();
        }

        assertEquals(1, executor.runs.get());
    }

    @Test
    void shouldMatchSynchronousSignWhenSigningAsynchronously() throws Exception {
        SigningContext context = SigningContext.of(
                SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant());

        try (Polymarket sdk = sdk()) {
            SignedOrder sync = sdk.trading().sign(new TokenId("123"), Side.BUY,
                    Price.of("0.52"), ShareQuantity.of("10"), RULES, context);
            SignedOrder async = AsyncTrading.wrap(sdk.trading())
                    .sign(new TokenId("123"), Side.BUY, Price.of("0.52"), ShareQuantity.of("10"),
                            RULES, context)
                    .get();
            assertEquals(sync, async);
        }
    }

    @Test
    void shouldPreserveSubmissionDispositionWhenSubmittingAsynchronously() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"success":true,"orderID":"0xabc","status":"live","tradeIDs":[]}"""));

        SubmissionOutcome outcome;
        try (Polymarket sdk = sdk()) {
            SigningContext context = SigningContext.of(
                    SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant());
            SignedOrder order = sdk.trading().sign(new TokenId("123"), Side.BUY,
                    Price.of("0.52"), ShareQuantity.of("10"), RULES, context);
            outcome = AsyncTrading.wrap(sdk.trading())
                    .submit(order, OrderPlacement.of(CREDENTIALS, OrderType.GTC))
                    .get();
        }

        assertInstanceOf(SubmissionOutcome.Accepted.class, outcome);
    }

    @Test
    void shouldThrowExecutionExceptionWhenReconcileTransportDisconnects() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        try (Polymarket sdk = sdk()) {
            CompletableFuture<?> future = AsyncTrading.wrap(sdk.trading())
                    .reconcile(CREDENTIALS, SigningIdentity.eoa(SIGNER.address()), "order-1",
                            java.util.List.of("t1"),
                            Duration.ofSeconds(1), Duration.ZERO);
            ExecutionException e = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(UncheckedIOException.class, e.getCause());
        }
    }

    @Test
    void shouldExposeNoSyncAccessorsWhenInspectingAsyncTrading() {
        for (var method : AsyncTrading.class.getMethods()) {
            assertTrue(!java.util.concurrent.Executor.class.isAssignableFrom(method.getReturnType())
                    && !com.polymarket.trading.Trading.class.isAssignableFrom(method.getReturnType()),
                    "unexpected sync escape hatch: " + method);
        }
    }

    @Test
    void shouldWrapEverySyncOperationWhenInspectingAsyncTrading() {
        java.util.Set<String> async = java.util.stream.Stream
                .of(AsyncTrading.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        List<String> missing = java.util.stream.Stream.of(Trading.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName)
                .filter(name -> !async.contains(name))
                .distinct()
                .toList();

        assertEquals(List.of(), missing, "synchronous Trading operations with no async wrapper");
    }
}
