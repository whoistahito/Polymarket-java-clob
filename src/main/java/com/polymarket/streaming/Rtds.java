package com.polymarket.streaming;

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
    private volatile boolean closed;

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

    public Rtds(@NonNull RtdsTransport transport) {
        this.transport = transport;
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

    public Registration addLifecycleListener(@NonNull RtdsLifecycleListener listener) {
        lifecycleListeners.add(listener);
        return () -> lifecycleListeners.remove(listener);
    }

    private static <T> Registration register(CopyOnWriteArrayList<Consumer<T>> list, @NonNull Consumer<T> handler) {
        list.add(handler);
        return () -> list.remove(handler);
    }

    // ------------------------------------------------------------------ //
    // Subscription — authoritative sets                                   //
    // ------------------------------------------------------------------ //

    public synchronized void subscribeBinancePrices(List<String> symbols) {
        requireOpen();
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols must not be empty");
        }
        addAll(binanceSymbols, symbols);
        publish();
    }

    public synchronized void unsubscribeBinancePrices(List<String> symbols) {
        if (closed || symbols == null || symbols.isEmpty()) {
            return;
        }
        if (!removeAll(binanceSymbols, symbols).isEmpty() && connection != null) {
            publish();
        }
    }

    public synchronized void subscribeChainlinkPrices(List<String> symbols) {
        requireOpen();
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols must not be empty");
        }
        addAll(chainlinkSymbols, symbols);
        publish();
    }

    public synchronized void unsubscribeChainlinkPrices(List<String> symbols) {
        if (closed || symbols == null || symbols.isEmpty()) {
            return;
        }
        if (!removeAll(chainlinkSymbols, symbols).isEmpty() && connection != null) {
            publish();
        }
    }

    /** Unfiltered: every comment event of this type, regardless of entity. */
    public void subscribeComments(@NonNull CommentEventType type) {
        subscribeComments(CommentSubscription.all(type));
    }

    /** Scoped to one official entity filter ({@code parentEntityType}/{@code parentEntityID}). */
    public void subscribeComments(
            @NonNull CommentEventType type, @NonNull RtdsEntityType entityType, long entityId) {
        subscribeComments(CommentSubscription.forEntity(type, entityType, entityId));
    }

    public void unsubscribeComments(@NonNull CommentEventType type) {
        unsubscribeComments(CommentSubscription.all(type));
    }

    public void unsubscribeComments(
            @NonNull CommentEventType type, @NonNull RtdsEntityType entityType, long entityId) {
        unsubscribeComments(CommentSubscription.forEntity(type, entityType, entityId));
    }

    private synchronized void subscribeComments(CommentSubscription subscription) {
        requireOpen();
        commentSubscriptions.add(subscription);
        publish();
    }

    private synchronized void unsubscribeComments(CommentSubscription subscription) {
        if (closed) {
            return;
        }
        if (commentSubscriptions.remove(subscription) && connection != null) {
            publish();
        }
    }

    /**
     * Hands the connection the whole Authoritative Subscription; the connection decides whether it
     * belongs in the initial frame or travels as a delta.
     */
    private void publish() {
        if (connection == null) {
            connection = transport.connect(currentState(), sink);
        } else {
            connection.subscription(currentState());
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

    /** True once {@link #close()} has run; a closed capability never reopens. */
    public boolean isClosed() {
        return closed;
    }

    /** Terminal: stops the socket, reconnect work, text keepalive and callback delivery. */
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

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Rtds is closed");
        }
    }

    private RtdsSubscriptions currentState() {
        return new RtdsSubscriptions(List.copyOf(binanceSymbols), List.copyOf(chainlinkSymbols),
                List.copyOf(commentSubscriptions));
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

        Registration register(Collection<String> filter, @NonNull Consumer<T> callback) {
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
