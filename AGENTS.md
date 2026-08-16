# Polymarket Java API Client — Instructions

## Build & Test

```bash
# Compile
mvn clean compile

# Run all tests (deterministic, offline — this is what CI runs)
mvn clean verify

# Run the opt-in read-only live checks (needs POLYMARKET_LIVE=1 and a private key)
mvn -Plive test

# Run a single test class
mvn test -Dtest=OrderBuilderTest

# Run a single test method
mvn test -Dtest=OrderBuilderTest#testCreateOrder

# Build the library JAR (this repo is a dependency, not an app — no main class)
mvn clean package

# Install to the local Maven repo so downstream projects (e.g. a trading bot) can depend on it
mvn clean install
```

Test classes match `**/*Test.java` or `**/*Tests.java`. The build targets **Java 21** via the compiler
`release` setting.

**The deterministic suite is offline by design.** A test-scope DNS provider
(`NoExternalNetworkResolverProvider`) resolves loopback names only, and `NoExternalNetworkExtension`
forces direct connections so an ambient HTTP proxy cannot tunnel past it. Anything reaching a real
host fails with `UnknownHostException`. Live checks carry `@Tag("live")`, are excluded from the normal
run, and are selected only by `-Plive` (which sets `-Dpolymarket.live=true` to lift the guard).

## Architecture (2.0, in progress)

The 2.0 redesign (issue #1) grows **beside** the 1.0 facade; both compile until the facade is
deleted. Domain packages are public, transport lives behind `internal`:

- `com.polymarket` — `Polymarket` (entry point, thread-safe, `AutoCloseable`), `PolymarketConfig`
  (JDK `URI`/`Duration` only), `ReadRetryPolicy`.
- `com.polymarket.operations` — operator value objects: `ServerTime`, `ServiceHealth`,
  `PolymarketService`, `GeoblockStatus`.
- `com.polymarket.internal.http` — `HttpRuntime`, `HttpOutcome`. **Retry is keyed on the
  operation's idempotency, not on client configuration**: `get` retries within the budget and
  honours `Retry-After`; `post` executes exactly once, so no read budget can replay an order.
- `com.polymarket.internal.operations` — `OperationsGateway`, the wire→domain translation.

Rules that later tickets inherit: no OkHttp/Jackson/Web3j type in a public signature, and no
transport import inside a public domain package — the gateway does the mapping.

## Architecture (1.0 facade)

This Java SDK stays compatible with Polymarket signing behavior from the upstream TypeScript and Rust
reference SDKs — `Polymarket/clob-client` (TS) and `Polymarket/rs-clob-client-v2` (Rust) on GitHub.
(These are external references; they are not vendored into this repo.)

**Package layout:**

- `com.polymarket.client` — Core API classes (`PolymarketClient`, `AsyncPolymarketClient`, `OrderBuilder`, `HttpClient`,
  `L1Eip712Signer`, `L2HmacSigner`, `ApiKeyCreds`, `ProxyConfig`, `PolymarketEndpoints`, `GammaClient`, `RfqClient`,
  `DataClient`, `HeartbeatManager`)
- `com.polymarket.model` — Immutable data models (`SignedOrder`, `PostOrderPayload`, `UserOrder`, `UserMarketOrder`, `OrderData`, `Side`, `OrderType`, `SignatureType`, `Chain`, `OrderStatusType`, `TradeStatusType`, `TraderSide`, `Token`, `ApiKeyRaw`, `HeartbeatResponse`, `OpenOrderParams`, `TradeParams`, `RfqRequestOrderCreationPayload`, and 40+ more)
- `com.polymarket.model.data` — Data API request/response models (`DataTrade`, `DataTradesRequest`, `DataSide`,
  `FilterType`)
- `com.polymarket.model.gamma` — GammaClient request + response models (45 classes)
- `com.polymarket.ctf` — Conditional Token Framework client (`CtfClient`) and split/merge/redeem + ID-computation
  request/response models
- `com.polymarket.ws` — WebSocket live-feed client (`WsClient`, `WsMessageListener`, `ChannelType`, `ConnectionState`)
- `com.polymarket.ws.model` — WS message types (`WsMessage`, `BookUpdate`, `PriceChange`, `TradeMessage`, `OrderMessage`, `MidpointUpdate`, etc.)
- `com.polymarket.util` — `Config` (properties loader), `PriceUtils` (tick rounding, decimal math, order-book hash, `decimalPlaces`, `orderToJson`), `WalletUtils` (CREATE2 proxy/safe wallet derivation), `OrderUtils` (standalone EIP-712 order builder)
This repo is a pure SDK (library) — it has no application entry point. Trading strategies/bots live in
separate projects that depend on this artifact.

**Two-level authentication flow:**
1. **L1 (EIP-712)** — `L1Eip712Signer` — used for API key derivation/creation. Signs a fixed message (`"This message attests that I control the given wallet"`) via EIP-712 with domain `ClobAuthDomain v1`. Produces headers: `POLY_ADDRESS`, `POLY_SIGNATURE`, `POLY_TIMESTAMP`, `POLY_NONCE`.
2. **L2 (HMAC-SHA256)** — `L2HmacSigner` — used for all trading operations. Produces headers: `POLY_ADDRESS`, `POLY_SIGNATURE`, `POLY_TIMESTAMP`, `POLY_API_KEY`, `POLY_PASSPHRASE`.

**`PolymarketClient`** is built via `PolymarketClient.Builder`. It holds `ConcurrentHashMap` caches for tick sizes, fee
rates, and neg-risk status to avoid redundant API calls. It delegates signing to `L1Eip712Signer`/`L2HmacSigner` and
order construction to `OrderBuilder`. Typed-params overloads (`getOpenOrders(OpenOrderParams)`,
`getTrades(TradeParams)`) and `postOnly`/`deferExec` convenience overloads are provided. Access `RfqClient` via
`client.rfq()`, `GammaClient` via `client.gamma()`, and `DataClient` via `client.data()`. Heartbeat lifecycle helpers
are exposed via `startHeartbeats()` / `startHeartbeats(intervalMs)` / `stopHeartbeats()` / `isHeartbeatsActive()`.

**`AsyncPolymarketClient`** wraps `PolymarketClient` via `AsyncPolymarketClient.wrap(client)`. Every method returns
`CompletableFuture<T>`. Accepts a custom `Executor`; defaults to `ForkJoinPool.commonPool()`. `AsyncRfqClient` is
accessible via `async.rfq()`, `DataClient` via `async.data()`, and heartbeat lifecycle helpers are mirrored on the async
wrapper.

**Order submission disposition (Ticket 022).** `PolymarketClient.submitOrder(...)` (and the async mirror) returns a
typed `OrderSubmission` instead of throwing: `ACCEPTED` only for a coherent success carrying a nonblank order ID and
status, `REJECTED` only when the exchange definitively refused the order (any 4xx, the documented 500
`order timed out`, a documented 503 service block, or an explicit `success=false`), and `UNKNOWN` for transport loss,
a generic 5xx, a null/unreadable body, or a contradictory success. `isSafeToRetry()` flags the documented
"not placed, try again" errors. `postOrder` keeps its throwing behaviour for existing callers.

**Typed market rules (Ticket 024).** `MarketRules` carries `orderPriceMinTickSize` and `orderMinSize` as exact
`BigDecimal`s (both nullable so callers can fail closed) and converts straight to `CreateOrderOptions` with no
`double` round trip. Read it with `PolymarketClient.getMarketRules(conditionId)`; `GammaMarket` and
`GammaMarketDetail` expose the same fields plus a `marketRules()` accessor.

**Reconciliation reads (Ticket 025).** `getOpenOrders(...)` follows the pagination cursor to the end;
`getOpenOrdersPaginated(params, cursor)` is the explicit single-page API. `DataClient.positions(DataPositionsRequest)`
returns typed `DataPosition` records with `BigDecimal` sizes. Positions are ABSOLUTE snapshots — the SDK deliberately
imposes no monotonic semantics, because clamping would hide a real sell.

**`GammaClient`** is a standalone client for `https://gamma-api.polymarket.com` covering 26 endpoints (events, markets, tags, series, comments, sports, profiles, search). Built via `new GammaClient.Builder().build()` or accessed via `PolymarketClient.gamma()`.

**`WsClient`** is built via `WsClient.builder()`. It wraps OkHttp's WebSocket API and supports:
- **Market channel** (`wss://ws-subscriptions-clob.polymarket.com/ws/market`) — unauthenticated; subscribe with a list of asset (token) IDs.
- **User channel** (`wss://ws-subscriptions-clob.polymarket.com/ws/user`) — L2-authenticated; auth fields embedded in subscription JSON.
- Optional `emitMidpointUpdates(true)` to synthesise `MidpointUpdate` messages from `BookUpdate` events.
- Incoming messages are dispatched as strongly-typed `WsMessage` subtypes to the registered `WsMessageListener`.
- Auto-reconnect with exponential backoff (`maxReconnectAttempts`, `reconnectDelayMs`, `maxReconnectDelayMs`); re-subscribes on reconnect.
- Per-channel health-check: `isMarketConnected()`, `isUserConnected()`, `getConnectionState(ChannelType)`, `getSubscriptionCount()`.
- **Registration is separate from subscription (Ticket 026).** `register*` methods (`registerBookUpdates`,
  `registerPriceChanges`, `registerLastTradePrices`, `registerTickSizeChanges`, `registerOrders`, `registerTrades`, …)
  attach a filtered callback and perform NO network action, returning a `WsClient.Registration` removal handle.
  Register every handler first, then call `subscribeMarket`/`subscribeUser` once — that ordering is what stops the
  initial snapshot arriving before a handler exists. The older `onBookUpdate`-style methods still work but are
  deprecated because each one sends its own subscribe frame.
- The subscribed token/market sets are authoritative: subscribe ADDS, unsubscribe REMOVES, and reconnect restores
  exactly what remains. Read them with `getSubscribedAssetIds()` / `getSubscribedMarkets()`.
- **Channel-identified lifecycle (Ticket 027).** `WsMessageListener` gained `onOpen(ChannelType, generation)`,
  `onError(ChannelType, generation, Exception)`, `onClose(ChannelType, generation, code, reason)`, and
  `onResubscribe(ChannelType, generation)` — the last fires before any frame of a new generation, so consumers can
  invalidate only the channel that dropped. `getConnectionGeneration(ChannelType)` exposes the counter.
  Reconnect is scheduled in a `finally` block and application-callback exceptions are isolated.
- The documented text `PING` heartbeat is sent every 10 s per open channel (`pingIntervalMs`), cancelled on close and
  restarted on reconnect. The reconnect budget resets only after a connection stays up for `stableConnectionMs`
  (default 30 s), so a handshake-then-close loop cannot spin forever.

**`OrderBuilder`** constructs EIP-712 signed order payloads. Contract addresses are hardcoded by chain ID (137 / 80002). Rounding precision is determined by a `RoundConfig` keyed on tick size string (`"0.1"`, `"0.01"`, `"0.001"`, `"0.0001"`). Salt is masked to the IEEE 754 safe integer range (`& ((1L << 53) - 1)`).

**`OrderUtils`** is a standalone, client-independent EIP-712 order builder (`util/OrderUtils.java`). Accepts raw `OrderData` with pre-scaled `BigInteger` amounts — no `PolymarketClient` required. Use when you have pre-calculated maker/taker amounts.

**`WalletUtils`** derives CREATE2 proxy and safe wallet addresses from an EOA address, matching the Rust SDK's `derive_proxy_wallet` / `derive_safe_wallet` exactly. Returns `Optional.empty()` for unsupported chain IDs.

**Configuration** is loaded from `src/main/resources/config.properties` via `Config.load()`. Credentials can be provided
directly with `credentials.private-key` / `credentials.funder-wallet`, or via external files referenced by
`secret.key.file` / `funder.wallet.file`.

## Key Conventions

### Decimal arithmetic
- All prices and amounts use `BigDecimal`. Never use `double`/`float` for financial values.
- Token amounts use **6 decimal places** (USDC standard); multiply by `10^6` before sending to contracts.
- **Tick sizes (Ticket 023):** the supported grid is `0.1`, `0.01`, `0.005`, `0.0025`, `0.001`, `0.0001`, matched by
  numeric value so `"0.010"` resolves like `"0.01"`. There is NO fallback profile — an unrecognised tick throws
  before any amount is calculated, because signing against the wrong grid mis-prices every order on a
  `0.005`/`0.0025` market.
- **Minimum order size (Ticket 023)** is compared against the NORMALIZED share quantity read back out of the computed
  maker/taker amounts (taker for a BUY, maker for a SELL), not the caller's raw size — `10.009` shares truncate to
  `10.00` and must be rejected against a `10.005` minimum. Enforced on the limit and market BUY/SELL paths alike.
- Rounding is per-field, matching the TS/Rust reference clients — do **not** use `HALF_UP` for amounts:
  - **Price → tick**: nearest tick (`HALF_UP`) via `roundToTickSize`.
  - **Order size**: `DOWN` (truncate) to `RoundConfig.size` decimals.
  - **Maker/taker amounts**: `DOWN` (truncate) — the Rust ref uses `trunc_with_scale`, the TS ref
    uses roundUp→roundDown. `RoundConfig.amount == price + size` decimals, so with a tick-rounded
    price the product already fits exactly (e.g. `1.9996` stays `1.9996`, never rounds up to `2.0`).
  - **Market-buy precision**: quantized `DOWN` to fixed unit steps (`normalizeMarketBuyPrecision`).

### Model classes
- Use Lombok (`@Data`, `@Builder`, `@Value`, etc.) to reduce boilerplate.
- Classes in the `client` package are `final`.
- Prefer Java records for purely immutable data carriers where Lombok is not needed.

### Chain IDs
| Network | ID |
|---|---|
| Polygon Mainnet | 137 |
| Polygon Amoy (Testnet) | 80002 |

### Order types
`GTC` (resting), `GTD` (expires by date), `FOK` (all-or-nothing immediate), `FAK` (fill what's available).

### Signing compatibility

EIP-712 signing: when modifying `L1Eip712Signer`, `OrderBuilder`, or `OrderUtils`, verify salt masking
matches the upstream Rust `order_builder.rs` (`to_ieee_754_int`) in `Polymarket/rs-clob-client-v2`.

### Testing conventions
- Framework: JUnit 5 + Mockito
- Test IDs follow `TC-XX-NNN` in `@DisplayName` (e.g., `TC-PC-001`)
- Unit tests use a well-known test private key: `ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80`
- Verified baseline on Java 21 (2026-08-16): `mvn clean verify` → **967 tests, 0 failures, 0 skipped**.
  `mvn -Plive test` selects the 14 live checks, all skipped without `POLYMARKET_LIVE=1`.

