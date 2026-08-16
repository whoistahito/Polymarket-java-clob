package com.polymarket.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Minimal HTTP client wrapper around OkHttp + Jackson.
 *
 * <p>For signed requests, serialize JSON once via {@link #toJsonMinified(Object)} and send exactly
 * the bytes you signed.
 */
public final class HttpClient {

    public static final MediaType JSON = MediaType.get(
        "application/json; charset=utf-8"
    );

    private final OkHttpClient ok;
    private final ObjectMapper mapper;
    private final int maxRetries;

    /**
     * Creates an HttpClient with default settings.
     */
    public HttpClient() {
        this(defaultOkHttpClient(null, null), defaultObjectMapper(), 0);
    }

    /**
     * Creates an HttpClient with a custom OkHttpClient and ObjectMapper.
     *
     * @param okHttpClient the OkHttp client to use
     * @param objectMapper the Jackson ObjectMapper to use
     */
    public HttpClient(OkHttpClient okHttpClient, ObjectMapper objectMapper) {
        this(okHttpClient, objectMapper, 0);
    }

    private HttpClient(
        OkHttpClient okHttpClient,
        ObjectMapper objectMapper,
        int maxRetries
    ) {
        this.ok = Objects.requireNonNull(okHttpClient, "okHttpClient");
        this.mapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.maxRetries = Math.max(0, maxRetries);
    }

    /**
     * @return the underlying OkHttpClient
     */
    public OkHttpClient okHttpClient() {
        return ok;
    }

    /**
     * @return the underlying ObjectMapper
     */
    public ObjectMapper objectMapper() {
        return mapper;
    }

    /**
     * Returns a derived client sharing this client's connection pool and object mapper, but
     * with BOTH of this SDK's automatic-replay mechanisms turned off, unconditionally — not
     * inherited from this client (Ticket 035).
     *
     * <p>{@code POST /order} gives no exactly-once guarantee: a resend can put the same order on the
     * book twice, so nothing is allowed to resubmit it without the caller's knowledge. Two separate
     * mechanisms can do exactly that, and both are disabled here regardless of how this client (the
     * one this method is called on) was configured:
     *
     * <ul>
     *   <li><b>OkHttp's connection-failure retry</b> — {@code retryOnConnectionFailure(true)} (the
     *       default here, see {@link #defaultOkHttpClient}) lets OkHttp silently resend a request on
     *       a fresh connection after the original one broke mid-flight; {@code call.execute()} then
     *       returns a normal response with no signal that two requests went out.</li>
     *   <li><b>This class's own app-level retry loop</b> ({@link #executeToString}, governed by
     *       {@link #maxRetries}) — it retries on any {@link IOException} and on retryable HTTP
     *       statuses (425/429/5xx) for ANY method, including POST. If the derived client inherited
     *       this client's {@code maxRetries}, a caller who raises it for GET resilience would
     *       silently re-enable the exact POST /order replay this method exists to remove. The
     *       returned client is therefore hardcoded to {@code maxRetries = 0}, independent of this
     *       client's value, so order placement is immune by construction rather than by the accident
     *       of whoever built this client leaving retries at zero.</li>
     * </ul>
     *
     * <p>Order placement must run on the client returned here (or otherwise be exempted from both
     * mechanisms above); GET/read paths may keep the client that made this call, retry budget and
     * all — repeating a lost read is safe.
     *
     * @return a new {@link HttpClient} wrapping the same connection pool, with connection-failure
     *     retry turned off and the app-level retry budget forced to zero
     */
    public HttpClient withoutConnectionFailureRetry() {
        OkHttpClient noReplayOk = ok.newBuilder().retryOnConnectionFailure(false).build();
        // maxRetries is hardcoded to 0, NOT inherited as `this.maxRetries`: see the class-level
        // mechanism #2 above. Order placement must never replay, no matter what a caller sets for
        // reads.
        return new HttpClient(noReplayOk, mapper, 0);
    }

    // =========================================================================
    // High-level JSON helpers
    // =========================================================================

    public Map<String, Object> getJsonObject(
        String url,
        Map<String, String> headers
    ) throws IOException {
        String body = get(url, headers);
        return parseJsonObject(body);
    }

    public <T> T getJson(
        String url,
        Map<String, String> headers,
        Class<T> clazz
    ) throws IOException {
        String body = get(url, headers);
        return parseJson(body, clazz);
    }

    public <T> T getJson(
        String url,
        Map<String, String> headers,
        TypeReference<T> type
    ) throws IOException {
        String body = get(url, headers);
        return parseJson(body, type);
    }

    public Map<String, Object> postJsonObject(
        String url,
        Map<String, String> headers,
        Object body
    ) throws IOException {
        String json = body == null ? null : toJsonMinified(body);
        String resp = post(url, headers, json);
        return parseJsonObject(resp);
    }

    public String postJson(String url, Map<String, String> headers, Object body)
        throws IOException {
        String json = body == null ? null : toJsonMinified(body);
        return post(url, headers, json);
    }

    public String postJsonRaw(
        String url,
        Map<String, String> headers,
        String jsonBody
    ) throws IOException {
        return post(url, headers, jsonBody);
    }

    public String deleteJson(
        String url,
        Map<String, String> headers,
        Object body
    ) throws IOException {
        String json = body == null ? null : toJsonMinified(body);
        return delete(url, headers, json);
    }

    public String deleteJsonRaw(
        String url,
        Map<String, String> headers,
        String jsonBody
    ) throws IOException {
        return delete(url, headers, jsonBody);
    }

    // =========================================================================
    // Core HTTP methods (raw string response)
    // =========================================================================

    public String get(String url, Map<String, String> headers)
        throws IOException {
        Request.Builder b = new Request.Builder().url(url).get();
        applyHeaders(b, headers, "GET", /*forceJsonContentType*/ false);

        return executeToString(b.build());
    }

    /**
     * POST with a pre-serialized JSON string.
     *
     * <p>If {@code jsonBody} is null, an empty body is sent (still valid for some endpoints).
     */
    public String post(String url, Map<String, String> headers, String jsonBody)
        throws IOException {
        RequestBody rb = RequestBody.create(
            jsonBody == null ? "" : jsonBody,
            JSON
        );

        Request.Builder b = new Request.Builder().url(url).post(rb);
        applyHeaders(b, headers, "POST", /*forceJsonContentType*/ true);

        return executeToString(b.build());
    }

    /**
     * DELETE with a pre-serialized JSON string.
     *
     * <p>Some CLOB endpoints (e.g. cancel order) require JSON bodies on DELETE.
     */
    public String delete(
        String url,
        Map<String, String> headers,
        String jsonBody
    ) throws IOException {
        RequestBody rb = RequestBody.create(
            jsonBody == null ? "" : jsonBody,
            JSON
        );

        Request.Builder b = new Request.Builder().url(url).delete(rb);
        applyHeaders(b, headers, "DELETE", /*forceJsonContentType*/ true);

        return executeToString(b.build());
    }

    // =========================================================================
    // JSON (de)serialization
    // =========================================================================

    /**
     * Minified deterministic JSON (no pretty printing).
     *
     * <p>For signing, generate a single JSON string with this method, sign it,
     * then send the exact same bytes.
     */
    public String toJsonMinified(Object value) throws JsonProcessingException {
        if (value == null) return "null";
        return mapper.writeValueAsString(value);
    }

    public Map<String, Object> parseJsonObject(String json)
        throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        return mapper.readValue(
            json,
            new TypeReference<Map<String, Object>>() {}
        );
    }

    public <T> T parseJson(String json, Class<T> clazz)
        throws JsonProcessingException {
        if (json == null) {
            return null;
        }
        return mapper.readValue(json, clazz);
    }

    public <T> T parseJson(String json, TypeReference<T> type)
        throws JsonProcessingException {
        if (json == null) {
            return null;
        }
        return mapper.readValue(json, type);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private String executeToString(Request request) throws IOException {
        IOException lastException = null;
        int attempts = maxRetries + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                Call call = ok.newCall(request);
                try (Response resp = call.execute()) {
                    if (resp.body() == null) {
                        throw new IOException(
                            "HTTP " +
                                resp.code() +
                                " with empty body for " +
                                request.method() +
                                " " +
                                request.url()
                        );
                    }
                    String body = resp.body().string();
                    if (!resp.isSuccessful()) {
                        throw new HttpStatusException(
                            resp.code(),
                            body,
                            "HTTP " +
                                resp.code() +
                                " for " +
                                request.method() +
                                " " +
                                request.url() +
                                ": " +
                                body
                        );
                    }
                    return body;
                }
            } catch (IOException e) {
                lastException = e;
                if (e instanceof HttpStatusException status
                    && !isRetryableStatus(status.statusCode())) {
                    throw e;
                }
                if (attempt < attempts - 1) {
                    // brief back-off before retry
                    try { Thread.sleep(200L * (attempt + 1)); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw lastException;
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 425 || statusCode == 429 || statusCode >= 500;
    }

    private static void applyHeaders(
        Request.Builder b,
        Map<String, String> headers,
        String method,
        boolean forceJsonContentType
    ) {
        // Mimic py-clob-client "overloadHeaders" defaults.
        b.header("User-Agent", "polymarket-java-clob");
        b.header("Accept", "*/*");
        b.header("Connection", "keep-alive");

        if (forceJsonContentType) {
            b.header("Content-Type", "application/json");
        } else {
            // Only set content-type for bodies; GET typically doesn't need it.
            // But if caller provided it explicitly, we keep it.
        }

        // Let OkHttp handle gzip transparently - do NOT set Accept-Encoding manually.
        // When Accept-Encoding is not set, OkHttp adds it automatically AND decompresses.
        // If we set it manually, OkHttp expects us to handle decompression ourselves.

        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) continue;
                if (e.getValue() == null) continue;
                b.header(e.getKey(), e.getValue());
            }
        }
    }

    private static OkHttpClient defaultOkHttpClient(
        Duration connectTimeout,
        Duration readTimeout
    ) {
        ConnectionPool pool = new ConnectionPool(20, 5, TimeUnit.MINUTES);

        return new OkHttpClient.Builder()
            .connectionPool(pool)
            .connectTimeout(
                connectTimeout != null ? connectTimeout : Duration.ofSeconds(5)
            )
            .readTimeout(
                readTimeout != null ? readTimeout : Duration.ofSeconds(10)
            )
            .writeTimeout(Duration.ofSeconds(10))
            .callTimeout(Duration.ofSeconds(20))
            .retryOnConnectionFailure(true)
            .build();
    }

    private static ObjectMapper defaultObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.findAndRegisterModules();
        om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // Polymarket mixes ID/Id casing across fields; match case-insensitively.
        om.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
        return om;
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Builder for creating HttpClient instances with custom configuration.
     */
    public static class Builder {

        private ObjectMapper objectMapper;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;
        private Duration callTimeout;
        private int connectionPoolSize = 20;
        private int connectionPoolKeepAliveMinutes = 5;
        private int maxRetries = 0;

        public Builder() {}

        /**
         * Set a custom ObjectMapper.
         *
         * @param objectMapper the ObjectMapper to use
         * @return this builder
         */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        /**
         * Set the connection timeout.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /**
         * Set the read timeout.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        /**
         * Set the write timeout.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }

        /**
         * Set the overall call timeout.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder callTimeout(Duration timeout) {
            this.callTimeout = timeout;
            return this;
        }

        /**
         * Set connection pool size.
         *
         * @param size the maximum number of idle connections
         * @return this builder
         */
        public Builder connectionPoolSize(int size) {
            this.connectionPoolSize = size;
            return this;
        }

        /**
         * Set connection pool keep-alive duration.
         *
         * @param minutes the keep-alive duration in minutes
         * @return this builder
         */
        public Builder connectionPoolKeepAlive(int minutes) {
            this.connectionPoolKeepAliveMinutes = minutes;
            return this;
        }

        /**
         * Set the number of retries on error.
         * A value of 1 means one retry (2 total attempts). Default is 0 (no retries).
         *
         * @param maxRetries the maximum number of retries
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Build the HttpClient with the configured settings.
         *
         * @return a new HttpClient instance
         */
        public HttpClient build() {
            ConnectionPool pool = new ConnectionPool(
                connectionPoolSize,
                connectionPoolKeepAliveMinutes,
                TimeUnit.MINUTES
            );

            OkHttpClient.Builder okBuilder = new OkHttpClient.Builder()
                .connectionPool(pool)
                .connectTimeout(
                    connectTimeout != null
                        ? connectTimeout
                        : Duration.ofSeconds(5)
                )
                .readTimeout(
                    readTimeout != null ? readTimeout : Duration.ofSeconds(10)
                )
                .writeTimeout(
                    writeTimeout != null ? writeTimeout : Duration.ofSeconds(10)
                )
                .callTimeout(
                    callTimeout != null ? callTimeout : Duration.ofSeconds(20)
                )
                .retryOnConnectionFailure(true);

            ObjectMapper mapper =
                objectMapper != null ? objectMapper : defaultObjectMapper();

            return new HttpClient(okBuilder.build(), mapper, maxRetries);
        }
    }
}
