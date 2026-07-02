package com.polymarket.client;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;

/**
 * Level 2 (L2) HMAC signer compatible with Polymarket's {@code py-clob-client}.
 *
 * <p>Compatibility notes (mirrors {@code py_clob_client/signing/hmac.py}):
 * <ul>
 *   <li>Secret is URL-safe base64 decoded before use as HMAC key</li>
 *   <li>Message format: {@code timestamp + method + requestPath + bodyIfPresent}</li>
 *   <li>If body is present, {@code str(body).replace("'", "\"")} is applied in python.
 *       In Java, you should pass a pre-serialized JSON string to avoid ambiguity and
 *       to match py-clob-client's deterministic signing behavior, then this class will
 *       apply the same single-quote replacement for parity.</li>
 *   <li>HMAC algorithm: HmacSHA256</li>
 *   <li>Signature output: URL-safe base64 encoded (with padding), as in python</li>
 * </ul>
 */
public final class L2HmacSigner {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final Clock clock;

    public L2HmacSigner() {
        this(Clock.systemUTC());
    }

    public L2HmacSigner(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Create an L2 signature for the given request parameters.
     *
     * @param apiSecretUrlSafeBase64 the API secret (URL-safe base64), as returned by Polymarket
     * @param timestampSeconds unix timestamp in seconds
     * @param method HTTP method (e.g., GET, POST, DELETE)
     * @param requestPath request path exactly as signed (e.g., "/order", "/data/order/{id}")
     * @param bodyForSig optional body string; pass the exact JSON string you will send (minified)
     * @return URL-safe base64 encoded signature string
     */
    public String sign(
            String apiSecretUrlSafeBase64,
            long timestampSeconds,
            String method,
            String requestPath,
            String bodyForSig
    ) {
        Objects.requireNonNull(apiSecretUrlSafeBase64, "apiSecretUrlSafeBase64");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(requestPath, "requestPath");

        String msg = buildMessage(timestampSeconds, method, requestPath, bodyForSig);

        byte[] keyBytes = decodeUrlSafeBase64(apiSecretUrlSafeBase64);
        byte[] mac = hmacSha256(keyBytes, msg.getBytes(StandardCharsets.UTF_8));

        // py-clob-client uses base64.urlsafe_b64encode which includes padding by default.
        return Base64.getUrlEncoder().encodeToString(mac);
    }

    /**
     * Convenience helper that signs using "now" (unix seconds) from the configured clock.
     */
    public SignedL2Payload signNow(
            String apiSecretUrlSafeBase64,
            String method,
            String requestPath,
            String bodyForSig
    ) {
        long ts = clock.instant().getEpochSecond();
        String sig = sign(apiSecretUrlSafeBase64, ts, method, requestPath, bodyForSig);
        return new SignedL2Payload(ts, sig);
    }

    /**
     * Build the exact python-compatible message:
     * {@code str(timestamp) + str(method) + str(requestPath) + str(body).replace("'", "\"")}
     * (only if body is "truthy" / present).
     */
    public static String buildMessage(long timestampSeconds, String method, String requestPath, String bodyForSig) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(requestPath, "requestPath");

        StringBuilder sb = new StringBuilder(64);
        sb.append(timestampSeconds);
        sb.append(method);
        sb.append(requestPath);

        if (bodyForSig != null && !bodyForSig.isEmpty()) {
            // Match py-clob-client: replace single quotes with double quotes before signing.
            // If you pass valid JSON, this is typically a no-op.
            sb.append(bodyForSig.replace('\'', '"'));
        }

        return sb.toString();
    }

    private static byte[] decodeUrlSafeBase64(String urlSafeBase64) {
        // py-clob-client uses base64.urlsafe_b64decode, which tolerates missing padding.
        // Java's decoder may require padding; we normalize length to a multiple of 4.
        String normalized = normalizeBase64Padding(urlSafeBase64);
        return Base64.getUrlDecoder().decode(normalized);
    }

    private static String normalizeBase64Padding(String s) {
        String trimmed = s.trim();
        int mod = trimmed.length() % 4;
        if (mod == 0) return trimmed;
        int pad = 4 - mod;
        return trimmed + "=".repeat(pad);
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            return mac.doFinal(message);
        } catch (Exception e) {
            // Keep as unchecked: signing failures are unrecoverable at runtime.
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    /**
     * Small value object returned by {@link #signNow(String, String, String, String)}.
     */
    public record SignedL2Payload(long timestampSeconds, String signature) {}
}
