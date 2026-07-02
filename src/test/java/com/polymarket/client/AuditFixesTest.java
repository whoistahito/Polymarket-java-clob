package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.model.gamma.GammaComment;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the docs-vs-implementation audit (June 2026).
 *
 * <p>Each test pins a behaviour where the Java SDK diverged from the live API field
 * names / wire shapes confirmed against the Rust SDK and the OpenAPI docs.
 */
@DisplayName("TC-AUD — endpoint audit fixes")
class AuditFixesTest {

    private static final String PK =
        "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private MockWebServer server;
    private PolymarketClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        client = new PolymarketClient.Builder()
            .privateKey(PK)
            .clobHost(baseUrl)
            .gammaHost(baseUrl)
            .apiCreds(new ApiKeyCreds("test-key", "c2VjcmV0", "pass123"))
            .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse()
            .setBody(body)
            .addHeader("Content-Type", "application/json"));
    }

    @Test
    @DisplayName("TC-AUD-001 getTickSize reads minimum_tick_size (canonical API field)")
    void tickSizeCanonicalField() throws Exception {
        enqueue("{\"minimum_tick_size\":\"0.001\"}");
        assertEquals("0.001", client.getTickSize("tok1"));
    }

    @Test
    @DisplayName("TC-AUD-002 getFeeRateBps reads base_fee (canonical API field)")
    void feeRateCanonicalField() throws Exception {
        enqueue("{\"base_fee\":30}");
        assertEquals(30, client.getFeeRateBps("tok2"));
    }

    @Test
    @DisplayName("TC-AUD-003 areOrdersScoring posts a bare JSON array, not {orderIds:[...]}")
    void ordersScoringBareArray() throws Exception {
        enqueue("{\"0xaaa\":true,\"0xbbb\":false}");
        Map<String, Boolean> scoring = client.areOrdersScoring(List.of("0xaaa", "0xbbb"));
        assertEquals(Boolean.TRUE, scoring.get("0xaaa"));

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8().trim();
        assertTrue(body.startsWith("["), "expected bare JSON array, got: " + body);
        assertEquals("[\"0xaaa\",\"0xbbb\"]", body);
    }

    @Test
    @DisplayName("TC-AUD-004 getRewardPercentages parses values as BigDecimal")
    void rewardPercentagesBigDecimal() throws Exception {
        enqueue("{\"0xmkt\":12.5}");
        Map<String, BigDecimal> pct = client.getRewardPercentages();
        assertEquals(0, new BigDecimal("12.5").compareTo(pct.get("0xmkt")));
    }

    @Test
    @DisplayName("TC-AUD-005 GammaComment deserializes parentEntityID/parentCommentID (mixed casing)")
    void gammaCommentMixedCasing() throws Exception {
        enqueue("[{\"id\":\"c1\",\"body\":\"hi\",\"parentEntityType\":\"Event\","
            + "\"parentEntityID\":\"18396\",\"parentCommentID\":\"42\","
            + "\"userAddress\":\"0xUser\"}]");
        List<GammaComment> comments = client.gamma()
            .commentsById(com.polymarket.model.gamma.CommentsByIdRequest.builder().id("c1").build());
        GammaComment c = comments.get(0);
        assertEquals("18396", c.parentEntityId());
        assertEquals("42", c.parentCommentId());
        assertEquals("0xUser", c.userAddress());
    }
}
