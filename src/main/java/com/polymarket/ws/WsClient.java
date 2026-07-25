package com.polymarket.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polymarket.client.ApiKeyCreds;
import com.polymarket.ws.model.BestBidAsk;
import com.polymarket.ws.model.BookUpdate;
import com.polymarket.ws.model.LastTradePrice;
import com.polymarket.ws.model.MarketResolved;
import com.polymarket.ws.model.MidpointUpdate;
import com.polymarket.ws.model.NewMarket;
import com.polymarket.ws.model.OrderBookLevel;
import com.polymarket.ws.model.OrderMessage;
import com.polymarket.ws.model.PriceChange;
import com.polymarket.ws.model.PriceChangeBatchEntry;
import com.polymarket.ws.model.TickSizeChange;
import com.polymarket.ws.model.TradeMessage;
import com.polymarket.ws.model.WsMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket client for real-time Polymarket market data and user events.
 *
 * <p>Wraps OkHttp's {@link WebSocket} and routes incoming JSON frames to a
 * {@link WsMessageListener}.  Two channels are supported:
 *
 * <ul>
 *   <li><b>Market channel</b> ({@code /ws/market}) — unauthenticated; delivers
 *       {@code book}, {@code price_change}, {@code tick_size_change},
 *       {@code last_trade_price}, and (with custom features) {@code best_bid_ask},
 *       {@code new_market}, {@code market_resolved} events.</li>
 *   <li><b>User channel</b> ({@code /ws/user}) — L2-authenticated; delivers
 *       {@code trade} and {@code order} events.</li>
 * </ul>
 *
 * <h3>Registration is separate from subscription (Ticket 026)</h3>
 *
 * <p>{@code register*} methods are purely local: they attach a typed callback with a token/market
 * filter and open no socket and send no frame. Subscription is a separate, explicit act. Register
 * every handler first, then subscribe once — that ordering is what guarantees the initial snapshot
 * cannot arrive before a handler exists to receive it:
 *
 * <pre>{@code
 * WsClient ws = WsClient.builder().listener(myListener).build();
 * ws.registerBookUpdates(tokens, book -> ...);
 * ws.registerPriceChanges(tokens, change -> ...);
 * ws.subscribeMarket(tokens);           // one frame, one initial dump
 * }</pre>
 *
 * <p>The subscribed token set is authoritative: {@link #subscribeMarket(List)} adds to it,
 * {@link #unsubscribeMarket(List)} removes from it, and a reconnect restores exactly what is in it.
 *
 * <h3>Lifecycle and heartbeats (Ticket 027)</h3>
 *
 * <p>Lifecycle callbacks carry the {@link ChannelType} and a per-channel connection generation, so a
 * consumer can invalidate only the channel that dropped and can tell pre-reconnect data from fresh
 * data. Each open channel sends the documented text {@code PING} every 10 seconds.
 *
 * <p>Call {@link #close()} to release the WebSocket connections.
 */
public final class WsClient {

    // --------------------------------------------------------------------- //
    // Constants                                                               //
    // --------------------------------------------------------------------- //

    /** Default WebSocket base URL for Polymarket. */
    public static final String DEFAULT_WS_BASE =
        "wss://ws-subscriptions-clob.polymarket.com";

    private static final String MARKET_PATH = "/ws/market";
    private static final String USER_PATH    = "/ws/user";

    /** The documented heartbeat: the literal text {@code PING}, answered with {@code PONG}. */
    private static final String PING_FRAME = "PING";

    /** Documented heartbeat cadence: every 10 seconds per open channel. */
    private static final long DEFAULT_PING_INTERVAL_MS = 10_000L;

    /**
     * How long a connection must stay up before its reconnect budget is considered spent.
     *
     * <p>Resetting the attempt counter on every successful handshake makes {@code
     * maxReconnectAttempts} meaningless against a server that accepts the handshake and closes
     * immediately — each cycle looks like a fresh start and the loop never ends. A connection only
     * counts as healthy once it has survived this long.
     */
    private static final long DEFAULT_STABLE_CONNECTION_MS = 30_000L;

    /** No-op listener used when the builder caller omits {@link Builder#listener}. */
    private static final WsMessageListener NOOP_LISTENER = new WsMessageListener() {
        @Override public void onMessage(WsMessage message) {}
        @Override public void onError(Exception error) {}
        @Override public void onClose(int code, String reason) {}
    };

    private static final Logger log = LoggerFactory.getLogger(WsClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --------------------------------------------------------------------- //
    // Fields                                                                  //
    // --------------------------------------------------------------------- //

    private final String wsBase;
    private final OkHttpClient okHttp;
    private final WsMessageListener listener;
    private final ApiKeyCreds apiKeyCreds;
    private final String walletAddress;
    private final boolean emitMidpointUpdates;
    private final long pingIntervalMs;
    private final long stableConnectionMs;

    /** Registry of typed per-subscription callbacks with their token/market filters. */
    private final TypedCallbackRegistry typedCallbacks = new TypedCallbackRegistry();

    // Reconnect configuration
    private final int maxReconnectAttempts; // <=0 means unlimited
    private final long reconnectDelayMs;    // base delay; doubles each attempt
    private final long maxReconnectDelayMs;

    /** Scheduler for reconnect tasks and heartbeats (shared; daemon threads). */
    private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "polymarket-ws-scheduler");
            t.setDaemon(true);
            return t;
        });

    /** Active market-channel WebSocket (may be null before first subscribe). */
    private volatile WebSocket marketWs;
    /** Active user-channel WebSocket (may be null before first subscribe). */
    private volatile WebSocket userWs;

    // Per-channel connection state
    private final AtomicReference<ConnectionState> marketState =
        new AtomicReference<>(ConnectionState.disconnected());
    private final AtomicReference<ConnectionState> userState =
        new AtomicReference<>(ConnectionState.disconnected());

    // Per-channel reconnect attempt counters
    private final AtomicInteger marketAttempt = new AtomicInteger(0);
    private final AtomicInteger userAttempt   = new AtomicInteger(0);

    /**
     * Per-channel connection generation (Ticket 027). Starts at 0 (never connected) and increments
     * on every successful open, so consumers can date cached state against a connection.
     */
    private final AtomicLong marketGeneration = new AtomicLong(0);
    private final AtomicLong userGeneration   = new AtomicLong(0);

    /** Wall-clock instant each channel last opened, for the stability check above. */
    private final AtomicLong marketOpenedAtMs = new AtomicLong(0);
    private final AtomicLong userOpenedAtMs   = new AtomicLong(0);

    /** Per-channel heartbeat tasks, cancelled on close and restarted on reconnect. */
    private volatile ScheduledFuture<?> marketHeartbeat;
    private volatile ScheduledFuture<?> userHeartbeat;

    /**
     * Authoritative subscription sets (Ticket 026). Subscribe adds, unsubscribe removes, and a
     * reconnect restores exactly these — so subscribing to A and then B reconnects as A+B rather
     * than as B alone. Insertion-ordered so frames are reproducible.
     */
    private final Set<String> marketAssetIds = new LinkedHashSet<>();
    private volatile boolean marketCustomFeatures = false;
    private final Set<String> userMarkets = new LinkedHashSet<>();

    /** Set when {@link #close()} is called to suppress further reconnects. */
    private volatile boolean closed = false;

    // --------------------------------------------------------------------- //
    // Constructor (via Builder)                                               //
    // --------------------------------------------------------------------- //

    private WsClient(Builder b) {
        this.wsBase               = b.wsBase;
        this.okHttp               = b.okHttp != null ? b.okHttp : defaultOkHttpClient();
        this.listener             = b.listener != null ? b.listener : NOOP_LISTENER;
        this.apiKeyCreds          = b.apiKeyCreds;
        this.walletAddress        = b.walletAddress;
        this.emitMidpointUpdates  = b.emitMidpointUpdates;
        this.maxReconnectAttempts = b.maxReconnectAttempts;
        this.reconnectDelayMs     = b.reconnectDelayMs;
        this.maxReconnectDelayMs  = b.maxReconnectDelayMs;
        this.pingIntervalMs       = b.pingIntervalMs;
        this.stableConnectionMs   = b.stableConnectionMs;
    }

    // --------------------------------------------------------------------- //
    // Registration handle                                                     //
    // --------------------------------------------------------------------- //

    /**
     * Handle to a registered callback (Ticket 026).
     *
     * <p>Removing a registration is local and idempotent — it never touches the socket, so tearing
     * down one consumer's handlers cannot disturb another's subscription.
     */
    public interface Registration extends AutoCloseable {

        /** Stop delivering events to this callback. Idempotent. */
        void remove();

        /** Alias for {@link #remove()} so a registration works in try-with-resources. */
        @Override
        default void close() { remove(); }
    }

    // --------------------------------------------------------------------- //
    // Subscription API                                                        //
    // --------------------------------------------------------------------- //

    /**
     * Open the market channel and subscribe to the given asset IDs.
     *
     * <p>The asset IDs are ADDED to the authoritative subscription set. On a fresh connection the
     * client sends the documented initial frame (carrying the whole set and {@code initial_dump});
     * on an already-open connection it sends a dynamic update frame carrying only the new IDs.
     *
     * @param assetIds      token IDs to subscribe to (must not be empty)
     * @param customFeatures if {@code true}, also receive {@code best_bid_ask},
     *                       {@code new_market}, and {@code market_resolved} events
     */
    public synchronized void subscribeMarket(List<String> assetIds, boolean customFeatures) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw new IllegalArgumentException("assetIds must not be empty");
        }
        List<String> added = new ArrayList<>();
        for (String id : assetIds) {
            if (id != null && marketAssetIds.add(id)) {
                added.add(id);
            }
        }
        this.marketCustomFeatures = customFeatures || this.marketCustomFeatures;

        if (marketWs == null) {
            marketState.set(ConnectionState.connecting());
            marketAttempt.set(0);
            // The listener registered on this socket sends the INITIAL frame once the handshake
            // completes: sending before onOpen races the connection and the frame is dropped.
            marketWs = openChannel(wsBase + MARKET_PATH, new MarketWebSocketListener());
            return;
        }
        if (!added.isEmpty()) {
            sendMarketUpdate(marketWs, added, "subscribe");
        }
    }

    /** Convenience overload — no custom features. */
    public void subscribeMarket(List<String> assetIds) {
        subscribeMarket(assetIds, false);
    }

    /**
     * Remove the given asset IDs from the authoritative set and send an unsubscribe frame.
     *
     * <p>A reconnect after this restores only what remains, so an unsubscribed token never comes
     * back on its own.
     *
     * @param assetIds token IDs to unsubscribe from
     */
    public synchronized void unsubscribeMarket(List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        List<String> removed = new ArrayList<>();
        for (String id : assetIds) {
            if (marketAssetIds.remove(id)) {
                removed.add(id);
            }
        }
        if (marketWs != null && !removed.isEmpty()) {
            sendMarketUpdate(marketWs, removed, "unsubscribe");
        }
    }

    /**
     * Open the authenticated user channel and subscribe to the given market condition IDs.
     *
     * <p>Requires {@link Builder#apiKeyCreds(ApiKeyCreds)} and
     * {@link Builder#walletAddress(String)} to be set. Condition IDs are ADDED to the authoritative
     * user set.
     *
     * @param markets condition IDs to subscribe to
     */
    public synchronized void subscribeUser(List<String> markets) {
        requireUserAuth();
        List<String> added = new ArrayList<>();
        if (markets != null) {
            for (String market : markets) {
                if (market != null && userMarkets.add(market)) {
                    added.add(market);
                }
            }
        }

        if (userWs == null) {
            userState.set(ConnectionState.connecting());
            userAttempt.set(0);
            userWs = openChannel(wsBase + USER_PATH, new UserWebSocketListener());
            return;
        }
        if (!added.isEmpty()) {
            sendUserSubscription(userWs, added, "subscribe", false);
        }
    }

    /**
     * Remove the given condition IDs from the authoritative set and send an unsubscribe frame.
     *
     * @param markets condition IDs to unsubscribe from
     */
    public synchronized void unsubscribeUser(List<String> markets) {
        if (markets == null || markets.isEmpty()) {
            return;
        }
        List<String> removed = new ArrayList<>();
        for (String market : markets) {
            if (userMarkets.remove(market)) {
                removed.add(market);
            }
        }
        if (userWs != null && !removed.isEmpty()) {
            sendUserSubscription(userWs, removed, "unsubscribe", false);
        }
    }

    /** Close both the market and user WebSocket connections and stop all scheduled work. */
    public synchronized void close() {
        closed = true;
        cancelHeartbeat(ChannelType.MARKET);
        cancelHeartbeat(ChannelType.USER);
        scheduler.shutdownNow();
        if (marketWs != null) {
            marketWs.close(1000, "Client closed");
            marketWs = null;
        }
        if (userWs != null) {
            userWs.close(1000, "Client closed");
            userWs = null;
        }
        marketState.set(ConnectionState.disconnected());
        userState.set(ConnectionState.disconnected());
    }

    // --------------------------------------------------------------------- //
    // Health-check API                                                       //
    // --------------------------------------------------------------------- //

    /** Returns {@code true} if the market channel is connected. */
    public boolean isMarketConnected() {
        return marketState.get().isConnected();
    }

    /** Returns {@code true} if the user channel is connected. */
    public boolean isUserConnected() {
        return userState.get().isConnected();
    }

    /**
     * Returns the current {@link ConnectionState} of the given channel.
     *
     * @param channel {@link ChannelType#MARKET} or {@link ChannelType#USER}
     */
    public ConnectionState getConnectionState(ChannelType channel) {
        return switch (channel) {
            case MARKET -> marketState.get();
            case USER   -> userState.get();
        };
    }

    /**
     * Returns the channel's connection generation (Ticket 027): {@code 0} before it has ever
     * connected, then 1 for the first connection and incrementing on each reconnect.
     *
     * <p>Data received under an older generation predates the current connection and must not be
     * combined with data received under the current one.
     */
    public long getConnectionGeneration(ChannelType channel) {
        return switch (channel) {
            case MARKET -> marketGeneration.get();
            case USER   -> userGeneration.get();
        };
    }

    /** The authoritative market subscription set, in subscription order. */
    public synchronized List<String> getSubscribedAssetIds() {
        return List.copyOf(marketAssetIds);
    }

    /** The authoritative user subscription set, in subscription order. */
    public synchronized List<String> getSubscribedMarkets() {
        return List.copyOf(userMarkets);
    }

    /** Total number of subscribed asset IDs plus user market IDs across both channels. */
    public synchronized int getSubscriptionCount() {
        return marketAssetIds.size() + userMarkets.size();
    }

    // --------------------------------------------------------------------- //
    // Registration-only typed callbacks (Ticket 026)                         //
    // --------------------------------------------------------------------- //

    /**
     * Register a book-snapshot callback for the given tokens. Performs NO network action.
     *
     * @param assetIds tokens this callback cares about; empty or {@code null} means every token
     * @param callback receives each matching {@link BookUpdate}
     * @return a handle that removes the callback
     */
    public Registration registerBookUpdates(
        Collection<String> assetIds, Consumer<BookUpdate> callback) {
        return typedCallbacks.bookUpdates.register(assetIds, callback);
    }

    /**
     * Register a price-change callback for the given tokens. Performs NO network action.
     *
     * <p>A batched {@code price_change} frame is delivered when ANY entry in the batch matches the
     * filter; entries for other tokens are still present on the message, so a filtered consumer must
     * check each entry's {@code asset_id}.
     */
    public Registration registerPriceChanges(
        Collection<String> assetIds, Consumer<PriceChange> callback) {
        return typedCallbacks.priceChanges.register(assetIds, callback);
    }

    /** Register a last-trade-price callback for the given tokens. Performs NO network action. */
    public Registration registerLastTradePrices(
        Collection<String> assetIds, Consumer<LastTradePrice> callback) {
        return typedCallbacks.lastTradePrices.register(assetIds, callback);
    }

    /** Register a tick-size-change callback for the given tokens. Performs NO network action. */
    public Registration registerTickSizeChanges(
        Collection<String> assetIds, Consumer<TickSizeChange> callback) {
        return typedCallbacks.tickSizeChanges.register(assetIds, callback);
    }

    /**
     * Register a synthetic midpoint callback for the given tokens. Performs NO network action.
     *
     * <p>Midpoints are derived from {@link BookUpdate} events: {@code (bestBid + bestAsk) / 2}.
     * Registering one enables midpoint derivation regardless of the builder flag.
     */
    public Registration registerMidpointUpdates(
        Collection<String> assetIds, Consumer<MidpointUpdate> callback) {
        return typedCallbacks.midpointUpdates.register(assetIds, callback);
    }

    /**
     * Register a best-bid/ask callback for the given tokens. Performs NO network action.
     *
     * <p>These events only arrive when the subscription enables custom features — pass
     * {@code customFeatures = true} to {@link #subscribeMarket(List, boolean)}.
     */
    public Registration registerBestBidAsks(
        Collection<String> assetIds, Consumer<BestBidAsk> callback) {
        return typedCallbacks.bestBidAsks.register(assetIds, callback);
    }

    /** Register a new-market callback. Requires custom features on the subscription. */
    public Registration registerNewMarkets(
        Collection<String> assetIds, Consumer<NewMarket> callback) {
        return typedCallbacks.newMarkets.register(assetIds, callback);
    }

    /** Register a market-resolved callback. Requires custom features on the subscription. */
    public Registration registerMarketResolutions(
        Collection<String> assetIds, Consumer<MarketResolved> callback) {
        return typedCallbacks.marketResolutions.register(assetIds, callback);
    }

    /**
     * Register an order-update callback filtered by market condition ID. Performs NO network action.
     *
     * @param markets condition IDs this callback cares about; empty or {@code null} means all
     */
    public Registration registerOrders(
        Collection<String> markets, Consumer<OrderMessage> callback) {
        return typedCallbacks.orders.register(markets, callback);
    }

    /** Register a trade callback filtered by market condition ID. Performs NO network action. */
    public Registration registerTrades(
        Collection<String> markets, Consumer<TradeMessage> callback) {
        return typedCallbacks.trades.register(markets, callback);
    }

    /**
     * Register a callback for every user-channel event (orders and trades), filtered by market
     * condition ID. Performs NO network action.
     */
    public Registration registerUserEvents(
        Collection<String> markets, Consumer<WsMessage> callback) {
        return typedCallbacks.userEvents.register(markets, callback);
    }

    // --------------------------------------------------------------------- //
    // Deprecated register-and-subscribe callbacks                            //
    // --------------------------------------------------------------------- //

    /**
     * @deprecated Sends a subscribe frame as a side effect, so registering several handlers requests
     *     several initial dumps and a handler registered after the first frame can miss the snapshot
     *     it was registered for. Use {@link #registerBookUpdates} followed by one explicit
     *     {@link #subscribeMarket(List)} (Ticket 026).
     */
    @Deprecated
    public void onBookUpdate(List<String> assetIds, Consumer<BookUpdate> callback) {
        registerBookUpdates(assetIds, callback);
        subscribeMarket(assetIds);
    }

    /** @deprecated Use {@link #registerPriceChanges} plus one explicit subscribe (Ticket 026). */
    @Deprecated
    public void onPriceChange(List<String> assetIds, Consumer<PriceChange> callback) {
        registerPriceChanges(assetIds, callback);
        subscribeMarket(assetIds);
    }

    /** @deprecated Use {@link #registerLastTradePrices} plus one explicit subscribe (Ticket 026). */
    @Deprecated
    public void onLastTradePrice(List<String> assetIds, Consumer<LastTradePrice> callback) {
        registerLastTradePrices(assetIds, callback);
        subscribeMarket(assetIds);
    }

    /** @deprecated Use {@link #registerTickSizeChanges} plus one explicit subscribe (Ticket 026). */
    @Deprecated
    public void onTickSizeChange(List<String> assetIds, Consumer<TickSizeChange> callback) {
        registerTickSizeChanges(assetIds, callback);
        subscribeMarket(assetIds);
    }

    /** @deprecated Use {@link #registerMidpointUpdates} plus one explicit subscribe (Ticket 026). */
    @Deprecated
    public void onMidpointUpdate(List<String> assetIds, Consumer<MidpointUpdate> callback) {
        registerMidpointUpdates(assetIds, callback);
        subscribeMarket(assetIds);
    }

    /** @deprecated Use {@link #registerBestBidAsks} plus one explicit subscribe (Ticket 026). */
    @Deprecated
    public void onBestBidAsk(List<String> assetIds, Consumer<BestBidAsk> callback) {
        registerBestBidAsks(assetIds, callback);
        subscribeMarket(assetIds, true);
    }

    /** @deprecated Use {@link #registerNewMarkets} plus one explicit subscribe (Ticket 026). */
    @Deprecated
    public void onNewMarket(List<String> assetIds, Consumer<NewMarket> callback) {
        registerNewMarkets(assetIds, callback);
        subscribeMarket(assetIds, true);
    }

    /** @deprecated Use {@link #registerMarketResolutions} plus one explicit subscribe (Ticket 026). */
    @Deprecated
    public void onMarketResolved(List<String> assetIds, Consumer<MarketResolved> callback) {
        registerMarketResolutions(assetIds, callback);
        subscribeMarket(assetIds, true);
    }

    /** @deprecated Use {@link #registerUserEvents} plus one explicit {@link #subscribeUser(List)}. */
    @Deprecated
    public void onUserEvent(List<String> markets, Consumer<WsMessage> callback) {
        registerUserEvents(markets, callback);
        subscribeUser(markets);
    }

    /** @deprecated Use {@link #registerOrders} plus one explicit {@link #subscribeUser(List)}. */
    @Deprecated
    public void onOrder(List<String> markets, Consumer<OrderMessage> callback) {
        registerOrders(markets, callback);
        subscribeUser(markets);
    }

    /** @deprecated Use {@link #registerTrades} plus one explicit {@link #subscribeUser(List)}. */
    @Deprecated
    public void onTrade(List<String> markets, Consumer<TradeMessage> callback) {
        registerTrades(markets, callback);
        subscribeUser(markets);
    }

    /** Returns the current market-channel {@link WebSocket}, or {@code null} if not connected. */
    public WebSocket getMarketWebSocket() { return marketWs; }

    /** Returns the current user-channel {@link WebSocket}, or {@code null} if not connected. */
    public WebSocket getUserWebSocket() { return userWs; }

    // --------------------------------------------------------------------- //
    // Factory                                                                 //
    // --------------------------------------------------------------------- //

    public static Builder builder() { return new Builder(); }

    // --------------------------------------------------------------------- //
    // Subscription frames                                                     //
    // --------------------------------------------------------------------- //

    private WebSocket openChannel(String url, WebSocketListener wsListener) {
        Request request = new Request.Builder().url(url).build();
        log.debug("Connecting to WebSocket: {}", url);
        return okHttp.newWebSocket(request, wsListener);
    }

    /**
     * Send the INITIAL market subscription for the whole authoritative set: the documented frame
     * carrying {@code initial_dump}, which is what makes the server replay a full book snapshot for
     * every subscribed token. Sent on connect and on every reconnect.
     */
    private void sendMarketInitialSubscription(WebSocket ws, Collection<String> assetIds) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "market");
            msg.put("operation", "subscribe");
            ArrayNode ids = msg.putArray("assets_ids");
            assetIds.forEach(ids::add);
            msg.put("initial_dump", true);
            if (marketCustomFeatures) {
                msg.put("custom_feature_enabled", true);
            }
            ws.send(MAPPER.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize initial market subscription", e);
        }
    }

    /**
     * Send a DYNAMIC market update frame for a delta of tokens.
     *
     * <p>Deliberately omits {@code initial_dump}: that field belongs to the initial subscription, and
     * re-sending it on every add would make the server replay snapshots for tokens the consumer
     * already has in sync.
     */
    private void sendMarketUpdate(WebSocket ws, Collection<String> assetIds, String operation) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "market");
            msg.put("operation", operation);
            ArrayNode ids = msg.putArray("assets_ids");
            assetIds.forEach(ids::add);
            if (marketCustomFeatures) {
                msg.put("custom_feature_enabled", true);
            }
            ws.send(MAPPER.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize market {} request", operation, e);
        }
    }

    private void sendUserSubscription(
        WebSocket ws,
        Collection<String> markets,
        String operation,
        boolean initial
    ) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "user");
            msg.put("operation", operation);
            ArrayNode mkt = msg.putArray("markets");
            markets.forEach(mkt::add);
            if (initial) {
                msg.put("initial_dump", true);
            }
            appendAuth(msg);
            ws.send(MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("Failed to serialize user subscription request", e);
        }
    }

    /**
     * Attach credentials to a user-channel request, matching Polymarket's official
     * rs-clob-client-v2: a nested {@code auth} object holding {@code apiKey}, {@code secret},
     * and {@code passphrase} — no HMAC signature or timestamp. The full frame is
     * {@code {type, operation, markets, [initial_dump], auth}}. The older shape (top-level
     * {@code signature}/{@code timestamp}) makes the server drop the connection on receipt;
     * omitting {@code operation} makes it reply {@code INVALID OPERATION}.
     */
    private void appendAuth(ObjectNode node) {
        ObjectNode auth = node.putObject("auth");
        auth.put("apiKey",     apiKeyCreds.getKey());
        auth.put("secret",     apiKeyCreds.getSecret());
        auth.put("passphrase", apiKeyCreds.getPassphrase());
    }

    private void requireUserAuth() {
        if (apiKeyCreds == null || walletAddress == null) {
            throw new IllegalStateException(
                "apiKeyCreds and walletAddress are required for user-channel subscriptions");
        }
    }

    private static OkHttpClient defaultOkHttpClient() {
        return new OkHttpClient.Builder()
            .pingInterval(0, TimeUnit.SECONDS)      // the documented heartbeat is a text PING
            .readTimeout(0, TimeUnit.MILLISECONDS)  // disable read timeout for WS
            .build();
    }

    // --------------------------------------------------------------------- //
    // Heartbeats (Ticket 027)                                                //
    // --------------------------------------------------------------------- //

    /**
     * Start the documented text heartbeat for a channel: {@code PING} every 10 seconds, which the
     * server answers with {@code PONG}. An OkHttp protocol ping is NOT the documented protocol and
     * does not keep the subscription alive.
     */
    private void startHeartbeat(ChannelType channel) {
        cancelHeartbeat(channel);
        if (closed || pingIntervalMs <= 0) {
            return;
        }
        try {
            ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> sendPing(channel), pingIntervalMs, pingIntervalMs, TimeUnit.MILLISECONDS);
            if (channel == ChannelType.MARKET) {
                marketHeartbeat = task;
            } else {
                userHeartbeat = task;
            }
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // close() won the race
        }
    }

    private void sendPing(ChannelType channel) {
        WebSocket ws = channel == ChannelType.MARKET ? marketWs : userWs;
        if (ws == null || closed) {
            return;
        }
        try {
            ws.send(PING_FRAME);
        } catch (RuntimeException e) {
            // A send failure is surfaced through the socket's own failure callback; swallowing it
            // here keeps a doomed heartbeat from killing the shared scheduler thread.
            log.debug("{} channel heartbeat send failed: {}", channel, e.toString());
        }
    }

    private void cancelHeartbeat(ChannelType channel) {
        ScheduledFuture<?> task = channel == ChannelType.MARKET ? marketHeartbeat : userHeartbeat;
        if (task != null) {
            task.cancel(false);
        }
        if (channel == ChannelType.MARKET) {
            marketHeartbeat = null;
        } else {
            userHeartbeat = null;
        }
    }

    // --------------------------------------------------------------------- //
    // Listener invocation (exception-isolated, Ticket 027)                    //
    // --------------------------------------------------------------------- //

    /**
     * Run an application callback without letting it escape.
     *
     * <p>An exception thrown by a consumer's {@code onClose}/{@code onError} used to propagate out of
     * the OkHttp callback and skip the reconnect scheduling that followed it — leaving the channel
     * permanently dead while the consumer believed it was live.
     */
    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error e) {
            log.warn("WebSocket listener threw from {}: {}", what, e.toString(), e);
        }
    }

    // --------------------------------------------------------------------- //
    // Dispatch                                                                //
    // --------------------------------------------------------------------- //

    /** Dispatch a raw JSON frame to the registered listener. */
    private void dispatch(String text) {
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || "PONG".equalsIgnoreCase(trimmed) || "PING".equalsIgnoreCase(trimmed)) {
            return; // heartbeat traffic, not a message
        }
        try {
            // Handle JSON arrays (batch) vs single objects
            JsonNode root = MAPPER.readTree(trimmed);
            if (root.isArray()) {
                for (JsonNode element : root) {
                    dispatchNode(element.toString());
                }
            } else {
                dispatchNode(trimmed);
            }
        } catch (Exception e) {
            log.warn("Failed to parse WebSocket frame: {}", text, e);
            safely("onError", () -> listener.onError(e));
        }
    }

    private void dispatchNode(String json) throws Exception {
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        safely("onMessage", () -> listener.onMessage(msg));
        typedCallbacks.dispatch(msg);

        // Optionally derive MidpointUpdate from BookUpdate
        if ((emitMidpointUpdates || typedCallbacks.midpointUpdates.hasCallbacks())
            && msg instanceof BookUpdate book) {
            emitMidpoint(book);
        }
    }

    private void emitMidpoint(BookUpdate book) {
        List<OrderBookLevel> bids = book.getBids();
        List<OrderBookLevel> asks = book.getAsks();
        if (bids == null || asks == null || bids.isEmpty() || asks.isEmpty()) return;
        try {
            BigDecimal bid = new BigDecimal(bids.get(0).getPrice());
            BigDecimal ask = new BigDecimal(asks.get(0).getPrice());
            BigDecimal mid = bid.add(ask).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);

            MidpointUpdate update = new MidpointUpdate();
            update.setAssetId(book.getAssetId());
            update.setMarket(book.getMarket());
            update.setMidpoint(mid.toPlainString());
            update.setTimestamp(book.getTimestamp());
            safely("onMessage", () -> listener.onMessage(update));
            typedCallbacks.dispatch(update);
        } catch (NumberFormatException e) {
            log.debug("Cannot compute midpoint: {}", e.getMessage());
        }
    }

    // --------------------------------------------------------------------- //
    // Inner WebSocketListeners                                                //
    // --------------------------------------------------------------------- //

    private class MarketWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            long generation = marketGeneration.incrementAndGet();
            marketOpenedAtMs.set(System.currentTimeMillis());
            marketState.set(ConnectionState.connected());
            log.debug("Market channel opened (generation {})", generation);

            List<String> ids;
            synchronized (WsClient.this) {
                ids = List.copyOf(marketAssetIds);
            }
            // Signal BEFORE the subscription frame: everything that follows belongs to this
            // generation, so a consumer clears freshness here and cannot mix in pre-reconnect data.
            safely("onResubscribe", () -> listener.onResubscribe(ChannelType.MARKET, generation));
            if (!ids.isEmpty()) {
                sendMarketInitialSubscription(ws, ids);
            }
            startHeartbeat(ChannelType.MARKET);
            safely("onOpen", () -> listener.onOpen(ChannelType.MARKET, generation));
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            dispatch(text);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response r) {
            if (closed) {
                log.debug("Market channel stopped after client close: {}", t.toString());
                return;
            }
            long generation = marketGeneration.get();
            try {
                log.error("Market channel failure", t);
                marketState.set(ConnectionState.disconnected());
                cancelHeartbeat(ChannelType.MARKET);
                synchronized (WsClient.this) { marketWs = null; }
                Exception error = t instanceof Exception ex ? ex : new RuntimeException(t);
                safely("onError", () -> listener.onError(ChannelType.MARKET, generation, error));
            } finally {
                // In `finally` so a throwing application callback can never strand the channel.
                scheduleReconnect(ChannelType.MARKET);
            }
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            ws.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            if (closed) {
                return;
            }
            long generation = marketGeneration.get();
            try {
                log.debug("Market channel closed: {} {}", code, reason);
                marketState.set(ConnectionState.disconnected());
                cancelHeartbeat(ChannelType.MARKET);
                synchronized (WsClient.this) { marketWs = null; }
                safely("onClose",
                    () -> listener.onClose(ChannelType.MARKET, generation, code, reason));
            } finally {
                scheduleReconnect(ChannelType.MARKET);
            }
        }
    }

    private class UserWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            long generation = userGeneration.incrementAndGet();
            userOpenedAtMs.set(System.currentTimeMillis());
            userState.set(ConnectionState.connected());
            log.debug("User channel opened (generation {})", generation);

            List<String> markets;
            synchronized (WsClient.this) {
                markets = List.copyOf(userMarkets);
            }
            safely("onResubscribe", () -> listener.onResubscribe(ChannelType.USER, generation));
            // The user channel is authenticated by the subscription frame itself, so it is sent even
            // with an empty market list (an empty list means "every market").
            sendUserSubscription(ws, markets, "subscribe", true);
            startHeartbeat(ChannelType.USER);
            safely("onOpen", () -> listener.onOpen(ChannelType.USER, generation));
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            dispatch(text);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response r) {
            if (closed) {
                log.debug("User channel stopped after client close: {}", t.toString());
                return;
            }
            long generation = userGeneration.get();
            try {
                log.error("User channel failure", t);
                userState.set(ConnectionState.disconnected());
                cancelHeartbeat(ChannelType.USER);
                synchronized (WsClient.this) { userWs = null; }
                Exception error = t instanceof Exception ex ? ex : new RuntimeException(t);
                safely("onError", () -> listener.onError(ChannelType.USER, generation, error));
            } finally {
                scheduleReconnect(ChannelType.USER);
            }
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            ws.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            if (closed) {
                return;
            }
            long generation = userGeneration.get();
            try {
                log.debug("User channel closed: {} {}", code, reason);
                userState.set(ConnectionState.disconnected());
                cancelHeartbeat(ChannelType.USER);
                synchronized (WsClient.this) { userWs = null; }
                safely("onClose", () -> listener.onClose(ChannelType.USER, generation, code, reason));
            } finally {
                scheduleReconnect(ChannelType.USER);
            }
        }
    }

    // --------------------------------------------------------------------- //
    // Reconnect logic                                                         //
    // --------------------------------------------------------------------- //

    /**
     * Schedule a reconnect attempt for the given channel with exponential backoff.
     *
     * <p>The delay for attempt {@code n} (1-based) is:
     * {@code min(reconnectDelayMs * 2^(n-1), maxReconnectDelayMs)}.
     *
     * <p>The attempt counter is reset only by a connection that STAYED UP for
     * {@code stableConnectionMs}. A server that completes the handshake and closes immediately
     * therefore burns the retry budget instead of resetting it every cycle.
     */
    private void scheduleReconnect(ChannelType channel) {
        if (closed) return;

        boolean hasSubscriptions;
        synchronized (this) {
            hasSubscriptions = channel == ChannelType.MARKET
                ? !marketAssetIds.isEmpty()
                : !userMarkets.isEmpty() || apiKeyCreds != null;
        }
        if (!hasSubscriptions) {
            log.debug("{} channel: nothing subscribed — not reconnecting", channel);
            return;
        }

        AtomicInteger attemptCounter = channel == ChannelType.MARKET ? marketAttempt : userAttempt;
        AtomicLong openedAt = channel == ChannelType.MARKET ? marketOpenedAtMs : userOpenedAtMs;
        long uptime = openedAt.get() == 0 ? 0 : System.currentTimeMillis() - openedAt.get();
        if (uptime >= stableConnectionMs) {
            attemptCounter.set(0);
        }
        openedAt.set(0);

        int attempt = attemptCounter.incrementAndGet();

        if (maxReconnectAttempts > 0 && attempt > maxReconnectAttempts) {
            log.warn("{} channel: max reconnect attempts ({}) reached — giving up",
                channel, maxReconnectAttempts);
            return;
        }

        long delay = Math.min(reconnectDelayMs * (1L << Math.min(attempt - 1, 30)),
                              maxReconnectDelayMs);

        if (channel == ChannelType.MARKET) {
            marketState.set(ConnectionState.reconnecting(attempt));
        } else {
            userState.set(ConnectionState.reconnecting(attempt));
        }

        log.info("{} channel: scheduling reconnect attempt {} in {} ms", channel, attempt, delay);
        try {
            scheduler.schedule(() -> doReconnect(channel, attempt), delay, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // close() won the race after the closed check above
        }
    }

    private synchronized void doReconnect(ChannelType channel, int attempt) {
        if (closed) return;
        log.info("{} channel: reconnecting (attempt {})", channel, attempt);

        // The reconnected socket re-subscribes from its own onOpen, using the authoritative set —
        // so an add or removal made while the channel was down is honoured on reconnect.
        if (channel == ChannelType.MARKET) {
            if (marketWs != null) return;   // already reconnected
            if (marketAssetIds.isEmpty()) return;
            marketState.set(ConnectionState.connecting());
            marketWs = openChannel(wsBase + MARKET_PATH, new MarketWebSocketListener());
        } else {
            if (userWs != null) return;
            if (apiKeyCreds == null) return;
            userState.set(ConnectionState.connecting());
            userWs = openChannel(wsBase + USER_PATH, new UserWebSocketListener());
        }
    }

    // --------------------------------------------------------------------- //
    // Builder                                                                 //
    // --------------------------------------------------------------------- //

    /**
     * Builder for {@link WsClient}.
     *
     * <pre>{@code
     * WsClient client = WsClient.builder()
     *     .listener(myListener)
     *     .build();
     * }</pre>
     */
    public static final class Builder {

        private String wsBase = DEFAULT_WS_BASE;
        private OkHttpClient okHttp;
        private WsMessageListener listener;
        private ApiKeyCreds apiKeyCreds;
        private String walletAddress;
        private boolean emitMidpointUpdates = false;
        // Reconnect defaults: unlimited attempts, 1 s base, 60 s cap
        private int maxReconnectAttempts = 0;
        private long reconnectDelayMs    = 1_000L;
        private long maxReconnectDelayMs = 60_000L;
        private long pingIntervalMs      = DEFAULT_PING_INTERVAL_MS;
        private long stableConnectionMs  = DEFAULT_STABLE_CONNECTION_MS;

        private Builder() {}

        /** Override the WebSocket base URL (default: {@value WsClient#DEFAULT_WS_BASE}). */
        public Builder wsBase(String wsBase) {
            this.wsBase = Objects.requireNonNull(wsBase);
            return this;
        }

        /** Supply a pre-configured {@link OkHttpClient} (optional). */
        public Builder okHttpClient(OkHttpClient okHttp) {
            this.okHttp = okHttp;
            return this;
        }

        /** The message listener — required. */
        public Builder listener(WsMessageListener listener) {
            this.listener = Objects.requireNonNull(listener);
            return this;
        }

        /**
         * API key credentials for the user channel (required for
         * {@link WsClient#subscribeUser(List)}).
         */
        public Builder apiKeyCreds(ApiKeyCreds creds) {
            this.apiKeyCreds = creds;
            return this;
        }

        /**
         * Wallet address used as user identity on the user channel (required for
         * {@link WsClient#subscribeUser(List)}).
         */
        public Builder walletAddress(String address) {
            this.walletAddress = address;
            return this;
        }

        /**
         * When {@code true}, the client will emit a synthetic {@link MidpointUpdate}
         * message after every {@link BookUpdate} that has both a best bid and a best ask.
         */
        public Builder emitMidpointUpdates(boolean emit) {
            this.emitMidpointUpdates = emit;
            return this;
        }

        /**
         * Maximum number of reconnect attempts per channel before giving up.
         * A value {@code <= 0} means unlimited retries (default).
         */
        public Builder maxReconnectAttempts(int max) {
            this.maxReconnectAttempts = max;
            return this;
        }

        /**
         * Base reconnect delay in milliseconds (default: 1000 ms).
         * The delay is doubled on each successive attempt up to {@link #maxReconnectDelayMs(long)}.
         */
        public Builder reconnectDelayMs(long delayMs) {
            if (delayMs <= 0) throw new IllegalArgumentException("reconnectDelayMs must be > 0");
            this.reconnectDelayMs = delayMs;
            return this;
        }

        /**
         * Maximum reconnect delay cap in milliseconds (default: 60 000 ms).
         */
        public Builder maxReconnectDelayMs(long maxDelayMs) {
            if (maxDelayMs <= 0) throw new IllegalArgumentException("maxReconnectDelayMs must be > 0");
            this.maxReconnectDelayMs = maxDelayMs;
            return this;
        }

        /**
         * Interval between documented text {@code PING} frames, per open channel
         * (default: 10 000 ms, the documented cadence). A value {@code <= 0} disables the heartbeat.
         */
        public Builder pingIntervalMs(long intervalMs) {
            this.pingIntervalMs = intervalMs;
            return this;
        }

        /**
         * How long a connection must stay up before its reconnect budget resets
         * (default: 30 000 ms). Lower it only in tests.
         */
        public Builder stableConnectionMs(long stableMs) {
            if (stableMs < 0) throw new IllegalArgumentException("stableConnectionMs must be >= 0");
            this.stableConnectionMs = stableMs;
            return this;
        }

        public WsClient build() {
            return new WsClient(this);
        }
    }

    // --------------------------------------------------------------------- //
    // TypedCallbackRegistry                                                   //
    // --------------------------------------------------------------------- //

    /**
     * One callback plus the token/market filter it was registered with.
     *
     * <p>An empty filter means "everything"; otherwise the message must match or the callback is not
     * invoked. Holding the filter alongside the callback is what stops a handler registered for one
     * token from seeing another token's events.
     */
    private static final class FilteredCallback<T> {
        private final Set<String> filter;
        private final Consumer<T> callback;

        FilteredCallback(Collection<String> filter, Consumer<T> callback) {
            this.filter = filter == null || filter.isEmpty()
                ? Collections.emptySet()
                : Set.copyOf(filter);
            this.callback = callback;
        }

        boolean matches(Predicate<Set<String>> messageMatchesFilter) {
            return filter.isEmpty() || messageMatchesFilter.test(filter);
        }
    }

    /** A list of filtered callbacks for one message type. */
    private static final class CallbackList<T> {

        private final CopyOnWriteArrayList<FilteredCallback<T>> callbacks =
            new CopyOnWriteArrayList<>();

        Registration register(Collection<String> filter, Consumer<T> callback) {
            Objects.requireNonNull(callback, "callback must not be null");
            FilteredCallback<T> entry = new FilteredCallback<>(filter, callback);
            callbacks.add(entry);
            // Removal is by identity and idempotent: removing twice is a no-op, never a surprise.
            return () -> callbacks.remove(entry);
        }

        boolean hasCallbacks() {
            return !callbacks.isEmpty();
        }

        /**
         * Invoke every callback whose filter matches. One callback throwing must not stop the rest —
         * consumers are independent and a recorder's bug must not blind the trading path.
         */
        void dispatch(T message, Predicate<Set<String>> messageMatchesFilter) {
            for (FilteredCallback<T> entry : callbacks) {
                if (!entry.matches(messageMatchesFilter)) {
                    continue;
                }
                try {
                    entry.callback.accept(message);
                } catch (RuntimeException | Error e) {
                    log.warn("WebSocket callback threw: {}", e.toString(), e);
                }
            }
        }
    }

    /**
     * Holds per-message-type callback lists.
     *
     * <p>All lists use {@link CopyOnWriteArrayList} so that callbacks can be registered
     * from any thread while dispatch happens on the OkHttp reader thread.
     */
    private static final class TypedCallbackRegistry {

        final CallbackList<BookUpdate>     bookUpdates       = new CallbackList<>();
        final CallbackList<PriceChange>    priceChanges      = new CallbackList<>();
        final CallbackList<LastTradePrice> lastTradePrices   = new CallbackList<>();
        final CallbackList<TickSizeChange> tickSizeChanges   = new CallbackList<>();
        final CallbackList<MidpointUpdate> midpointUpdates   = new CallbackList<>();
        final CallbackList<BestBidAsk>     bestBidAsks       = new CallbackList<>();
        final CallbackList<NewMarket>      newMarkets        = new CallbackList<>();
        final CallbackList<MarketResolved> marketResolutions = new CallbackList<>();
        final CallbackList<WsMessage>      userEvents        = new CallbackList<>();
        final CallbackList<OrderMessage>   orders            = new CallbackList<>();
        final CallbackList<TradeMessage>   trades            = new CallbackList<>();

        /** Matcher for a message identified by a single asset/market ID. */
        private static Predicate<Set<String>> byId(String id) {
            return filter -> id != null && filter.contains(id);
        }

        /**
         * Route {@code msg} to all matching typed callback lists.
         *
         * <p>For user-channel messages ({@link OrderMessage}, {@link TradeMessage}),
         * the message is also forwarded to the generic {@code userEvents} list.
         */
        void dispatch(WsMessage msg) {
            if (msg instanceof BookUpdate book) {
                bookUpdates.dispatch(book, byId(book.getAssetId()));
            } else if (msg instanceof PriceChange pc) {
                // A batch is delivered when ANY entry touches a filtered token.
                priceChanges.dispatch(pc, filter -> batchTouches(pc, filter));
            } else if (msg instanceof LastTradePrice ltp) {
                lastTradePrices.dispatch(ltp, byId(ltp.getAssetId()));
            } else if (msg instanceof TickSizeChange tsc) {
                tickSizeChanges.dispatch(tsc, byId(tsc.getAssetId()));
            } else if (msg instanceof MidpointUpdate mid) {
                midpointUpdates.dispatch(mid, byId(mid.getAssetId()));
            } else if (msg instanceof BestBidAsk bba) {
                bestBidAsks.dispatch(bba, byId(bba.getAssetId()));
            } else if (msg instanceof NewMarket nm) {
                // These lifecycle events name every token in the market, so match on any of them.
                newMarkets.dispatch(nm, filter -> containsAny(filter, nm.getAssetIds()));
            } else if (msg instanceof MarketResolved mr) {
                marketResolutions.dispatch(mr, filter -> containsAny(filter, mr.getAssetIds()));
            } else if (msg instanceof OrderMessage order) {
                orders.dispatch(order, byId(order.getMarket()));
                userEvents.dispatch(order, byId(order.getMarket()));
            } else if (msg instanceof TradeMessage trade) {
                trades.dispatch(trade, byId(trade.getMarket()));
                userEvents.dispatch(trade, byId(trade.getMarket()));
            }
        }

        private static boolean containsAny(Set<String> filter, List<String> ids) {
            if (ids == null) {
                return false;
            }
            for (String id : ids) {
                if (id != null && filter.contains(id)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean batchTouches(PriceChange pc, Set<String> filter) {
            List<PriceChangeBatchEntry> entries = pc.getPriceChanges();
            if (entries == null) {
                return false;
            }
            for (PriceChangeBatchEntry entry : entries) {
                if (entry != null && filter.contains(entry.getAssetId())) {
                    return true;
                }
            }
            return false;
        }
    }
}
