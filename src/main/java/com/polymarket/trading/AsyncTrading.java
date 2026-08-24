package com.polymarket.trading;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.markets.AssetId;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Async decorator over {@link Trading}. Every future completes on the supplied executor and
 * carries the same typed outcome as its synchronous counterpart — never a transparent retry.
 */
public final class AsyncTrading {

    private final Trading trading;
    private final Executor executor;

    private AsyncTrading(Trading trading, Executor executor) {
        this.trading = Objects.requireNonNull(trading, "trading");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public static AsyncTrading wrap(Trading trading) {
        return new AsyncTrading(trading, ForkJoinPool.commonPool());
    }

    public static AsyncTrading wrap(Trading trading, Executor executor) {
        return new AsyncTrading(trading, executor);
    }

    public CompletableFuture<SignedOrder> sign(AssetId asset, Side side, PusdAmount pusdLeg,
            ShareQuantity shareLeg, MarketRules rules, SigningContext context) {
        return CompletableFuture.supplyAsync(
                () -> trading.sign(asset, side, pusdLeg, shareLeg, rules, context), executor);
    }

    public CompletableFuture<SubmissionOutcome> submit(SignedOrder order, OrderPlacement placement) {
        return CompletableFuture.supplyAsync(() -> trading.submit(order, placement), executor);
    }

    public CompletableFuture<SubmissionOutcome> submit(SignedOrder order, OrderPlacement placement,
            OrderIntent intent) {
        return CompletableFuture.supplyAsync(() -> trading.submit(order, placement, intent), executor);
    }

    public CompletableFuture<SubmissionOutcome> place(OrderExecution execution,
            SigningContext context, ApiCredentials credentials) {
        return CompletableFuture.supplyAsync(
                () -> trading.place(execution, context, credentials), executor);
    }

    public CompletableFuture<BatchSubmissionOutcome> submitBatch(List<BatchItem> items) {
        return CompletableFuture.supplyAsync(() -> trading.submitBatch(items), executor);
    }

    public CompletableFuture<CancellationOutcome> cancel(ApiCredentials credentials, String address,
            List<String> orderIds) {
        return CompletableFuture.supplyAsync(
                () -> trading.cancel(credentials, address, orderIds), executor);
    }

    public CompletableFuture<ReconciliationOutcome> reconcile(ApiCredentials credentials,
            String address, String orderId, List<String> tradeIds, Duration timeout, Duration pollInterval) {
        return io(() -> trading.reconcile(credentials, address, orderId, tradeIds, timeout, pollInterval));
    }

    public CompletableFuture<ReconciliationOutcome> reconcile(ApiCredentials credentials,
            String address, String orderId, String rfqId, List<String> tradeIds, Duration timeout,
            Duration pollInterval) {
        return io(() -> trading.reconcile(credentials, address, orderId, rfqId, tradeIds, timeout,
                pollInterval));
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }

    private <T> CompletableFuture<T> io(IoSupplier<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.get();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }
}
