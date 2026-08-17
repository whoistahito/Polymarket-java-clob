package com.polymarket.rfq;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentials;
import com.polymarket.trading.OrderSigner;
import com.polymarket.trading.Side;
import com.polymarket.trading.SigningContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Async decorator over {@link Rfq}. Every future completes on the supplied executor and carries
 * the same typed {@link RfqOutcome} as its synchronous counterpart — never a transparent retry.
 */
public final class AsyncRfq {

    private final Rfq rfq;
    private final Executor executor;

    private AsyncRfq(Rfq rfq, Executor executor) {
        this.rfq = Objects.requireNonNull(rfq, "rfq");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public static AsyncRfq wrap(Rfq rfq) {
        return new AsyncRfq(rfq, ForkJoinPool.commonPool());
    }

    public static AsyncRfq wrap(Rfq rfq, Executor executor) {
        return new AsyncRfq(rfq, executor);
    }

    public CompletableFuture<RfqOutcome> request(RfqRequest request, SigningIdentity identity,
            ApiCredentials accountCredentials, BuilderCredentials builderCredentials) {
        return io(() -> rfq.request(request, identity, accountCredentials, builderCredentials));
    }

    public CompletableFuture<RfqOutcome> status(String rfqId, ApiCredentials accountCredentials,
            String address) {
        return io(() -> rfq.status(rfqId, accountCredentials, address));
    }

    public CompletableFuture<RfqOutcome> waitForQuote(String rfqId, ApiCredentials accountCredentials,
            String address, Duration timeout, Duration pollInterval) {
        return io(() -> rfq.waitForQuote(rfqId, accountCredentials, address, timeout, pollInterval));
    }

    public CompletableFuture<RfqOutcome> accept(RfqOutcome.Quoted quote, Side side, OrderSigner signer,
            SigningContext context, ApiCredentials accountCredentials, BuilderCredentials builderCredentials) {
        return CompletableFuture.supplyAsync(() -> rfq.accept(
                quote, side, signer, context, accountCredentials, builderCredentials), executor);
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
