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
import com.polymarket.ws.model.TickSizeChange;
import com.polymarket.ws.model.TradeMessage;
import com.polymarket.ws.model.WsMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
 * <p>Build via the nested {@link Builder}:
 * <pre>{@code
 * WsClient ws = WsClient.builder()
 *     .listener(myListener)
 *     .build();
 * ws.subscribeMarket(List.of("token123..."));
 * }</pre>
 *
 * <p>For user-channel subscriptions supply {@link ApiKeyCreds} and the wallet
 * address to the builder:
 * <pre>{@code
 * WsClient ws = WsClient.builder()
 *     .apiKeyCreds(creds)
 *     .walletAddress("0x...")
 *     .listener(myListener)
 *     .build();
 * ws.subscribeUser(List.of("0xConditionId..."));
 * }</pre>
 *
 * <p>Call {@link #close()} to release the WebSocket connection.
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

    /** Registry of typed per-subscription callbacks (Rust parity). */
    private final TypedCallbackRegistry typedCallbacks = new TypedCallbackRegistry();

    // Reconnect configuration
    private final int maxReconnectAttempts; // <=0 means unlimited
    private final long reconnectDelayMs;    // base delay; doubles each attempt
    private final long maxReconnectDelayMs;

    // Scheduler for reconnect tasks (shared; single-threaded daemon thread)
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "polymarket-ws-reconnect");
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

    // Stored subscriptions for re-subscription after reconnect
    private volatile List<String> marketAssetIds = Collections.emptyList();
    private volatile boolean marketCustomFeatures = false;
    private volatile List<String> userMarkets = Collections.emptyList();

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
    }

    // --------------------------------------------------------------------- //
    // Public API                                                              //
    // --------------------------------------------------------------------- //

    /**
     * Open the market channel and subscribe to the given asset IDs.
     *
     * <p>The underlying WebSocket connection is established lazily on the first call
     * to this method.  Subsequent calls reuse the same connection and send a new
     * subscribe frame.
     *
     * @param assetIds      token IDs to subscribe to (must not be empty)
     * @param customFeatures if {@code true}, also receive {@code best_bid_ask},
     *                       {@code new_market}, and {@code market_resolved} events
     */
    public synchronized void subscribeMarket(List<String> assetIds, boolean customFeatures) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw new IllegalArgumentException("assetIds must not be empty");
        }
        // Store for re-subscription after reconnect
        this.marketAssetIds = new ArrayList<>(assetIds);
        this.marketCustomFeatures = customFeatures;

        if (marketWs == null) {
            marketState.set(ConnectionState.connecting());
            marketAttempt.set(0);
            marketWs = openChannel(wsBase + MARKET_PATH, new MarketWebSocketListener());
        }
        sendMarketSubscription(marketWs, assetIds, customFeatures, "subscribe");
    }

    /** Convenience overload — no custom features. */
    public void subscribeMarket(List<String> assetIds) {
        subscribeMarket(assetIds, false);
    }

    /**
     * Send an unsubscribe frame for the given asset IDs on the market channel.
     *
     * @param assetIds token IDs to unsubscribe from
     */
    public synchronized void unsubscribeMarket(List<String> assetIds) {
        if (marketWs == null) return;
        sendMarketSubscription(marketWs, assetIds, false, "unsubscribe");
    }

    /**
     * Open the authenticated user channel and subscribe to the given market condition IDs.
     *
     * <p>Requires {@link Builder#apiKeyCreds(ApiKeyCreds)} and
     * {@link Builder#walletAddress(String)} to be set.
     *
     * @param markets condition IDs to subscribe to
     */
    public synchronized void subscribeUser(List<String> markets) {
        requireUserAuth();
        // Store for re-subscription after reconnect
        this.userMarkets = new ArrayList<>(markets);

        if (userWs == null) {
            userState.set(ConnectionState.connecting());
            userAttempt.set(0);
            userWs = openChannel(wsBase + USER_PATH, new UserWebSocketListener());
        }
        sendUserSubscription(userWs, markets, "subscribe");
    }

    /**
     * Send an unsubscribe frame for the given condition IDs on the user channel.
     *
     * @param markets condition IDs to unsubscribe from
     */
    public synchronized void unsubscribeUser(List<String> markets) {
        if (userWs == null) return;
        sendUserSubscription(userWs, markets, "unsubscribe");
    }

    /** Close both the market and user WebSocket connections and stop the reconnect scheduler. */
    public synchronized void close() {
        closed = true;
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
    // Health-check API (Milestone 3)                                         //
    // --------------------------------------------------------------------- //

    /**
     * Returns {@code true} if the market channel is in the {@link ConnectionState.Connected} state.
     */
    public boolean isMarketConnected() {
        return marketState.get().isConnected();
    }

    /**
     * Returns {@code true} if the user channel is in the {@link ConnectionState.Connected} state.
     */
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
     * Returns the total number of subscribed asset IDs across both channels.
     *
     * <p>Counts market channel asset IDs plus user channel market IDs.
     */
    public int getSubscriptionCount() {
        return marketAssetIds.size() + userMarkets.size();
    }

    // --------------------------------------------------------------------- //
    // Typed per-subscription callbacks (Rust parity)                         //
    // --------------------------------------------------------------------- //

    /**
     * Subscribe to real-time orderbook snapshots and register a typed callback.
     *
     * <p>Mirrors Rust {@code subscribe_orderbook() -> Stream<BookUpdate>}.
     * Multiple callbacks may be registered; all are invoked for each update.
     *
     * @param assetIds token IDs to monitor (opens the market channel if not yet connected)
     * @param callback receives each {@link BookUpdate}
     */
    public void onBookUpdate(List<String> assetIds, Consumer<BookUpdate> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        typedCallbacks.bookUpdates.add(callback);
        subscribeMarket(assetIds);
    }

    /**
     * Subscribe to real-time price change events and register a typed callback.
     *
     * <p>Mirrors Rust {@code subscribe_prices() -> Stream<PriceChange>}.
     *
     * @param assetIds token IDs to monitor
     * @param callback receives each {@link PriceChange}
     */
    public void onPriceChange(List<String> assetIds, Consumer<PriceChange> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        typedCallbacks.priceChanges.add(callback);
        subscribeMarket(assetIds);
    }

    /**
     * Subscribe to last-trade-price updates and register a typed callback.
     *
     * <p>Mirrors Rust {@code subscribe_last_trade_price() -> Stream<LastTradePrice>}.
     *
     * @param assetIds token IDs to monitor
     * @param callback receives each {@link LastTradePrice}
     */
    public void onLastTradePrice(List<String> assetIds, Consumer<LastTradePrice> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        typedCallbacks.lastTradePrices.add(callback);
        subscribeMarket(assetIds);
    }

    /**
     * Subscribe to tick-size-change events and register a typed callback.
     *
     * <p>Mirrors Rust {@code subscribe_tick_size_change() -> Stream<TickSizeChange>}.
     *
     * @param assetIds token IDs to monitor
     * @param callback receives each {@link TickSizeChange}
     */
    public void onTickSizeChange(List<String> assetIds, Consumer<TickSizeChange> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        typedCallbacks.tickSizeChanges.add(callback);
        subscribeMarket(assetIds);
    }

    /**
     * Subscribe to synthetic midpoint updates and register a typed callback.
     *
     * <p>Midpoints are derived from {@link BookUpdate} events: {@code (bestBid + bestAsk) / 2}.
     * Calling this method automatically enables midpoint derivation regardless of the
     * {@link Builder#emitMidpointUpdates(boolean)} builder flag.
     *
     * <p>Mirrors Rust {@code subscribe_midpoints() -> Stream<MidpointUpdate>}.
     *
     * @param assetIds token IDs to monitor
     * @param callback receives each {@link MidpointUpdate}
     */
    public void onMidpointUpdate(List<String> assetIds, Consumer<MidpointUpdate> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        typedCallbacks.midpointUpdates.add(callback);
        subscribeMarket(assetIds);
    }

    /**
     * Subscribe to best-bid/ask events (custom features) and register a typed callback.
     *
     * <p>Enables {@code custom_feature_enabled} on the subscription frame.
     * Mirrors Rust {@code subscribe_best_bid_ask() -> Stream<BestBidAsk>}.
     *
     * @param assetIds token IDs to monitor
     * @param callback receives each {@link BestBidAsk}
     */
    public void onBestBidAsk(List<String> assetIds, Consumer<BestBidAsk> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        typedCallbacks.bestBidAsks.add(callback);
        subscribeMarket(assetIds, true);
    }

    /**
     * Subscribe to new-market events (custom features) and register a typed callback.
     *
     * <p>Enables {@code custom_feature_enabled} on the subscription frame.
     * Mirrors Rust {@code subscribe_new_markets() -> Stream<NewMarket>}.
     *
     * @param assetIds token IDs to monitor
     * @param callback receives each {@link NewMarket}
     */
    public void onNewMarket(List<String> assetIds, Consumer<NewMarket> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        typedCallbacks.newMarkets.add(callback);
        subscribeMarket(assetIds, true);
    }

    /**
     * Subscribe to market-resolved events (custom features) and register a typed callback.
     *
     * <p>Enables {@code custom_feature_enabled} on the subscription frame.
     * Mirrors Rust {@code subscribe_market_resolutions() -> Stream<MarketResolved>}.
     *
     * @param assetIds token IDs to monitor
     * @param callback receives each {@link MarketResolved}
     */
    public void onMarketResolved(List<String> assetIds, Consumer<MarketResolved> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        typedCallbacks.marketResolutions.add(callback);
        subscribeMarket(assetIds, true);
    }

    /**
     * Subscribe to all user-channel events and register a typed callback.
     *
     * <p>Receives both {@link TradeMessage} and {@link OrderMessage} events.
     * Requires {@link Builder#apiKeyCreds(ApiKeyCreds)} and
     * {@link Builder#walletAddress(String)} to be set.
     * Mirrors Rust {@code subscribe_user_events() -> Stream<WsMessage>}.
     *
     * @param markets condition IDs to monitor
     * @param callback receives each raw {@link WsMessage} from the user channel
     */
    public void onUserEvent(List<String> markets, Consumer<WsMessage> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        typedCallbacks.userEvents.add(callback);
        subscribeUser(markets);
    }

    /**
     * Subscribe to order-update events on the user channel and register a typed callback.
     *
     * <p>Receives only {@link OrderMessage} events (placements, fills, cancellations).
     * Requires authentication.
     * Mirrors Rust {@code subscribe_orders() -> Stream<OrderMessage>}.
     *
     * @param markets condition IDs to monitor
     * @param callback receives each {@link OrderMessage}
     */
    public void onOrder(List<String> markets, Consumer<OrderMessage> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        typedCallbacks.orders.add(callback);
        subscribeUser(markets);
    }

    /**
     * Subscribe to trade-execution events on the user channel and register a typed callback.
     *
     * <p>Receives only {@link TradeMessage} events.
     * Requires authentication.
     * Mirrors Rust {@code subscribe_trades() -> Stream<TradeMessage>}.
     *
     * @param markets condition IDs to monitor
     * @param callback receives each {@link TradeMessage}
     */
    public void onTrade(List<String> markets, Consumer<TradeMessage> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        typedCallbacks.trades.add(callback);
        subscribeUser(markets);
    }

    /**
     * Returns the current market-channel {@link WebSocket}, or {@code null} if
     * not yet connected.
     */
    public WebSocket getMarketWebSocket() { return marketWs; }

    /**
     * Returns the current user-channel {@link WebSocket}, or {@code null} if
     * not yet connected.
     */
    public WebSocket getUserWebSocket() { return userWs; }

    // --------------------------------------------------------------------- //
    // Factory                                                                 //
    // --------------------------------------------------------------------- //

    public static Builder builder() { return new Builder(); }

    // --------------------------------------------------------------------- //
    // Internal helpers                                                        //
    // --------------------------------------------------------------------- //

    private WebSocket openChannel(String url, WebSocketListener wsListener) {
        Request request = new Request.Builder().url(url).build();
        log.debug("Connecting to WebSocket: {}", url);
        return okHttp.newWebSocket(request, wsListener);
    }

    private static void sendMarketSubscription(
        WebSocket ws,
        List<String> assetIds,
        boolean customFeatures,
        String operation
    ) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "market");
            msg.put("operation", operation);
            ArrayNode ids = msg.putArray("assets_ids");
            assetIds.forEach(ids::add);
            if ("subscribe".equals(operation)) {
                msg.put("initial_dump", true);
            }
            if (customFeatures) {
                msg.put("custom_feature_enabled", true);
            }
            ws.send(MAPPER.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize market subscription request", e);
        }
    }

    private void sendUserSubscription(
        WebSocket ws,
        List<String> markets,
        String operation
    ) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "user");
            ArrayNode mkt = msg.putArray("markets");
            markets.forEach(mkt::add);
            if ("subscribe".equals(operation)) {
                appendAuth(msg);
            } else {
                msg.put("operation", operation);
            }
            ws.send(MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("Failed to serialize user subscription request", e);
        }
    }

    /**
     * Attach credentials to a user-channel subscribe request.
     *
     * <p>The Polymarket user channel expects a nested {@code auth} object holding
     * {@code apiKey}, {@code secret}, and {@code passphrase} — no HMAC signature or
     * timestamp. Sending the older top-level {@code signature}/{@code timestamp}
     * shape makes the server drop the connection the instant the frame arrives.
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
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)   // disable read timeout for WS
            .build();
    }

    /** Dispatch a raw JSON frame to the registered listener. */
    private void dispatch(String text) {
        try {
            // Handle JSON arrays (batch) vs single objects
            JsonNode root = MAPPER.readTree(text);
            if (root.isArray()) {
                for (JsonNode element : root) {
                    dispatchNode(element.toString());
                }
            } else {
                dispatchNode(text);
            }
        } catch (Exception e) {
            log.warn("Failed to parse WebSocket frame: {}", text, e);
            listener.onError(e);
        }
    }

    private void dispatchNode(String json) throws Exception {
        WsMessage msg = MAPPER.readValue(json, WsMessage.class);
        listener.onMessage(msg);
        typedCallbacks.dispatch(msg);

        // Optionally derive MidpointUpdate from BookUpdate
        if ((emitMidpointUpdates || typedCallbacks.hasMidpointCallbacks()) && msg instanceof BookUpdate book) {
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
            listener.onMessage(update);
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
            log.debug("Market channel opened");
            marketAttempt.set(0);
            marketState.set(ConnectionState.connected());
            listener.onOpen();
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            dispatch(text);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response r) {
            log.error("Market channel failure", t);
            marketState.set(ConnectionState.disconnected());
            synchronized (WsClient.this) { marketWs = null; }
            listener.onError(t instanceof Exception ? (Exception) t : new RuntimeException(t));
            scheduleReconnect(ChannelType.MARKET);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            log.debug("Market channel closed: {} {}", code, reason);
            marketState.set(ConnectionState.disconnected());
            synchronized (WsClient.this) { marketWs = null; }
            listener.onClose(code, reason);
        }
    }

    private class UserWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            log.debug("User channel opened");
            userAttempt.set(0);
            userState.set(ConnectionState.connected());
            listener.onOpen();
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            dispatch(text);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response r) {
            log.error("User channel failure", t);
            userState.set(ConnectionState.disconnected());
            synchronized (WsClient.this) { userWs = null; }
            listener.onError(t instanceof Exception ? (Exception) t : new RuntimeException(t));
            scheduleReconnect(ChannelType.USER);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            log.debug("User channel closed: {} {}", code, reason);
            userState.set(ConnectionState.disconnected());
            synchronized (WsClient.this) { userWs = null; }
            listener.onClose(code, reason);
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
     */
    private void scheduleReconnect(ChannelType channel) {
        if (closed) return;

        AtomicInteger attemptCounter = channel == ChannelType.MARKET ? marketAttempt : userAttempt;
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
        scheduler.schedule(() -> doReconnect(channel, attempt), delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void doReconnect(ChannelType channel, int attempt) {
        if (closed) return;
        log.info("{} channel: reconnecting (attempt {})", channel, attempt);

        if (channel == ChannelType.MARKET) {
            if (marketWs != null) return; // already reconnected
            List<String> ids = marketAssetIds;
            if (ids.isEmpty()) return;    // nothing to re-subscribe
            marketState.set(ConnectionState.connecting());
            marketWs = openChannel(wsBase + MARKET_PATH, new MarketWebSocketListener());
            sendMarketSubscription(marketWs, ids, marketCustomFeatures, "subscribe");
        } else {
            if (userWs != null) return;
            List<String> markets = userMarkets;
            if (markets.isEmpty()) return;
            userState.set(ConnectionState.connecting());
            userWs = openChannel(wsBase + USER_PATH, new UserWebSocketListener());
            sendUserSubscription(userWs, markets, "subscribe");
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

        public WsClient build() {
            return new WsClient(this);
        }
    }

    // --------------------------------------------------------------------- //
    // TypedCallbackRegistry                                                   //
    // --------------------------------------------------------------------- //

    /**
     * Holds per-message-type callback lists.
     *
     * <p>All lists use {@link CopyOnWriteArrayList} so that callbacks can be registered
     * from any thread while dispatch happens on the OkHttp reader thread.
     */
    private static final class TypedCallbackRegistry {

        final CopyOnWriteArrayList<Consumer<BookUpdate>>     bookUpdates      = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Consumer<PriceChange>>    priceChanges     = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Consumer<LastTradePrice>> lastTradePrices  = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Consumer<TickSizeChange>> tickSizeChanges  = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Consumer<MidpointUpdate>> midpointUpdates  = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Consumer<BestBidAsk>>     bestBidAsks      = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Consumer<NewMarket>>      newMarkets       = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Consumer<MarketResolved>> marketResolutions = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Consumer<WsMessage>>      userEvents       = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Consumer<OrderMessage>>   orders           = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Consumer<TradeMessage>>   trades           = new CopyOnWriteArrayList<>();

        /** Returns {@code true} if any midpoint callbacks are registered. */
        boolean hasMidpointCallbacks() {
            return !midpointUpdates.isEmpty();
        }

        /**
         * Route {@code msg} to all matching typed callback lists.
         *
         * <p>For user-channel messages ({@link OrderMessage}, {@link TradeMessage}),
         * the message is also forwarded to the generic {@code userEvents} list.
         */
        void dispatch(WsMessage msg) {
            if (msg instanceof BookUpdate book) {
                bookUpdates.forEach(cb -> cb.accept(book));
            } else if (msg instanceof PriceChange pc) {
                priceChanges.forEach(cb -> cb.accept(pc));
            } else if (msg instanceof LastTradePrice ltp) {
                lastTradePrices.forEach(cb -> cb.accept(ltp));
            } else if (msg instanceof TickSizeChange tsc) {
                tickSizeChanges.forEach(cb -> cb.accept(tsc));
            } else if (msg instanceof MidpointUpdate mid) {
                midpointUpdates.forEach(cb -> cb.accept(mid));
            } else if (msg instanceof BestBidAsk bba) {
                bestBidAsks.forEach(cb -> cb.accept(bba));
            } else if (msg instanceof NewMarket nm) {
                newMarkets.forEach(cb -> cb.accept(nm));
            } else if (msg instanceof MarketResolved mr) {
                marketResolutions.forEach(cb -> cb.accept(mr));
            } else if (msg instanceof OrderMessage order) {
                orders.forEach(cb -> cb.accept(order));
                userEvents.forEach(cb -> cb.accept(msg));
            } else if (msg instanceof TradeMessage trade) {
                trades.forEach(cb -> cb.accept(trade));
                userEvents.forEach(cb -> cb.accept(msg));
            }
        }
    }
}
