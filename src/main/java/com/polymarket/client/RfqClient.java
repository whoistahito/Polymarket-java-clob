package com.polymarket.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.polymarket.model.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.polymarket.client.PolymarketEndpoints.*;

/**
 * RFQ (Request For Quote) client.
 *
 * <p>Provides methods for creating, cancelling, and managing RFQ requests and quotes.
 * Mirrors the TypeScript {@code RfqClient} in {@code clob-client/src/rfq-client.ts}.
 *
 * <p>Use via {@code PolymarketClient.rfq()}.
 */
public final class RfqClient {

    /** USDC has 6 decimal places. */
    private static final int COLLATERAL_TOKEN_DECIMALS = 6;

    private final PolymarketClient client;

    RfqClient(PolymarketClient client) {
        this.client = client;
    }

    // -------------------------------------------------------------------------
    // Request management
    // -------------------------------------------------------------------------

    /**
     * Creates and posts an RFQ request from user-order parameters.
     *
     * @param userOrder the user order to convert to an RFQ request
     * @param tickSize  optional tick size override
     * @return {@link RfqRequestResponse} with the generated request ID
     */
    public RfqRequestResponse createRfqRequest(RfqUserOrder userOrder, String tickSize)
        throws IOException {
        requireL2Auth();

        String resolvedTickSize = resolveTickSize(userOrder.getTokenID(), tickSize);
        int[] roundConfig = getRoundConfig(resolvedTickSize);

        BigDecimal price = roundNormal(userOrder.getPrice(), roundConfig[0]);
        BigDecimal size = roundDown(userOrder.getSize(), roundConfig[1]);
        BigDecimal notional = size.multiply(price);

        String amountIn;
        String amountOut;
        String assetIn;
        String assetOut;

        if (userOrder.getSide() == Side.BUY) {
            // Buying: pay USDC (asset 0), receive tokens (tokenID)
            amountIn = toTokenAmount(size, roundConfig[2]);
            amountOut = toTokenAmount(notional, roundConfig[2]);
            assetIn   = userOrder.getTokenID();
            assetOut  = "0";
        } else {
            // Selling: pay tokens (tokenID), receive USDC (asset 0)
            amountIn = toTokenAmount(notional, roundConfig[2]);
            amountOut = toTokenAmount(size, roundConfig[2]);
            assetIn   = "0";
            assetOut  = userOrder.getTokenID();
        }

        CreateRfqRequestParams payload = CreateRfqRequestParams.builder()
            .assetIn(assetIn)
            .assetOut(assetOut)
            .amountIn(amountIn)
            .amountOut(amountOut)
            .userType(client.getOrderBuilder().getSignatureTypeValue())
            .build();

        String endpoint = RFQ_CREATE_REQUEST;
        String body = client.getHttp().toJsonMinified(payload);
        String response = client.getHttp().postJsonRaw(
            client.getClobUrl(endpoint),
            client.l2Headers("POST", endpoint, body),
            body
        );
        return client.getHttp().parseJson(response, RfqRequestResponse.class);
    }

    /**
     * Cancels an existing RFQ request.
     *
     * @param params the request params containing the request ID
     */
    public void cancelRfqRequest(CancelRfqRequestParams params) throws IOException {
        requireL2Auth();
        String endpoint = RFQ_CANCEL_REQUEST;
        String body = client.getHttp().toJsonMinified(params);
        client.getHttp().deleteJsonRaw(
            client.getClobUrl(endpoint),
            client.l2Headers("DELETE", endpoint, body),
            body
        );
    }

    /**
     * Gets RFQ requests with optional filtering parameters.
     *
     * @param params optional filter/pagination params
     * @return paginated list of {@link RfqRequest}
     */
    public RfqPaginatedResponse<RfqRequest> getRfqRequests(GetRfqRequestsParams params)
        throws IOException {
        requireL2Auth();
        String endpoint = RFQ_GET_REQUESTS;
        String query = buildRepeatedQuery(paramsToMap(params));
        String url = client.getClobUrl(endpoint) + (query.isEmpty() ? "" : "?" + query);
        String response = client.getHttp().get(url, client.l2Headers("GET", endpoint, null));
        return client.getHttp().parseJson(
            response,
            new TypeReference<RfqPaginatedResponse<RfqRequest>>() {}
        );
    }

    // -------------------------------------------------------------------------
    // Quote management
    // -------------------------------------------------------------------------

    /**
     * Creates a quote in response to an RFQ request.
     *
     * @param userQuote the user quote parameters
     * @param tickSize  optional tick size override
     * @return {@link RfqQuoteResponse} with the generated quote ID
     */
    public RfqQuoteResponse createRfqQuote(RfqUserQuote userQuote, String tickSize)
        throws IOException {
        requireL2Auth();

        String resolvedTickSize = resolveTickSize(userQuote.getTokenID(), tickSize);
        int[] roundConfig = getRoundConfig(resolvedTickSize);

        BigDecimal price = roundNormal(userQuote.getPrice(), roundConfig[0]);
        BigDecimal size = roundDown(userQuote.getSize(), roundConfig[1]);
        BigDecimal notional = size.multiply(price);

        String amountIn;
        String amountOut;
        String assetIn;
        String assetOut;

        if (userQuote.getSide() == Side.SELL) {
            // Quoter selling tokens: receive USDC (asset 0), give tokens (tokenID)
            amountIn = toTokenAmount(notional, roundConfig[2]);
            amountOut = toTokenAmount(size, roundConfig[2]);
            assetIn   = "0";
            assetOut  = userQuote.getTokenID();
        } else {
            // Quoter buying tokens: receive tokens (tokenID), give USDC (asset 0)
            amountIn = toTokenAmount(size, roundConfig[2]);
            amountOut = toTokenAmount(notional, roundConfig[2]);
            assetIn   = userQuote.getTokenID();
            assetOut  = "0";
        }

        CreateRfqQuoteParams payload = CreateRfqQuoteParams.builder()
            .requestId(userQuote.getRequestId())
            .assetIn(assetIn)
            .assetOut(assetOut)
            .amountIn(amountIn)
            .amountOut(amountOut)
            .userType(client.getOrderBuilder().getSignatureTypeValue())
            .build();

        String endpoint = RFQ_CREATE_QUOTE;
        String body = client.getHttp().toJsonMinified(payload);
        String response = client.getHttp().postJsonRaw(
            client.getClobUrl(endpoint),
            client.l2Headers("POST", endpoint, body),
            body
        );
        return client.getHttp().parseJson(response, RfqQuoteResponse.class);
    }

    /**
     * Gets quotes on requests created by the authenticated user (requester view).
     *
     * @param params optional filter/pagination params
     * @return paginated list of {@link RfqQuote}
     */
    public RfqPaginatedResponse<RfqQuote> getRfqRequesterQuotes(GetRfqQuotesParams params)
        throws IOException {
        requireL2Auth();
        String endpoint = RFQ_REQUESTER_QUOTES;
        String query = buildRepeatedQuery(paramsToMap(params));
        String url = client.getClobUrl(endpoint) + (query.isEmpty() ? "" : "?" + query);
        String response = client.getHttp().get(url, client.l2Headers("GET", endpoint, null));
        return client.getHttp().parseJson(
            response,
            new TypeReference<RfqPaginatedResponse<RfqQuote>>() {}
        );
    }

    /**
     * Gets quotes created by the authenticated user (quoter view).
     *
     * @param params optional filter/pagination params
     * @return paginated list of {@link RfqQuote}
     */
    public RfqPaginatedResponse<RfqQuote> getRfqQuoterQuotes(GetRfqQuotesParams params)
        throws IOException {
        requireL2Auth();
        String endpoint = RFQ_QUOTER_QUOTES;
        String query = buildRepeatedQuery(paramsToMap(params));
        String url = client.getClobUrl(endpoint) + (query.isEmpty() ? "" : "?" + query);
        String response = client.getHttp().get(url, client.l2Headers("GET", endpoint, null));
        return client.getHttp().parseJson(
            response,
            new TypeReference<RfqPaginatedResponse<RfqQuote>>() {}
        );
    }

    /**
     * Gets the best quote for a given request.
     *
     * @param params request ID filter
     * @return the best {@link RfqQuote}
     */
    public RfqQuote getRfqBestQuote(GetRfqBestQuoteParams params) throws IOException {
        requireL2Auth();
        String endpoint = RFQ_BEST_QUOTE;
        Map<String, String> qParams = new HashMap<>();
        if (params != null && params.getRequestId() != null) {
            qParams.put("requestId", params.getRequestId());
        }
        String queryString = buildQueryString(qParams);
        String requestPath = endpoint + (queryString.isEmpty() ? "" : "?" + queryString);
        String response = client.getHttp().get(
            client.getClobUrl(endpoint) + (queryString.isEmpty() ? "" : "?" + queryString),
            client.l2Headers("GET", requestPath, null)
        );
        return client.getHttp().parseJson(response, RfqQuote.class);
    }

    /**
     * Cancels an existing RFQ quote.
     *
     * @param params the params containing the quote ID
     */
    public void cancelRfqQuote(CancelRfqQuoteParams params) throws IOException {
        requireL2Auth();
        String endpoint = RFQ_CANCEL_QUOTE;
        String body = client.getHttp().toJsonMinified(params);
        client.getHttp().deleteJsonRaw(
            client.getClobUrl(endpoint),
            client.l2Headers("DELETE", endpoint, body),
            body
        );
    }

    /**
     * Gets the RFQ configuration from the server.
     *
     * @return raw config as a map
     */
    public Map<String, Object> rfqConfig() throws IOException {
        requireL2Auth();
        String endpoint = RFQ_CONFIG;
        String response = client.getHttp().get(
            client.getClobUrl(endpoint),
            client.l2Headers("GET", endpoint, null)
        );
        return client.getHttp().parseJsonObject(response);
    }

    /**
     * Accepts a quote and creates an order (taker side).
     *
     * @param payload accept parameters (requestId, quoteId, expiration)
     */
    public void acceptRfqQuote(AcceptQuoteParams payload) throws IOException {
        requireL2Auth();

        // Fetch the quote to understand what order to create
        GetRfqQuotesParams quotesParams = GetRfqQuotesParams.builder()
            .quoteIds(List.of(payload.getQuoteId()))
            .build();
        RfqPaginatedResponse<RfqQuote> rfqQuotes = getRfqRequesterQuotes(quotesParams);
        if (rfqQuotes.getData() == null || rfqQuotes.getData().isEmpty()) {
            throw new IllegalStateException("RFQ quote not found: " + payload.getQuoteId());
        }
        RfqQuote rfqQuote = rfqQuotes.getData().get(0);

        // Determine order side and size from match type
        OrderCreationInfo info = getRequestOrderCreationInfo(rfqQuote);

        SignedOrder order = client.createOrder(
            com.polymarket.model.UserOrder.builder()
                .tokenID(info.token)
                .price(new BigDecimal(String.valueOf(info.price)))
                .size(new BigDecimal(String.valueOf(info.size)))
                .side(info.side)
                .expiration(payload.getExpiration())
                .build(),
            CreateOrderOptions.builder()
                .tickSize(client.getTickSize(info.token))
                .negRisk(client.getNegRisk(info.token))
                .build()
        );

        Map<String, Object> acceptBody = buildOrderMap(order, client.getApiCreds().getKey());
        acceptBody.put("requestId", payload.getRequestId());
        acceptBody.put("quoteId", payload.getQuoteId());
        acceptBody.put("side", info.side.name());
        acceptBody.put("expiration", payload.getExpiration());

        String endpoint = RFQ_ACCEPT_REQUEST;
        String body = client.getHttp().toJsonMinified(acceptBody);
        client.getHttp().postJsonRaw(
            client.getClobUrl(endpoint),
            client.l2Headers("POST", endpoint, body),
            body
        );
    }

    /**
     * Approves a quote and creates an order (maker side).
     *
     * @param payload approve parameters (requestId, quoteId, expiration)
     */
    public void approveRfqOrder(ApproveOrderParams payload) throws IOException {
        requireL2Auth();

        // Fetch the quote
        GetRfqQuotesParams quotesParams = GetRfqQuotesParams.builder()
            .quoteIds(List.of(payload.getQuoteId()))
            .build();
        RfqPaginatedResponse<RfqQuote> rfqQuotes = getRfqQuoterQuotes(quotesParams);
        if (rfqQuotes.getData() == null || rfqQuotes.getData().isEmpty()) {
            throw new IllegalStateException("RFQ quote not found: " + payload.getQuoteId());
        }
        RfqQuote rfqQuote = rfqQuotes.getData().get(0);

        Side side = "BUY".equals(rfqQuote.getSide()) ? Side.BUY : Side.SELL;
        String size = "BUY".equals(rfqQuote.getSide()) ? rfqQuote.getSizeIn() : rfqQuote.getSizeOut();

        SignedOrder order = client.createOrder(
            com.polymarket.model.UserOrder.builder()
                .tokenID(rfqQuote.getToken())
                .price(new BigDecimal(String.valueOf(rfqQuote.getPrice())))
                .size(new BigDecimal(size))
                .side(side)
                .expiration(payload.getExpiration())
                .build(),
            CreateOrderOptions.builder()
                .tickSize(client.getTickSize(rfqQuote.getToken()))
                .negRisk(client.getNegRisk(rfqQuote.getToken()))
                .build()
        );

        Map<String, Object> approveBody = buildOrderMap(order, client.getApiCreds().getKey());
        approveBody.put("requestId", payload.getRequestId());
        approveBody.put("quoteId", payload.getQuoteId());
        approveBody.put("side", side.name());
        approveBody.put("expiration", payload.getExpiration());

        String endpoint = RFQ_APPROVE_QUOTE;
        String body = client.getHttp().toJsonMinified(approveBody);
        client.getHttp().postJsonRaw(
            client.getClobUrl(endpoint),
            client.l2Headers("POST", endpoint, body),
            body
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requireL2Auth() {
        if (!client.hasApiCreds()) {
            throw new IllegalStateException("API credentials required for RFQ operations");
        }
    }

    private String resolveTickSize(String tokenId, String tickSize) throws IOException {
        if (tickSize != null && !tickSize.isEmpty()) {
            return tickSize;
        }
        return client.getTickSize(tokenId);
    }

    /**
     * Returns [pricePrecision, sizePrecision, amountPrecision] for the given tick size.
     * Mirrors TS {@code ROUNDING_CONFIG}.
     */
    private static int[] getRoundConfig(String tickSize) {
        return switch (tickSize) {
            case "0.1"    -> new int[]{1, 2, 3};
            case "0.01"   -> new int[]{2, 2, 4};
            case "0.001"  -> new int[]{3, 2, 5};
            case "0.0001" -> new int[]{4, 2, 6};
            default       -> new int[]{2, 2, 4};
        };
    }

    private static BigDecimal roundNormal(BigDecimal value, int decimalPlaces) {
        return value.setScale(decimalPlaces, RoundingMode.HALF_UP);
    }

    private static BigDecimal roundDown(BigDecimal value, int decimalPlaces) {
        return value.setScale(decimalPlaces, RoundingMode.FLOOR);
    }

    /** Scale a decimal amount to 6-decimal token integer (as string). */
    private static String toTokenAmount(BigDecimal amount, int amountPrecision) {
        BigDecimal bd = amount
            .setScale(amountPrecision, RoundingMode.FLOOR)
            .multiply(BigDecimal.TEN.pow(COLLATERAL_TOKEN_DECIMALS));
        return bd.toBigInteger().toString();
    }

    private static String buildQueryString(Map<String, String> params) {
        if (params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(urlEncode(e.getKey())).append("=").append(urlEncode(e.getValue()));
        }
        return sb.toString();
    }

    /**
     * Percent-encodes a query key/value per {@code application/x-www-form-urlencoded}.
     */
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Builds a query string that supports repeated keys (for list parameters).
     * e.g. quoteIds=a&quoteIds=b
     */
    private static String buildRepeatedQuery(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            Object val = e.getValue();
            if (val == null) continue;
            if (val instanceof List<?> list) {
                for (Object item : list) {
                    if (sb.length() > 0) sb.append("&");
                    sb.append(urlEncode(e.getKey())).append("=").append(urlEncode(String.valueOf(item)));
                }
            } else {
                if (sb.length() > 0) sb.append("&");
                sb.append(urlEncode(e.getKey())).append("=").append(urlEncode(String.valueOf(val)));
            }
        }
        return sb.toString();
    }

    private static Map<String, Object> paramsToMap(GetRfqRequestsParams p) {
        if (p == null) return Collections.emptyMap();
        Map<String, Object> m = new LinkedHashMap<>();
        if (p.getOffset() != null) m.put("offset", p.getOffset());
        if (p.getLimit() != null) m.put("limit", p.getLimit());
        if (p.getState() != null) m.put("state", p.getState());
        if (p.getRequestIds() != null) m.put("requestIds", p.getRequestIds());
        if (p.getMarkets() != null) m.put("markets", p.getMarkets());
        if (p.getSizeMin() != null) m.put("sizeMin", p.getSizeMin());
        if (p.getSizeMax() != null) m.put("sizeMax", p.getSizeMax());
        if (p.getSizeUsdcMin() != null) m.put("sizeUsdcMin", p.getSizeUsdcMin());
        if (p.getSizeUsdcMax() != null) m.put("sizeUsdcMax", p.getSizeUsdcMax());
        if (p.getPriceMin() != null) m.put("priceMin", p.getPriceMin());
        if (p.getPriceMax() != null) m.put("priceMax", p.getPriceMax());
        if (p.getSortBy() != null) m.put("sortBy", p.getSortBy());
        if (p.getSortDir() != null) m.put("sortDir", p.getSortDir());
        return m;
    }

    private static Map<String, Object> paramsToMap(GetRfqQuotesParams p) {
        if (p == null) return Collections.emptyMap();
        Map<String, Object> m = new LinkedHashMap<>();
        if (p.getOffset() != null) m.put("offset", p.getOffset());
        if (p.getLimit() != null) m.put("limit", p.getLimit());
        if (p.getState() != null) m.put("state", p.getState());
        if (p.getQuoteIds() != null) m.put("quoteIds", p.getQuoteIds());
        if (p.getRequestIds() != null) m.put("requestIds", p.getRequestIds());
        if (p.getMarkets() != null) m.put("markets", p.getMarkets());
        if (p.getSizeMin() != null) m.put("sizeMin", p.getSizeMin());
        if (p.getSizeMax() != null) m.put("sizeMax", p.getSizeMax());
        if (p.getSizeUsdcMin() != null) m.put("sizeUsdcMin", p.getSizeUsdcMin());
        if (p.getSizeUsdcMax() != null) m.put("sizeUsdcMax", p.getSizeUsdcMax());
        if (p.getPriceMin() != null) m.put("priceMin", p.getPriceMin());
        if (p.getPriceMax() != null) m.put("priceMax", p.getPriceMax());
        if (p.getSortBy() != null) m.put("sortBy", p.getSortBy());
        if (p.getSortDir() != null) m.put("sortDir", p.getSortDir());
        return m;
    }

    private static OrderCreationInfo getRequestOrderCreationInfo(RfqQuote quote) {
        String quoteSide = quote.getSide();
        String matchType = quote.getMatchType();

        if (RfqMatchType.COMPLEMENTARY.getValue().equals(matchType)) {
            // Order side is opposite the quote side
            Side side = "BUY".equals(quoteSide) ? Side.SELL : Side.BUY;
            String size = (side == Side.BUY) ? quote.getSizeOut() : quote.getSizeIn();
            return new OrderCreationInfo(quote.getToken(), side, size, quote.getPrice());
        } else if (RfqMatchType.MINT.getValue().equals(matchType) ||
                   RfqMatchType.MERGE.getValue().equals(matchType)) {
            // Order side is same as quote side but uses complement token
            Side side = "BUY".equals(quoteSide) ? Side.BUY : Side.SELL;
            String size = (side == Side.BUY) ? quote.getSizeIn() : quote.getSizeOut();
            // Price is inverted for MINT/MERGE
            double price = 1.0 - quote.getPrice();
            return new OrderCreationInfo(quote.getComplement(), side, size, price);
        }
        throw new IllegalArgumentException("Invalid match type: " + matchType);
    }

    private static Map<String, Object> buildOrderMap(SignedOrder order, String owner) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("owner", owner);
        m.put("salt", order.salt());
        m.put("maker", order.maker());
        m.put("signer", order.signer());
        m.put("taker", order.taker());
        m.put("tokenId", order.tokenId());
        m.put("makerAmount", order.makerAmount());
        m.put("takerAmount", order.takerAmount());
        m.put("nonce", order.nonce());
        m.put("feeRateBps", order.feeRateBps());
        m.put("signatureType", order.signatureType().getValue());
        m.put("signature", order.signature());
        return m;
    }

    private record OrderCreationInfo(String token, Side side, String size, double price) {}
}
