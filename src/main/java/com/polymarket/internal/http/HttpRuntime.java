package com.polymarket.internal.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.ReadRetryPolicy;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    // ponytail: seconds-form Retry-After only. The HTTP-date form is legal but Polymarket does not
    // send it; add a date parse here if that ever changes.
    private static Duration retryAfterOf(Response response) {
        String header = response.header("Retry-After");
        if (header == null) return null;
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException notSeconds) {
            return null;
        }
    }

    @Override
    public void close() {
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }
}
