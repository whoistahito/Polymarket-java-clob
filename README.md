# Polymarket Java API Client

An unofficial Java SDK for the [Polymarket](https://polymarket.com) CLOB, Gamma, Data and real-time
APIs. It is a library — no bots, no entry point. Its signing is proven byte-for-byte against
vectors derived from Polymarket's published typed data by an independent signer (ethers v6), never
against this SDK's own output; the typed data, domains and contract addresses are official, the
signature bytes are independently derived. See `docs/protocol/SOURCES.md`.

Requires Java 21+.

```xml
<dependency>
    <groupId>com.polymarket</groupId>
    <artifactId>polymarket-api</artifactId>
    <version>2.0.0</version>
</dependency>
```

> **2.0.0 is a breaking redesign.** The 1.0 `PolymarketClient` / `GammaClient` / `DataClient` /
> `WsClient` facade is gone, with no compatibility adapters. See [docs/MIGRATION.md](docs/MIGRATION.md).

## What's in it

Everything hangs off one `Polymarket` entry point. Construction takes no credentials and makes no
network call; it is thread-safe and `AutoCloseable`.

| Capability | Reach it with | What it does |
|---|---|---|
| **Authentication** | `sdk.authentication()` | L1/L2 API-key create, derive, list, validate, delete. `SigningIdentity` covers EOA, Proxy, Safe and Deposit wallets, and derives Proxy/Safe addresses locally. |
| **Markets** | `sdk.markets()` | Gamma discovery: events, markets, tags, series, sports, search. Credential-free. |
| **Order books** | `sdk.orderBooks()` | Live CLOB books. One `GET /book` supplies every signing rule — tick size, minimum shares, neg-risk. Credential-free. |
| **Trading** | `sdk.trading()` | EIP-712 signing (Exchange V2 tokens, V3 Combo positions), submit, batch submit, cancel, and trade-ID reconciliation. Every write is classified, never replayed. |
| **Portfolio** | `sdk.portfolio()` | Positions, trades, activity and CLOB notifications, one typed cursor page at a time. |
| **Rewards** | `sdk.rewards()` | Market reward programmes, user earnings and percentages. |
| **Streaming** | `sdk.streaming()` | CLOB market and user WebSocket channels with typed events and closeable registrations. |
| **Operations** | `sdk.serverTime()`, `sdk.health()`, `sdk.geoblock()`, `sdk.startHeartbeat()` | Deployment checks and the dead-man-switch heartbeat, which is idle until you start it. |
| **RTDS** | `com.polymarket.streaming.Rtds` | The separate real-time data host: Binance/Chainlink prices, comment and reaction events. |
| **Builders** | `com.polymarket.builders.Builders` | Builder API keys and builder-attributed trades. Attribution rides `SigningContext.withBuilder(..)`. |
| **Social** | `com.polymarket.social.Social` | Read-only Gamma profiles, comments and profile search. |
| **RFQ** | `com.polymarket.rfq.Rfq` | Builder Gateway **requester** flow for V3 Combo orders: discover Combo legs, request a Quote, accept it, follow settlement. |

Every capability hangs off `Polymarket`, which owns and closes each one. `sdk.rfq(gatewayHost)` takes
the Builder Gateway host issued during builder onboarding, because the root cannot know it up front.
`AsyncTrading` and `AsyncRfq` wrap the two write-heavy capabilities in `CompletableFuture`s.

For exactly which endpoints are and are not covered, see [docs/API_COVERAGE.md](docs/API_COVERAGE.md).

## Usage

Public market data needs no credentials:

```java
import com.polymarket.Polymarket;
import com.polymarket.markets.DiscoveredMarket;
import com.polymarket.markets.MarketQuery;
import com.polymarket.markets.OrderBookSnapshot;
import com.polymarket.markets.TokenId;

try (Polymarket sdk = Polymarket.withDefaults()) {
    DiscoveredMarket market = sdk.markets()
            .markets(MarketQuery.create().limit(1).closed(false))
            .get(0);
    TokenId token = new TokenId(market.outcomes().get(0).tokenId().orElseThrow());

    OrderBookSnapshot book = sdk.orderBooks().book(token).orElseThrow();
    System.out.println(book.bestAsk().orElseThrow().price() + " @ tick " + book.rules().tickSize());
}
```

Trading supplies a signing authority, and reads the signing rules from the same book it just fetched:

```java
import com.polymarket.Polymarket;
import com.polymarket.PolymarketConfig;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.markets.OrderBookSnapshot;
import com.polymarket.markets.Price;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TokenId;
import com.polymarket.trading.LimitOrder;
import com.polymarket.trading.OrderExecution;
import com.polymarket.trading.Side;
import com.polymarket.trading.SigningContext;
import com.polymarket.trading.SubmissionOutcome;
import java.time.Instant;

// The Account Signer holds the key. The Trading Wallet holds the funds and is named as maker.
// For an EOA they are the same address; for Proxy, Safe and Deposit Wallets they are not.
// A Deposit Wallet goes further: it is ERC-1271-verified, so it is also the order's signer,
// while the Account Signer stays the address every authenticated request is made as.
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
```

Streaming registers handlers before it subscribes, so no snapshot can arrive unhandled:

```java
try (Polymarket sdk = Polymarket.withDefaults()) {
    sdk.streaming().onBookUpdate(List.of(tokenId), event ->
            System.out.println(event.assetId() + " " + event.bids().size() + " bids"));
    sdk.streaming().subscribeMarket(List.of(tokenId));
    Thread.sleep(30_000);
}
```

Credentials and hosts are always caller-supplied: the SDK loads no property file, reads no secret file,
prints no configuration, and ships no HTTP proxy support.

## Build

```bash
mvn clean verify      # full deterministic offline suite — what CI runs
mvn clean package     # build the JAR
mvn clean install     # install to the local Maven repo
mvn -Plive test       # opt-in credential-free read-only smoke checks against production
```

The deterministic suite is offline by construction — a test-scope DNS guard resolves loopback names
only. The live checks are `@Tag("live")`, excluded from the normal run, and only ever perform public
GET/subscribe operations: they never place, accept or cancel an order, and take no credentials.

## Documentation

- [AGENTS.md](AGENTS.md) — architecture, invariants and conventions. Read this first before changing code.
- [docs/API_COVERAGE.md](docs/API_COVERAGE.md) — what is supported, unsupported and out of scope, per endpoint.
- [docs/MIGRATION.md](docs/MIGRATION.md) — the 1.0 → 2.0 map.
- [docs/WORKFLOW.md](docs/WORKFLOW.md) — how work on this repo is planned and landed.

## Disclaimer

Unofficial and not affiliated with Polymarket. Trades real money on live markets — use at your own
risk.
