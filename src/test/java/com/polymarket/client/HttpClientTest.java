package com.polymarket.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for HttpClient.
 * Verifies JSON serialization/deserialization and HTTP helper methods.
 */
@DisplayName("HttpClient Tests")
class HttpClientTest {

    private HttpClient client;

    @BeforeEach
    void setUp() {
        client = new HttpClient();
    }

    @Test
    @DisplayName("TC-HC-001: JSON serialization is deterministic and minified")
    void testJsonSerializationDeterministic() throws JsonProcessingException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key1", "value1");
        data.put("key2", 123);
        data.put("key3", true);

        String json = client.toJsonMinified(data);

        assertEquals("{\"key1\":\"value1\",\"key2\":123,\"key3\":true}", json);
    }

    @Test
    @DisplayName("TC-HC-002: JSON deserialization works correctly")
    void testJsonDeserialization() throws JsonProcessingException {
        String json = "{\"apiKey\":\"test-key\",\"secret\":\"test-secret\",\"passphrase\":\"test-pass\"}";
        Map<String, Object> result = client.parseJsonObject(json);

        assertEquals("test-key", result.get("apiKey"));
        assertEquals("test-secret", result.get("secret"));
        assertEquals("test-pass", result.get("passphrase"));
    }

    @Test
    @DisplayName("TC-HC-003: Empty/null JSON handling returns empty map")
    void testEmptyJsonHandling() throws JsonProcessingException {
        assertEquals(Collections.emptyMap(), client.parseJsonObject(null));
        assertEquals(Collections.emptyMap(), client.parseJsonObject(""));
        assertEquals(Collections.emptyMap(), client.parseJsonObject("   "));
    }

    @Test
    @DisplayName("TC-HC-004: Nested objects serialize correctly")
    void testNestedObjectsSerialization() throws JsonProcessingException {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("nested", "value");

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("outer", inner);

        String json = client.toJsonMinified(outer);

        assertEquals("{\"outer\":{\"nested\":\"value\"}}", json);
    }

    @Test
    @DisplayName("TC-HC-005: Arrays serialize correctly")
    void testArraysSerialization() throws JsonProcessingException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", List.of("a", "b", "c"));

        String json = client.toJsonMinified(data);

        assertEquals("{\"items\":[\"a\",\"b\",\"c\"]}", json);
    }

    @Test
    @DisplayName("TC-HC-006: Null value serializes as null")
    void testNullValueSerialization() throws JsonProcessingException {
        String json = client.toJsonMinified(null);

        assertEquals("null", json);
    }

    @Test
    @DisplayName("TC-HC-007: Parse nested JSON object")
    void testParseNestedJsonObject() throws JsonProcessingException {
        String json = "{\"order\":{\"tokenId\":\"123\",\"side\":\"BUY\"},\"type\":\"GTC\"}";

        Map<String, Object> result = client.parseJsonObject(json);

        assertNotNull(result.get("order"));
        assertEquals("GTC", result.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> order = (Map<String, Object>) result.get("order");
        assertEquals("123", order.get("tokenId"));
        assertEquals("BUY", order.get("side"));
    }

    @Test
    @DisplayName("TC-HC-008: Parse JSON with array")
    void testParseJsonWithArray() throws JsonProcessingException {
        String json = "{\"bids\":[{\"price\":\"0.50\",\"size\":\"100\"},{\"price\":\"0.49\",\"size\":\"200\"}]}";

        Map<String, Object> result = client.parseJsonObject(json);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bids = (List<Map<String, Object>>) result.get("bids");
        assertEquals(2, bids.size());
        assertEquals("0.50", bids.get(0).get("price"));
        assertEquals("100", bids.get(0).get("size"));
    }

    @Test
    @DisplayName("TC-HC-009: Serialize order-like structure")
    void testSerializeOrderStructure() throws JsonProcessingException {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("salt", 12345);
        order.put("maker", "0x123");
        order.put("signer", "0x123");
        order.put("tokenId", "67890");
        order.put("makerAmount", "1000000");
        order.put("takerAmount", "2000000");
        order.put("side", "BUY");
        order.put("signature", "0xabc");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order", order);
        payload.put("orderType", "GTC");
        payload.put("owner", "api-key");

        String json = client.toJsonMinified(payload);

        assertTrue(json.contains("\"order\":"));
        assertTrue(json.contains("\"orderType\":\"GTC\""));
        assertTrue(json.contains("\"owner\":\"api-key\""));
        assertTrue(json.contains("\"side\":\"BUY\""));
    }

    @Test
    @DisplayName("TC-HC-010: Parse JSON with numeric values")
    void testParseJsonWithNumericValues() throws JsonProcessingException {
        String json = "{\"intVal\":42,\"floatVal\":3.14,\"negVal\":-100}";

        Map<String, Object> result = client.parseJsonObject(json);

        assertEquals(42, result.get("intVal"));
        assertEquals(3.14, result.get("floatVal"));
        assertEquals(-100, result.get("negVal"));
    }

    @Test
    @DisplayName("TC-HC-011: Parse JSON with boolean values")
    void testParseJsonWithBooleanValues() throws JsonProcessingException {
        String json = "{\"trueVal\":true,\"falseVal\":false}";

        Map<String, Object> result = client.parseJsonObject(json);

        assertEquals(true, result.get("trueVal"));
        assertEquals(false, result.get("falseVal"));
    }

    @Test
    @DisplayName("TC-HC-012: Parse JSON with null value")
    void testParseJsonWithNullValue() throws JsonProcessingException {
        String json = "{\"nullVal\":null,\"normalVal\":\"test\"}";

        Map<String, Object> result = client.parseJsonObject(json);

        assertNull(result.get("nullVal"));
        assertEquals("test", result.get("normalVal"));
    }

    @Test
    @DisplayName("TC-HC-015: Serialize empty map")
    void testSerializeEmptyMap() throws JsonProcessingException {
        String json = client.toJsonMinified(Collections.emptyMap());

        assertEquals("{}", json);
    }

    @Test
    @DisplayName("TC-HC-016: Serialize empty list")
    void testSerializeEmptyList() throws JsonProcessingException {
        String json = client.toJsonMinified(Collections.emptyList());

        assertEquals("[]", json);
    }

    @Test
    @DisplayName("TC-HC-017: Parse empty JSON object")
    void testParseEmptyJsonObject() throws JsonProcessingException {
        Map<String, Object> result = client.parseJsonObject("{}");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("TC-HC-018: Special characters in string values")
    void testSpecialCharactersInStringValues() throws JsonProcessingException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "Hello \"World\"");
        data.put("path", "C:\\Users\\Test");

        String json = client.toJsonMinified(data);

        assertTrue(json.contains("\\\"World\\\""));
        assertTrue(json.contains("C:\\\\Users\\\\Test"));
    }

    @Test
    @DisplayName("TC-HC-019: Unicode characters handled correctly")
    void testUnicodeCharactersHandled() throws JsonProcessingException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emoji", "🚀");
        data.put("chinese", "中文");

        String json = client.toJsonMinified(data);
        Map<String, Object> parsed = client.parseJsonObject(json);

        assertEquals("🚀", parsed.get("emoji"));
        assertEquals("中文", parsed.get("chinese"));
    }

    @Test
    @DisplayName("TC-HC-020: Large number handling")
    void testLargeNumberHandling() throws JsonProcessingException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("large", 9999999999999L);

        String json = client.toJsonMinified(data);
        Map<String, Object> parsed = client.parseJsonObject(json);

        assertEquals(9999999999999L, parsed.get("large"));
    }

    @Test
    @DisplayName("TC-HC-021: Parse JSON ignores unknown properties")
    void testParseJsonIgnoresUnknownProperties() throws JsonProcessingException {
        // This tests that DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES is disabled
        String json = "{\"known\":\"value\",\"unknown\":\"ignored\"}";

        Map<String, Object> result = client.parseJsonObject(json);

        assertEquals("value", result.get("known"));
        // Unknown field should still be present in Map (only ignored for typed deserialization)
        assertEquals("ignored", result.get("unknown"));
    }

    @Test
    @DisplayName("TC-HC-022: Constructor with custom OkHttpClient and ObjectMapper")
    void testConstructorWithCustomDependencies() {
        okhttp3.OkHttpClient customOk = new okhttp3.OkHttpClient();
        com.fasterxml.jackson.databind.ObjectMapper customMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        HttpClient customClient = new HttpClient(customOk, customMapper);

        assertSame(customOk, customClient.okHttpClient());
        assertSame(customMapper, customClient.objectMapper());
    }

    @Test
    @DisplayName("TC-HC-023: Constructor rejects null OkHttpClient")
    void testConstructorRejectsNullOkHttpClient() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        assertThrows(NullPointerException.class,
                () -> new HttpClient(null, mapper));
    }

    @Test
    @DisplayName("TC-HC-024: Constructor rejects null ObjectMapper")
    void testConstructorRejectsNullObjectMapper() {
        okhttp3.OkHttpClient ok = new okhttp3.OkHttpClient();

        assertThrows(NullPointerException.class,
                () -> new HttpClient(ok, null));
    }

    @Test
    @DisplayName("TC-HC-025: JSON media type is application/json")
    void testJsonMediaType() {
        assertEquals("application/json; charset=utf-8", HttpClient.JSON.toString());
    }

    @Test
    @DisplayName("TC-HC-026: Serialize primitive values")
    void testSerializePrimitiveValues() throws JsonProcessingException {
        assertEquals("\"string\"", client.toJsonMinified("string"));
        assertEquals("42", client.toJsonMinified(42));
        assertEquals("true", client.toJsonMinified(true));
        assertEquals("3.14", client.toJsonMinified(3.14));
    }

    @Test
    @DisplayName("TC-HC-027: Consistent ordering in LinkedHashMap")
    void testConsistentOrderingInLinkedHashMap() throws JsonProcessingException {
        // LinkedHashMap maintains insertion order
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("z", 1);
        data.put("a", 2);
        data.put("m", 3);

        String json = client.toJsonMinified(data);

        // Should maintain insertion order, not alphabetical
        assertEquals("{\"z\":1,\"a\":2,\"m\":3}", json);
    }

    @Test
    @DisplayName("TC-HC-028: Parse deep nested structure")
    void testParseDeepNestedStructure() throws JsonProcessingException {
        String json = "{\"level1\":{\"level2\":{\"level3\":{\"value\":\"deep\"}}}}";

        Map<String, Object> result = client.parseJsonObject(json);

        @SuppressWarnings("unchecked")
        Map<String, Object> level1 = (Map<String, Object>) result.get("level1");
        @SuppressWarnings("unchecked")
        Map<String, Object> level2 = (Map<String, Object>) level1.get("level2");
        @SuppressWarnings("unchecked")
        Map<String, Object> level3 = (Map<String, Object>) level2.get("level3");

        assertEquals("deep", level3.get("value"));
    }
}
