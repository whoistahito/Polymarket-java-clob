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
import org.junit.jupiter.api.Test;

/** Ground truth: src/test/resources/protocol/builder-gateway.json. */
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
                new ComboMarketGateway(host, runtime), new Eip712OrderSigner(), FIXED);
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
                expiresAt, BUILDER_CODE, depositWallet());
    }

    private static SigningContext depositWalletContext() {
        return SigningContext.of(depositWallet(), SIGNER, 479249096354L, FIXED.instant());
    }

    @Test
    void shouldAuthenticateWithAccountSignerWhenOrderNamesTradingWallet() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"EXECUTING"}"""));
        SignedOrder order = new SignedOrder(1L, DEPOSIT_WALLET, DEPOSIT_WALLET, SIGNER.address(),
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
    void shouldCarryErc7739AuthorizationAndBuilderCodeWhenAcceptingDepositWalletQuote() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"EXECUTING"}"""));

        rfq().accept(quote(Side.BUY, FIXED.instant().plusSeconds(60)),
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
    void shouldSignSideOneWithSameAmountsWhenQuoteIsSell() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"EXECUTING"}"""));

        rfq().accept(quote(Side.SELL, FIXED.instant().plusSeconds(60)),
                depositWalletContext(), ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"side\":1"), body);
        assertTrue(body.contains("\"makerAmount\":\"966191\""), body);
        assertTrue(body.contains("\"takerAmount\":\"1932381\""), body);
        assertTrue(body.contains("\"tokenId\":\"333\""), body);
    }

    @Test
    void shouldExposeNoCallerQuoteTermsWhenAcceptanceSignatureIsInspected() throws Exception {
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
    void shouldKeepTakerOrderHashWhenAcceptanceResponseProvidesIt() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION",
                 "taker_order_hash":"0xabc123"}"""));

        RfqOutcome outcome = rfq().accept(quote(Side.BUY, FIXED.instant().plusSeconds(60)),
                depositWalletContext(),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Waiting waiting = assertInstanceOf(RfqOutcome.Waiting.class, outcome);
        assertEquals(Optional.of("0xabc123"), waiting.takerOrderHash());
        assertTrue(waiting.status().is(com.polymarket.rfq.RfqStatus.Known.AWAITING_MAKER_CONFIRMATION));
    }

    @Test
    void shouldKeepOutcomeAndSendOnceWhenTakerOrderHashIsOmitted() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"AWAITING_MAKER_CONFIRMATION"}"""));

        RfqOutcome outcome = rfq().accept(quote(Side.BUY, FIXED.instant().plusSeconds(60)),
                depositWalletContext(),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Waiting waiting = assertInstanceOf(RfqOutcome.Waiting.class, outcome);
        assertEquals(Optional.empty(), waiting.takerOrderHash());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenQuoteIsOneMillisecondPastExpiry() {
        RfqOutcome.Quoted justExpired = quote(Side.BUY, FIXED.instant().minusMillis(1));

        assertThrows(IllegalArgumentException.class, () -> rfq().accept(justExpired,
                depositWalletContext(),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldKeepSettlementOutcomesDistinctWhenStatusesDiffer() throws Exception {
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
    void shouldPreserveRfqIdWithoutReplayWhenAcceptanceLosesTransport() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(
                okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START));

        RfqOutcome outcome = rfq().accept(quote(Side.BUY, FIXED.instant().plusSeconds(60)),
                depositWalletContext(),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);

        RfqOutcome.Unknown unknown = assertInstanceOf(RfqOutcome.Unknown.class, outcome);
        assertEquals("rfq-1", unknown.rfqId());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenIdentityDiffersFromRequester() {
        RfqOutcome.Quoted quote = quote(Side.BUY, FIXED.instant().plusSeconds(60));
        SigningContext other = SigningContext.of(
                SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant());

        assertThrows(IllegalArgumentException.class,
                () -> rfq().accept(quote, other, ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS),
                "the quote was priced for the Deposit Wallet, not this EOA");
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void shouldKeepSettlementTransactionHashWhenStatusIsConfirmed() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"CONFIRMED","tx_hash":"0xdead"}"""));

        RfqOutcome outcome = rfq().status("rfq-1", ACCOUNT_CREDENTIALS, SIGNER.address());

        RfqOutcome.Confirmed confirmed = assertInstanceOf(RfqOutcome.Confirmed.class, outcome);
        assertEquals("0xdead", confirmed.txHash().orElseThrow(),
                "the settlement hash is the whole point of following an RFQ to CONFIRMED");
    }

    @Test
    void shouldOmitCallerSuppliedSignerWhenAcceptMethodsAreInspected() {
        for (java.lang.reflect.Method method : Rfq.class.getMethods()) {
            if (!method.getName().equals("accept")) continue;
            assertFalse(List.of(method.getParameterTypes()).contains(ComboQuoteSigner.class),
                    "a caller-supplied signer can produce an order the requester never authorised");
        }
    }
}
