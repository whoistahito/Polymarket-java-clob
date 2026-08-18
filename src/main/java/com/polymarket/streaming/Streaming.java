package com.polymarket.streaming;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.SigningAuthority;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
    private boolean closed;

    private final AtomicLong marketGeneration = new AtomicLong(0);
    private final AtomicLong userGeneration = new AtomicLong(0);

    private final FilteredCallbacks<BookEvent> bookCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<PriceChangeEvent> priceChangeCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<LastTradePriceEvent> lastTradeCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<TickSizeChangeEvent> tickSizeCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<OrderEvent> orderCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<TradeEvent> tradeCallbacks = new FilteredCallbacks<>();
    private final CopyOnWriteArrayList<StreamLifecycleListener> lifecycleListeners =
            new CopyOnWriteArrayList<>();

    public Streaming(StreamTransport transport, SigningAuthority authority) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.authority = Objects.requireNonNull(authority, "authority");
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

    public Registration onOrder(Collection<String> markets, Consumer<OrderEvent> handler) {
        return orderCallbacks.register(markets, handler);
    }

    public Registration onTrade(Collection<String> markets, Consumer<TradeEvent> handler) {
        return tradeCallbacks.register(markets, handler);
    }

    public Registration addLifecycleListener(StreamLifecycleListener listener) {
        Objects.requireNonNull(listener, "listener");
        lifecycleListeners.add(listener);
        return () -> lifecycleListeners.remove(listener);
    }

    // ------------------------------------------------------------------ //
    // Subscription — authoritative sets                                   //
    // ------------------------------------------------------------------ //

    public synchronized void subscribeMarket(List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw new IllegalArgumentException("assetIds must not be empty");
        }
        List<String> added = addAll(marketAssetIds, assetIds);
        if (marketConnection == null) {
            marketConnection = transport.connectMarket(this::currentAssetIds, sink);
            return;
        }
        if (!added.isEmpty()) {
            marketConnection.subscribe(added);
        }
    }

    public synchronized void unsubscribeMarket(List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        List<String> removed = removeAll(marketAssetIds, assetIds);
        if (marketConnection != null && !removed.isEmpty()) {
            marketConnection.unsubscribe(removed);
        }
    }

    /** Requires L2 credentials; missing credentials throw before any socket is opened. */
    public synchronized void subscribeUser(List<String> markets) {
        ApiCredentials credentials = authority.requireApiCredentials("Streaming.subscribeUser");
        List<String> added = markets == null ? List.of() : addAll(userMarkets, markets);
        if (userConnection == null) {
            userConnection = transport.connectUser(credentials, this::currentUserMarkets, sink);
            return;
        }
        if (!added.isEmpty()) {
            userConnection.subscribe(added);
        }
    }

    public synchronized void unsubscribeUser(List<String> markets) {
        if (markets == null || markets.isEmpty()) {
            return;
        }
        List<String> removed = removeAll(userMarkets, markets);
        if (userConnection != null && !removed.isEmpty()) {
            userConnection.unsubscribe(removed);
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

    /** Terminates both channels' sockets, reconnect, and heartbeat. Idempotent. */
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

    private synchronized List<String> currentAssetIds() {
        return List.copyOf(marketAssetIds);
    }

    private synchronized List<String> currentUserMarkets() {
        return List.copyOf(userMarkets);
    }

    private static List<String> addAll(Set<String> authoritative, List<String> ids) {
        List<String> added = new ArrayList<>();
        for (String id : ids) {
            if (id != null && authoritative.add(id)) {
                added.add(id);
            }
        }
        return added;
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
            lifecycleListeners.forEach(l -> safely("onOpen", () -> l.onOpen(channel, generation)));
        }

        @Override
        public void onResubscribe(StreamChannel channel, long generation) {
            lifecycleListeners.forEach(l -> safely("onResubscribe", () -> l.onResubscribe(channel, generation)));
        }

        @Override
        public void onError(StreamChannel channel, long generation, Exception error) {
            lifecycleListeners.forEach(l -> safely("onError", () -> l.onError(channel, generation, error)));
        }

        @Override
        public void onClose(StreamChannel channel, long generation, int code, String reason) {
            lifecycleListeners.forEach(
                    l -> safely("onClose", () -> l.onClose(channel, generation, code, reason)));
        }
    };

    private AtomicLong generationOf(StreamChannel channel) {
        return channel == StreamChannel.MARKET ? marketGeneration : userGeneration;
    }

    private static Predicate<Set<String>> byId(String id) {
        return filter -> id != null && filter.contains(id);
    }

    private static boolean batchTouches(PriceChangeEvent event, Set<String> filter) {
        for (PriceChangeEntry entry : event.changes()) {
            if (entry != null && filter.contains(entry.assetId())) {
                return true;
            }
        }
        return false;
    }

    private static void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error e) {
            log.warn("Streaming lifecycle listener threw from {}: {}", what, e.toString(), e);
        }
    }

    // ------------------------------------------------------------------ //
    // FilteredCallbacks                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Filtered callback list for one event type. An empty filter matches every message; one
     * callback throwing must not stop the rest from receiving the same event.
     */
    private static final class FilteredCallbacks<T> {
        private record Entry<T>(Set<String> filter, Consumer<T> callback) {}

        private final CopyOnWriteArrayList<Entry<T>> entries = new CopyOnWriteArrayList<>();

        Registration register(Collection<String> filter, Consumer<T> callback) {
            Objects.requireNonNull(callback, "callback");
            Set<String> copy = filter == null || filter.isEmpty()
                    ? Collections.emptySet() : Set.copyOf(filter);
            Entry<T> entry = new Entry<>(copy, callback);
            entries.add(entry);
            return () -> entries.remove(entry);
        }

        void dispatch(T message, Predicate<Set<String>> matches) {
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
