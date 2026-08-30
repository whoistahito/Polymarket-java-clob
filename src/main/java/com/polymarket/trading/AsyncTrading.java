package com.polymarket.trading;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.markets.AssetId;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import lombok.NonNull;

/**
 * Async decorator over {@link Trading}. Every future completes on the supplied executor and
 * carries the same typed outcome as its synchronous counterpart — never a transparent retry.
 */
public final class AsyncTrading {

    private final Trading trading;
    private final Executor executor;

    private AsyncTrading(@NonNull Trading trading, @NonNull Executor executor) {
        this.trading = trading;
        this.executor = executor;
    }

    public static AsyncTrading wrap(Trading trading) {
        return new AsyncTrading(trading, ForkJoinPool.commonPool());
    }

    public static AsyncTrading wrap(Trading trading, Executor executor) {
        return new AsyncTrading(trading, executor);
    }

    public CompletableFuture<SignedOrder> sign(AssetId asset, Side side, Price price,
            ShareQuantity shares, MarketRules rules, SigningContext context) {
        return CompletableFuture.supplyAsync(
                () -> trading.sign(asset, side, price, shares, rules, context), executor);
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
            SigningIdentity identity, String orderId, List<String> tradeIds, Duration timeout,
            Duration pollInterval) {
        return io(() -> trading.reconcile(credentials, identity, orderId, tradeIds, timeout,
                pollInterval));
    }

    public CompletableFuture<ReconciliationOutcome> reconcile(ApiCredentials credentials,
            SigningIdentity identity, String orderId, String rfqId, List<String> tradeIds,
            Duration timeout, Duration pollInterval) {
        return io(() -> trading.reconcile(credentials, identity, orderId, rfqId, tradeIds, timeout,
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
