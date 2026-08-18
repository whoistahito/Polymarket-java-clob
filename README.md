# Polymarket Java API Client

An unofficial Java SDK for the [Polymarket](https://polymarket.com) CLOB. It's a
library — no bots or entry points — that stays compatible with Polymarket's
official TypeScript and Rust signing behavior.

Requires Java 21+.

```xml
<dependency>
    <groupId>com.polymarket</groupId>
    <artifactId>polymarket-api</artifactId>
    <version>2.0.0</version>
</dependency>
```

## What's in it

- **Trading** — `PolymarketClient` (sync) and `AsyncPolymarketClient` (`CompletableFuture`):
  create/sign/post/cancel orders (GTC, GTD, FOK, FAK), open orders, trades,
  balances, order books, tick sizes.
- **Order signing** — EIP-712 order construction (`OrderBuilder`, standalone
  `OrderUtils`), two-level auth: L1 EIP-712 (API-key derivation) and L2
  HMAC-SHA256 (trading).
- **Wallets** — `WalletUtils` derives CREATE2 proxy and Gnosis Safe deposit
  wallets from an EOA. Signature types: EOA, POLY_PROXY, POLY_GNOSIS_SAFE.
- **Gamma API** — `GammaClient`: events, markets, tags, series, comments,
  sports, profiles, search.
- **Data API** — `DataClient`: trade history and holdings.
- **RFQ** — `RfqClient` for request-for-quote order flow.
- **WebSockets** — `WsClient`: live market and user feeds with typed messages
  and auto-reconnect.

## Usage

```java
PolymarketClient client = new PolymarketClient.Builder()
        .privateKey(privateKey)
        .chainId(137)                        // 137 mainnet, 80002 Amoy testnet
        .signatureType(SignatureType.POLY_PROXY)
        .funderAddress(depositWallet)
        .build();

SignedOrder order = client.createOrder(
        UserOrder.builder()
                .tokenID(tokenId)
                .price(new BigDecimal("0.42"))
                .size(new BigDecimal("5"))
                .side(Side.BUY)
                .build(),
        CreateOrderOptions.builder().tickSize("0.01").negRisk(false).build());

client.postOrder(order, OrderType.GTC);
```

Credentials are supplied by the caller — the SDK never reads them from property or secret files.
See `AGENTS.md` for full architecture and conventions.

## Build

```bash
mvn clean package     # build the JAR
mvn test              # run tests
mvn clean install     # install to local Maven repo
```

## Disclaimer

Unofficial and not affiliated with Polymarket. Trades real money on live
markets — use at your own risk.
