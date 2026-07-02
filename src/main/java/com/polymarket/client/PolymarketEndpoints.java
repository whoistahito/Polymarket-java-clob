package com.polymarket.client;

/**
 * Centralized endpoint and header constants for Polymarket.
 *
 * <p>Notes:
 * <ul>
 *   <li>CLOB API base: https://clob.polymarket.com</li>
 *   <li>Gamma API base: https://gamma-api.polymarket.com</li>
 *   <li>Auth header names follow py-clob-client: underscores, not hyphens.</li>
 * </ul>
 */
public final class PolymarketEndpoints {

    private PolymarketEndpoints() {}

    // =========================================================================
    // Base URLs
    // =========================================================================

    /** Default Polymarket CLOB host (trading + CLOB market metadata). */
    public static final String CLOB_BASE_URL = "https://clob.polymarket.com";

    /** Polymarket Gamma API host (market discovery/data). */
    public static final String GAMMA_BASE_URL = "https://gamma-api.polymarket.com";

    // =========================================================================
    // Auth Headers (exact, underscore form)
    // =========================================================================

    public static final String HDR_POLY_ADDRESS = "POLY_ADDRESS";
    public static final String HDR_POLY_SIGNATURE = "POLY_SIGNATURE";
    public static final String HDR_POLY_TIMESTAMP = "POLY_TIMESTAMP";

    /** Used by L1 attestation signing (wallet control message). */
    public static final String HDR_POLY_NONCE = "POLY_NONCE";

    /** Used by L2 HMAC auth. */
    public static final String HDR_POLY_API_KEY = "POLY_API_KEY";

    /** Used by L2 HMAC auth. */
    public static final String HDR_POLY_PASSPHRASE = "POLY_PASSPHRASE";

    // =========================================================================
    // CLOB Auth Endpoints
    // =========================================================================

    /** GET in py-clob-client (derive existing API key for address+nonce). */
    public static final String CLOB_DERIVE_API_KEY = "/auth/derive-api-key";

    /** Create API key (used by py-clob-client as POST). */
    public static final String CLOB_CREATE_API_KEY = "/auth/api-key";

    /** List API keys (L2). */
    public static final String CLOB_GET_API_KEYS = "/auth/api-keys";

    /** Alias used internally. */
    public static final String CLOB_API_KEYS = CLOB_GET_API_KEYS;

    /** Delete API key (L2). */
    public static final String CLOB_DELETE_API_KEY = "/auth/api-key";

    // =========================================================================
    // CLOB Market / Pricing Endpoints
    // =========================================================================

    /** Server time endpoint. */
    public static final String CLOB_TIME = "/time";

    /** CLOB order-protocol version endpoint (PMK-004). Returns {@code {"version": 1|2}}. */
    public static final String CLOB_VERSION = "/version";

    /** CLOB markets list (cursor-based in py-clob-client). */
    public static final String CLOB_GET_MARKETS = "/markets";

    /** Alias for CLOB_GET_MARKETS. */
    public static final String CLOB_MARKETS = CLOB_GET_MARKETS;

    /** Simplified markets endpoint. */
    public static final String CLOB_SIMPLIFIED_MARKETS = "/simplified-markets";

    /** Sampling markets endpoint. */
    public static final String CLOB_SAMPLING_MARKETS = "/sampling-markets";

    /** Sampling simplified markets endpoint. */
    public static final String CLOB_SAMPLING_SIMPLIFIED_MARKETS = "/sampling-simplified-markets";

    /** Market details by condition id (append condition id). */
    public static final String CLOB_GET_MARKET_PREFIX = "/markets/";

    /** Executable price endpoint. */
    public static final String CLOB_PRICE = "/price";

    /** Spread endpoint. */
    public static final String CLOB_SPREAD = "/spread";

    /** Tick size endpoint. */
    public static final String CLOB_TICK_SIZE = "/tick-size";

    /** Fee rate endpoint. */
    public static final String CLOB_FEE_RATE = "/fee-rate";

    /** Negative risk endpoint. */
    public static final String CLOB_NEG_RISK = "/neg-risk";

    /** Last trade price endpoint. */
    public static final String CLOB_LAST_TRADE_PRICE = "/last-trade-price";

    /** Full order book endpoint. */
    public static final String CLOB_ORDER_BOOK = "/book";

    /** Alias used internally. */
    public static final String CLOB_BOOK = CLOB_ORDER_BOOK;

    /** Multiple order books endpoint. */
    public static final String CLOB_ORDER_BOOKS = "/books";

    /** Midpoint endpoint. */
    public static final String CLOB_MIDPOINT = "/midpoint";

    /** Multiple midpoints endpoint. */
    public static final String CLOB_MIDPOINTS = "/midpoints";

    /** Multiple prices endpoint. */
    public static final String CLOB_PRICES = "/prices";

    /** Multiple spreads endpoint. */
    public static final String CLOB_SPREADS = "/spreads";

    /** Last trades prices endpoint. */
    public static final String CLOB_LAST_TRADES_PRICES = "/last-trades-prices";

    /** Prices history endpoint. */
    public static final String CLOB_PRICES_HISTORY = "/prices-history";

    // =========================================================================
    // CLOB Orders / User Data Endpoints
    // =========================================================================

    /** Post single order. */
    public static final String CLOB_POST_ORDER = "/order";

    /** Post multiple orders. */
    public static final String CLOB_POST_ORDERS = "/orders";

    /** Cancel single order (DELETE /order with body {"orderID": "..."}). */
    public static final String CLOB_CANCEL_ORDER = "/order";

    /** Cancel multiple orders. */
    public static final String CLOB_CANCEL_ORDERS = "/orders";

    /** Cancel all orders. */
    public static final String CLOB_CANCEL_ALL = "/cancel-all";

    /** Cancel market orders. */
    public static final String CLOB_CANCEL_MARKET_ORDERS = "/cancel-market-orders";

    /** Get single order details (append order id). */
    public static final String CLOB_GET_ORDER_PREFIX = "/data/order/";

    /** Alias used internally for single order lookup. */
    public static final String CLOB_ORDER = CLOB_GET_ORDER_PREFIX;

    /** List user orders. */
    public static final String CLOB_GET_ORDERS = "/data/orders";

    /** Alias used internally for open orders list. */
    public static final String CLOB_OPEN_ORDERS = CLOB_GET_ORDERS;

    /** Get trades. */
    public static final String CLOB_GET_TRADES = "/data/trades";

    /** Alias used internally for trades. */
    public static final String CLOB_TRADES = CLOB_GET_TRADES;

    /** Order scoring endpoint. */
    public static final String CLOB_ORDER_SCORING = "/order-scoring";

    /** Orders scoring endpoint. */
    public static final String CLOB_ORDERS_SCORING = "/orders-scoring";

    // =========================================================================
    // CLOB Balance/Allowance Endpoints
    // =========================================================================

    /** Balance allowance endpoint. */
    public static final String CLOB_BALANCE_ALLOWANCE = "/balance-allowance";

    /** Update balance allowance endpoint. */
    public static final String CLOB_UPDATE_BALANCE_ALLOWANCE = "/balance-allowance/update";

    // =========================================================================
    // CLOB Notifications Endpoints
    // =========================================================================

    /** Notifications endpoint. */
    public static final String CLOB_NOTIFICATIONS = "/notifications";

    // =========================================================================
    // CLOB Heartbeat Endpoint
    // =========================================================================

    /** Heartbeat endpoint. */
    public static final String CLOB_HEARTBEAT = "/v1/heartbeats";

    // =========================================================================
    // CLOB Readonly API Key Endpoints
    // =========================================================================

    /** Create readonly API key. */
    public static final String CLOB_CREATE_READONLY_API_KEY = "/auth/readonly-api-key";

    /** Get readonly API keys. */
    public static final String CLOB_GET_READONLY_API_KEYS = "/auth/readonly-api-keys";

    /** Delete readonly API key. */
    public static final String CLOB_DELETE_READONLY_API_KEY = "/auth/readonly-api-key";

    /** Validate readonly API key. */
    public static final String CLOB_VALIDATE_READONLY_API_KEY = "/auth/validate-readonly-api-key";

    // =========================================================================
    // CLOB Ban Status Endpoint
    // =========================================================================

    /** Closed only mode/ban status. */
    public static final String CLOB_CLOSED_ONLY = "/auth/ban-status/closed-only";

    // =========================================================================
    // CLOB Rewards Endpoints
    // =========================================================================

    /** User earnings for day. */
    public static final String CLOB_REWARDS_USER = "/rewards/user";

    /** Total user earnings for day. */
    public static final String CLOB_REWARDS_USER_TOTAL = "/rewards/user/total";

    /** Liquidity reward percentages. */
    public static final String CLOB_REWARDS_PERCENTAGES = "/rewards/user/percentages";

    /** Current market rewards. */
    public static final String CLOB_REWARDS_MARKETS_CURRENT = "/rewards/markets/current";

    /** Market rewards (append market id). */
    public static final String CLOB_REWARDS_MARKETS_PREFIX = "/rewards/markets/";

    /** Rewards earnings percentages. */
    public static final String CLOB_REWARDS_USER_MARKETS = "/rewards/user/markets";

    // =========================================================================
    // CLOB Live Activity Endpoints
    // =========================================================================

    /** Market trades events (append market id). */
    public static final String CLOB_LIVE_ACTIVITY_EVENTS_PREFIX = "/live-activity/events/";

    // =========================================================================
    // CLOB Builder Trades Endpoint
    // =========================================================================

    /** Builder trades endpoint. */
    public static final String CLOB_BUILDER_TRADES = "/builder/trades";

    // =========================================================================
    // CLOB Builder API Key Endpoints
    // =========================================================================

    /** Create builder API key. */
    public static final String CLOB_CREATE_BUILDER_API_KEY = "/auth/builder-api-key";

    /** Get builder API keys. */
    public static final String CLOB_GET_BUILDER_API_KEYS = "/auth/builder-api-key";

    /** Revoke builder API key. */
    public static final String CLOB_REVOKE_BUILDER_API_KEY = "/auth/builder-api-key";

    // =========================================================================
    // RFQ Endpoints
    // =========================================================================

    /** Create RFQ request. */
    public static final String RFQ_CREATE_REQUEST = "/rfq/request";

    /** Cancel RFQ request (DELETE). */
    public static final String RFQ_CANCEL_REQUEST = "/rfq/request";

    /** List RFQ requests. */
    public static final String RFQ_GET_REQUESTS = "/rfq/data/requests";

    /** Create RFQ quote. */
    public static final String RFQ_CREATE_QUOTE = "/rfq/quote";

    /** Cancel RFQ quote (DELETE). */
    public static final String RFQ_CANCEL_QUOTE = "/rfq/quote";

    /** Accept an RFQ request (taker side). */
    public static final String RFQ_ACCEPT_REQUEST = "/rfq/request/accept";

    /** Approve an RFQ order (maker side). */
    public static final String RFQ_APPROVE_QUOTE = "/rfq/quote/approve";

    /** Get RFQ quotes for the requester. */
    public static final String RFQ_REQUESTER_QUOTES = "/rfq/data/requester/quotes";

    /** Get RFQ quotes for the quoter. */
    public static final String RFQ_QUOTER_QUOTES = "/rfq/data/quoter/quotes";

    /** Get the best RFQ quote. */
    public static final String RFQ_BEST_QUOTE = "/rfq/data/best-quote";

    /** RFQ config endpoint. */
    public static final String RFQ_CONFIG = "/rfq/config";

    // =========================================================================
    // Gamma API Endpoints
    // =========================================================================

    /** Gamma markets list endpoint (used for scanning). */
    public static final String GAMMA_MARKETS = "/markets";

    // =========================================================================
    // Helper utilities
    // =========================================================================

    /**
     * Builds a full URL from a base URL and a path.
     * This does not perform URL encoding.
     */
    public static String url(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    /** Convenience: builds a CLOB URL for an endpoint path. */
    public static String clobUrl(String path) {
        return url(CLOB_BASE_URL, path);
    }

    /** Convenience: builds a Gamma URL for an endpoint path. */
    public static String gammaUrl(String path) {
        return url(GAMMA_BASE_URL, path);
    }
}
