package com.polymarket.streaming;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningAuthority;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live CLOB market and user events over the {@link StreamTransport} port. Register every handler
 * before the one explicit subscribe call, so no snapshot can arrive before a handler exists for it.
 */
public final class Streaming implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Streaming.class);

    private final StreamTransport transport;
    private final SigningAuthority authority;

    private final Set<String> marketAssetIds = new LinkedHashSet<>();
    private final Set<String> userMarkets = new LinkedHashSet<>();
    private StreamConnection marketConnection;
    private StreamConnection userConnection;
    private boolean customMarketEvents;
    private volatile boolean closed;

    private final AtomicLong marketGeneration = new AtomicLong(0);
    private final AtomicLong userGeneration = new AtomicLong(0);

    private final FilteredCallbacks<BookEvent> bookCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<PriceChangeEvent> priceChangeCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<LastTradePriceEvent> lastTradeCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<TickSizeChangeEvent> tickSizeCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<BestBidAskEvent> bestBidAskCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<NewMarketEvent> newMarketCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<MarketResolvedEvent> marketResolvedCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<OrderEvent> orderCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<TradeEvent> tradeCallbacks = new FilteredCallbacks<>();
    private final CopyOnWriteArrayList<StreamLifecycleListener> lifecycleListeners =
            new CopyOnWriteArrayList<>();

    public Streaming(@NonNull StreamTransport transport, @NonNull SigningAuthority authority) {
        this.transport = transport;
        this.authority = authority;
    }

    /** Handle to a registered callback or lifecycle listener. Removal is local and idempotent. */
    public interface Registration extends AutoCloseable {
        void remove();

        @Override
        default void close() {
            remove();
        }
    }

    // ------------------------------------------------------------------ //
    // Registration — no network action                                    //
    // ------------------------------------------------------------------ //

    public Registration onBookUpdate(Collection<String> assetIds, Consumer<BookEvent> handler) {
        return bookCallbacks.register(assetIds, handler);
    }

    /** A batch fires if ANY entry matches; a filtered consumer must still check each entry's asset. */
    public Registration onPriceChange(Collection<String> assetIds, Consumer<PriceChangeEvent> handler) {
        return priceChangeCallbacks.register(assetIds, handler);
    }

    public Registration onLastTradePrice(Collection<String> assetIds, Consumer<LastTradePriceEvent> handler) {
        return lastTradeCallbacks.register(assetIds, handler);
    }

    public Registration onTickSizeChange(Collection<String> assetIds, Consumer<TickSizeChangeEvent> handler) {
        return tickSizeCallbacks.register(assetIds, handler);
    }

    /** Custom market event; delivered only when {@link #enableCustomMarketEvents()} preceded the subscribe. */
    public Registration onBestBidAsk(Collection<String> assetIds, Consumer<BestBidAskEvent> handler) {
        return bestBidAskCallbacks.register(assetIds, handler);
    }

    /** Custom market event; a new market is filtered on any of the asset IDs it lists. */
    public Registration onNewMarket(Collection<String> assetIds, Consumer<NewMarketEvent> handler) {
        return newMarketCallbacks.register(assetIds, handler);
    }

    /** Custom market event; a resolution is filtered on any of the asset IDs it lists. */
    public Registration onMarketResolved(Collection<String> assetIds, Consumer<MarketResolvedEvent> handler) {
        return marketResolvedCallbacks.register(assetIds, handler);
    }

    public Registration onOrder(Collection<String> markets, Consumer<OrderEvent> handler) {
        return orderCallbacks.register(markets, handler);
    }

    public Registration onTrade(Collection<String> markets, Consumer<TradeEvent> handler) {
        return tradeCallbacks.register(markets, handler);
    }

    public Registration addLifecycleListener(@NonNull StreamLifecycleListener listener) {
        lifecycleListeners.add(listener);
        return () -> lifecycleListeners.remove(listener);
    }

    // ------------------------------------------------------------------ //
    // Subscription — authoritative sets                                   //
    // ------------------------------------------------------------------ //

    /**
     * Asks the market channel for the documented custom events ({@code best_bid_ask},
     * {@code new_market}, {@code market_resolved}). Must precede the first {@link #subscribeMarket}.
     */
    public synchronized void enableCustomMarketEvents() {
        requireOpen();
        if (marketConnection != null) {
            throw new IllegalStateException("the market channel is already subscribed");
        }
        customMarketEvents = true;
    }

    public synchronized boolean customMarketEventsEnabled() {
        return customMarketEvents;
    }

    public synchronized void subscribeMarket(List<String> assetIds) {
        requireOpen();
        if (assetIds == null || assetIds.isEmpty()) {
            throw new IllegalArgumentException("assetIds must not be empty");
        }
        addAll(marketAssetIds, assetIds);
        if (marketConnection == null) {
            marketConnection = transport.connectMarket(List.copyOf(marketAssetIds), customMarketEvents, sink);
        } else {
            marketConnection.subscription(List.copyOf(marketAssetIds));
        }
    }

    public synchronized void unsubscribeMarket(List<String> assetIds) {
        if (closed || assetIds == null || assetIds.isEmpty()) {
            return;
        }
        if (!removeAll(marketAssetIds, assetIds).isEmpty() && marketConnection != null) {
            marketConnection.subscription(List.copyOf(marketAssetIds));
        }
    }

    /** Requires L2 credentials; missing credentials throw before any socket is opened. */
    public synchronized void subscribeUser(List<String> markets) {
        requireOpen();
        ApiCredentials credentials = authority.requireApiCredentials("Streaming.subscribeUser");
        if (markets != null) {
            addAll(userMarkets, markets);
        }
        if (userConnection == null) {
            userConnection = transport.connectUser(credentials, List.copyOf(userMarkets), sink);
        } else {
            userConnection.subscription(List.copyOf(userMarkets));
        }
    }

    public synchronized void unsubscribeUser(List<String> markets) {
        if (closed || markets == null || markets.isEmpty()) {
            return;
        }
        if (!removeAll(userMarkets, markets).isEmpty() && userConnection != null) {
            userConnection.subscription(List.copyOf(userMarkets));
        }
    }

    public synchronized List<String> subscribedAssetIds() {
        return List.copyOf(marketAssetIds);
    }

    public synchronized List<String> subscribedMarkets() {
        return List.copyOf(userMarkets);
    }

    public long marketGeneration() {
        return marketGeneration.get();
    }

    public long userGeneration() {
        return userGeneration.get();
    }

    /** True once {@link #close()} has run; a closed capability never reopens. */
    public boolean isClosed() {
        return closed;
    }

    /** Terminal: stops both channels' sockets, reconnect work, heartbeat and callback delivery. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (marketConnection != null) {
            marketConnection.close();
            marketConnection = null;
        }
        if (userConnection != null) {
            userConnection.close();
            userConnection = null;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Streaming is closed");
        }
    }

    private static void addAll(Set<String> authoritative, List<String> ids) {
        for (String id : ids) {
            if (id != null) {
                authoritative.add(id);
            }
        }
    }

    private static List<String> removeAll(Set<String> authoritative, List<String> ids) {
        List<String> removed = new ArrayList<>();
        for (String id : ids) {
            if (authoritative.remove(id)) {
                removed.add(id);
            }
        }
        return removed;
    }

    // ------------------------------------------------------------------ //
    // Dispatch — the sink implementation the transport calls into          //
    // ------------------------------------------------------------------ //

    private final StreamEventSink sink = new StreamEventSink() {
        @Override
        public void onBook(BookEvent event) {
            bookCallbacks.dispatch(event, byId(event.assetId()));
        }

        @Override
        public void onPriceChange(PriceChangeEvent event) {
            priceChangeCallbacks.dispatch(event, filter -> batchTouches(event, filter));
        }

        @Override
        public void onLastTradePrice(LastTradePriceEvent event) {
            lastTradeCallbacks.dispatch(event, byId(event.assetId()));
        }

        @Override
        public void onTickSizeChange(TickSizeChangeEvent event) {
            tickSizeCallbacks.dispatch(event, byId(event.assetId()));
        }

        @Override
        public void onBestBidAsk(BestBidAskEvent event) {
            bestBidAskCallbacks.dispatch(event, byId(event.assetId()));
        }

        @Override
        public void onNewMarket(NewMarketEvent event) {
            newMarketCallbacks.dispatch(event, byAnyId(event.assetIds()));
        }

        @Override
        public void onMarketResolved(MarketResolvedEvent event) {
            marketResolvedCallbacks.dispatch(event, byAnyId(event.assetIds()));
        }

        @Override
        public void onOrder(OrderEvent event) {
            orderCallbacks.dispatch(event, byId(event.market()));
        }

        @Override
        public void onTrade(TradeEvent event) {
            tradeCallbacks.dispatch(event, byId(event.market()));
        }

        @Override
        public void onOpen(StreamChannel channel, long generation) {
            generationOf(channel).set(generation);
            notifyListeners("onOpen", l -> l.onOpen(channel, generation));
        }

        @Override
        public void onResubscribe(StreamChannel channel, long generation) {
            notifyListeners("onResubscribe", l -> l.onResubscribe(channel, generation));
        }

        @Override
        public void onError(StreamChannel channel, long generation, Exception error) {
            notifyListeners("onError", l -> l.onError(channel, generation, error));
        }

        @Override
        public void onClose(StreamChannel channel, long generation, int code, String reason) {
            notifyListeners("onClose", l -> l.onClose(channel, generation, code, reason));
        }
    };

    private void notifyListeners(String what, Consumer<StreamLifecycleListener> call) {
        if (closed) {
            return;
        }
        for (StreamLifecycleListener listener : lifecycleListeners) {
            try {
                call.accept(listener);
            } catch (RuntimeException | Error e) {
                log.warn("Streaming lifecycle listener threw from {}: {}", what, e.toString(), e);
            }
        }
    }

    private AtomicLong generationOf(StreamChannel channel) {
        return channel == StreamChannel.MARKET ? marketGeneration : userGeneration;
    }

    private static Predicate<Set<String>> byId(String id) {
        return filter -> id != null && filter.contains(id);
    }

    private static Predicate<Set<String>> byAnyId(List<String> ids) {
        return filter -> ids.stream().anyMatch(filter::contains);
    }

    private static boolean batchTouches(PriceChangeEvent event, Set<String> filter) {
        for (PriceChangeEntry entry : event.changes()) {
            if (entry != null && filter.contains(entry.assetId())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ //
    // FilteredCallbacks                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Filtered callback list for one event type. An empty filter matches every message; one
     * callback throwing must not stop the rest from receiving the same event.
     */
    private final class FilteredCallbacks<T> {
        private record Entry<T>(Set<String> filter, Consumer<T> callback) {}

        private final CopyOnWriteArrayList<Entry<T>> entries = new CopyOnWriteArrayList<>();

        Registration register(Collection<String> filter, @NonNull Consumer<T> callback) {
            Set<String> copy = filter == null || filter.isEmpty()
                    ? Collections.emptySet() : Set.copyOf(filter);
            Entry<T> entry = new Entry<>(copy, callback);
            entries.add(entry);
            return () -> entries.remove(entry);
        }

        void dispatch(T message, Predicate<Set<String>> matches) {
            if (closed) {
                return; // a frame already in flight when close() landed reaches no callback
            }
            for (Entry<T> entry : entries) {
                if (!entry.filter().isEmpty() && !matches.test(entry.filter())) {
                    continue;
                }
                try {
                    entry.callback().accept(message);
                } catch (RuntimeException | Error e) {
                    log.warn("Streaming callback threw: {}", e.toString(), e);
                }
            }
        }
    }
}
