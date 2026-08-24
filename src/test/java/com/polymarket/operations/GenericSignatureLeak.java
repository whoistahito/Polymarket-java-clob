package com.polymarket.operations;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import okhttp3.Response;

/**
 * Test-only forbidden dependency: every leak here hides inside a generic signature, an array, or a
 * type-variable bound, so the raw-type rule passes it. Never shipped.
 */
public final class GenericSignatureLeak {

    /** Leak in a type argument. */
    public List<Response> responses() {
        return null;
    }

    /** Leak nested two levels deep. */
    public Map<String, Optional<JsonNode>> nested() {
        return null;
    }

    /** Leak behind a wildcard bound. */
    public CompletableFuture<? extends Response> pending() {
        return null;
    }

    /** Leak in a parameter's type argument. */
    public void accept(List<JsonNode> nodes) {
    }

    /** Leak as an array component inside a type argument. */
    public List<Response[]> arrays() {
        return null;
    }

    /** Leak in a method type-parameter bound. */
    public <T extends JsonNode> T bounded() {
        return null;
    }
}
