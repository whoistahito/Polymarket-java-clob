package com.polymarket.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;

/**
 * Deserializes fields that the Gamma API returns as JSON-embedded strings,
 * e.g. {@code "clobTokenIds": "[\"123\",\"456\"]"}, into a typed {@code List<String>}.
 *
 * <p>Returns an empty list for {@code null} or blank values.
 */
public class JsonEmbeddedListDeserializer extends JsonDeserializer<List<String>> {

    private static final ObjectMapper INNER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {};

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String raw = p.getText();
        if (raw == null || raw.isBlank() || raw.equals("null")) return List.of();
        return INNER.readValue(raw, LIST_OF_STRING);
    }

    @Override
    public List<String> getNullValue(DeserializationContext ctxt) {
        return List.of();
    }
}
