package com.polymarket.internal.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymarket.PolymarketConfig;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.operations.GeoblockStatus;
import com.polymarket.operations.PolymarketService;
import com.polymarket.operations.ServerTime;
import com.polymarket.operations.ServiceHealth;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Translates operator wire responses into domain values, keeping JSON and HTTP out of
 * the public domain packages.
 */
public final class OperationsGateway {

    private static final Map<String, String> ACCEPT_JSON = Map.of("Accept", "application/json");

    private final PolymarketConfig config;
    private final HttpRuntime runtime;

    public OperationsGateway(PolymarketConfig config, HttpRuntime runtime) {
        this.config = config;
        this.runtime = runtime;
    }

    public ServerTime serverTime() throws IOException {
        HttpOutcome outcome = read(config.clobHost(), "/time");
        if (!outcome.successful()) {
            throw new IOException("server time read failed with HTTP " + outcome.status());
        }
        try {
            return ServerTime.ofEpochSeconds(Long.parseLong(outcome.body().trim()));
        } catch (NumberFormatException e) {
            throw new IOException("server time was not a unix timestamp: " + outcome.body(), e);
        }
    }

    public List<ServiceHealth> health() {
        List<ServiceHealth> results = new ArrayList<>();
        results.add(probe(PolymarketService.CLOB, config.clobHost(), "/time"));
        results.add(probe(PolymarketService.GAMMA, config.gammaHost(), "/status"));
        results.add(probe(PolymarketService.DATA, config.dataHost(), "/"));
        return List.copyOf(results);
    }

    public GeoblockStatus geoblock() throws IOException {
        HttpOutcome outcome = read(config.geoblockHost(), "/api/geoblock");
        if (!outcome.successful()) {
            throw new IOException("geoblock read failed with HTTP " + outcome.status());
        }
        JsonNode node = runtime.parse(outcome.body());
        return new GeoblockStatus(
                node.path("blocked").asBoolean(false),
                text(node, "ip"), text(node, "country"), text(node, "region"));
    }

    private ServiceHealth probe(PolymarketService service, URI host, String path) {
        try {
            HttpOutcome outcome = read(host, path);
            return outcome.successful()
                    ? ServiceHealth.up(service)
                    : ServiceHealth.down(service, "HTTP " + outcome.status());
        } catch (IOException e) {
            return ServiceHealth.down(service, e.getMessage());
        }
    }

    private HttpOutcome read(URI host, String path) throws IOException {
        return runtime.get(host, path, ACCEPT_JSON);
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? Optional.empty() : Optional.of(value.asText());
    }
}
