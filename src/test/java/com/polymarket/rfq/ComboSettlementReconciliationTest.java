package com.polymarket.rfq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.Polymarket;
import com.polymarket.PolymarketConfig;
import com.polymarket.ReadRetryPolicy;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.internal.rfq.ComboMarketGateway;
import com.polymarket.internal.rfq.RfqGateway;
import com.polymarket.internal.trading.Eip712OrderSigner;
import com.polymarket.markets.PositionId;
import com.polymarket.portfolio.ComboPositionQuery;
import com.polymarket.portfolio.ComboPositionSnapshot;
import com.polymarket.portfolio.PortfolioPage;
import com.polymarket.trading.Side;
import com.polymarket.trading.SigningContext;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An accepted RFQ settles into Combo positions that are reconciled by reading absolute
 * Portfolio snapshots — never by accumulating the Quote amounts locally.
 */
@DisplayName("Combo settlement reconciliation through absolute Portfolio snapshots (issue #26)")
class ComboSettlementReconciliationTest {

    private static final String TEST_KEY =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(TEST_KEY);
    private static final ApiCredentials ACCOUNT_CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final com.polymarket.builders.BuilderCredentials BUILDER_CREDENTIALS =
            new com.polymarket.builders.BuilderCredentials(
                    "builder-key", "YnVpbGRlci1zZWNyZXQtYnVpbGRlci1zZWNyZXQ=", "builder-passphrase");
    private static final Clock FIXED =
            Clock.fixed(Instant.ofEpochSecond(1773890758L), ZoneOffset.UTC);
    private static final String COMBO_POSITION_ID = "77120044";
    private static final String COMBO_CONDITION =
            "0x0391ab0ebea17b65ba87e071b0566e816b0000000000000000000000000000";
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

    private Rfq rfq() {
        URI host = server.url("/").uri();
        HttpRuntime runtime = new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                ReadRetryPolicy.none(), d -> {
                });
        return new Rfq(new RfqGateway(host, runtime, FIXED),
                new ComboMarketGateway(host, runtime), FIXED);
    }

    private Polymarket sdk() {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host)
                .connectTimeout(Duration.ofSeconds(2)).requestTimeout(Duration.ofSeconds(5))
                .readRetryPolicy(ReadRetryPolicy.none());
        return Polymarket.with(config,
                SigningAuthority.signing(SIGNER, SigningIdentity.eoa(SIGNER.address()))
                        .withApiCredentials(ACCOUNT_CREDENTIALS));
    }

    /** 2,000 Combo shares bought for 1,000 pUSD: maker 1000000, taker 2000000 base units. */
    private static RfqOutcome.Quoted quote() {
        return new RfqOutcome.Quoted("rfq-1", "quote-1", Side.BUY,
                new PositionId(COMBO_POSITION_ID), List.of(new PositionId("111"),
                new PositionId("222")),
                new QuoteAmounts(500000L, 1000000L, 2000000L, 1000000L, 2000000L),
                FIXED.instant().plusSeconds(60), BUILDER_CODE);
    }

    /** Body shaped from data-openapi.yaml CombosResponse/ComboPosition. */
    private void enqueueComboSnapshot(String sharesBalance) {
        server.enqueue(new MockResponse().setBody("""
                {"combos":[{"combo_condition_id":"%s","combo_position_id":"%s",
                  "user_address":"%s","shares_balance":"%s","status":"OPEN",
                  "legs":[{"leg_index":0,"leg_position_id":"111","leg_status":"OPEN"},
                          {"leg_index":1,"leg_position_id":"222","leg_status":"OPEN"}]}],
                 "pagination":{"limit":100,"offset":0,"has_more":false,"next_cursor":null}}"""
                .formatted(COMBO_CONDITION, COMBO_POSITION_ID, SIGNER.address(), sharesBalance)));
    }

    @Test
    @DisplayName("TC-CS-001: an accepted RFQ is reconciled by reading the Combo position snapshot it settles into")
    void acceptedRfqIsReconciledThroughTheComboPositionSnapshot() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"rfq_id":"rfq-1","status":"CONFIRMED","tx_hash":"0xdead"}"""));
        enqueueComboSnapshot("2000.000000");

        RfqOutcome accepted = rfq().accept(quote(), new Eip712OrderSigner(),
                SigningContext.of(SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L,
                        FIXED.instant()),
                ACCOUNT_CREDENTIALS, BUILDER_CREDENTIALS);
        assertInstanceOf(RfqOutcome.Confirmed.class, accepted);

        PortfolioPage<ComboPositionSnapshot> page;
        try (Polymarket sdk = sdk()) {
            page = sdk.portfolio().comboPositions(
                    ComboPositionQuery.forUser(SIGNER.address()));
        }

        ComboPositionSnapshot combo = page.items().get(0);
        assertEquals(COMBO_POSITION_ID, combo.comboPositionId());
        assertEquals(quote().comboPositionId().value(), combo.comboPositionId());
        assertEquals(new BigDecimal("2000.000000"), combo.sharesBalance());
        assertEquals(List.of("111", "222"),
                combo.legs().stream().map(leg -> leg.positionId()).toList());
    }

    @Test
    @DisplayName("TC-CS-002: a second fill reports the server's absolute balance, never a locally accumulated delta")
    void secondFillReportsTheServersAbsoluteBalance() throws Exception {
        enqueueComboSnapshot("2000.000000");
        // The server rebased this holding (a leg redemption, a partial exit); the absolute
        // snapshot is 2500, not the 4000 that adding the second Quote's shares would give.
        enqueueComboSnapshot("2500.000000");

        BigDecimal first;
        BigDecimal second;
        try (Polymarket sdk = sdk()) {
            first = sdk.portfolio().comboPositions(ComboPositionQuery.forUser(SIGNER.address()))
                    .items().get(0).sharesBalance();
            second = sdk.portfolio().comboPositions(ComboPositionQuery.forUser(SIGNER.address()))
                    .items().get(0).sharesBalance();
        }

        assertEquals(new BigDecimal("2000.000000"), first);
        assertEquals(new BigDecimal("2500.000000"), second);
        assertNotEquals(first.add(new BigDecimal("2000.000000")), second);
    }

    @Test
    @DisplayName("TC-CS-003: reconciliation is one documented Data API read and no chain call")
    void reconciliationIsOneDocumentedReadAndNoChainCall() throws Exception {
        enqueueComboSnapshot("2000.000000");

        try (Polymarket sdk = sdk()) {
            sdk.portfolio().comboPositions(ComboPositionQuery.forUser(SIGNER.address()));
        }

        assertEquals(1, server.getRequestCount());
        String path = server.takeRequest().getPath();
        assertTrue(path.startsWith("/v1/positions/combos?user=" + SIGNER.address()), path);
    }
}
