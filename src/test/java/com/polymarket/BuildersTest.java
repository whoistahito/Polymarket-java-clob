package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.AuthenticationRequiredException;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentialRevocation;
import com.polymarket.builders.BuilderCredentialSummary;
import com.polymarket.builders.BuilderCredentials;
import com.polymarket.builders.BuilderCursor;
import com.polymarket.builders.BuilderTrade;
import com.polymarket.builders.BuilderTradePage;
import com.polymarket.builders.BuilderTradeQuery;
import com.polymarket.builders.Builders;
import com.polymarket.builders.Side;
import com.polymarket.internal.builders.BuildersGateway;
import com.polymarket.internal.http.HttpRuntime;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Builders: credential lifecycle and builder trades (issue #19)")
class BuildersTest {

    /** The documented 32-byte hex Builder code shape: ^0x[a-fA-F0-9]{64}$. */
    private static final String BUILDER_CODE = "0x" + "ab".repeat(32);

    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final ApiCredentials CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final Clock FIXED =
            Clock.fixed(Instant.ofEpochSecond(1773890758L), ZoneOffset.UTC);

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    private Builders builders(SigningAuthority authority) {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults().clobHost(host);
        HttpRuntime runtime = new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                ReadRetryPolicy.none(), d -> {
                });
        return new Builders(authority, new BuildersGateway(config, runtime, FIXED));
    }

    private static SigningAuthority authority() {
        return SigningAuthority.signing(SIGNER, SigningIdentity.eoa(SIGNER.address()))
                .withApiCredentials(CREDENTIALS);
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse().setBody(body));
    }

    @Test
    @DisplayName("TC-BD-001: creating a builder credential returns a typed, fully redacted value")
    void createCredentialsReturnsTypedAndRedactedCredentials() throws Exception {
        enqueue("""
                {"key":"builder-key-1","secret":"c2VjcmV0LXNlY3JldA==","passphrase":"builder-pass"}""");

        BuilderCredentials credentials = builders(authority()).createCredentials();

        assertEquals("builder-key-1", credentials.key());
        assertEquals("c2VjcmV0LXNlY3JldA==", credentials.secret());
        assertEquals("builder-pass", credentials.passphrase());
        assertEquals("BuilderCredentials[key=***, secret=***, passphrase=***]", credentials.toString());
        assertFalse(credentials.toString().contains("builder-key-1"));
        assertFalse(credentials.toString().contains("c2VjcmV0LXNlY3JldA=="));
        assertFalse(credentials.toString().contains("builder-pass"));

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/auth/builder-api-key", request.getPath());
        assertEquals(CREDENTIALS.key(), request.getHeader("POLY_API_KEY"));
        assertEquals(SIGNER.address(), request.getHeader("POLY_ADDRESS"));
    }

    @Test
    @DisplayName("TC-BD-002: listing builder credentials returns typed summaries, never a raw map")
    void listCredentialsReturnsTypedSummaries() throws Exception {
        enqueue("""
                [{"key":"builder-key-1","createdAt":"2024-01-01T00:00:00Z","revokedAt":null},
                 {"key":"builder-key-2","createdAt":"2024-02-01T00:00:00Z","revokedAt":"2024-03-01T00:00:00Z"}]""");

        List<BuilderCredentialSummary> summaries = builders(authority()).listCredentials();

        assertEquals(2, summaries.size());
        assertEquals("builder-key-1", summaries.get(0).key());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), summaries.get(0).createdAt().orElseThrow());
        assertEquals(Optional.empty(), summaries.get(0).revokedAt());
        assertFalse(summaries.get(0).revoked());
        assertTrue(summaries.get(1).revoked());
        assertEquals(Instant.parse("2024-03-01T00:00:00Z"), summaries.get(1).revokedAt().orElseThrow());

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/auth/builder-api-key", request.getPath());
    }

    @Test
    @DisplayName("TC-BD-003: revoking succeeds on a 2xx response")
    void revokeCredentialsSucceedsOnSuccess() throws Exception {
        enqueue("{}");

        BuilderCredentialRevocation outcome = builders(authority()).revokeCredentials();

        assertTrue(outcome.revoked());
        assertEquals(Optional.empty(), outcome.detail());

        RecordedRequest request = server.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/auth/builder-api-key", request.getPath());
    }

    @Test
    @DisplayName("TC-BD-004: revoking reports failure as data, not an exception, on a non-2xx response")
    void revokeCredentialsFailsAsData() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));

        BuilderCredentialRevocation outcome = builders(authority()).revokeCredentials();

        assertFalse(outcome.revoked());
        assertTrue(outcome.detail().orElseThrow().contains("404"));
    }

    @Test
    @DisplayName("TC-BD-005: every builder operation fails before sending when credentials are absent")
    void everyOperationRequiresCredentialsBeforeSending() {
        Builders builders = builders(SigningAuthority.none());

        assertThrows(AuthenticationRequiredException.class, builders::createCredentials);
        assertThrows(AuthenticationRequiredException.class, builders::listCredentials);
        assertThrows(AuthenticationRequiredException.class, builders::revokeCredentials);
        assertThrows(AuthenticationRequiredException.class,
                () -> builders.trades(BuilderTradeQuery.forBuilder(BUILDER_CODE)));

        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-BD-006: a builder trades page carries exact decimal values and its next cursor")
    void tradesReturnTypedPageWithExactValues() throws Exception {
        enqueue("""
                {"limit":100,"count":1,"next_cursor":"MQ==","data":[{
                  "id":"trade-1","tradeType":"MATCH","takerOrderHash":"0xabc",
                  "builder":"0xbuilder","market":"0xmarket","assetId":"123","side":"BUY",
                  "size":"10.5","sizeUsdc":"5.25","price":"0.5","status":"CONFIRMED",
                  "outcome":"YES","outcomeIndex":0,"owner":"0xowner","maker":"0xmaker",
                  "transactionHash":"0xtxn","matchTime":"1711411200","bucketIndex":7,
                  "fee":"0.01","feeUsdc":"0.005","createdAt":"2024-03-26T00:00:01Z",
                  "updatedAt":"2024-03-26T00:00:02Z"}]}""");

        BuilderTradePage page = builders(authority()).trades(BuilderTradeQuery.forBuilder(BUILDER_CODE));

        assertEquals(100, page.limit());
        assertEquals(1, page.count());
        assertEquals("MQ==", page.nextCursor().orElseThrow().value());

        BuilderTrade trade = page.items().get(0);
        assertEquals("trade-1", trade.id());
        assertEquals("0xbuilder", trade.builder());
        assertEquals(Side.BUY, trade.side());
        assertEquals(new BigDecimal("10.5"), trade.size());
        assertEquals(new BigDecimal("5.25"), trade.sizeUsdc());
        assertEquals(new BigDecimal("0.5"), trade.price());
        assertEquals("CONFIRMED", trade.status());
        assertEquals(0, trade.outcomeIndex());
        assertEquals(7, trade.bucketIndex());
        assertEquals(new BigDecimal("0.01"), trade.fee());
        assertEquals(Instant.parse("2024-03-26T00:00:00Z"), trade.matchTime());
        assertEquals(Instant.parse("2024-03-26T00:00:01Z"), trade.createdAt().orElseThrow());

        RecordedRequest request = server.takeRequest();
        assertEquals("/builder/trades?builder_code=" + BUILDER_CODE + "&next_cursor=MA%3D%3D",
                request.getPath());
        assertEquals(CREDENTIALS.key(), request.getHeader("POLY_API_KEY"));
    }

    @Test
    @DisplayName("TC-BD-007: a filtered trades read signs its filters and cursor in the path")
    void tradesFilterIsSignedInThePath() throws Exception {
        enqueue("""
                {"limit":100,"count":0,"next_cursor":"LTE=","data":[]}""");

        BuilderTradeQuery query =
                BuilderTradeQuery.forBuilder(BUILDER_CODE).market("0xmarket").assetId("123");
        BuilderTradePage page = builders(authority()).trades(query);

        assertEquals(Optional.empty(), page.nextCursor());
        assertEquals("/builder/trades?builder_code=" + BUILDER_CODE
                        + "&market=0xmarket&asset_id=123&next_cursor=MA%3D%3D",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-BD-008: an explicit next cursor is walked forward and its value travels on the wire")
    void tradesWalkForwardWithAnExplicitCursor() throws Exception {
        enqueue("""
                {"limit":100,"count":0,"next_cursor":"LTE=","data":[]}""");

        builders(authority()).trades(BuilderTradeQuery.forBuilder(BUILDER_CODE),
                BuilderCursor.of("MQ=="));

        assertEquals("/builder/trades?builder_code=" + BUILDER_CODE + "&next_cursor=MQ%3D%3D",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-BD-010: a builder trades read sends the required Builder code")
    void tradesSendTheRequiredBuilderCode() throws Exception {
        enqueue("""
                {"limit":300,"count":0,"next_cursor":"LTE=","data":[]}""");

        builders(authority()).trades(BuilderTradeQuery.forBuilder(BUILDER_CODE));

        assertEquals("/builder/trades?builder_code=" + BUILDER_CODE + "&next_cursor=MA%3D%3D",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-BD-011: a Builder code outside the documented 32-byte hex form is rejected before sending")
    void anUndocumentedBuilderCodeIsRejectedBeforeSending() {
        assertThrows(IllegalArgumentException.class, () -> BuilderTradeQuery.forBuilder("nope"));
        assertThrows(IllegalArgumentException.class, () -> BuilderTradeQuery.forBuilder("0xab"));
        assertThrows(IllegalArgumentException.class, () -> BuilderTradeQuery.forBuilder(" "));

        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-BD-012: a bounded trades window sends before and after as unix seconds")
    void tradesWindowSendsBeforeAndAfterAsUnixSeconds() throws Exception {
        enqueue("""
                {"limit":300,"count":0,"next_cursor":"LTE=","data":[]}""");

        // The documented before/after examples: 1700000000 and 1600000000 unix seconds.
        BuilderTradeQuery query = BuilderTradeQuery.forBuilder(BUILDER_CODE)
                .after(Instant.parse("2020-09-13T12:26:40Z"))
                .before(Instant.parse("2023-11-14T22:13:20Z"));

        builders(authority()).trades(query);

        assertEquals("/builder/trades?builder_code=" + BUILDER_CODE
                        + "&before=1700000000&after=1600000000&next_cursor=MA%3D%3D",
                server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TC-BD-013: continuation is explicit — a page hands back the cursor the caller must send")
    void continuationIsExplicitAndEndsOnTheDocumentedSentinel() throws Exception {
        enqueue("""
                {"limit":300,"count":0,"next_cursor":"MzAw","data":[]}""");
        enqueue("""
                {"limit":300,"count":0,"next_cursor":"LTE=","data":[]}""");

        BuilderTradeQuery query = BuilderTradeQuery.forBuilder(BUILDER_CODE);
        Builders builders = builders(authority());

        BuilderTradePage first = builders.trades(query);
        assertEquals("MzAw", first.nextCursor().orElseThrow().value());

        BuilderTradePage second = builders.trades(query, first.nextCursor().orElseThrow());
        assertEquals(Optional.empty(), second.nextCursor());

        server.takeRequest();
        assertEquals("/builder/trades?builder_code=" + BUILDER_CODE + "&next_cursor=MzAw",
                server.takeRequest().getPath());
    }

    /** The official GET /builder/trades example row, verbatim (builder-trades.json). */
    private static final String DOCUMENTED_ROW = """
            {"id":"trade-123","tradeType":"TAKER",
             "takerOrderHash":"0xabcdef1234567890abcdef1234567890abcdef12",
             "builder":"0x0000000000000000000000000000000000000000000000000000000000000001",
             "market":"0x0000000000000000000000000000000000000000000000000000000000000001",
             "assetId":"15871154585880608648532107628464183779895785213830018178010423617714102767076",
             "side":"BUY","size":"100000000","sizeUsdc":"50000000","price":"0.5",
             "status":"TRADE_STATUS_CONFIRMED","outcome":"YES","outcomeIndex":0,
             "owner":"f4f247b7-4ac7-ff29-a152-04fda0a8755a",
             "maker":"0x1234567890123456789012345678901234567890",
             "transactionHash":"0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
             "matchTime":"1700000000","bucketIndex":0,"fee":"300000","feeUsdc":"150000",
             "createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}""";

    private BuilderTrade documentedTrade() throws Exception {
        enqueue("{\"limit\":300,\"count\":1,\"next_cursor\":\"LTE=\",\"data\":["
                + DOCUMENTED_ROW + "]}");
        return builders(authority()).trades(BuilderTradeQuery.forBuilder(BUILDER_CODE))
                .items().get(0);
    }

    @Test
    @DisplayName("TC-BD-014: unix match time and ISO creation/update times map by their distinct units")
    void matchTimeIsUnixWhileCreationAndUpdateTimesAreIso() throws Exception {
        BuilderTrade trade = documentedTrade();

        // 1700000000 unix seconds is 2023-11-14T22:13:20Z; createdAt/updatedAt are ISO-8601.
        assertEquals(Instant.parse("2023-11-14T22:13:20Z"), trade.matchTime());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), trade.createdAt().orElseThrow());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), trade.updatedAt().orElseThrow());
    }

    @Test
    @DisplayName("TC-BD-015: every required trade field, bucket identity included, is preserved exactly")
    void everyRequiredTradeFieldIncludingBucketIndexSurvivesExactly() throws Exception {
        BuilderTrade trade = documentedTrade();

        assertEquals("trade-123", trade.id());
        assertEquals("TAKER", trade.tradeType());
        assertEquals("0xabcdef1234567890abcdef1234567890abcdef12", trade.takerOrderHash());
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000001",
                trade.builder());
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000001",
                trade.market());
        assertEquals("15871154585880608648532107628464183779895785213830018178010423617714102767076",
                trade.assetId());
        assertEquals(Side.BUY, trade.side());
        assertEquals(new BigDecimal("100000000"), trade.size());
        assertEquals(new BigDecimal("50000000"), trade.sizeUsdc());
        assertEquals(new BigDecimal("0.5"), trade.price());
        assertEquals("TRADE_STATUS_CONFIRMED", trade.status());
        assertEquals("YES", trade.outcome());
        assertEquals(0, trade.outcomeIndex());
        assertEquals("f4f247b7-4ac7-ff29-a152-04fda0a8755a", trade.owner());
        assertEquals("0x1234567890123456789012345678901234567890", trade.maker());
        assertEquals("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
                trade.transactionHash());
        assertEquals(0, trade.bucketIndex());
        assertEquals(new BigDecimal("300000"), trade.fee());
        assertEquals(new BigDecimal("150000"), trade.feeUsdc());
        assertEquals(Optional.empty(), trade.errorMessage());
    }

    @Test
    @DisplayName("TC-BD-016: a trade row missing a required field fails the read explicitly")
    void aTradeRowMissingARequiredFieldFailsTheRead() throws Exception {
        enqueue("{\"limit\":300,\"count\":1,\"next_cursor\":\"LTE=\",\"data\":["
                + DOCUMENTED_ROW.replace("\"bucketIndex\":0,", "") + "]}");

        Builders builders = builders(authority());
        assertThrows(java.io.IOException.class,
                () -> builders.trades(BuilderTradeQuery.forBuilder(BUILDER_CODE)));
    }

    @Test
    @DisplayName("TC-BD-017: a builder read authenticates with the Account Signer and never sends the API secret")
    void aBuilderReadAuthenticatesWithTheAccountSignerAndKeepsTheSecretOffTheWire()
            throws Exception {
        enqueue("""
                {"limit":300,"count":0,"next_cursor":"LTE=","data":[]}""");

        String accountSigner = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
        Builders builders = builders(
                SigningAuthority.apiCredentials(CREDENTIALS, accountSigner));

        builders.trades(BuilderTradeQuery.forBuilder(BUILDER_CODE));

        RecordedRequest request = server.takeRequest();
        assertTrue(accountSigner.equalsIgnoreCase(request.getHeader("POLY_ADDRESS")),
                request.getHeader("POLY_ADDRESS"));
        assertEquals(CREDENTIALS.key(), request.getHeader("POLY_API_KEY"));
        assertFalse(request.getHeaders().toString().contains(CREDENTIALS.secret()),
                "the API secret signs the request, it is never sent");
        assertFalse(request.getPath().contains(CREDENTIALS.secret()));
    }

    @Test
    @DisplayName("TC-BD-009: a server failure reading builder credentials is thrown, not swallowed")
    void aServerFailureListingCredentialsIsThrown() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        Builders builders = builders(authority());
        assertThrows(java.io.IOException.class, builders::listCredentials);
    }
}
