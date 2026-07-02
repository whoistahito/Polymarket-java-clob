package com.polymarket.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.polymarket.model.data.DataTrade;
import com.polymarket.model.data.DataTradesRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Client for the Polymarket Data API ({@code https://data-api.polymarket.com}).
 *
 * <p>Provides unauthenticated read-only access to public trade history, positions,
 * and analytics data. This is a standalone client that does not require any
 * Polymarket API credentials.
 *
 * <p>Mirrors the Rust SDK's {@code data::Client} struct.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Standalone
 * DataClient dataClient = new DataClient.Builder().build();
 *
 * // Or via PolymarketClient
 * DataClient dataClient = polymarketClient.data();
 *
 * List<DataTrade> trades = dataClient.trades(
 *     DataTradesRequest.builder()
 *         .user("0x1234567890abcdef1234567890abcdef12345678")
 *         .limit(50)
 *         .build()
 * );
 * }</pre>
 */
public final class DataClient {

    static final String DEFAULT_HOST = "https://data-api.polymarket.com";

    private final String host;
    private final HttpClient http;

    private DataClient(Builder builder) {
        this.host = builder.host != null ? builder.host : DEFAULT_HOST;
        this.http = builder.http != null ? builder.http : new HttpClient();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {
        private String host;
        private HttpClient http;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder httpClient(HttpClient http) {
            this.http = http;
            return this;
        }

        public DataClient build() {
            return new DataClient(this);
        }
    }

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    /**
     * Fetches trade history for a user or markets from the Data API.
     *
     * <p>Maps to {@code GET https://data-api.polymarket.com/trades}.
     * No authentication required.
     *
     * @param req filter parameters; pass {@code null} or an empty request for no filtering
     * @return list of trades matching the filters
     * @throws IOException on HTTP or deserialization failure
     */
    public List<DataTrade> trades(DataTradesRequest req) throws IOException {
        Map<String, String> queryParams = req != null ? req.toQueryParams() : Collections.emptyMap();
        String url = buildUrl("trades", queryParams);
        String json = http.get(url, Collections.emptyMap());
        return http.parseJson(json, new TypeReference<List<DataTrade>>() {});
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String buildUrl(String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(host).append('/').append(path);
        if (params != null && !params.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) sb.append('&');
                sb.append(encodeParam(entry.getKey()))
                  .append('=')
                  .append(encodeParam(entry.getValue()));
                first = false;
            }
        }
        return sb.toString();
    }

    private static String encodeParam(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }
}
