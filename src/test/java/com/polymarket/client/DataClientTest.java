package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.*;

import com.polymarket.model.data.DataSide;
import com.polymarket.model.data.DataTrade;
import com.polymarket.model.data.DataTradesRequest;
import com.polymarket.model.data.FilterType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DataClient}.
 *
 * <p>Test data and JSON payloads are derived from the Rust SDK's
 * {@code tests/data.rs} module to ensure parity. Uses {@link MockWebServer}
 * (OkHttp) to serve canned responses without real network calls.
 */
@DisplayName("DataClient")
class DataClientTest {

    // Test constants matching Rust SDK test vectors (tests/data.rs)
    private static final String TEST_USER    = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String TEST_COND_ID = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String TEST_TX_HASH = "0x2222222222222222222222222222222222222222222222222222222222222222";
    private static final String TEST_ASSET   = "0x1111111111111111111111111111111111111111111111111111111111111111";

    /** Full trade JSON matching Rust SDK tests/data.rs {@code trades_should_succeed} fixture. */
    private static final String TRADE_JSON = "["
        + "{"
        + "\"proxyWallet\":\"" + TEST_USER + "\","
        + "\"side\":\"BUY\","
        + "\"asset\":\"" + TEST_ASSET + "\","
        + "\"conditionId\":\"" + TEST_COND_ID + "\","
        + "\"size\":50.0,"
        + "\"price\":0.55,"
        + "\"timestamp\":1703980800,"
        + "\"title\":\"Market Title\","
        + "\"slug\":\"market-slug\","
        + "\"icon\":\"https://example.com/icon.png\","
        + "\"eventSlug\":\"event-slug\","
        + "\"outcome\":\"Yes\","
        + "\"outcomeIndex\":0,"
        + "\"name\":\"Trader Name\","
        + "\"pseudonym\":\"TraderX\","
        + "\"bio\":\"A trader\","
        + "\"profileImage\":\"https://example.com/avatar.png\","
        + "\"profileImageOptimized\":\"https://example.com/avatar-opt.png\","
        + "\"transactionHash\":\"" + TEST_TX_HASH + "\""
        + "}"
        + "]";

    private MockWebServer server;
    private DataClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String base = server.url("").toString().replaceAll("/$", "");
        client = new DataClient.Builder().host(base).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .addHeader("Content-Type", "application/json"));
    }

    // -------------------------------------------------------------------------
    // trades() — request/response integration
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("trades()")
    class TradesTests {

        @Test
        @DisplayName("TC-DC-001: trades() happy path deserializes all fields")
        void tradesHappyPath() throws Exception {
            enqueue(TRADE_JSON);

            List<DataTrade> result = client.trades(null);

            assertEquals(1, result.size());
            DataTrade t = result.get(0);
            assertEquals(TEST_USER,    t.getProxyWallet());
            assertEquals(DataSide.BUY, t.getSide());
            assertEquals(TEST_ASSET,   t.getAsset());
            assertEquals(TEST_COND_ID, t.getConditionId());
            assertEquals(new BigDecimal("50.0"),  t.getSize());
            assertEquals(new BigDecimal("0.55"),  t.getPrice());
            assertEquals(1_703_980_800L,           t.getTimestamp());
            assertEquals("Market Title",           t.getTitle());
            assertEquals("market-slug",            t.getSlug());
            assertEquals("https://example.com/icon.png", t.getIcon());
            assertEquals("event-slug",             t.getEventSlug());
            assertEquals("Yes",                    t.getOutcome());
            assertEquals(0,                        t.getOutcomeIndex());
            assertEquals("Trader Name",            t.getName());
            assertEquals("TraderX",                t.getPseudonym());
            assertEquals("A trader",               t.getBio());
            assertEquals("https://example.com/avatar.png",     t.getProfileImage());
            assertEquals("https://example.com/avatar-opt.png", t.getProfileImageOptimized());
            assertEquals(TEST_TX_HASH,             t.getTransactionHash());

            RecordedRequest req = server.takeRequest();
            assertEquals("/trades", req.getPath());
        }

        @Test
        @DisplayName("TC-DC-002: trades() empty response returns empty list")
        void tradesEmptyResult() throws Exception {
            enqueue("[]");

            List<DataTrade> result = client.trades(DataTradesRequest.builder().build());

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("TC-DC-003: trades() null request hits /trades with no query params")
        void tradesNullRequest() throws Exception {
            enqueue("[]");

            client.trades(null);

            RecordedRequest req = server.takeRequest();
            assertEquals("/trades", req.getPath());
        }

        @Test
        @DisplayName("TC-DC-004: trades() with user filter adds user query param")
        void tradesWithUser() throws Exception {
            enqueue("[]");

            client.trades(DataTradesRequest.builder().user(TEST_USER).build());

            RecordedRequest req = server.takeRequest();
            assertTrue(req.getPath().contains("user="), "Path should include user param: " + req.getPath());
            assertTrue(req.getPath().contains("1234567890abcdef"), "Path should include user address");
        }

        @Test
        @DisplayName("TC-DC-005: trades() with side=BUY adds side query param")
        void tradesWithSide() throws Exception {
            enqueue("[]");

            client.trades(DataTradesRequest.builder().side(DataSide.BUY).build());

            RecordedRequest req = server.takeRequest();
            assertTrue(req.getPath().contains("side=BUY"), "Path should include side=BUY: " + req.getPath());
        }

        @Test
        @DisplayName("TC-DC-006: trades() with limit and offset adds pagination params")
        void tradesWithPagination() throws Exception {
            enqueue("[]");

            client.trades(DataTradesRequest.builder().limit(50).offset(100).build());

            RecordedRequest req = server.takeRequest();
            String path = req.getPath();
            assertTrue(path.contains("limit=50"),   "Path should include limit=50: "   + path);
            assertTrue(path.contains("offset=100"), "Path should include offset=100: " + path);
        }

        @Test
        @DisplayName("TC-DC-007: trades() with takerOnly=false adds takerOnly=false param")
        void tradesWithTakerOnly() throws Exception {
            enqueue("[]");

            client.trades(DataTradesRequest.builder().takerOnly(false).build());

            RecordedRequest req = server.takeRequest();
            assertTrue(req.getPath().contains("takerOnly=false"),
                "Path should include takerOnly=false: " + req.getPath());
        }

        @Test
        @DisplayName("TC-DC-008: trades() with filterType+filterAmount adds both params")
        void tradesWithFilter() throws Exception {
            enqueue("[]");

            client.trades(DataTradesRequest.builder()
                .filterType(FilterType.CASH)
                .filterAmount(new BigDecimal("100"))
                .build());

            RecordedRequest req = server.takeRequest();
            String path = req.getPath();
            assertTrue(path.contains("filterType=CASH"),  "Path should include filterType=CASH: "  + path);
            assertTrue(path.contains("filterAmount=100"), "Path should include filterAmount=100: "  + path);
        }

        @Test
        @DisplayName("TC-DC-009: trades() with single market adds market query param")
        void tradesWithMarket() throws Exception {
            enqueue("[]");

            client.trades(DataTradesRequest.builder().market(TEST_COND_ID).build());

            RecordedRequest req = server.takeRequest();
            assertTrue(req.getPath().contains("market="),
                "Path should include market param: " + req.getPath());
        }

        @Test
        @DisplayName("TC-DC-010: trades() with multiple markets encodes comma-separated list")
        void tradesWithMultipleMarkets() throws Exception {
            enqueue("[]");
            String cond2 = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

            client.trades(DataTradesRequest.builder()
                .market(TEST_COND_ID)
                .market(cond2)
                .build());

            RecordedRequest req = server.takeRequest();
            String path = req.getPath();
            assertTrue(path.contains("market="),
                "Path should contain market param: " + path);
            assertTrue(path.contains("abcdef1234567890"),
                "Path should contain first condition ID");
            assertTrue(path.contains("bbbbbbbb"),
                "Path should contain second condition ID");
        }

        @Test
        @DisplayName("TC-DC-011: trades() empty string fields map to null (Rust NoneAsEmptyString parity)")
        void tradesEmptyStringFieldsMappedToNull() throws Exception {
            String jsonWithEmptyStrings = "["
                + "{"
                + "\"proxyWallet\":\"" + TEST_USER + "\","
                + "\"side\":\"SELL\","
                + "\"asset\":\"" + TEST_ASSET + "\","
                + "\"conditionId\":\"" + TEST_COND_ID + "\","
                + "\"size\":10.0,"
                + "\"price\":0.9,"
                + "\"timestamp\":1703980900,"
                + "\"title\":\"T\",\"slug\":\"s\",\"icon\":\"i\","
                + "\"eventSlug\":\"e\",\"outcome\":\"No\",\"outcomeIndex\":1,"
                + "\"name\":\"\","
                + "\"pseudonym\":\"\","
                + "\"bio\":\"\","
                + "\"profileImage\":\"\","
                + "\"profileImageOptimized\":\"\","
                + "\"transactionHash\":\"" + TEST_TX_HASH + "\""
                + "}"
                + "]";
            enqueue(jsonWithEmptyStrings);

            List<DataTrade> result = client.trades(null);

            DataTrade t = result.get(0);
            assertNull(t.getName(),                 "name should be null for empty string");
            assertNull(t.getPseudonym(),            "pseudonym should be null for empty string");
            assertNull(t.getBio(),                  "bio should be null for empty string");
            assertNull(t.getProfileImage(),         "profileImage should be null for empty string");
            assertNull(t.getProfileImageOptimized(),"profileImageOptimized should be null for empty string");
        }

        @Test
        @DisplayName("TC-DC-012: trades() with eventId adds eventId query param")
        void tradesWithEventId() throws Exception {
            enqueue("[]");

            client.trades(DataTradesRequest.builder().eventId("12345").build());

            RecordedRequest req = server.takeRequest();
            assertTrue(req.getPath().contains("eventId=12345"),
                "Path should include eventId=12345: " + req.getPath());
        }

        @Test
        @DisplayName("TC-DC-013: trades() side=SELL deserialized correctly")
        void tradesSideSell() throws Exception {
            String sellJson = TRADE_JSON.replace("\"side\":\"BUY\"", "\"side\":\"SELL\"");
            enqueue(sellJson);

            List<DataTrade> result = client.trades(null);

            assertEquals(DataSide.SELL, result.get(0).getSide());
        }
    }

    // -------------------------------------------------------------------------
    // DataTradesRequest model tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DataTradesRequest")
    class DataTradesRequestTests {

        @Test
        @DisplayName("TC-DC-020: DataTradesRequest all-null builder produces empty params")
        void allNullProducesEmptyParams() {
            DataTradesRequest req = DataTradesRequest.builder().build();
            Map<String, String> params = req.toQueryParams();
            assertTrue(params.isEmpty());
        }

        @Test
        @DisplayName("TC-DC-021: DataTradesRequest with all fields produces correct params")
        void allFields() {
            DataTradesRequest req = DataTradesRequest.builder()
                .user(TEST_USER)
                .market(TEST_COND_ID)
                .eventId("999")
                .limit(100)
                .offset(50)
                .takerOnly(true)
                .filterType(FilterType.TOKENS)
                .filterAmount(new BigDecimal("25"))
                .side(DataSide.SELL)
                .build();

            Map<String, String> params = req.toQueryParams();
            assertEquals(TEST_USER,    params.get("user"));
            assertEquals(TEST_COND_ID, params.get("market"));
            assertEquals("999",        params.get("eventId"));
            assertEquals("100",        params.get("limit"));
            assertEquals("50",         params.get("offset"));
            assertEquals("true",       params.get("takerOnly"));
            assertEquals("TOKENS",     params.get("filterType"));
            assertEquals("25",         params.get("filterAmount"));
            assertEquals("SELL",       params.get("side"));
        }

        @Test
        @DisplayName("TC-DC-022: DataTradesRequest filterType without filterAmount throws")
        void filterTypeWithoutAmountThrows() {
            DataTradesRequest req = DataTradesRequest.builder()
                .filterType(FilterType.CASH)
                .build();
            assertThrows(IllegalStateException.class, req::toQueryParams);
        }

        @Test
        @DisplayName("TC-DC-023: DataTradesRequest filterAmount without filterType throws")
        void filterAmountWithoutTypeThrows() {
            DataTradesRequest req = DataTradesRequest.builder()
                .filterAmount(new BigDecimal("50"))
                .build();
            assertThrows(IllegalStateException.class, req::toQueryParams);
        }

        @Test
        @DisplayName("TC-DC-024: DataTradesRequest equality")
        void equality() {
            DataTradesRequest a = DataTradesRequest.builder().user("u1").limit(10).build();
            DataTradesRequest b = DataTradesRequest.builder().user("u1").limit(10).build();
            assertEquals(a, b);
        }

        @Test
        @DisplayName("TC-DC-025: DataTradesRequest both filterType+filterAmount provided succeeds")
        void filterBothProvided() {
            DataTradesRequest req = DataTradesRequest.builder()
                .filterType(FilterType.TOKENS)
                .filterAmount(BigDecimal.ZERO)
                .build();
            assertDoesNotThrow(req::toQueryParams);
            Map<String, String> params = req.toQueryParams();
            assertEquals("TOKENS", params.get("filterType"));
            assertEquals("0",      params.get("filterAmount"));
        }

        @Test
        @DisplayName("TC-DC-026: DataTradesRequest multiple markets joined with comma")
        void multipleMarketsJoined() {
            String cond2 = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
            DataTradesRequest req = DataTradesRequest.builder()
                .market(TEST_COND_ID)
                .market(cond2)
                .build();
            Map<String, String> params = req.toQueryParams();
            String marketParam = params.get("market");
            assertNotNull(marketParam);
            assertTrue(marketParam.contains(TEST_COND_ID), "should contain first condition ID");
            assertTrue(marketParam.contains(cond2),         "should contain second condition ID");
            assertTrue(marketParam.contains(","),            "should be comma-separated");
        }
    }

    // -------------------------------------------------------------------------
    // DataClient builder / PolymarketClient accessor
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TC-DC-030: DataClient.Builder with custom host uses that host in URL")
    void customHostUsedInUrl() throws Exception {
        // The client in setUp() already points to MockWebServer — just verify it works
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]")
            .addHeader("Content-Type", "application/json"));

        client.trades(null);

        RecordedRequest req = server.takeRequest();
        assertNotNull(req, "MockWebServer should have received a request");
    }

    @Test
    @DisplayName("TC-DC-040: PolymarketClient.data() returns non-null DataClient")
    void polymarketClientDataAccessor() {
        PolymarketClient c = new PolymarketClient.Builder()
            .privateKey("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80")
            .build();
        assertNotNull(c.data());
        assertSame(c.data(), c.data(), "data() should return the same instance each call");
    }
}
