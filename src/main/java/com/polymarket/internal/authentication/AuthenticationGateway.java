package com.polymarket.internal.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.PolymarketConfig;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.ApiKeyDirectory;
import com.polymarket.authentication.ApiKeyDeletion;
import com.polymarket.authentication.ApiKeyValidation;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Transport and protocol for the API-key lifecycle; returns domain values only. */
public final class AuthenticationGateway implements ApiKeyDirectory {

    // Polygon mainnet is the only signing network in 2.0; Amoy was removed.
    private static final int CHAIN_ID = 137;
    private static final String API_KEY_PATH = "/auth/api-key";
    private static final String DERIVE_PATH = "/auth/derive-api-key";
    private static final String API_KEYS_PATH = "/auth/api-keys";
    private static final String CLOSED_ONLY_PATH = "/auth/ban-status/closed-only";

    private final PolymarketConfig config;
    private final HttpRuntime runtime;
    private final Clock clock;

    public AuthenticationGateway(PolymarketConfig config, HttpRuntime runtime, Clock clock) {
        this.config = config;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Override
    public ApiCredentials create(PrivateKeySigner signer) throws IOException {
        // Creation is not idempotent, so it goes through the write path and is never replayed.
        HttpOutcome outcome = runtime.post(
                config.clobHost(), API_KEY_PATH, l1Headers(signer), "");
        return credentials(outcome, "create");
    }

    @Override
    public ApiCredentials derive(PrivateKeySigner signer) throws IOException {
        return credentials(
                runtime.get(config.clobHost(), DERIVE_PATH, l1Headers(signer)), "derive");
    }

    @Override
    public List<String> list(PrivateKeySigner signer) throws IOException {
        HttpOutcome outcome = runtime.get(config.clobHost(), API_KEYS_PATH, l1Headers(signer));
        if (!outcome.successful()) {
            throw new IOException("could not list API keys: HTTP " + outcome.status());
        }
        List<String> keys = new ArrayList<>();
        runtime.parse(outcome.body()).path("apiKeys").forEach(node -> keys.add(node.asText()));
        return List.copyOf(keys);
    }

    @Override
    public ApiKeyValidation validate(ApiCredentials credentials, String address) throws IOException {
        HttpOutcome outcome = runtime.get(config.clobHost(), CLOSED_ONLY_PATH,
                l2Headers(credentials, address, "GET", CLOSED_ONLY_PATH, null));
        if (outcome.successful()) return ApiKeyValidation.accepted();
        if (outcome.status() == 401) return ApiKeyValidation.rejected("HTTP 401");
        throw new IOException("could not validate credentials: HTTP " + outcome.status());
    }

    @Override
    public ApiKeyDeletion delete(ApiCredentials credentials, String address)
            throws IOException {
        HttpOutcome outcome = runtime.delete(config.clobHost(), API_KEY_PATH,
                l2Headers(credentials, address, "DELETE", API_KEY_PATH, null));
        return outcome.successful()
                ? ApiKeyDeletion.succeeded()
                : ApiKeyDeletion.failed("HTTP " + outcome.status());
    }

    private Map<String, String> l1Headers(PrivateKeySigner signer) throws IOException {
        return L1Attestation.headers(signer, CHAIN_ID, clock.instant().getEpochSecond(), 0);
    }

    private Map<String, String> l2Headers(ApiCredentials credentials, String address,
            String method, String path, String body) {
        return L2Attestation.headers(
                credentials, address, clock.instant().getEpochSecond(), method, path, body);
    }

    private ApiCredentials credentials(HttpOutcome outcome, String operation) throws IOException {
        if (!outcome.successful()) {
            throw new IOException("could not " + operation + " an API key: HTTP " + outcome.status());
        }
        JsonNode node = runtime.parse(outcome.body());
        return new ApiCredentials(
                node.path("apiKey").asText(),
                node.path("secret").asText(),
                node.path("passphrase").asText());
    }
}
