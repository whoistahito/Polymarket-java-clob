package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.model.OpenOrder;
import com.polymarket.model.OpenOrderParams;
import com.polymarket.model.PaginationPayload;
import com.polymarket.model.data.DataPosition;
import com.polymarket.model.data.DataPositionsRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ticket 025 — complete reconciliation reads.
 *
 * <p>Recovering a missed user-channel fill means reading the truth over REST. That only works if the
 * reads are complete: open orders must follow the pagination cursor to the end (a second page of
 * resting orders was previously invisible), and positions must be available as a typed absolute
 * snapshot with exact sizes rather than hand-parsed HTTP.
 */
@DisplayName("TC-RCR — reconciliation reads (Ticket 025)")
class ReconciliationReadsTest {

    private static final String PK =
        "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private MockWebServer server;
    private PolymarketClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new PolymarketClient.Builder()
            .privateKey(PK)
            .clobHost(server.url("/").toString())
            .dataHost(server.url("/").toString())
            .apiCreds(new ApiKeyCreds("test-key", "c2VjcmV0", "pass123"))
            .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
    }

    private static String orderJson(String id, String assetId, String matched) {
        return "{\"id\":\"" + id + "\",\"status\":\"LIVE\",\"owner\":\"api-key\","
            + "\"maker_address\":\"0xwallet\",\"market\":\"0xmkt\",\"asset_id\":\"" + assetId + "\","
            + "\"side\":\"SELL\",\"original_size\":\"7\",\"size_matched\":\"" + matched + "\","
            + "\"price\":\"0.52\",\"outcome\":\"Up\",\"created_at\":1700000000,"
            + "\"expiration\":\"0\",\"order_type\":\"GTC\",\"associate_trades\":[\"t1\"]}";
    }

    // ------------------------------------------------------------------ //
    // Open orders: auto-pagination                                        //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-RCR-001 two open-order pages return one complete list without duplication")
    void openOrdersFollowCursorToTheEnd() throws Exception {
        enqueue("{\"limit\":2,\"count\":2,\"next_cursor\":\"MTAw\",\"data\":["
            + orderJson("0x1", "tokA", "0") + "," + orderJson("0x2", "tokA", "1") + "]}");
        enqueue("{\"limit\":2,\"count\":1,\"next_cursor\":\"LTE=\",\"data\":["
            + orderJson("0x3", "tokB", "2") + "]}");

        List<OpenOrder> orders = client.getOpenOrders();

        assertEquals(List.of("0x1", "0x2", "0x3"),
            orders.stream().map(OpenOrder::getId).collect(Collectors.toList()));
        assertEquals(3, orders.size(), "no duplication across pages");
        assertEquals(2, server.getRequestCount(), "both pages must be fetched");
    }

    @Test
    @DisplayName("TC-RCR-002 pagination stops on the terminal cursor and on an empty cursor")
    void paginationTerminates() throws Exception {
        enqueue("{\"limit\":1,\"count\":1,\"next_cursor\":\"\",\"data\":["
            + orderJson("0x1", "tokA", "0") + "]}");

        assertEquals(1, client.getOpenOrders().size());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RCR-003 a bare-array response still works — no envelope, one page")
    void bareArrayResponseSupported() throws Exception {
        enqueue("[" + orderJson("0x1", "tokA", "0") + "]");

        List<OpenOrder> orders = client.getOpenOrders();
        assertEquals(1, orders.size());
        assertEquals("0x1", orders.get(0).getId());
    }

    @Test
    @DisplayName("TC-RCR-004 the explicit page API returns one page plus its cursor")
    void explicitPageApiRetained() throws Exception {
        enqueue("{\"limit\":2,\"count\":2,\"next_cursor\":\"MTAw\",\"data\":["
            + orderJson("0x1", "tokA", "0") + "," + orderJson("0x2", "tokA", "1") + "]}");

        PaginationPayload<OpenOrder> page = client.getOpenOrdersPaginated(Map.of(), null);

        assertEquals(2, page.getData().size());
        assertEquals("MTAw", page.getNextCursor());
        assertEquals(1, server.getRequestCount(), "the page API must not follow the cursor");
    }

    @Test
    @DisplayName("TC-RCR-005 every reconciliation field on an open order is preserved")
    void openOrderFieldsPreserved() throws Exception {
        enqueue("{\"next_cursor\":\"LTE=\",\"data\":[" + orderJson("0x1", "tokA", "3") + "]}");

        OpenOrder order = client.getOpenOrders().get(0);

        assertEquals("0x1", order.getId());
        assertEquals("tokA", order.getAssetId());
        assertEquals("0xmkt", order.getMarket());
        assertEquals("0xwallet", order.getMakerAddress());
        assertEquals("SELL", order.getSide());
        assertEquals("3", order.getSizeMatched());
        assertEquals("7", order.getOriginalSize());
        assertEquals("GTC", order.getOrderType());
        assertEquals(List.of("t1"), order.getAssociateTrades());
        assertEquals(1700000000L, order.getCreatedAt());
    }

    @Test
    @DisplayName("TC-RCR-006 filters and the cursor serialize exactly on every page request")
    void filtersAndCursorsSerializeExactly() throws Exception {
        enqueue("{\"next_cursor\":\"MTAw\",\"data\":[" + orderJson("0x1", "tokA", "0") + "]}");
        enqueue("{\"next_cursor\":\"LTE=\",\"data\":[]}");

        client.getOpenOrders(OpenOrderParams.builder().market("0xmkt").assetId("tokA").build());

        RecordedRequest first = server.takeRequest();
        assertTrue(first.getPath().contains("market=0xmkt"), first.getPath());
        assertTrue(first.getPath().contains("asset_id=tokA"), first.getPath());

        RecordedRequest second = server.takeRequest();
        assertTrue(second.getPath().contains("next_cursor=MTAw"), second.getPath());
        assertTrue(second.getPath().contains("market=0xmkt"),
            "filters must survive onto later pages: " + second.getPath());
    }

    // ------------------------------------------------------------------ //
    // Data API positions                                                  //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("TC-RCR-007 a position size of 5.0000000000000001 remains exact")
    void positionSizeRemainsExact() throws Exception {
        enqueue("[{\"proxyWallet\":\"0xwallet\",\"asset\":\"tokA\","
            + "\"conditionId\":\"0xmkt\",\"size\":5.0000000000000001,\"avgPrice\":0.45,"
            + "\"outcome\":\"Up\",\"outcomeIndex\":0,\"redeemable\":false,\"mergeable\":false}]");

        List<DataPosition> positions =
            client.data().positions(DataPositionsRequest.builder().user("0xwallet").build());

        assertEquals(1, positions.size());
        assertEquals(new BigDecimal("5.0000000000000001"), positions.get(0).getSize());
        assertTrue(positions.get(0).getSize().compareTo(new BigDecimal("5")) > 0);
    }

    @Test
    @DisplayName("TC-RCR-008 every field needed to route a position is preserved")
    void positionRoutingFieldsPreserved() throws Exception {
        enqueue("[{\"proxyWallet\":\"0xwallet\",\"asset\":\"tokA\",\"conditionId\":\"0xmkt\","
            + "\"size\":7,\"avgPrice\":0.45,\"outcome\":\"Up\",\"outcomeIndex\":0,"
            + "\"oppositeOutcome\":\"Down\",\"oppositeAsset\":\"tokB\",\"negativeRisk\":false,"
            + "\"redeemable\":false,\"mergeable\":false,\"title\":\"Bitcoin Up or Down\","
            + "\"endDate\":\"2026-07-20T12:00:00Z\"}]");

        DataPosition position =
            client.data().positions(DataPositionsRequest.builder().user("0xwallet").build()).get(0);

        assertEquals("0xwallet", position.getProxyWallet());
        assertEquals("tokA", position.getAsset());
        assertEquals("tokB", position.getOppositeAsset());
        assertEquals("0xmkt", position.getConditionId());
        assertEquals("Up", position.getOutcome());
        assertEquals(0, position.getOutcomeIndex());
        assertEquals(0, new BigDecimal("7").compareTo(position.getSize()));
        assertEquals(Boolean.FALSE, position.getRedeemable());
    }

    @Test
    @DisplayName("TC-RCR-009 an empty positions response yields an empty typed list, not null")
    void emptyPositionsYieldsEmptyList() throws Exception {
        enqueue("[]");

        List<DataPosition> positions =
            client.data().positions(DataPositionsRequest.builder().user("0xwallet").build());

        assertNotNull(positions);
        assertTrue(positions.isEmpty());
    }

    @Test
    @DisplayName("TC-RCR-010 positions filters serialize exactly onto the query string")
    void positionFiltersSerializeExactly() throws Exception {
        enqueue("[]");

        client.data().positions(DataPositionsRequest.builder()
            .user("0xwallet")
            .market("0xmkt1")
            .market("0xmkt2")
            .sizeThreshold(new BigDecimal("0.1"))
            .limit(500)
            .offset(20)
            .sortBy("TOKENS")
            .sortDirection("ASC")
            .build());

        String path = server.takeRequest().getPath();
        assertTrue(path.contains("user=0xwallet"), path);
        assertTrue(path.contains("market=0xmkt1%2C0xmkt2"), path);
        assertTrue(path.contains("sizeThreshold=0.1"), path);
        assertTrue(path.contains("limit=500"), path);
        assertTrue(path.contains("offset=20"), path);
        assertTrue(path.contains("sortBy=TOKENS"), path);
        assertTrue(path.contains("sortDirection=ASC"), path);
    }

    @Test
    @DisplayName("TC-RCR-011 a positions request requires a user address")
    void positionsRequireUser() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> DataPositionsRequest.builder().build().toQueryParams());
    }

    @Test
    @DisplayName("TC-RCR-012 market and eventId filters are mutually exclusive, as documented")
    void marketAndEventIdMutuallyExclusive() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> DataPositionsRequest.builder()
                .user("0xwallet").market("0xmkt").eventId("123").build().toQueryParams());
    }

    @Test
    @DisplayName("TC-RCR-013 a later snapshot may report less — positions are absolute, not monotonic")
    void positionsAreAbsoluteSnapshots() throws Exception {
        enqueue("[{\"proxyWallet\":\"0xwallet\",\"asset\":\"tokA\",\"conditionId\":\"0xmkt\","
            + "\"size\":7,\"outcome\":\"Up\",\"outcomeIndex\":0}]");
        enqueue("[{\"proxyWallet\":\"0xwallet\",\"asset\":\"tokA\",\"conditionId\":\"0xmkt\","
            + "\"size\":3,\"outcome\":\"Up\",\"outcomeIndex\":0}]");

        DataPositionsRequest req = DataPositionsRequest.builder().user("0xwallet").build();
        assertEquals(0, new BigDecimal("7").compareTo(client.data().positions(req).get(0).getSize()));
        // The SDK must report the decrease verbatim; clamping it upward here would hide a real sell.
        assertEquals(0, new BigDecimal("3").compareTo(client.data().positions(req).get(0).getSize()));
    }

    @Test
    @DisplayName("TC-RCR-014 a missing optional numeric field stays null rather than becoming zero")
    void missingNumericFieldStaysNull() throws Exception {
        enqueue("[{\"proxyWallet\":\"0xwallet\",\"asset\":\"tokA\",\"conditionId\":\"0xmkt\","
            + "\"size\":7,\"outcome\":\"Up\",\"outcomeIndex\":0}]");

        DataPosition position =
            client.data().positions(DataPositionsRequest.builder().user("0xwallet").build()).get(0);

        assertNull(position.getAvgPrice());
        assertNull(position.getCashPnl());
    }
}
