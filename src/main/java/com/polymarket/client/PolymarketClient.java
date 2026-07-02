package com.polymarket.client;

import static com.polymarket.client.PolymarketEndpoints.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.polymarket.model.BalanceAllowanceParams;
import com.polymarket.model.BalanceAllowanceResponse;
import com.polymarket.model.BanStatus;
import com.polymarket.model.BookParams;
import com.polymarket.model.BuilderApiKey;
import com.polymarket.model.BuilderApiKeyResponse;
import com.polymarket.model.BuilderTrade;
import com.polymarket.model.CreateOrderOptions;
import com.polymarket.model.DropNotificationParams;
import com.polymarket.model.GammaMarket;
import com.polymarket.model.LastTradePriceResult;
import com.polymarket.model.MarketPrice;
import com.polymarket.model.MarketReward;
import com.polymarket.model.MarketTradeEvent;
import com.polymarket.model.Notification;
import com.polymarket.model.OpenOrder;
import com.polymarket.model.OrderBookSummary;
import com.polymarket.model.OrderMarketCancelParams;
import com.polymarket.model.OrderResponse;
import com.polymarket.model.OrderScoring;
import com.polymarket.model.OrderSummary;
import com.polymarket.model.OrderType;
import com.polymarket.model.PaginationPayload;
import com.polymarket.model.PostOrderPayload;
import com.polymarket.model.PriceHistoryFilterParams;
import com.polymarket.model.ReadonlyApiKeyResponse;
import com.polymarket.model.Side;
import com.polymarket.model.SignatureType;
import com.polymarket.model.SignedOrder;
import com.polymarket.model.SpreadResult;
import com.polymarket.model.TotalUserEarning;
import com.polymarket.model.Trade;
import com.polymarket.model.UserEarning;
import com.polymarket.model.UserMarketOrder;
import com.polymarket.model.UserOrder;
import com.polymarket.model.UserRewardsEarning;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;

/** Main client for interacting with the Polymarket API. */
public final class PolymarketClient {

  private static final Logger log = LoggerFactory.getLogger(PolymarketClient.class);
  private static final String TIME = "time";
  private static final int MAX_ORDERS_PER_REQUEST = 15;

  /** Cursor value representing the first page in cursor-based pagination. */
  public static final String INITIAL_CURSOR = "MA==";

  /** Cursor value returned on the last page — iteration should stop here. */
  public static final String END_CURSOR = "LTE=";

  private final String clobHost;
  private final String gammaHost;
  private final int chainId;
  private final Credentials credentials;
  private final ApiKeyCreds apiCreds;
  private final String funderAddress;
  private final SignatureType signatureType;
  private final HttpClient http;
  private final L1Eip712Signer l1Signer;
  private final L2HmacSigner l2Signer;
  private final OrderBuilder orderBuilder;
  private final boolean useServerTime;
  private final String geoBlockToken;

  private final Map<String, String> tickSizeCache = new ConcurrentHashMap<>();
  private final Map<String, Integer> feeRateCache = new ConcurrentHashMap<>();
  private final Map<String, Boolean> negRiskCache = new ConcurrentHashMap<>();

  /** Cached CLOB order-protocol version (PMK-004). {@code 0} = not yet resolved. */
  private volatile int cachedVersion = 0;

  private final GammaClient gammaClient;
  private final DataClient dataClient;
  private final HeartbeatManager heartbeatManager;

  private PolymarketClient(Builder builder) {
    this.clobHost = builder.clobHost;
    this.gammaHost = builder.gammaHost;
    this.chainId = builder.chainId;
    this.credentials = builder.credentials;
    this.apiCreds = builder.apiCreds;
    this.funderAddress = builder.funderAddress;
    this.signatureType = builder.signatureType;
    this.http = builder.http;
    this.useServerTime = builder.useServerTime;
    this.geoBlockToken = builder.geoBlockToken;

    this.l1Signer = new L1Eip712Signer();
    this.l2Signer = new L2HmacSigner();

    if (this.credentials != null) {
      this.orderBuilder =
          new OrderBuilder(this.credentials, this.chainId, this.signatureType, this.funderAddress);
    } else {
      this.orderBuilder = null;
    }

    this.gammaClient = new GammaClient.Builder().host(this.gammaHost).httpClient(this.http).build();

    this.dataClient = new DataClient.Builder().httpClient(this.http).build();

    this.heartbeatManager = new HeartbeatManager(id -> postHeartbeat(id));
  }

  public static class Builder {

    private String clobHost = "https://clob.polymarket.com";
    private String gammaHost = "https://gamma-api.polymarket.com";
    private int chainId = 137; // Polygon Mainnet
    private Credentials credentials;
    private ApiKeyCreds apiCreds;
    private String funderAddress;
    private SignatureType signatureType = SignatureType.EOA;
    private HttpClient http;
    private ProxyConfig proxyConfig;
    private boolean useServerTime = false;
    private String geoBlockToken;
    private int maxRetries = 0;

    public Builder clobHost(String clobHost) {
      this.clobHost = stripTrailingSlash(clobHost);
      return this;
    }

    public Builder gammaHost(String gammaHost) {
      this.gammaHost = stripTrailingSlash(gammaHost);
      return this;
    }

    public Builder chainId(int chainId) {
      this.chainId = chainId;
      return this;
    }

    /** Type-safe overload; delegates to {@link #chainId(int)}. */
    public Builder chainId(com.polymarket.model.Chain chain) {
      this.chainId = chain.getId();
      return this;
    }

    public Builder privateKey(String privateKey) {
      this.credentials = Credentials.create(privateKey);
      return this;
    }

    public Builder credentials(Credentials credentials) {
      this.credentials = credentials;
      return this;
    }

    public Builder apiCreds(ApiKeyCreds apiCreds) {
      this.apiCreds = apiCreds;
      return this;
    }

    public Builder funderAddress(String funderAddress) {
      this.funderAddress = funderAddress;
      return this;
    }

    public Builder signatureType(SignatureType signatureType) {
      this.signatureType = signatureType != null ? signatureType : SignatureType.EOA;
      return this;
    }

    public Builder httpClient(HttpClient http) {
      this.http = http;
      return this;
    }

    public Builder proxy(ProxyConfig proxyConfig) {
      this.proxyConfig = proxyConfig;
      return this;
    }

    public Builder proxy(String host, int port, String username, String password) {
      this.proxyConfig = new ProxyConfig(host, port, username, password);
      return this;
    }

    public Builder proxy(String host, int port) {
      this.proxyConfig = new ProxyConfig(host, port);
      return this;
    }

    public Builder useServerTime(boolean useServerTime) {
      this.useServerTime = useServerTime;
      return this;
    }

    public Builder geoBlockToken(String geoBlockToken) {
      this.geoBlockToken = geoBlockToken;
      return this;
    }

    public Builder maxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    public PolymarketClient build() {
      Objects.requireNonNull(credentials, "credentials (or privateKey) is required");
      if (http == null) {
        HttpClient.Builder httpBuilder = new HttpClient.Builder();
        if (proxyConfig != null) {
          httpBuilder.proxy(proxyConfig);
        }
        if (maxRetries > 0) {
          httpBuilder.maxRetries(maxRetries);
        }
        this.http = httpBuilder.build();
      }
      return new PolymarketClient(this);
    }

    private static String stripTrailingSlash(String s) {
      return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
  }

  // Accessors

  public String getAddress() {
    if (funderAddress != null && !funderAddress.isEmpty()) {
      return funderAddress;
    }
    return credentials != null ? credentials.getAddress() : null;
  }

  private String getSignerAddress() {
    return credentials != null ? credentials.getAddress() : null;
  }

  public int getChainId() {
    return chainId;
  }

  public String getFunderAddress() {
    return funderAddress;
  }

  public SignatureType getSignatureType() {
    return signatureType;
  }

  public boolean hasApiCreds() {
    return apiCreds != null;
  }

  public ApiKeyCreds getApiCreds() {
    return apiCreds;
  }

  /** Returns the RFQ sub-client. */
  public RfqClient rfq() {
    return new RfqClient(this);
  }

  /** Returns the Gamma API sub-client. */
  public GammaClient gamma() {
    return gammaClient;
  }

  /** Returns the Data API sub-client ({@code https://data-api.polymarket.com}). */
  public DataClient data() {
    return dataClient;
  }

  // Package-private helpers used by RfqClient

  HttpClient getHttp() {
    return http;
  }

  OrderBuilder getOrderBuilder() {
    return orderBuilder;
  }

  String getClobUrl(String path) {
    return clobUrl(path);
  }

  // --- Utilities ---

  public long getServerTime() throws IOException {
    String response = http.get(clobUrl(CLOB_TIME), Collections.emptyMap());
    return Long.parseLong(response.trim());
  }

  // --- Auth Endpoints ---

  public ApiKeyCreds deriveApiKey() throws IOException {
    return deriveApiKey(0); // 0 is default nonce
  }

  public ApiKeyCreds deriveApiKey(int nonce) throws IOException {
    if (credentials == null) {
      throw new IllegalStateException("Private key required to derive API key");
    }
    long timestamp = useServerTime ? getServerTime() : System.currentTimeMillis() / 1000;
    Map<String, String> headers = l1Signer.createL1Headers(credentials, chainId, nonce, timestamp);
    String response = http.get(clobUrl(CLOB_DERIVE_API_KEY), headers);
    return parseApiKeyCreds(http.parseJsonObject(response));
  }

  public ApiKeyCreds createApiKey() throws IOException {
    return createApiKey(0);
  }

  public ApiKeyCreds createApiKey(int nonce) throws IOException {
    if (credentials == null) {
      throw new IllegalStateException("Private key required to create API key");
    }
    long timestamp = useServerTime ? getServerTime() : System.currentTimeMillis() / 1000;
    Map<String, String> headers = l1Signer.createL1Headers(credentials, chainId, nonce, timestamp);
    String response = http.postJsonRaw(clobUrl(CLOB_CREATE_API_KEY), headers, "{}");
    return parseApiKeyCreds(http.parseJsonObject(response));
  }

  public ApiKeyCreds createOrDeriveApiKey() throws IOException {
    return createOrDeriveApiKey(0);
  }

  public ApiKeyCreds createOrDeriveApiKey(int nonce) throws IOException {
    try {
      return createApiKey(nonce);
    } catch (HttpStatusException e) {
      // Server rejected create (e.g. key already exists for this nonce) -> derive it.
      // Non-status IOExceptions (network/parse) propagate, matching the Rust SDK.
      return deriveApiKey(nonce);
    }
  }

  /** Parse raw API key response ({@code apiKey}/{@code key}, secret, passphrase). */
  private static ApiKeyCreds parseApiKeyCreds(Map<String, Object> raw) throws IOException {
    // TS API returns { apiKey, secret, passphrase }
    String key = requireStringField(raw, "auth/api-key", "apiKey", "key", "api_key");
    String secret = requireStringField(raw, "auth/api-key", "secret");
    String passphrase = requireStringField(raw, "auth/api-key", "passphrase");
    return new ApiKeyCreds(key, secret, passphrase);
  }

  // --- Market Data ---

  public Map<String, Object> getMarkets(String nextCursor) throws IOException {
    Map<String, String> params = new HashMap<>();
    params.put("next_cursor", nextCursor != null ? nextCursor : INITIAL_CURSOR);
    String response =
        http.get(clobUrl(CLOB_MARKETS) + buildQueryString(params), Collections.emptyMap());
    return http.parseJsonObject(response);
  }

  public Map<String, Object> getMarket(String conditionId) throws IOException {
    String response = http.get(clobUrl(CLOB_MARKETS + "/" + conditionId), Collections.emptyMap());
    return http.parseJsonObject(response);
  }

  public PaginationPayload<Map<String, Object>> getSimplifiedMarkets(String nextCursor)
      throws IOException {
    Map<String, String> params = new HashMap<>();
    params.put("next_cursor", nextCursor != null ? nextCursor : INITIAL_CURSOR);
    String response =
        http.get(
            clobUrl(CLOB_SIMPLIFIED_MARKETS) + buildQueryString(params), Collections.emptyMap());
    return http.parseJson(response, new TypeReference<PaginationPayload<Map<String, Object>>>() {});
  }

  public PaginationPayload<Map<String, Object>> getSamplingMarkets(String nextCursor)
      throws IOException {
    Map<String, String> params = new HashMap<>();
    params.put("next_cursor", nextCursor != null ? nextCursor : INITIAL_CURSOR);
    String response =
        http.get(clobUrl(CLOB_SAMPLING_MARKETS) + buildQueryString(params), Collections.emptyMap());
    return http.parseJson(response, new TypeReference<PaginationPayload<Map<String, Object>>>() {});
  }

  public PaginationPayload<Map<String, Object>> getSamplingSimplifiedMarkets(String nextCursor)
      throws IOException {
    Map<String, String> params = new HashMap<>();
    params.put("next_cursor", nextCursor != null ? nextCursor : INITIAL_CURSOR);
    String response =
        http.get(
            clobUrl(CLOB_SAMPLING_SIMPLIFIED_MARKETS) + buildQueryString(params),
            Collections.emptyMap());
    return http.parseJson(response, new TypeReference<PaginationPayload<Map<String, Object>>>() {});
  }

  public List<GammaMarket> getGammaMarkets(Map<String, String> params) throws IOException {
    String response =
        http.get(gammaUrl(GAMMA_MARKETS) + buildQueryString(params), Collections.emptyMap());
    return http.parseJson(response, new TypeReference<List<GammaMarket>>() {});
  }

  public String getTickSize(String tokenId) throws IOException {
    if (tickSizeCache.containsKey(tokenId)) {
      return tickSizeCache.get(tokenId);
    }
    return fetchTickSize(tokenId);
  }

  /**
   * Clears the tick-size, fee-rate, and neg-risk caches for all tokens.
   *
   * <p>Mirrors the TypeScript {@code ClobClient.clearTickSizeCache()} method.
   */
  public void clearTickSizeCache() {
    tickSizeCache.clear();
    feeRateCache.clear();
    negRiskCache.clear();
  }

  /**
   * Removes the tick-size, fee-rate, and neg-risk cache entries for a single token.
   *
   * <p>Mirrors the TypeScript {@code ClobClient.clearTickSizeCache(tokenId)} overload.
   *
   * @param tokenId the token ID whose cache entries should be evicted
   */
  public void clearTickSizeCache(String tokenId) {
    tickSizeCache.remove(tokenId);
    feeRateCache.remove(tokenId);
    negRiskCache.remove(tokenId);
  }

  /**
   * Computes the SHA-1 fingerprint for an order-book snapshot.
   *
   * <p>Delegates to {@link com.polymarket.util.PriceUtils#generateOrderBookSummaryHash}. Mirrors
   * the TypeScript {@code ClobClient.getOrderBookHash(orderbook)} method.
   *
   * @param orderbook the order-book snapshot
   * @return hex SHA-1 digest string
   */
  public String getOrderBookHash(OrderBookSummary orderbook) {
    return com.polymarket.util.PriceUtils.generateOrderBookSummaryHash(orderbook);
  }

  public int getFeeRateBps(String tokenId) throws IOException {
    if (feeRateCache.containsKey(tokenId)) {
      return feeRateCache.get(tokenId);
    }
    return fetchFeeRate(tokenId);
  }

  public boolean getNegRisk(String tokenId) throws IOException {
    if (negRiskCache.containsKey(tokenId)) {
      return negRiskCache.get(tokenId);
    }
    return fetchNegRisk(tokenId);
  }

  /**
   * Resolves the CLOB order-protocol version via {@code GET /version} (PMK-004).
   *
   * <p>The first successful call hits the network and caches the result; subsequent calls return
   * the cached value. {@code force=true} bypasses the cache and re-hits the network — call this
   * after a post-order failure mentioning {@code order_version_mismatch}.
   *
   * @param force {@code true} to force a network refresh
   * @return the resolved version (1 or 2)
   */
  public int resolveVersion(boolean force) throws IOException {
    if (!force && cachedVersion != 0) {
      return cachedVersion;
    }
    String response = http.get(clobUrl(CLOB_VERSION), Collections.emptyMap());
    Map<String, Object> parsed = http.parseJsonObject(response);
    Object v = parsed.get("version");
    if (v == null) {
      throw new IOException("Unexpected /version response: " + response);
    }
    int version;
    try {
      version = Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      throw new IOException("Invalid /version value: " + v, e);
    }
    cachedVersion = version;
    applyVersionToBuilder();
    return version;
  }

  /** Resolves and caches the version without forcing (mirrors Rust {@code resolve_version(false)}). */
  public int resolveVersion() throws IOException {
    return resolveVersion(false);
  }

  /** Returns the cached version (0 if not yet resolved). */
  public int getCachedVersion() {
    return cachedVersion;
  }

  /** Clears the cached version (e.g. on {@code order_version_mismatch} before a forced refresh). */
  public void clearVersionCache() {
    cachedVersion = 0;
    applyVersionToBuilder();
  }

  private void applyVersionToBuilder() {
    if (orderBuilder != null && cachedVersion != 0) {
      orderBuilder.setVersion(cachedVersion);
    }
  }

  public OrderBookSummary getOrderBook(String tokenId) throws IOException {
    Map<String, String> params = new HashMap<>();
    params.put("token_id", tokenId);
    String response =
        http.get(clobUrl(CLOB_BOOK) + buildQueryString(params), Collections.emptyMap());
    return http.parseJson(response, OrderBookSummary.class);
  }

  public List<OrderBookSummary> getOrderBooks(List<BookParams> params) throws IOException {
    String body = http.toJsonMinified(params);
    String response = http.postJsonRaw(clobUrl(CLOB_ORDER_BOOKS), Collections.emptyMap(), body);
    return http.parseJson(response, new TypeReference<List<OrderBookSummary>>() {});
  }

  public BigDecimal getMidpoint(String tokenId) throws IOException {
    Map<String, String> params = new HashMap<>();
    params.put("token_id", tokenId);
    String response =
        http.get(clobUrl(CLOB_MIDPOINT) + buildQueryString(params), Collections.emptyMap());
    Map<String, Object> json = http.parseJsonObject(response);
    return parseBigDecimal(json, "mid");
  }

  /** Returns a map of token id to midpoint price. The endpoint responds with a JSON object. */
  public Map<String, BigDecimal> getMidpoints(List<BookParams> params) throws IOException {
    String body = http.toJsonMinified(params);
    String response = http.postJsonRaw(clobUrl(CLOB_MIDPOINTS), Collections.emptyMap(), body);
    return http.parseJson(response, new TypeReference<Map<String, BigDecimal>>() {});
  }

  public BigDecimal getPrice(String tokenId, String side) throws IOException {
    Map<String, String> params = new HashMap<>();
    params.put("token_id", tokenId);
    params.put("side", side);
    String response =
        http.get(clobUrl(CLOB_PRICE) + buildQueryString(params), Collections.emptyMap());
    Map<String, Object> json = http.parseJsonObject(response);
    return parseBigDecimal(json, "price");
  }

  /** Returns a map of token id to {@code {side: price}}. The endpoint responds with a JSON object. */
  public Map<String, Map<Side, BigDecimal>> getPrices(List<BookParams> params) throws IOException {
    String body = http.toJsonMinified(params);
    String response = http.postJsonRaw(clobUrl(CLOB_PRICES), Collections.emptyMap(), body);
    return http.parseJson(response, new TypeReference<Map<String, Map<Side, BigDecimal>>>() {});
  }

  public SpreadResult getSpread(String tokenId) throws IOException {
    Map<String, String> params = new HashMap<>();
    params.put("token_id", tokenId);
    String response =
        http.get(clobUrl(CLOB_SPREAD) + buildQueryString(params), Collections.emptyMap());
    Map<String, Object> json = http.parseJsonObject(response);
    return SpreadResult.builder().tokenId(tokenId).spread(parseBigDecimal(json, "spread")).build();
  }

  /** Returns a map of token id to spread. The endpoint responds with a JSON object. */
  public Map<String, BigDecimal> getSpreads(List<BookParams> params) throws IOException {
    String body = http.toJsonMinified(params);
    String response = http.postJsonRaw(clobUrl(CLOB_SPREADS), Collections.emptyMap(), body);
    return http.parseJson(response, new TypeReference<Map<String, BigDecimal>>() {});
  }

  public LastTradePriceResult getLastTradePrice(String tokenId) throws IOException {
    Map<String, String> params = new HashMap<>();
    params.put("token_id", tokenId);
    String response =
        http.get(clobUrl(CLOB_LAST_TRADE_PRICE) + buildQueryString(params), Collections.emptyMap());
    return http.parseJson(response, LastTradePriceResult.class);
  }

  public List<LastTradePriceResult> getLastTradesPrices(List<BookParams> params)
      throws IOException {
    String body = http.toJsonMinified(params);
    String response =
        http.postJsonRaw(clobUrl(CLOB_LAST_TRADES_PRICES), Collections.emptyMap(), body);
    return http.parseJson(response, new TypeReference<List<LastTradePriceResult>>() {});
  }

  // --- Orders ---

  public OpenOrder getOrder(String orderId) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_ORDER + orderId;
    String response = http.get(clobUrl(endpoint), l2Headers("GET", endpoint, null));
    return http.parseJson(response, OpenOrder.class);
  }

  public List<OpenOrder> getOpenOrders() throws IOException {
    return getOpenOrders((Map<String, String>) null);
  }

  /**
   * Fetch open orders with typed filter parameters.
   *
   * <p>Mirrors the TypeScript {@code getOpenOrders(params: OpenOrderParams)} overload.
   *
   * @param params typed filter params (id, market, asset_id)
   */
  public List<OpenOrder> getOpenOrders(com.polymarket.model.OpenOrderParams params)
      throws IOException {
    Map<String, String> qParams = new HashMap<>();
    if (params != null) {
      if (params.getId() != null) qParams.put("id", params.getId());
      if (params.getMarket() != null) qParams.put("market", params.getMarket());
      if (params.getAssetId() != null) qParams.put("asset_id", params.getAssetId());
    }
    return getOpenOrders(qParams);
  }

  public List<OpenOrder> getOpenOrders(Map<String, String> params) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_OPEN_ORDERS;
    Map<String, String> qParams = params != null ? new HashMap<>(params) : new HashMap<>();
    String queryString = buildQueryString(qParams);
    String requestPath = endpoint + queryString;

    String response =
        http.get(clobUrl(endpoint) + queryString, l2Headers("GET", requestPath, null));
    return http.parseJson(response, new TypeReference<List<OpenOrder>>() {});
  }

  /**
   * Create and post a limit order in one call. Auto-fetches tick size, fee rate, and neg risk for
   * the token.
   */
  public OrderResponse createAndPostOrder(
      String tokenId, Side side, BigDecimal price, BigDecimal size, OrderType orderType)
      throws IOException {
    requireL2Auth();

    String tickSize = getTickSize(tokenId);
    int feeRateBps = getFeeRateBps(tokenId);
    boolean negRisk = getNegRisk(tokenId);

    UserOrder userOrder =
        UserOrder.builder()
            .tokenID(tokenId)
            .side(side)
            .price(price)
            .size(size)
            .feeRateBps(feeRateBps)
            .build();

    CreateOrderOptions options =
        CreateOrderOptions.builder().tickSize(tickSize).negRisk(negRisk).build();

    SignedOrder signedOrder = orderBuilder.buildOrder(userOrder, options, orderType);

    PostOrderPayload payload =
        orderBuilder.buildPayload(signedOrder, apiCreds.getKey(), orderType, false, false);

    return postOrder(payload);
  }

  /**
   * Create and post multiple limit orders using the batch endpoint.
   *
   * <p>Orders are chunked to the exchange limit (15 per request) and processed in submission order.
   */
  public List<OrderResponse> createAndPostOrders(List<UserOrder> orders, OrderType orderType)
      throws IOException {
    return createAndPostOrders(orders, orderType, false, false);
  }

  /**
   * Create and post multiple limit orders using the batch endpoint.
   *
   * <p>Each order resolves tick size / fee rate / neg-risk metadata before signing.
   */
  public List<OrderResponse> createAndPostOrders(
      List<UserOrder> orders, OrderType orderType, boolean postOnly, boolean deferExec)
      throws IOException {
    requireL2Auth();
    if (orders == null || orders.isEmpty()) {
      throw new IllegalArgumentException("orders must not be null or empty");
    }

    List<SignedOrder> signedOrders = new ArrayList<>(orders.size());
    for (UserOrder order : orders) {
      if (order == null) {
        throw new IllegalArgumentException("orders must not contain null entries");
      }

      String tokenId = Objects.requireNonNull(order.tokenID(), "order.tokenID is required");
      Side side = Objects.requireNonNull(order.side(), "order.side is required");
      BigDecimal price = Objects.requireNonNull(order.price(), "order.price is required");
      BigDecimal size = Objects.requireNonNull(order.size(), "order.size is required");
      if (size.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("order.size must be > 0 for token " + tokenId);
      }

      String tickSize = getTickSize(tokenId);
      int feeRateBps =
          (order.feeRateBps() != null && order.feeRateBps() > 0)
              ? order.feeRateBps()
              : getFeeRateBps(tokenId);
      boolean negRisk = getNegRisk(tokenId);

      UserOrder normalizedOrder =
          UserOrder.builder()
              .tokenID(tokenId)
              .side(side)
              .price(price)
              .size(size)
              .feeRateBps(feeRateBps)
              .nonce(order.nonce())
              .expiration(order.expiration())
              .taker(order.taker())
              .build();

      CreateOrderOptions options =
          CreateOrderOptions.builder().tickSize(tickSize).negRisk(negRisk).build();
      signedOrders.add(orderBuilder.buildOrder(normalizedOrder, options, orderType));
    }

    return postOrders(signedOrders, orderType, postOnly, deferExec);
  }

  /** Create and post a market order in one call. */
  public OrderResponse createAndPostMarketOrder(
      String tokenId, Side side, BigDecimal amount, OrderType orderType // FOK or FAK
      ) throws IOException {
    requireL2Auth();

    String tickSize = getTickSize(tokenId);
    int feeRateBps = getFeeRateBps(tokenId);
    boolean negRisk = getNegRisk(tokenId);

    UserMarketOrder userOrder =
        UserMarketOrder.builder()
            .tokenID(tokenId)
            .side(side)
            .amount(amount)
            .feeRateBps(feeRateBps)
            .orderType(orderType)
            .build();

    CreateOrderOptions options =
        CreateOrderOptions.builder().tickSize(tickSize).negRisk(negRisk).build();

    SignedOrder signedOrder = orderBuilder.buildMarketOrder(userOrder, options);

    PostOrderPayload payload =
        orderBuilder.buildPayload(signedOrder, apiCreds.getKey(), orderType, false, false);

    return postOrder(payload);
  }

  public OrderResponse postOrder(PostOrderPayload payload) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_POST_ORDER;
    String body = http.toJsonMinified(payload);

    try {
      String response = http.postJsonRaw(clobUrl(endpoint), l2Headers("POST", endpoint, body), body);
      return http.parseJson(response, OrderResponse.class);
    } catch (HttpStatusException e) {
      invalidateVersionOnMismatch(e.getMessage());
      throw e;
    }
  }

  // Deprecated / Untyped overload for backward compatibility if needed, or remove.
  // I will keep untyped map version for raw usage but prefer typed.
  public Map<String, Object> postOrder(Map<String, Object> orderPayload) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_POST_ORDER;
    String body = http.toJsonMinified(orderPayload);

    String response = http.postJsonRaw(clobUrl(endpoint), l2Headers("POST", endpoint, body), body);
    return http.parseJsonObject(response);
  }

  public List<OrderResponse> postOrders(List<PostOrderPayload> orderPayloads) throws IOException {
    requireL2Auth();
    if (orderPayloads == null || orderPayloads.isEmpty()) {
      throw new IllegalArgumentException("orderPayloads must not be null or empty");
    }

    if (orderPayloads.size() > MAX_ORDERS_PER_REQUEST) {
      List<OrderResponse> aggregated = new ArrayList<>(orderPayloads.size());
      for (int i = 0; i < orderPayloads.size(); i += MAX_ORDERS_PER_REQUEST) {
        int end = Math.min(i + MAX_ORDERS_PER_REQUEST, orderPayloads.size());
        aggregated.addAll(postOrdersChunk(orderPayloads.subList(i, end)));
      }
      return aggregated;
    }

    return postOrdersChunk(orderPayloads);
  }

private List<OrderResponse> postOrdersChunk(List<PostOrderPayload> orderPayloads)
      throws IOException {
    List<PostOrderPayload> normalizedPayloads = normalizePostOrderPayloadOwners(orderPayloads);
    String endpoint = CLOB_POST_ORDERS;
    String body = http.toJsonMinified(normalizedPayloads);

    try {
      String response = http.postJsonRaw(clobUrl(endpoint), l2Headers("POST", endpoint, body), body);

      if (Boolean.getBoolean("bot.debug.execution")) {
        System.out.printf("[HTTP] POST %s response: %s%n", endpoint, response);
      }

      return http.parseJson(response, new TypeReference<List<OrderResponse>>() {});
    } catch (HttpStatusException e) {
      invalidateVersionOnMismatch(e.getMessage());
      throw e;
    }
  }

  /**
   * On {@code order_version_mismatch} responses, force-refresh the cached version so the next
   * order build signs against the correct protocol (PMK-004).
   */
private void invalidateVersionOnMismatch(String message) {
    if (message != null && message.contains("order_version_mismatch")) {
      try {
        resolveVersion(true);
      } catch (IOException refreshError) {
        log.warn("Failed to force-refresh CLOB version after order_version_mismatch", refreshError);
      }
    }
  }

  private List<PostOrderPayload> normalizePostOrderPayloadOwners(List<PostOrderPayload> payloads) {
    List<PostOrderPayload> normalized = new ArrayList<>(payloads.size());
    String expectedOwner = apiCreds.getKey();

    for (PostOrderPayload payload : payloads) {
      if (payload == null) {
        throw new IllegalArgumentException("orderPayloads must not contain null entries");
      }

      if (payload.order() == null) {
        throw new IllegalArgumentException("payload.order must not be null");
      }

      String owner = payload.owner();
      if (owner == null || owner.isBlank()) {
        owner = expectedOwner;
      } else if (!owner.equals(expectedOwner)) {
        throw new IllegalArgumentException(
            "payload owner mismatch: expected API key owner for this client");
      }

      normalized.add(
          PostOrderPayload.builder()
              .order(payload.order())
              .owner(owner)
              .orderType(payload.orderType())
              .deferExec(payload.deferExec())
              .postOnly(payload.postOnly())
              .build());
    }

    return normalized;
  }

  /**
   * Post a signed order using the TS-style convenience signature.
   *
   * <p>Mirrors the TypeScript {@code postOrder(order, orderType, postOnly)} call signature. {@code
   * postOnly} is only honoured for {@code GTC} and {@code GTD} orders (enforced in {@link
   * com.polymarket.client.OrderBuilder#buildPayload}).
   *
   * @param signedOrder the signed EIP-712 order
   * @param orderType GTC / GTD / FOK / FAK
   * @param postOnly if {@code true}, order is post-only (GTC/GTD only)
   * @param deferExec if {@code true}, defer execution
   */
  public OrderResponse postOrder(
      SignedOrder signedOrder, OrderType orderType, boolean postOnly, boolean deferExec)
      throws IOException {
    requireL2Auth();
    PostOrderPayload payload =
        orderBuilder.buildPayload(signedOrder, apiCreds.getKey(), orderType, deferExec, postOnly);
    return postOrder(payload);
  }

  /**
   * Post multiple signed orders using the TS-style convenience signature.
   *
   * @param signedOrders list of signed EIP-712 orders
   * @param orderType GTC / GTD / FOK / FAK (applied to all orders)
   * @param postOnly if {@code true}, orders are post-only (GTC/GTD only)
   * @param deferExec if {@code true}, defer execution
   */
  public List<OrderResponse> postOrders(
      List<SignedOrder> signedOrders, OrderType orderType, boolean postOnly, boolean deferExec)
      throws IOException {
    requireL2Auth();
    List<PostOrderPayload> payloads = new java.util.ArrayList<>();
    for (SignedOrder so : signedOrders) {
      payloads.add(
          orderBuilder.buildPayload(so, apiCreds.getKey(), orderType, deferExec, postOnly));
    }
    return postOrders(payloads);
  }

  public Map<String, Object> cancelOrder(String orderId) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_CANCEL_ORDER;
    String body = "{\"orderID\": \"" + orderId + "\"}";

    String response =
        http.deleteJsonRaw(clobUrl(endpoint), l2Headers("DELETE", endpoint, body), body);
    return http.parseJsonObject(response);
  }

  public Map<String, Object> cancelOrders(List<String> orderIds) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_CANCEL_ORDERS;
    // payload: [ "id1", "id2" ]
    String body = http.toJsonMinified(orderIds);

    String response =
        http.deleteJsonRaw(clobUrl(endpoint), l2Headers("DELETE", endpoint, body), body);
    return http.parseJsonObject(response);
  }

  public Map<String, Object> cancelAll() throws IOException {
    requireL2Auth();
    String endpoint = CLOB_CANCEL_ALL;
    String response =
        http.deleteJsonRaw(clobUrl(endpoint), l2Headers("DELETE", endpoint, null), "{}");
    return http.parseJsonObject(response);
  }

  // --- API Management ---

  public Map<String, Object> getApiKeys() throws IOException {
    requireL2Auth();
    String endpoint = CLOB_API_KEYS;
    String response = http.get(clobUrl(endpoint), l2Headers("GET", endpoint, null));
    return http.parseJsonObject(response);
  }

  public Map<String, Object> deleteApiKey() throws IOException {
    requireL2Auth();
    String endpoint = CLOB_DELETE_API_KEY;
    String response =
        http.deleteJsonRaw(clobUrl(endpoint), l2Headers("DELETE", endpoint, null), "{}");
    return http.parseJsonObject(response);
  }

  public BanStatus getClosedOnlyMode() throws IOException {
    requireL2Auth();
    String endpoint = CLOB_CLOSED_ONLY;
    String response = http.get(clobUrl(endpoint), l2Headers("GET", endpoint, null));
    return http.parseJson(response, BanStatus.class);
  }

  // --- Account ---

  /** Fetch all trades (auto-paginated) with no filter. */
  public List<Trade> getTrades() throws IOException {
    return getTrades((Map<String, String>) null);
  }

  /**
   * Fetch all trades (auto-paginated) with typed filter parameters.
   *
   * <p>Mirrors the TypeScript {@code getTrades(params: TradeParams)} overload.
   *
   * @param params typed filter params (id, maker_address, market, asset_id, before, after)
   */
  public List<Trade> getTrades(com.polymarket.model.TradeParams params) throws IOException {
    Map<String, String> qParams = new HashMap<>();
    if (params != null) {
      if (params.getId() != null) qParams.put("id", params.getId());
      if (params.getMakerAddress() != null) qParams.put("maker_address", params.getMakerAddress());
      if (params.getMarket() != null) qParams.put("market", params.getMarket());
      if (params.getAssetId() != null) qParams.put("asset_id", params.getAssetId());
      if (params.getBefore() != null) qParams.put("before", params.getBefore());
      if (params.getAfter() != null) qParams.put("after", params.getAfter());
    }
    return getTrades(qParams);
  }

  public List<Trade> getTrades(Map<String, String> params) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_TRADES;
    Map<String, String> qParams = params != null ? new HashMap<>(params) : new HashMap<>();
    // Always start from initial cursor and page through all results
    List<Trade> results = new java.util.ArrayList<>();
    String cursor = INITIAL_CURSOR;
    while (!END_CURSOR.equals(cursor)) {
      qParams.put("next_cursor", cursor);
      String queryString = buildQueryString(qParams);
      String requestPath = endpoint + queryString;
      String response =
          http.get(clobUrl(endpoint) + queryString, l2Headers("GET", requestPath, null));
      PaginationPayload<Trade> page =
          http.parseJson(response, new TypeReference<PaginationPayload<Trade>>() {});
      if (page.getData() != null) {
        results.addAll(page.getData());
      }
      cursor = page.getNextCursor();
      if (cursor == null || cursor.isEmpty()) break;
    }
    return results;
  }

  public PaginationPayload<Trade> getTradesPaginated(Map<String, String> params, String nextCursor)
      throws IOException {
    requireL2Auth();
    String endpoint = CLOB_TRADES;
    Map<String, String> qParams = params != null ? new HashMap<>(params) : new HashMap<>();
    qParams.put("next_cursor", nextCursor != null ? nextCursor : INITIAL_CURSOR);
    String queryString = buildQueryString(qParams);
    String requestPath = endpoint + queryString;
    String response =
        http.get(clobUrl(endpoint) + queryString, l2Headers("GET", requestPath, null));
    return http.parseJson(response, new TypeReference<PaginationPayload<Trade>>() {});
  }

  public Map<String, Object> cancelMarketOrders(OrderMarketCancelParams cancelParams)
      throws IOException {
    requireL2Auth();
    String endpoint = CLOB_CANCEL_MARKET_ORDERS;
    String body = http.toJsonMinified(cancelParams);
    String response =
        http.deleteJsonRaw(clobUrl(endpoint), l2Headers("DELETE", endpoint, body), body);
    return http.parseJsonObject(response);
  }

  public OrderScoring isOrderScoring(String orderId) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_ORDER_SCORING;
    Map<String, String> qParams = Map.of("order_id", orderId);
    String queryString = buildQueryString(new HashMap<>(qParams));
    String requestPath = endpoint + queryString;
    String response =
        http.get(clobUrl(endpoint) + queryString, l2Headers("GET", requestPath, null));
    return http.parseJson(response, OrderScoring.class);
  }

  public Map<String, Boolean> areOrdersScoring(List<String> orderIds) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_ORDERS_SCORING;
    // The API expects a JSON array of order IDs directly, e.g. ["id1","id2"]
    String body = http.toJsonMinified(orderIds);
    String response = http.postJsonRaw(clobUrl(endpoint), l2Headers("POST", endpoint, body), body);
    return http.parseJson(response, new TypeReference<Map<String, Boolean>>() {});
  }

  public Map<String, Boolean> areOrdersScoring(com.polymarket.model.OrdersScoringParams params)
      throws IOException {
    return areOrdersScoring(params.getOrderIds());
  }

  public BalanceAllowanceResponse getBalanceAllowance(BalanceAllowanceParams params)
      throws IOException {
    requireL2Auth();
    String endpoint = CLOB_BALANCE_ALLOWANCE;
    Map<String, String> qParams = buildBalanceAllowanceQueryParams(params);
    String queryString = buildQueryString(qParams);
    String requestPath = endpoint + queryString;
    String response =
        http.get(clobUrl(endpoint) + queryString, l2Headers("GET", requestPath, null));
    return http.parseJson(response, BalanceAllowanceResponse.class);
  }

  public void updateBalanceAllowance(BalanceAllowanceParams params) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_UPDATE_BALANCE_ALLOWANCE;
    Map<String, String> qParams = buildBalanceAllowanceQueryParams(params);
    String queryString = buildQueryString(qParams);
    String requestPath = endpoint + queryString;
    http.get(clobUrl(endpoint) + queryString, l2Headers("GET", requestPath, null));
  }

  private Map<String, String> buildBalanceAllowanceQueryParams(BalanceAllowanceParams params) {
    Map<String, String> qParams = new HashMap<>();
    if (params.getAssetType() != null) {
      qParams.put("asset_type", params.getAssetType().name());
    }
    if (params.getTokenId() != null) {
      qParams.put("token_id", params.getTokenId());
    }

    SignatureType resolvedSignatureType =
        params.getSignatureType() != null ? params.getSignatureType() : signatureType;
    if (resolvedSignatureType != null) {
      qParams.put("signature_type", String.valueOf(resolvedSignatureType.getValue()));
    }

    String resolvedFunder =
        params.getFunderAddress() != null && !params.getFunderAddress().isBlank()
            ? params.getFunderAddress()
            : funderAddress;
    if (resolvedFunder != null && !resolvedFunder.isBlank()) {
      qParams.put("funder", resolvedFunder);
    }

    return qParams;
  }

  public com.polymarket.model.HeartbeatResponse postHeartbeat(String heartbeatId)
      throws IOException {
    requireL2Auth();
    String endpoint = CLOB_HEARTBEAT;
    String body = "{\"heartbeat_id\": \"" + heartbeatId + "\"}";
    String response = http.postJsonRaw(clobUrl(endpoint), l2Headers("POST", endpoint, body), body);
    return http.parseJson(response, com.polymarket.model.HeartbeatResponse.class);
  }

  /**
   * Starts automatic heartbeat posting with the default interval (5 seconds).
   *
   * <p>The background task posts a heartbeat on each tick, chaining the returned {@code
   * heartbeat_id} into each subsequent request (Rust SDK parity).
   *
   * @throws IllegalStateException if heartbeats are already active or L2 auth is missing
   */
  public void startHeartbeats() {
    requireL2Auth();
    heartbeatManager.start();
  }

  /**
   * Starts automatic heartbeat posting with a custom interval.
   *
   * @param intervalMs milliseconds between heartbeat posts (must be &gt; 0)
   * @throws IllegalArgumentException if {@code intervalMs} is not positive
   * @throws IllegalStateException if heartbeats are already active or L2 auth is missing
   */
  public void startHeartbeats(long intervalMs) {
    requireL2Auth();
    heartbeatManager.start(intervalMs);
  }

  /**
   * Stops automatic heartbeat posting.
   *
   * <p>Does nothing if heartbeats are not currently active.
   */
  public void stopHeartbeats() {
    heartbeatManager.stop();
  }

  /** Returns {@code true} if automatic heartbeats are currently active. */
  public boolean isHeartbeatsActive() {
    return heartbeatManager.isActive();
  }

  // --- Order Creation Wrappers ---

  /**
   * Create a signed limit order (does not post to exchange). Validates price against tick size and
   * fetches missing metadata. Mirrors TS {@code ClobClient.createOrder()}.
   */
  public SignedOrder createOrder(UserOrder order, CreateOrderOptions options) throws IOException {
    if (orderBuilder == null) {
      throw new IllegalStateException("Private key required to create orders");
    }
    resolveVersion();
    String tickSize =
        options.tickSize() != null ? options.tickSize() : getTickSize(order.tokenID());
    boolean negRisk = Boolean.TRUE.equals(options.negRisk());

    int feeRateBps =
        (order.feeRateBps() != null && order.feeRateBps() > 0)
            ? order.feeRateBps()
            : getFeeRateBps(order.tokenID());

    UserOrder withFee =
        UserOrder.builder()
            .tokenID(order.tokenID())
            .side(order.side())
            .price(order.price())
            .size(order.size())
            .feeRateBps(feeRateBps)
            .nonce(order.nonce())
            .expiration(order.expiration())
            .taker(order.taker())
            .build();

    CreateOrderOptions resolvedOptions =
        CreateOrderOptions.builder().tickSize(tickSize).negRisk(negRisk).build();

    return orderBuilder.buildOrder(withFee, resolvedOptions);
  }

  /**
   * Create a signed market order (does not post to exchange). Uses the live order book to calculate
   * the executable price if not provided. Mirrors TS {@code ClobClient.createMarketOrder()}.
   */
  public SignedOrder createMarketOrder(UserMarketOrder order, CreateOrderOptions options)
      throws IOException {
    if (orderBuilder == null) {
      throw new IllegalStateException("Private key required to create orders");
    }
    resolveVersion();
    String tickSize =
        options.tickSize() != null ? options.tickSize() : getTickSize(order.tokenID());
    boolean negRisk = Boolean.TRUE.equals(options.negRisk());

    int feeRateBps =
        (order.feeRateBps() != null && order.feeRateBps() > 0)
            ? order.feeRateBps()
            : getFeeRateBps(order.tokenID());

    // Calculate market price from live order book if not specified
    BigDecimal price = order.price();
    if (price == null) {
      OrderBookSummary book = getOrderBook(order.tokenID());
      OrderType ot = order.orderType() != null ? order.orderType() : OrderType.FOK;
      price = calculateMarketPrice(order.side(), order.amount(), ot, book);
    }

    UserMarketOrder withDetails =
        UserMarketOrder.builder()
            .tokenID(order.tokenID())
            .side(order.side())
            .amount(order.amount())
            .price(price)
            .feeRateBps(feeRateBps)
            .orderType(order.orderType())
            .nonce(order.nonce())
            .taker(order.taker())
            .build();

    CreateOrderOptions resolvedOptions =
        CreateOrderOptions.builder().tickSize(tickSize).negRisk(negRisk).build();

    return orderBuilder.buildMarketOrder(withDetails, resolvedOptions);
  }

  /**
   * Calculate the executable market price by walking the order book. For BUY orders: walks asks
   * from best (lowest) upward. For SELL orders: walks bids from best (highest) downward.
   *
   * @param side BUY or SELL
   * @param amount dollar amount for BUY, share amount for SELL
   * @param orderType FOK (throw if no match) or FAK (return worst price if partial)
   * @param book order book to walk
   * @return calculated price
   */
  public BigDecimal calculateMarketPrice(
      Side side, BigDecimal amount, OrderType orderType, OrderBookSummary book) {
    if (side == Side.BUY) {
      return calculateBuyMarketPrice(book.getAsks(), amount, orderType);
    } else {
      return calculateSellMarketPrice(book.getBids(), amount, orderType);
    }
  }

  private BigDecimal calculateBuyMarketPrice(
      List<OrderSummary> asks, BigDecimal amountToMatch, OrderType orderType) {
    if (asks == null || asks.isEmpty()) {
      throw new IllegalStateException("No asks available for market price calculation");
    }
    BigDecimal sum = BigDecimal.ZERO;
    // Asks are ordered best-first (lowest price first); walk from last to first
    for (int i = asks.size() - 1; i >= 0; i--) {
      OrderSummary ask = asks.get(i);
      BigDecimal price = new BigDecimal(ask.getPrice());
      BigDecimal size = new BigDecimal(ask.getSize());
      sum = sum.add(size.multiply(price));
      if (sum.compareTo(amountToMatch) >= 0) {
        return price;
      }
    }
    if (orderType == OrderType.FOK) {
      throw new IllegalStateException("Insufficient liquidity for FOK order");
    }
    return new BigDecimal(asks.get(0).getPrice());
  }

  private BigDecimal calculateSellMarketPrice(
      List<OrderSummary> bids, BigDecimal amountToMatch, OrderType orderType) {
    if (bids == null || bids.isEmpty()) {
      throw new IllegalStateException("No bids available for market price calculation");
    }
    BigDecimal sum = BigDecimal.ZERO;
    // Bids are ordered best-first (highest price first); walk from last to first
    for (int i = bids.size() - 1; i >= 0; i--) {
      OrderSummary bid = bids.get(i);
      BigDecimal price = new BigDecimal(bid.getPrice());
      BigDecimal size = new BigDecimal(bid.getSize());
      sum = sum.add(size);
      if (sum.compareTo(amountToMatch) >= 0) {
        return price;
      }
    }
    if (orderType == OrderType.FOK) {
      throw new IllegalStateException("Insufficient liquidity for FOK order");
    }
    return new BigDecimal(bids.get(0).getPrice());
  }

  // --- Internal Helpers ---

  private void requireL2Auth() {
    if (apiCreds == null) {
      throw new IllegalStateException("API credentials required for this operation");
    }
  }

  Map<String, String> l2Headers(String method, String path, String body) {
    long timestamp = System.currentTimeMillis() / 1000;
    if (useServerTime) {
      try {
        timestamp = getServerTime();
      } catch (IOException e) {
        log.warn("Failed to get server time, using local time", e);
      }
    }

    String pathForSignature = path;
    int queryStart = path.indexOf('?');
    if (queryStart >= 0) {
      pathForSignature = path.substring(0, queryStart);
    }

    String sign = l2Signer.sign(apiCreds.getSecret(), timestamp, method, pathForSignature, body);

    Map<String, String> headers = new HashMap<>();
    // L2 credentials are bound to the signer wallet; funder is only for order construction.
    headers.put("POLY_ADDRESS", getSignerAddress());
    headers.put("POLY_API_KEY", apiCreds.getKey());
    headers.put("POLY_TIMESTAMP", String.valueOf(timestamp));
    headers.put("POLY_SIGNATURE", sign);
    headers.put("POLY_PASSPHRASE", apiCreds.getPassphrase());
    return headers;
  }

  private String clobUrl(String path) {
    String url = clobHost + path;
    if (geoBlockToken != null && !geoBlockToken.isEmpty()) {
      url += (url.contains("?") ? "&" : "?") + "geo_block_token=" + geoBlockToken;
    }
    return url;
  }

  private String gammaUrl(String path) {
    return gammaHost + path;
  }

  private static String buildQueryString(Map<String, String> params) {
    if (params.isEmpty()) return "";
    StringBuilder sb = new StringBuilder("?");
    for (Map.Entry<String, String> entry : params.entrySet()) {
      sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
    }
    return sb.substring(0, sb.length() - 1);
  }

  private static BigDecimal parseBigDecimal(Map<String, Object> map, String key) {
    Object val = map.get(key);
    if (val == null) return null;
    return new BigDecimal(val.toString());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> extractDataList(Object obj) {
    if (obj instanceof List) {
      return (List<Map<String, Object>>) obj;
    }
    return Collections.emptyList();
  }

  private String fetchTickSize(String tokenId) throws IOException {
    String response =
        http.get(
            clobUrl(CLOB_TICK_SIZE) + buildQueryString(Map.of("token_id", tokenId)),
            Collections.emptyMap());
    Map<String, Object> map = http.parseJsonObject(response);
    // Canonical field is `minimum_tick_size`; tolerate legacy `tick_size`.
    String tickSize =
        requireStringField(
            map, CLOB_TICK_SIZE, "minimum_tick_size", "tick_size", "minimumTickSize");
    tickSizeCache.put(tokenId, tickSize);
    return tickSize;
  }

  private int fetchFeeRate(String tokenId) throws IOException {
    String response =
        http.get(
            clobUrl(CLOB_FEE_RATE) + buildQueryString(Map.of("token_id", tokenId)),
            Collections.emptyMap());
    Map<String, Object> map = http.parseJsonObject(response);
    // Canonical field is `base_fee`; tolerate legacy `fee_rate_bps`.
    int feeRate = requireIntField(map, CLOB_FEE_RATE, "base_fee", "fee_rate_bps", "baseFee");
    feeRateCache.put(tokenId, feeRate);
    return feeRate;
  }

  private boolean fetchNegRisk(String tokenId) throws IOException {
    String response =
        http.get(
            clobUrl(CLOB_NEG_RISK) + buildQueryString(Map.of("token_id", tokenId)),
            Collections.emptyMap());
    Map<String, Object> map = http.parseJsonObject(response);
    boolean negRisk = requireBooleanField(map, CLOB_NEG_RISK, "neg_risk", "negRisk");
    negRiskCache.put(tokenId, negRisk);
    return negRisk;
  }

  private static String requireStringField(Map<String, Object> raw, String endpoint, String... keys)
      throws IOException {
    Object value = firstPresentValue(raw, keys);
    if (value == null) {
      throw new IOException(
          "Missing required field "
              + Arrays.toString(keys)
              + " in response from "
              + endpoint
              + ": "
              + raw);
    }
    return value.toString();
  }

  private static int requireIntField(Map<String, Object> raw, String endpoint, String... keys)
      throws IOException {
    String value = requireStringField(raw, endpoint, keys);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IOException(
          "Invalid integer for field "
              + Arrays.toString(keys)
              + " in response from "
              + endpoint
              + ": "
              + value,
          e);
    }
  }

  private static boolean requireBooleanField(
      Map<String, Object> raw, String endpoint, String... keys) throws IOException {
    String value = requireStringField(raw, endpoint, keys);
    return Boolean.parseBoolean(value);
  }

  private static Object firstPresentValue(Map<String, Object> raw, String... keys) {
    for (String key : keys) {
      Object value = raw.get(key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  // --- Public Endpoints (no auth) ---

  /** Health-check endpoint. Returns server status. */
  public Map<String, Object> getOk() throws IOException {
    String response = http.get(clobHost + "/", Collections.emptyMap());
    return http.parseJsonObject(response);
  }

  /**
   * Fetch price history for a market.
   *
   * @param params filter parameters (market, startTs, endTs, fidelity, interval)
   * @return list of {@link MarketPrice} data points
   */
  public List<MarketPrice> getPricesHistory(PriceHistoryFilterParams params) throws IOException {
    Map<String, String> qParams = new HashMap<>();
    if (params.getMarket() != null) qParams.put("market", params.getMarket());
    if (params.getStartTs() != null) qParams.put("startTs", String.valueOf(params.getStartTs()));
    if (params.getEndTs() != null) qParams.put("endTs", String.valueOf(params.getEndTs()));
    if (params.getFidelity() != null) qParams.put("fidelity", String.valueOf(params.getFidelity()));
    if (params.getInterval() != null) qParams.put("interval", params.getInterval().getValue());
    String response =
        http.get(clobUrl(CLOB_PRICES_HISTORY) + buildQueryString(qParams), Collections.emptyMap());
    return http.parseJson(response, new TypeReference<List<MarketPrice>>() {});
  }

  /**
   * Get live market trade events for a condition ID.
   *
   * @param conditionId the condition ID of the market
   * @return list of {@link MarketTradeEvent}
   */
  public List<MarketTradeEvent> getMarketTradesEvents(String conditionId) throws IOException {
    String response =
        http.get(clobUrl(CLOB_LIVE_ACTIVITY_EVENTS_PREFIX + conditionId), Collections.emptyMap());
    return http.parseJson(response, new TypeReference<List<MarketTradeEvent>>() {});
  }

  /**
   * Validate a readonly API key for an address (no auth required).
   *
   * @param address wallet address
   * @param key readonly API key to validate
   * @return raw response string
   */
  public String validateReadonlyApiKey(String address, String key) throws IOException {
    String queryString = buildQueryString(new HashMap<>(Map.of("address", address, "key", key)));
    return http.get(
        clobUrl(CLOB_VALIDATE_READONLY_API_KEY) + queryString, Collections.emptyMap());
  }

  /**
   * Get all current reward market configurations (paginated, auto-paginated).
   *
   * @return list of {@link MarketReward}
   */
  public List<MarketReward> getCurrentRewards() throws IOException {
    List<MarketReward> results = new java.util.ArrayList<>();
    String cursor = INITIAL_CURSOR;
    while (!END_CURSOR.equals(cursor)) {
      String response =
          http.get(clobUrl(CLOB_REWARDS_MARKETS_CURRENT), Map.of("next_cursor", cursor));
      PaginationPayload<MarketReward> page =
          http.parseJson(response, new TypeReference<PaginationPayload<MarketReward>>() {});
      if (page.getData() != null) results.addAll(page.getData());
      cursor = page.getNextCursor();
      if (cursor == null || cursor.isEmpty()) break;
    }
    return results;
  }

  /**
   * Get raw rewards configuration for a specific market (auto-paginated).
   *
   * @param conditionId the condition ID of the market
   * @return list of {@link MarketReward}
   */
  public List<MarketReward> getRawRewardsForMarket(String conditionId) throws IOException {
    List<MarketReward> results = new java.util.ArrayList<>();
    String cursor = INITIAL_CURSOR;
    while (!END_CURSOR.equals(cursor)) {
      String response =
          http.get(
              clobUrl(CLOB_REWARDS_MARKETS_PREFIX + conditionId), Map.of("next_cursor", cursor));
      PaginationPayload<MarketReward> page =
          http.parseJson(response, new TypeReference<PaginationPayload<MarketReward>>() {});
      if (page.getData() != null) results.addAll(page.getData());
      cursor = page.getNextCursor();
      if (cursor == null || cursor.isEmpty()) break;
    }
    return results;
  }

  // --- Notifications (L2 auth) ---

  /**
   * Get all notifications for the authenticated user.
   *
   * @return list of {@link Notification}
   */
  public List<Notification> getNotifications() throws IOException {
    requireL2Auth();
    String endpoint = CLOB_NOTIFICATIONS;
    String response = http.get(clobUrl(endpoint), l2Headers("GET", endpoint, null));
    return http.parseJson(response, new TypeReference<List<Notification>>() {});
  }

  /**
   * Delete (drop) specific notifications by ID.
   *
   * @param params notification IDs to drop (null to drop all)
   */
  public void dropNotifications(DropNotificationParams params) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_NOTIFICATIONS;
    String body = params != null ? http.toJsonMinified(Map.of("ids", params.getIds())) : "{}";
    http.deleteJsonRaw(clobUrl(endpoint), l2Headers("DELETE", endpoint, body), body);
  }

  // --- Rewards (L2 auth) ---

  /**
   * Get user earnings for a specific day (auto-paginated).
   *
   * @param date date string (e.g. "2024-01-01")
   * @return list of {@link UserEarning}
   */
  public List<UserEarning> getEarningsForUserForDay(String date) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_REWARDS_USER;
    List<UserEarning> results = new java.util.ArrayList<>();
    String cursor = INITIAL_CURSOR;
    while (!END_CURSOR.equals(cursor)) {
      Map<String, String> qParams = new HashMap<>();
      qParams.put("date", date);
      qParams.put("next_cursor", cursor);
      String queryString = buildQueryString(qParams);
      String requestPath = endpoint + queryString;
      String response =
          http.get(clobUrl(endpoint) + queryString, l2Headers("GET", requestPath, null));
      PaginationPayload<UserEarning> page =
          http.parseJson(response, new TypeReference<PaginationPayload<UserEarning>>() {});
      if (page.getData() != null) results.addAll(page.getData());
      cursor = page.getNextCursor();
      if (cursor == null || cursor.isEmpty()) break;
    }
    return results;
  }

  /**
   * Get total user earnings for a specific day.
   *
   * @param date date string (e.g. "2024-01-01")
   * @return list of {@link TotalUserEarning}
   */
  public List<TotalUserEarning> getTotalEarningsForUserForDay(String date) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_REWARDS_USER_TOTAL;
    Map<String, String> qParams = Map.of("date", date);
    String queryString = buildQueryString(new HashMap<>(qParams));
    String requestPath = endpoint + queryString;
    String response =
        http.get(clobUrl(endpoint) + queryString, l2Headers("GET", requestPath, null));
    return http.parseJson(response, new TypeReference<List<TotalUserEarning>>() {});
  }

  /**
   * Get user rewards earnings along with market configs (auto-paginated).
   *
   * @param date date string
   * @param orderBy optional sort field
   * @param position optional position filter
   * @param noCompetition whether to exclude competition
   * @return list of {@link UserRewardsEarning}
   */
  public List<UserRewardsEarning> getUserEarningsAndMarketsConfig(
      String date, String orderBy, String position, boolean noCompetition) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_REWARDS_USER_MARKETS;
    List<UserRewardsEarning> results = new java.util.ArrayList<>();
    String cursor = INITIAL_CURSOR;
    while (!END_CURSOR.equals(cursor)) {
      Map<String, String> qParams = new HashMap<>();
      qParams.put("date", date);
      if (orderBy != null && !orderBy.isEmpty()) qParams.put("order_by", orderBy);
      if (position != null && !position.isEmpty()) qParams.put("position", position);
      if (noCompetition) qParams.put("no_competition", "true");
      qParams.put("next_cursor", cursor);
      String queryString = buildQueryString(qParams);
      String requestPath = endpoint + queryString;
      String response =
          http.get(clobUrl(endpoint) + queryString, l2Headers("GET", requestPath, null));
      PaginationPayload<UserRewardsEarning> page =
          http.parseJson(response, new TypeReference<PaginationPayload<UserRewardsEarning>>() {});
      if (page.getData() != null) results.addAll(page.getData());
      cursor = page.getNextCursor();
      if (cursor == null || cursor.isEmpty()) break;
    }
    return results;
  }

  /**
   * Get liquidity reward percentages for the authenticated user.
   *
   * @return map of market → percentage
   */
  public Map<String, BigDecimal> getRewardPercentages() throws IOException {
    requireL2Auth();
    String endpoint = CLOB_REWARDS_PERCENTAGES;
    String response = http.get(clobUrl(endpoint), l2Headers("GET", endpoint, null));
    return http.parseJson(response, new TypeReference<Map<String, BigDecimal>>() {});
  }

  // --- Builder Trades (L2 auth) ---

  /**
   * Get trades executed via the builder program (single page).
   *
   * @param params optional trade filter params (id, market, asset_id, etc.)
   * @param nextCursor pagination cursor (null or {@link #INITIAL_CURSOR} for first page)
   * @return paginated list of {@link BuilderTrade}
   */
  public PaginationPayload<BuilderTrade> getBuilderTrades(
      Map<String, String> params, String nextCursor) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_BUILDER_TRADES;
    Map<String, String> qParams = params != null ? new HashMap<>(params) : new HashMap<>();
    qParams.put("next_cursor", nextCursor != null ? nextCursor : INITIAL_CURSOR);
    String queryString = buildQueryString(qParams);
    String requestPath = endpoint + queryString;
    String response =
        http.get(clobUrl(endpoint) + queryString, l2Headers("GET", requestPath, null));
    return http.parseJson(response, new TypeReference<PaginationPayload<BuilderTrade>>() {});
  }

  // --- Readonly API Keys (L2 auth) ---

  /**
   * Create a new readonly API key.
   *
   * @return {@link ReadonlyApiKeyResponse} containing the new key
   */
  public ReadonlyApiKeyResponse createReadonlyApiKey() throws IOException {
    requireL2Auth();
    String endpoint = CLOB_CREATE_READONLY_API_KEY;
    String response = http.postJsonRaw(clobUrl(endpoint), l2Headers("POST", endpoint, "{}"), "{}");
    return http.parseJson(response, ReadonlyApiKeyResponse.class);
  }

  /**
   * Get all readonly API keys for the authenticated user.
   *
   * @return list of readonly API key strings
   */
  public List<String> getReadonlyApiKeys() throws IOException {
    requireL2Auth();
    String endpoint = CLOB_GET_READONLY_API_KEYS;
    String response = http.get(clobUrl(endpoint), l2Headers("GET", endpoint, null));
    return http.parseJson(response, new TypeReference<List<String>>() {});
  }

  /**
   * Delete a specific readonly API key.
   *
   * @param key the readonly API key to delete
   * @return raw response
   */
  public Map<String, Object> deleteReadonlyApiKey(String key) throws IOException {
    requireL2Auth();
    String endpoint = CLOB_DELETE_READONLY_API_KEY;
    String body = http.toJsonMinified(Map.of("key", key));
    String response =
        http.deleteJsonRaw(clobUrl(endpoint), l2Headers("DELETE", endpoint, body), body);
    return http.parseJsonObject(response);
  }

  // --- Builder API Keys (L2 auth) ---

  /**
   * Create a new builder API key.
   *
   * @return {@link BuilderApiKey} containing key, secret, and passphrase
   */
  public BuilderApiKey createBuilderApiKey() throws IOException {
    requireL2Auth();
    String endpoint = CLOB_CREATE_BUILDER_API_KEY;
    String response = http.postJsonRaw(clobUrl(endpoint), l2Headers("POST", endpoint, "{}"), "{}");
    return http.parseJson(response, BuilderApiKey.class);
  }

  /**
   * Get all builder API keys for the authenticated user.
   *
   * @return list of {@link BuilderApiKeyResponse}
   */
  public List<BuilderApiKeyResponse> getBuilderApiKeys() throws IOException {
    requireL2Auth();
    String endpoint = CLOB_GET_BUILDER_API_KEYS;
    String response = http.get(clobUrl(endpoint), l2Headers("GET", endpoint, null));
    return http.parseJson(response, new TypeReference<List<BuilderApiKeyResponse>>() {});
  }

  /**
   * Revoke the builder API key for the authenticated user.
   *
   * @return raw response
   */
  public Map<String, Object> revokeBuilderApiKey() throws IOException {
    requireL2Auth();
    String endpoint = CLOB_REVOKE_BUILDER_API_KEY;
    String response =
        http.deleteJsonRaw(clobUrl(endpoint), l2Headers("DELETE", endpoint, null), "{}");
    return http.parseJsonObject(response);
  }

  // --- Records ---

  public record OrderBookResult(
      String tokenId, List<Map<String, Object>> bids, List<Map<String, Object>> asks) {}
}
