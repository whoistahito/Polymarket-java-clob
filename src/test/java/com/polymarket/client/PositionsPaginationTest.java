package com.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.model.data.DataPosition;
import com.polymarket.model.data.DataPositionsRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ticket 036 — {@code DataClient.positions(...)} follows offset pagination to the end.
 *
 * <p>{@code GET /positions} answers with a bare array and no total count, so "fewer results than
 * requested" is the only end-of-data signal available. A wallet holding more than one page's worth
 * of positions must not read as partially flat just because the SDK stopped at page one.
 */
@DisplayName("TC-PGN — positions pagination (Ticket 036)")
class PositionsPaginationTest {

    private static final String USER = "0xwallet";

    private MockWebServer server;
    private DataClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String base = server.url("").toString().replaceAll("/$", "");
        client = new DataClient.Builder().host(base).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static String position(String asset, String size) {
        return "{\"proxyWallet\":\"" + USER + "\",\"asset\":\"" + asset + "\","
            + "\"conditionId\":\"0xmkt\",\"size\":" + size + ",\"outcome\":\"Up\",\"outcomeIndex\":0}";
    }

    private static String page(String... assets) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < assets.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(position(assets[i], "1"));
        }
        return sb.append(']').toString();
    }

    private void enqueue(String body) {
        server.enqueue(
            new MockResponse().setResponseCode(200).setBody(body)
                .addHeader("Content-Type", "application/json"));
    }

    @Test
    @DisplayName("TC-PGN-001 two full pages plus a short final page merge with no duplication")
    void twoFullPagesPlusShortFinalPage() throws IOException {
        enqueue(page("tokA", "tokB"));
        enqueue(page("tokC", "tokD"));
        enqueue(page("tokE"));

        List<DataPosition> positions = client.positions(
            DataPositionsRequest.builder().user(USER).limit(2).build());

        assertEquals(List.of("tokA", "tokB", "tokC", "tokD", "tokE"),
            positions.stream().map(DataPosition::getAsset).collect(Collectors.toList()));
        assertEquals(3, server.getRequestCount(), "all three pages must be fetched");
    }

    @Test
    @DisplayName("TC-PGN-002 a short first page issues no second request")
    void shortFirstPageIssuesNoSecondRequest() throws IOException {
        enqueue(page("tokA"));

        List<DataPosition> positions = client.positions(
            DataPositionsRequest.builder().user(USER).limit(2).build());

        assertEquals(1, positions.size());
        assertEquals(1, server.getRequestCount(), "a page shorter than the limit is the last page");
    }

    @Test
    @DisplayName("TC-PGN-003 limit above 500 is clamped to the documented maximum on every page")
    void limitAboveMaxIsClamped() throws Exception {
        enqueue("[]");

        client.positions(DataPositionsRequest.builder().user(USER).limit(600).build());

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("limit=500"),
            "limit must be clamped to the documented maximum of 500: " + req.getPath());
    }

    @Test
    @DisplayName("TC-PGN-004 an empty result returns an empty typed list, not null")
    void emptyResultReturnsEmptyList() throws IOException {
        enqueue("[]");

        List<DataPosition> positions =
            client.positions(DataPositionsRequest.builder().user(USER).build());

        assertNotNull(positions);
        assertTrue(positions.isEmpty());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-PGN-005 sizeThreshold=0 survives onto every page request")
    void sizeThresholdZeroPreservedAcrossPages() throws Exception {
        enqueue(page("tokA", "tokB"));
        enqueue(page("tokC"));

        client.positions(DataPositionsRequest.builder()
            .user(USER)
            .limit(2)
            .sizeThreshold(BigDecimal.ZERO)
            .build());

        RecordedRequest first = server.takeRequest();
        assertTrue(first.getPath().contains("sizeThreshold=0"), first.getPath());
        RecordedRequest second = server.takeRequest();
        assertTrue(second.getPath().contains("sizeThreshold=0"),
            "the threshold must survive onto later pages: " + second.getPath());
        assertTrue(second.getPath().contains("offset=2"), second.getPath());
    }

    @Test
    @DisplayName("TC-PGN-006 offsets advance by the page size and never repeat")
    void offsetsAdvanceWithoutRepetition() throws Exception {
        enqueue(page("tokA", "tokB"));
        enqueue(page("tokC", "tokD"));
        enqueue(page("tokE"));

        client.positions(DataPositionsRequest.builder().user(USER).limit(2).build());

        assertTrue(server.takeRequest().getPath().contains("offset=0"));
        assertTrue(server.takeRequest().getPath().contains("offset=2"));
        assertTrue(server.takeRequest().getPath().contains("offset=4"));
    }

    @Test
    @DisplayName("TC-PGN-007 the explicit single-page API issues exactly one request")
    void explicitSinglePageApiDoesNotWalk() throws IOException {
        enqueue(page("tokA", "tokB"));

        List<DataPosition> page = client.positionsPaginated(
            DataPositionsRequest.builder().user(USER).limit(2).build());

        assertEquals(2, page.size());
        assertEquals(1, server.getRequestCount(), "the single-page API must not follow pagination");
    }

    @Test
    @DisplayName("TC-PGN-008 the explicit single-page API also clamps limit to the documented maximum")
    void explicitSinglePageApiAlsoClampsLimit() throws Exception {
        enqueue("[]");

        client.positionsPaginated(DataPositionsRequest.builder().user(USER).limit(999).build());

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("limit=500"), req.getPath());
    }
}
