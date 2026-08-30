package com.polymarket.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** The pinned official stream frames (issues #21/#22/#23), read from {@code protocol/streams.json}. */
final class StreamProtocol {

    private static final JsonNode ROOT = load();

    private StreamProtocol() {}

    static JsonNode at(String... path) {
        JsonNode node = ROOT;
        for (String step : path) {
            node = node.get(step);
            if (node == null) {
                throw new AssertionError("streams.json has no " + String.join("/", path));
            }
        }
        return node;
    }

    /** The field names of a pinned frame or schema, in documented order. */
    static List<String> fieldsOf(String... path) {
        JsonNode node = at(path);
        List<String> names = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> names.add(n.asText()));
        } else {
            node.fieldNames().forEachRemaining(names::add);
        }
        return names;
    }

    private static JsonNode load() {
        try (InputStream in = StreamProtocol.class.getResourceAsStream("/protocol/streams.json")) {
            return new ObjectMapper().readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("protocol/streams.json is missing or unreadable", e);
        }
    }
}
