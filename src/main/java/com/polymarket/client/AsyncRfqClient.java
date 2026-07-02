package com.polymarket.client;

import com.polymarket.model.AcceptQuoteParams;
import com.polymarket.model.ApproveOrderParams;
import com.polymarket.model.CancelRfqQuoteParams;
import com.polymarket.model.CancelRfqRequestParams;
import com.polymarket.model.GetRfqBestQuoteParams;
import com.polymarket.model.GetRfqQuotesParams;
import com.polymarket.model.GetRfqRequestsParams;
import com.polymarket.model.RfqPaginatedResponse;
import com.polymarket.model.RfqQuote;
import com.polymarket.model.RfqQuoteResponse;
import com.polymarket.model.RfqRequest;
import com.polymarket.model.RfqRequestResponse;
import com.polymarket.model.RfqUserOrder;
import com.polymarket.model.RfqUserQuote;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Async wrapper around {@link RfqClient}.
 *
 * <p>Every method returns a {@link CompletableFuture} that completes on the provided
 * {@link Executor}. Obtain an instance via {@link AsyncPolymarketClient#rfq()}.
 */
public final class AsyncRfqClient {

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }

    @FunctionalInterface
    private interface IoRunnable {
        void run() throws IOException;
    }

    private final RfqClient rfq;
    private final Executor executor;

    AsyncRfqClient(RfqClient rfq, Executor executor) {
        this.rfq = Objects.requireNonNull(rfq, "rfq");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    private <T> CompletableFuture<T> async(IoSupplier<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.get();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }

    private CompletableFuture<Void> asyncVoid(IoRunnable task) {
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }

    public CompletableFuture<RfqRequestResponse> createRfqRequest(RfqUserOrder userOrder, String tickSize) {
        return async(() -> rfq.createRfqRequest(userOrder, tickSize));
    }

    public CompletableFuture<Void> cancelRfqRequest(CancelRfqRequestParams params) {
        return asyncVoid(() -> rfq.cancelRfqRequest(params));
    }

    public CompletableFuture<RfqPaginatedResponse<RfqRequest>> getRfqRequests(GetRfqRequestsParams params) {
        return async(() -> rfq.getRfqRequests(params));
    }

    public CompletableFuture<RfqQuoteResponse> createRfqQuote(RfqUserQuote userQuote, String tickSize) {
        return async(() -> rfq.createRfqQuote(userQuote, tickSize));
    }

    public CompletableFuture<RfqPaginatedResponse<RfqQuote>> getRfqRequesterQuotes(GetRfqQuotesParams params) {
        return async(() -> rfq.getRfqRequesterQuotes(params));
    }

    public CompletableFuture<RfqPaginatedResponse<RfqQuote>> getRfqQuoterQuotes(GetRfqQuotesParams params) {
        return async(() -> rfq.getRfqQuoterQuotes(params));
    }

    public CompletableFuture<RfqQuote> getRfqBestQuote(GetRfqBestQuoteParams params) {
        return async(() -> rfq.getRfqBestQuote(params));
    }

    public CompletableFuture<Void> cancelRfqQuote(CancelRfqQuoteParams params) {
        return asyncVoid(() -> rfq.cancelRfqQuote(params));
    }

    public CompletableFuture<Map<String, Object>> rfqConfig() {
        return async(rfq::rfqConfig);
    }

    public CompletableFuture<Void> acceptRfqQuote(AcceptQuoteParams payload) {
        return asyncVoid(() -> rfq.acceptRfqQuote(payload));
    }

    public CompletableFuture<Void> approveRfqOrder(ApproveOrderParams payload) {
        return asyncVoid(() -> rfq.approveRfqOrder(payload));
    }
}
