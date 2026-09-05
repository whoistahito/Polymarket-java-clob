package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import com.polymarket.trading.LimitOrder;
import com.polymarket.trading.OrderExecution;
import com.polymarket.trading.OrderPlacement;
import com.polymarket.trading.OrderType;
import com.polymarket.trading.Side;
import com.polymarket.trading.SignedOrder;
import com.polymarket.trading.SigningContext;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Proves builder attribution and metadata survive intent, signing, and the POST /order wire body. */
class BuilderAttributionTest {

    private static final String BUILDER_CODE = "0x" + "ab".repeat(32);
    private static final String METADATA_CODE = "0x" + "cd".repeat(32);

    private static final PrivateKeySigner SIGNER = PrivateKeySigner.of(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
    private static final ApiCredentials CREDENTIALS = new ApiCredentials(
            "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
            "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    private static final Clock FIXED =
            Clock.fixed(Instant.ofEpochSecond(1773890758L), ZoneOffset.UTC);
    private static final MarketRules RULES =
            new MarketRules(TickSize.of("0.01"), ShareQuantity.of("1"), false);

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

    private Polymarket sdk() {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        SigningAuthority authority = SigningAuthority
                .signing(SIGNER, SigningIdentity.eoa(SIGNER.address()))
                .withApiCredentials(CREDENTIALS);
        return Polymarket.with(config, new HttpRuntime(Duration.ofSeconds(2),
                Duration.ofSeconds(5), ReadRetryPolicy.none(), d -> {
                }), authority, FIXED);
    }

    @Test
    void shouldPreserveBuilderCodeWhenSigningOrder() {
        SigningContext context = SigningContext.of(
                        SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant())
                .withBuilder(BUILDER_CODE);

        SignedOrder order;
        try (Polymarket sdk = sdk()) {
            order = sdk.trading().sign(new TokenId("123"), Side.BUY,
                    Price.of("0.52"), ShareQuantity.of("10"), RULES, context);
        }

        assertEquals(BUILDER_CODE, order.builder());
    }

    @Test
    void shouldSendBuilderAndMetadataWhenSubmittingOrder() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"success":true,"orderID":"0xabc","status":"live","tradeIDs":[]}"""));

        SigningContext context = SigningContext.of(
                        SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant())
                .withBuilder(BUILDER_CODE)
                .withMetadata(METADATA_CODE);

        try (Polymarket sdk = sdk()) {
            sdk.trading().place(OrderExecution.of(new LimitOrder(new TokenId("123"), Side.BUY,
                    Price.of("0.52"), ShareQuantity.of("10")), RULES), context, CREDENTIALS);
        }

        RecordedRequest request = server.takeRequest();
        assertEquals("/order", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"builder\":\"" + BUILDER_CODE + "\""), body);
        assertTrue(body.contains("\"metadata\":\"" + METADATA_CODE + "\""), body);
    }

    @Test
    void shouldSendZeroBuilderCodeWhenBuilderIsAbsent() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"success":true,"orderID":"0xabc","status":"live","tradeIDs":[]}"""));

        SigningContext context = SigningContext.of(
                SigningIdentity.eoa(SIGNER.address()), SIGNER, 1L, FIXED.instant());

        try (Polymarket sdk = sdk()) {
            sdk.trading().place(OrderExecution.of(new LimitOrder(new TokenId("123"), Side.BUY,
                    Price.of("0.52"), ShareQuantity.of("10")), RULES), context, CREDENTIALS);
        }

        String body = server.takeRequest().getBody().readUtf8();
        String zeroBytes32 = "0x" + "0".repeat(64);
        assertTrue(body.contains("\"builder\":\"" + zeroBytes32 + "\""), body);
    }
}
