package com.polymarket.examples;

import com.polymarket.Polymarket;
import com.polymarket.PolymarketConfig;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.markets.DiscoveredMarket;
import com.polymarket.markets.MarketQuery;
import com.polymarket.markets.OrderBookSnapshot;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TokenId;
import com.polymarket.trading.LimitOrder;
import com.polymarket.trading.OrderExecution;
import com.polymarket.trading.Side;
import com.polymarket.trading.SigningContext;
import com.polymarket.trading.SubmissionOutcome;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Every README example, compiled by the normal build. {@code ReadmeExamplesTest} asserts the
 * README still contains these exact bodies, so a drifting example fails CI instead of a reader.
 */
@SuppressWarnings("unused")
final class ReadmeExamples {

    private String privateKeyHex;
    private String tradingWallet;
    private String apiKey;
    private String apiSecret;
    private String passphrase;
    private String tokenId;
    private long salt;

    void publicMarketData() throws IOException {
        // README:public-market-data
        try (Polymarket sdk = Polymarket.withDefaults()) {
            DiscoveredMarket market = sdk.markets()
                    .markets(MarketQuery.create().limit(1).closed(false))
                    .get(0);
            TokenId token = new TokenId(market.outcomes().get(0).tokenId().orElseThrow());

            OrderBookSnapshot book = sdk.orderBooks().book(token).orElseThrow();
            System.out.println(book.bestAsk().orElseThrow().price() + " @ tick " + book.rules().tickSize());
        }
        // README:end
    }

    void trading() throws IOException {
        // README:trading
        // The Account Signer holds the key. The Trading Wallet holds the funds and is named as maker.
        // For an EOA they are the same address; for Proxy, Safe and Deposit Wallets they are not.
        PrivateKeySigner accountSigner = PrivateKeySigner.of(privateKeyHex);
        SigningIdentity identity = SigningIdentity.proxyWallet(tradingWallet, accountSigner.address());
        ApiCredentials credentials = new ApiCredentials(apiKey, apiSecret, passphrase);
        SigningAuthority authority =
                SigningAuthority.signing(accountSigner, identity).withApiCredentials(credentials);

        try (Polymarket sdk = Polymarket.with(PolymarketConfig.defaults(), authority)) {
            TokenId token = new TokenId(tokenId);
            OrderBookSnapshot book = sdk.orderBooks().book(token).orElseThrow();

            // The Order Intent carries the order type, Maker-Only promise and lifetime, so submission
            // cannot contradict what was signed.
            OrderExecution execution = OrderExecution.of(
                    new LimitOrder(token, Side.BUY, Price.of("0.42"), ShareQuantity.of("10")),
                    book.rules());               // live tick, minimum and neg-risk

            SigningContext context = SigningContext.of(identity, accountSigner, salt, Instant.now());
            SubmissionOutcome outcome = sdk.trading().place(execution, context, credentials);

            switch (outcome) {
                case SubmissionOutcome.Accepted a -> System.out.println("live: " + a.orderId());
                case SubmissionOutcome.Rejected r -> System.out.println("rejected: " + r.reason());
                // Never a silent replay: one signed order is exactly one POST /order.
                case SubmissionOutcome.Unknown u -> System.out.println("uncertain: " + u.reason());
            }
        }
        // README:end
    }

    void streaming() throws InterruptedException {
        // README:streaming
        try (Polymarket sdk = Polymarket.withDefaults()) {
            sdk.streaming().onBookUpdate(List.of(tokenId), event ->
                    System.out.println(event.assetId() + " " + event.bids().size() + " bids"));
            sdk.streaming().subscribeMarket(List.of(tokenId));
            Thread.sleep(30_000);
        }
        // README:end
    }
}
