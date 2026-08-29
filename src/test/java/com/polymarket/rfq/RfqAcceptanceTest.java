package com.polymarket.rfq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.ReadRetryPolicy;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.builders.BuilderCredentials;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.internal.rfq.ComboMarketGateway;
import com.polymarket.internal.rfq.RfqGateway;
import com.polymarket.internal.trading.Eip712OrderSigner;
import com.polymarket.markets.PositionId;
import com.polymarket.trading.Side;
import com.polymarket.trading.SignedOrder;
import com.polymarket.trading.SigningContext;
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

/** Ground truth: src/test/resources/protocol/builder-gateway.json. */
@DisplayName("Rfq acceptance and Settlement Outcomes (issue #26)")
class RfqAcceptanceTest {

    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final ApiCredentials ACCOUNT_CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final BuilderCredentials BUILDER_CREDENTIALS = new BuilderCredentials(
            "builder-key", "YnVpbGRlci1zZWNyZXQtYnVpbGRlci1zZWNyZXQ=", "builder-passphrase");
    private static final Clock FIXED =
            Clock.fixed(Instant.ofEpochSecond(1773890758L), ZoneOffset.UTC);
    private static final String DEPOSIT_WALLET = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String BUILDER_CODE = "0x" + "b".repeat(64);

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

    private RfqGateway gateway() {
        return new RfqGateway(server.url("/").uri(), runtime(), FIXED);
    }

    private Rfq rfq() {
        HttpRuntime runtime = runtime();
        URI host = server.url("/").uri();
        return new Rfq(new RfqGateway(host, runtime, FIXED),
                new ComboMarketGateway(host, runtime), FIXED);
    }

    private static HttpRuntime runtime() {
        return new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                ReadRetryPolicy.none(), d -> {
                });
    }

    private static SigningIdentity depositWallet() {
        return SigningIdentity.depositWallet(DEPOSIT_WALLET, SIGNER.address());
    }

    /** Amounts pinned in builder-gateway.json's acceptRequestBody example. */
    private static RfqOutcome.Quoted quote(Side direction, Instant expiresAt) {
        return new RfqOutcome.Quoted("rfq-1", "quote-1", direction, new PositionId("333"),
                List.of(new PositionId("111"), new PositionId("222")),
                new QuoteAmounts(500000L, 966191L, 1932381L, 966191L, 1932381L),
                expiresAt, BUILDER_CODE);
    }

    private static SigningContext depositWalletContext() {
        return SigningContext.of(depositWallet(), SIGNER, 479249096354L, FIXED.instant());
    }

    @Test
    @DisplayName("TC-RA-001: POLY_ADDRESS is the Account Signer even when the Signed Order names the Trading Wallet")
    void acceptanceAuthenticatesWithTheAccountSignerNotTheOrderAddresses() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"EXECUTING"}"""));
        // A Deposit Wallet order whose signer field is the wallet, as the builders page shows.
        SignedOrder order = new SignedOrder(1L, DEPOSIT_WALLET, DEPOSIT_WALLET,
                new PositionId("333"), Side.BUY, 3, 966191L, 1932381L, 1773890758L,
                "0x" + "0".repeat(64), BUILDER_CODE, "0x" + "ab".repeat(65));

        gateway().accept("rfq-1", "quote-1", order, depositWallet(),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RecordedRequest request = server.takeRequest();
        assertEquals(SIGNER.address(), request.getHeader("POLY_ADDRESS"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"maker\":\"" + DEPOSIT_WALLET + "\""), body);
        assertTrue(body.contains("\"signer\":\"" + DEPOSIT_WALLET + "\""), body);
    }

    @Test
    @DisplayName("TC-RA-002: a Deposit Wallet acceptance carries the complete ERC-7739 authorization and builder code")
    void depositWalletAcceptanceCarriesErc7739AuthorizationAndBuilderCode() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"EXECUTING"}"""));

        rfq().accept(quote(Side.BUY, FIXED.instant().plusSeconds(60)), new Eip712OrderSigner(),
                depositWalletContext(), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"signatureType\":3"), body);
        assertTrue(body.contains("\"maker\":\"" + DEPOSIT_WALLET + "\""), body);
        assertTrue(body.contains("\"builder\":\"" + BUILDER_CODE + "\""), body);
        // ERC-1271 envelope: inner sig || appDomainSeparator || contentsHash || contentsDescr
        // || uint16(len). The 186-byte Order type string and its 0x00ba length must both be there.
        String orderTypeStringHex = "4f726465722875696e743235362073616c742c61646472657373206d616b657"
                + "22c61646472657373207369676e65722c75696e7432353620746f6b656e49642c75696e743235"
                + "36206d616b6572416d6f756e742c75696e743235362074616b6572416d6f756e742c75696e743"
                + "820736964652c75696e7438207369676e6174757265547970652c75696e74323536207469"
                + "6d657374616d702c62797465733332206d657461646174612c62797465733332206275696c646"
                + "5722900ba";
        assertTrue(body.contains(orderTypeStringHex), body);
    }

    @Test
    @DisplayName("TC-RA-003: a SELL Quote signs side 1 with the same maker and taker amounts as a BUY")
    void sellQuoteSignsSideOneWithTheSameAmounts() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"EXECUTING"}"""));

        rfq().accept(quote(Side.SELL, FIXED.instant().plusSeconds(60)), new Eip712OrderSigner(),
                depositWalletContext(), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"side\":1"), body);
        assertTrue(body.contains("\"makerAmount\":\"966191\""), body);
        assertTrue(body.contains("\"takerAmount\":\"1932381\""), body);
        assertTrue(body.contains("\"tokenId\":\"333\""), body);
    }

    @Test
    @DisplayName("TC-RA-004: acceptance takes no direction, amount, Combo position or deadline from its caller")
    void acceptanceTakesNoQuoteTermsFromItsCaller() throws Exception {
        for (var method : Rfq.class.getMethods()) {
            if (!method.getName().equals("accept")) continue;
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter == Side.class || parameter == PositionId.class
                                || parameter == Instant.class || parameter == long.class,
                        "acceptance must not let the caller restate a Quote term: " + parameter);
            }
        }
    }

    @Test
    @DisplayName("TC-RA-005: the acceptance response's taker_order_hash is kept")
    void acceptanceResponseTakerOrderHashIsKept() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION",
                 "taker_order_hash":"0xabc123"}"""));

        RfqOutcome outcome = rfq().accept(quote(Side.BUY, FIXED.instant().plusSeconds(60)),
                new Eip712OrderSigner(), depositWalletContext(),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Waiting waiting = assertInstanceOf(RfqOutcome.Waiting.class, outcome);
        assertEquals(Optional.of("0xabc123"), waiting.takerOrderHash());
        assertTrue(waiting.status().is(com.polymarket.rfq.RfqStatus.Known.AWAITING_MAKER_CONFIRMATION));
    }

    @Test
    @DisplayName("TC-RA-006: a safe retry that omits taker_order_hash still yields the same outcome, sent once")
    void safeRetryOmittingTakerOrderHashIsStillTheSameOutcome() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}"""));

        RfqOutcome outcome = rfq().accept(quote(Side.BUY, FIXED.instant().plusSeconds(60)),
                new Eip712OrderSigner(), depositWalletContext(),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Waiting waiting = assertInstanceOf(RfqOutcome.Waiting.class, outcome);
        assertEquals(Optional.empty(), waiting.takerOrderHash());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RA-007: a Quote is rejected one millisecond past expires_at, not on a fixed window")
    void quoteIsRejectedOneMillisecondPastExpiry() {
        RfqOutcome.Quoted justExpired = quote(Side.BUY, FIXED.instant().minusMillis(1));

        assertThrows(IllegalArgumentException.class, () -> rfq().accept(justExpired,
                new Eip712OrderSigner(), depositWalletContext(),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-RA-008: confirmed, failed, expired, canceled and unknown settlements stay distinguishable")
    void settlementOutcomesStayDistinguishable() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"CONFIRMED","tx_hash":"0xdead"}"""));
        assertInstanceOf(RfqOutcome.Confirmed.class,
                rfq().status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address()));

        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"FAILED","error":{"message":"maker declined"}}"""));
        RfqOutcome.Failed failed = assertInstanceOf(RfqOutcome.Failed.class,
                rfq().status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address()));
        assertEquals("maker declined", failed.reason());

        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"EXPIRED"}"""));
        assertInstanceOf(RfqOutcome.Expired.class,
                rfq().status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address()));

        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"CANCELED"}"""));
        assertInstanceOf(RfqOutcome.Canceled.class,
                rfq().status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address()));

        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"SOME_NEW_STATE_2027"}"""));
        assertInstanceOf(RfqOutcome.Unknown.class,
                rfq().status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address()));
    }

    @Test
    @DisplayName("TC-RA-009: transport loss on acceptance preserves the RFQ ID and never re-sends")
    void transportLossPreservesTheRfqIdWithoutReplay() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(
                okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START));

        RfqOutcome outcome = rfq().accept(quote(Side.BUY, FIXED.instant().plusSeconds(60)),
                new Eip712OrderSigner(), depositWalletContext(),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Unknown unknown = assertInstanceOf(RfqOutcome.Unknown.class, outcome);
        assertEquals("rfq-1", unknown.rfqId());
        assertEquals(1, server.getRequestCount());
    }
}
