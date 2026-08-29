package com.polymarket.internal.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.ReadRetryPolicy;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Internal transport adapter. Retry lives here and is keyed on the operation's idempotency,
 * never on client configuration, so a read budget can never replay a write.
 */
public final class HttpRuntime implements AutoCloseable {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final ObjectMapper json;
    private final ReadRetryPolicy readRetryPolicy;
    private final Sleeper sleeper;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Injected so backoff tests need no wall-clock sleeps. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public HttpRuntime(Duration connectTimeout, Duration requestTimeout,
            ReadRetryPolicy readRetryPolicy) {
        this(connectTimeout, requestTimeout, readRetryPolicy, d -> Thread.sleep(d.toMillis()));
    }

    public HttpRuntime(Duration connectTimeout, Duration requestTimeout,
            ReadRetryPolicy readRetryPolicy, Sleeper sleeper) {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
                // Order submission must never be resent behind the caller's back.
                .retryOnConnectionFailure(false)
                .build();
        this.json = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.readRetryPolicy = readRetryPolicy;
        this.sleeper = sleeper;
    }

    /** Idempotent read: retried within the configured budget, honouring Retry-After. */
    public HttpOutcome get(URI base, String path, Map<String, String> headers) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= readRetryPolicy.maxAttempts(); attempt++) {
            try {
                HttpOutcome outcome = execute(request(base, path, headers).get().build());
                if (attempt == readRetryPolicy.maxAttempts() || !outcome.retryable()) {
                    return outcome;
                }
                backoff(attempt, outcome.retryAfter());
            } catch (IOException e) {
                lastFailure = e;
                if (attempt == readRetryPolicy.maxAttempts()) break;
                backoff(attempt, null);
            }
        }
        throw lastFailure != null ? lastFailure : new IOException("read exhausted its retry budget");
    }

    /** Non-idempotent write: executed exactly once, whatever the read retry budget says. */
    public HttpOutcome post(URI base, String path, Map<String, String> headers, String body)
            throws IOException {
        return execute(request(base, path, headers)
                .post(RequestBody.create(body, JSON)).build());
    }

    /** Write: executed exactly once. Retrying a delete can race a concurrent recreate. */
    public HttpOutcome delete(URI base, String path, Map<String, String> headers)
            throws IOException {
        return execute(request(base, path, headers).delete().build());
    }

    /** As {@link #delete(URI, String, Map)}, carrying a body — the CLOB cancel endpoints require one. */
    public HttpOutcome delete(URI base, String path, Map<String, String> headers, String body)
            throws IOException {
        return execute(request(base, path, headers)
                .delete(RequestBody.create(body, JSON)).build());
    }

    public JsonNode parse(String body) throws IOException {
        return json.readTree(body);
    }

    private Request.Builder request(URI base, String path, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(resolve(base, path));
        headers.forEach(builder::header);
        return builder;
    }

    private static String resolve(URI base, String path) {
        String root = base.toString();
        if (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        return root + (path.startsWith("/") ? path : "/" + path);
    }

    private HttpOutcome execute(Request request) throws IOException {
        // A capability handed out before close() keeps its reference to this runtime, so the
        // guard belongs here rather than only on the root's accessors.
        if (closed.get()) throw new IllegalStateException("Polymarket is closed");
        try (Response response = http.newCall(request).execute()) {
            ResponseBody body = response.body();
            return new HttpOutcome(response.code(), body != null ? body.string() : "",
                    retryAfterOf(response));
        }
    }

    private void backoff(int attempt, Duration retryAfter) throws IOException {
        Duration wait = retryAfter != null ? retryAfter : exponential(attempt);
        if (wait.compareTo(readRetryPolicy.maxBackoff()) > 0) wait = readRetryPolicy.maxBackoff();
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while backing off", e);
        }
    }

    private Duration exponential(int attempt) {
        return readRetryPolicy.initialBackoff().multipliedBy(1L << (attempt - 1));
    }

    /** Both legal forms: delay-seconds, and an HTTP-date measured from when this response arrived. */
    private static Duration retryAfterOf(Response response) {
        String header = response.header("Retry-After");
        if (header == null) return null;
        String value = header.trim();
        try {
            return Duration.ofSeconds(Long.parseLong(value));
        } catch (NumberFormatException notSeconds) {
            return untilHttpDate(value, Instant.ofEpochMilli(response.receivedResponseAtMillis()));
        }
    }

    private static Duration untilHttpDate(String value, Instant receivedAt) {
        try {
            Duration wait = Duration.between(receivedAt,
                    Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value)));
            return wait.isNegative() ? Duration.ZERO : wait;
        } catch (DateTimeParseException notADate) {
            return null;
        }
    }

    @Override
    public void close() {
        closed.set(true);
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }
}
