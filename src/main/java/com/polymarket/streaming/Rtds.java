package com.polymarket.streaming;

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
 * Live Binance/Chainlink reference prices and market comments over the RTDS {@link RtdsTransport}
 * port. Register every handler before the one explicit subscribe call, so no event can arrive
 * before a handler exists for it. RTDS is unauthenticated: no credentials are ever sent.
 */
public final class Rtds implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Rtds.class);

    private final RtdsTransport transport;

    private final Set<String> binanceSymbols = new LinkedHashSet<>();
    private final Set<String> chainlinkSymbols = new LinkedHashSet<>();
    private final Set<CommentSubscription> commentSubscriptions = new LinkedHashSet<>();
    private RtdsConnection connection;
    private boolean closed;

    private final AtomicLong generation = new AtomicLong(0);

    private final FilteredCallbacks<BinancePriceEvent> binancePriceCallbacks = new FilteredCallbacks<>();
    private final FilteredCallbacks<ChainlinkPriceEvent> chainlinkPriceCallbacks = new FilteredCallbacks<>();
    private final CopyOnWriteArrayList<Consumer<CommentCreatedEvent>> commentCreatedCallbacks =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<CommentRemovedEvent>> commentRemovedCallbacks =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ReactionCreatedEvent>> reactionCreatedCallbacks =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ReactionRemovedEvent>> reactionRemovedCallbacks =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<RtdsLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

    public Rtds(RtdsTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
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

    public Registration onBinancePrice(Collection<String> symbols, Consumer<BinancePriceEvent> handler) {
        return binancePriceCallbacks.register(symbols, handler);
    }

    public Registration onChainlinkPrice(Collection<String> symbols, Consumer<ChainlinkPriceEvent> handler) {
        return chainlinkPriceCallbacks.register(symbols, handler);
    }

    public Registration onCommentCreated(Consumer<CommentCreatedEvent> handler) {
        return register(commentCreatedCallbacks, handler);
    }

    public Registration onCommentRemoved(Consumer<CommentRemovedEvent> handler) {
        return register(commentRemovedCallbacks, handler);
    }

    public Registration onReactionCreated(Consumer<ReactionCreatedEvent> handler) {
        return register(reactionCreatedCallbacks, handler);
    }

    public Registration onReactionRemoved(Consumer<ReactionRemovedEvent> handler) {
        return register(reactionRemovedCallbacks, handler);
    }

    public Registration addLifecycleListener(RtdsLifecycleListener listener) {
        Objects.requireNonNull(listener, "listener");
        lifecycleListeners.add(listener);
        return () -> lifecycleListeners.remove(listener);
    }

    private static <T> Registration register(CopyOnWriteArrayList<Consumer<T>> list, Consumer<T> handler) {
        Objects.requireNonNull(handler, "handler");
        list.add(handler);
        return () -> list.remove(handler);
    }

    // ------------------------------------------------------------------ //
    // Subscription — authoritative sets                                   //
    // ------------------------------------------------------------------ //

    public synchronized void subscribeBinancePrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols must not be empty");
        }
        List<String> added = addAll(binanceSymbols, symbols);
        if (connection == null) {
            connection = transport.connect(this::currentState, sink); // sends the full state itself
            return;
        }
        if (!added.isEmpty()) {
            connection.subscribeBinance(added);
        }
    }

    public synchronized void unsubscribeBinancePrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return;
        }
        List<String> removed = removeAll(binanceSymbols, symbols);
        if (connection != null && !removed.isEmpty()) {
            connection.unsubscribeBinance(removed);
        }
    }

    public synchronized void subscribeChainlinkPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols must not be empty");
        }
        List<String> added = addAll(chainlinkSymbols, symbols);
        if (connection == null) {
            connection = transport.connect(this::currentState, sink); // sends the full state itself
            return;
        }
        if (!added.isEmpty()) {
            connection.subscribeChainlink(added);
        }
    }

    public synchronized void unsubscribeChainlinkPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return;
        }
        List<String> removed = removeAll(chainlinkSymbols, symbols);
        if (connection != null && !removed.isEmpty()) {
            connection.unsubscribeChainlink(removed);
        }
    }

    /** Unfiltered: every comment event of this type, regardless of entity. */
    public void subscribeComments(CommentEventType type) {
        subscribeComments(CommentSubscription.all(Objects.requireNonNull(type, "type")));
    }

    /** Scoped to one official entity filter ({@code parentEntityType}/{@code parentEntityID}). */
    public void subscribeComments(CommentEventType type, RtdsEntityType entityType, long entityId) {
        subscribeComments(CommentSubscription.forEntity(
                Objects.requireNonNull(type, "type"), Objects.requireNonNull(entityType, "entityType"), entityId));
    }

    public void unsubscribeComments(CommentEventType type) {
        unsubscribeComments(CommentSubscription.all(Objects.requireNonNull(type, "type")));
    }

    public void unsubscribeComments(CommentEventType type, RtdsEntityType entityType, long entityId) {
        unsubscribeComments(CommentSubscription.forEntity(
                Objects.requireNonNull(type, "type"), Objects.requireNonNull(entityType, "entityType"), entityId));
    }

    private synchronized void subscribeComments(CommentSubscription subscription) {
        boolean added = commentSubscriptions.add(subscription);
        if (connection == null) {
            connection = transport.connect(this::currentState, sink); // sends the full state itself
            return;
        }
        if (added) {
            connection.subscribeComments(List.of(subscription));
        }
    }

    private synchronized void unsubscribeComments(CommentSubscription subscription) {
        if (connection != null && commentSubscriptions.remove(subscription)) {
            connection.unsubscribeComments(List.of(subscription));
        }
    }

    public synchronized List<String> subscribedBinanceSymbols() {
        return List.copyOf(binanceSymbols);
    }

    public synchronized List<String> subscribedChainlinkSymbols() {
        return List.copyOf(chainlinkSymbols);
    }

    public synchronized List<CommentSubscription> subscribedComments() {
        return List.copyOf(commentSubscriptions);
    }

    public long generation() {
        return generation.get();
    }

    /** Terminates the socket, reconnect, and heartbeat. Idempotent. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    private synchronized RtdsSubscriptions currentState() {
        return new RtdsSubscriptions(List.copyOf(binanceSymbols), List.copyOf(chainlinkSymbols),
                List.copyOf(commentSubscriptions));
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

    private final RtdsEventSink sink = new RtdsEventSink() {
        @Override
        public void onBinancePrice(BinancePriceEvent event) {
            binancePriceCallbacks.dispatch(event, bySymbol(event.symbol()));
        }

        @Override
        public void onChainlinkPrice(ChainlinkPriceEvent event) {
            chainlinkPriceCallbacks.dispatch(event, bySymbol(event.symbol()));
        }

        @Override
        public void onCommentCreated(CommentCreatedEvent event) {
            dispatch(commentCreatedCallbacks, event);
        }

        @Override
        public void onCommentRemoved(CommentRemovedEvent event) {
            dispatch(commentRemovedCallbacks, event);
        }

        @Override
        public void onReactionCreated(ReactionCreatedEvent event) {
            dispatch(reactionCreatedCallbacks, event);
        }

        @Override
        public void onReactionRemoved(ReactionRemovedEvent event) {
            dispatch(reactionRemovedCallbacks, event);
        }

        @Override
        public void onOpen(long gen) {
            generation.set(gen);
            lifecycleListeners.forEach(l -> safely("onOpen", () -> l.onOpen(gen)));
        }

        @Override
        public void onResubscribe(long gen) {
            lifecycleListeners.forEach(l -> safely("onResubscribe", () -> l.onResubscribe(gen)));
        }

        @Override
        public void onError(long gen, Exception error) {
            lifecycleListeners.forEach(l -> safely("onError", () -> l.onError(gen, error)));
        }

        @Override
        public void onClose(long gen, int code, String reason) {
            lifecycleListeners.forEach(l -> safely("onClose", () -> l.onClose(gen, code, reason)));
        }
    };

    private static <T> void dispatch(CopyOnWriteArrayList<Consumer<T>> callbacks, T event) {
        for (Consumer<T> callback : callbacks) {
            try {
                callback.accept(event);
            } catch (RuntimeException | Error e) {
                log.warn("Rtds callback threw: {}", e.toString(), e);
            }
        }
    }

    private static Predicate<Set<String>> bySymbol(String symbol) {
        return filter -> symbol != null && filter.contains(symbol);
    }

    private static void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error e) {
            log.warn("Rtds lifecycle listener threw from {}: {}", what, e.toString(), e);
        }
    }

    // ------------------------------------------------------------------ //
    // FilteredCallbacks                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Filtered callback list for one price event type. An empty filter matches every symbol; one
     * callback throwing must not stop the rest from receiving the same event.
     */
    private static final class FilteredCallbacks<T> {
        private record Entry<T>(Set<String> filter, Consumer<T> callback) {}

        private final CopyOnWriteArrayList<Entry<T>> entries = new CopyOnWriteArrayList<>();

        Registration register(Collection<String> filter, Consumer<T> callback) {
            Objects.requireNonNull(callback, "callback");
            Set<String> copy = filter == null || filter.isEmpty() ? Collections.emptySet() : Set.copyOf(filter);
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
                    log.warn("Rtds callback threw: {}", e.toString(), e);
                }
            }
        }
    }
}
