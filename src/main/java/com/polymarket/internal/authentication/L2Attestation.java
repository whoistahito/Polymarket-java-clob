package com.polymarket.internal.authentication;

import com.polymarket.authentication.ApiCredentials;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Builds the CLOB L2 HMAC headers over timestamp + method + path + body. */
public final class L2Attestation {

    private L2Attestation() {
    }

    public static Map<String, String> headers(ApiCredentials credentials, String address,
            long timestampSeconds, String method, String path, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("POLY_ADDRESS", address);
        headers.put("POLY_SIGNATURE", sign(credentials.secret(), timestampSeconds, method, path, body));
        headers.put("POLY_TIMESTAMP", String.valueOf(timestampSeconds));
        headers.put("POLY_API_KEY", credentials.key());
        headers.put("POLY_PASSPHRASE", credentials.passphrase());
        return headers;
    }

    static String sign(String urlSafeBase64Secret, long timestampSeconds, String method,
            String path, String body) {
        String message = timestampSeconds + method + path + (body != null ? body : "");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    Base64.getUrlDecoder().decode(urlSafeBase64Secret), "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("could not compute the L2 signature", e);
        }
    }
}
