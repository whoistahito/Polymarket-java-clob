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
- `com.polymarket.authentication` — `Authentication` capability, `SigningAuthority`,
  `PrivateKeySigner` (holds the key, exposes only `address()` and `sign(digest)`), `ApiCredentials`,
  `SigningIdentity` (sealed: `Eoa`/`ProxyWallet`/`SafeWallet`/`DepositWallet`, valid by
  construction, each carrying its official signature type), typed outcomes, and the
  `ApiKeyDirectory` **port**.
- `com.polymarket.internal.operations` / `.authentication` — `OperationsGateway`,
  `AuthenticationGateway` (implements `ApiKeyDirectory`), `L1Attestation`, `L2Attestation`.
- `com.polymarket.markets` — the exact value kernel (`Price`, `TickSize`, `ShareQuantity`,
  `PusdAmount`, `AssetId`, `MarketRules`, `PriceLevel`, `OrderBookSnapshot`), the `MarketCatalog`
  port for Gamma discovery, and the `OrderBookSource` port for live CLOB books. **One `GET /book`
  supplies every signing rule** — tick, minimum shares and neg-risk — so the 2.0 path caches no rule
  and never substitutes a Gamma value (issue #10). Batches go through `GET /books?token_ids=`, not
  the documented POST form, because reads must keep their retry budget.
- `com.polymarket.trading` (issues #11, #12, #13) — the closed `OrderIntent` hierarchy
  (`LimitOrder`/`MakerOnlyLimitOrder`/`GoodTilDateOrder`/`ImmediateBuy`/`ImmediateSell`),
  `ImmediatePlanner` (pure depth-walking, no network), and `OrderSigner`/`SigningContext`/
  `SignedOrder`. Routing is the asset's sealed type alone — a `TokenId` signs against Exchange V2,
  a `PositionId` against V3 — proven byte-for-byte against the official vectors in
  `Eip712OrderSignerVectorTest`. A Deposit Wallet (signature type 3) signs the ERC-7739
  `TypedDataSign` wrapper under the exchange's own domain, not the wallet's. Implemented by
  `com.polymarket.internal.trading.Eip712OrderSigner`; signing is offline and takes no port.
  `Trading` (issue #14) adds `submit`/`place` over the `OrderSubmitter` **port**, classifying
  every `POST /order` outcome as `SubmissionOutcome.Accepted`/`Rejected`/`Unknown` — a documented
  4xx/duplicate/5xx-"order timed out" is a definitive `Rejected`, transport loss and a
  contradictory or malformed success are `Unknown`, and nothing is ever silently replayed
  (`HttpRuntime.post` executes exactly once). A `PositionId` order is rejected before any request:
  V3 Combo orders route through the RFQ Builder Gateway (issues #25/#26), not `POST /order`.
  Implemented by `com.polymarket.internal.trading.TradingGateway`.
  `Trading.reconcile` (issue #16) polls `GET /data/trades?id=` (one request per trade ID — the
  filter has no batch form) via the `TradeReader` **port** until every ID reaches the terminal
  `TradeStatus.Known.CONFIRMED`/`FAILED`, so a delayed transaction hash just shows up on a later
  poll. A missing or non-terminal record keeps polling; an unrecognised status is kept as its
  `raw()` text and treated as non-terminal. A local deadline yields `ReconciliationOutcome.Pending`
  (order and trade IDs preserved) rather than a reported failure. Implemented by
  `com.polymarket.internal.trading.TradeReaderGateway`.
  `Trading.submitBatch`/`cancel` (issue #17) add the `OrderBatch` **port**: official limits
  (15 orders, 1000 cancel IDs) and blank/duplicate cancel IDs are rejected before any request, one
  logical batch is exactly one `POST /orders`/`DELETE /orders`, and a batch is never silently
  chunked. Per-item outcomes are attached **positionally** — the wire array carries no per-item
  ID — and only when the response is a same-length array; any mismatch, unparseable body, or
  transport failure is `BatchSubmissionOutcome.Indeterminate` rather than inventing which item
  succeeded. A cancel ID the server does not confirm is `notCanceled`, even without a server
  reason. Also implemented by `com.polymarket.internal.trading.TradingGateway`.
- `com.polymarket.rewards` (issue #18) — `Rewards` capability, the `RewardLedger` **port**,
  `RewardCursor`/`RewardPage<T>`, and exact-decimal reward models. Cursors travel in the documented
  `next_cursor` **query** parameter, never a header; `RewardCursor.next` treats `LTE=`, blank and a
  non-advancing cursor as the end, so no read can spin. `allMarketRewards` is the only all-pages
  convenience — one market's programmes are practically bounded — and it also refuses to revisit a
  cursor. Implemented by `com.polymarket.internal.rewards.RewardsGateway`; user reads are L2.
- `com.polymarket.portfolio` (issue #15) — `Portfolio` capability, the `PortfolioLedger` **port**,
  absolute position snapshots, and CLOB notifications. A zero size survives untouched and every
  non-identity field is `Optional`, so clamping or fabrication can never hide a real change.
  Implemented by `com.polymarket.internal.portfolio.PortfolioGateway`; user reads are L2.
- `com.polymarket.social` (issue #20) — the read-only `Social` capability over Gamma profiles,
  comments (general, by entity, by user, and single-comment/thread lookup) and profile search,
  reached through the `SocialDirectory` **port**. Every read is credential-free and bounded (a
  `limit` is required so no comment read can become an unbounded walk); an unrecognised
  `parentEntityType` degrades to `Optional.empty()` rather than failing the read. `Markets.search()`
  keeps owning event/tag search results — `Social.search()` returns only the profile matches.
  Implemented by `com.polymarket.internal.social.SocialGateway`.
- `com.polymarket.builders` (issue #19) — `Builders` capability: L2-authenticated builder
  API-key create/list/revoke as typed outcomes (`BuilderCredentials` fully redacts every secret,
  matching `ApiCredentials`), plus `BuilderCursor`/`BuilderTradePage` reads of builder-attributed
  trades. Builder attribution itself needed no new plumbing — `SignedOrder.builder()` and
  `SigningContext.withBuilder(...)` (issues #12-#14) already carry it through signing and
  submission; `BuilderAttributionTest` proves that end to end. Implemented by
  `com.polymarket.internal.builders.BuildersGateway`.
- `com.polymarket.rfq` (issues #25, #26) — the Builder Gateway requester flow: `Rfq.request`/
  `status`/`waitForQuote`/`accept` over the `RfqDirectory` **port**, reached at a caller-supplied
  gateway host (issued per builder onboarding, so it is not one of `PolymarketConfig`'s fixed
  hosts). `request` and `accept` each sign with two independent HMAC header sets — account
  (`L2Attestation`) and builder (`POLY_BUILDER_*`, the same HMAC primitive, now `public` on
  `L2Attestation`). `RfqOutcome` is sealed (`Quoted`/`Confirmed`/`Waiting`/`Failed`/`Expired`/
  `Canceled`/`Pending`/`Unknown`); a business failure (no quote, maker decline, execution
  failure) is wire-indistinguishable as anything but status `FAILED` with a free-text nested
  error, so those three stay one `Failed(reason)` case rather than an invented error-code
  schema, and `CONFIRMED`/`FILLED` collapse into one `Confirmed` case matching how the official
  fixture itself groups them as `"success"`. `waitForQuote` mirrors `Trading.reconcile`'s
  injected-`Clock` poll loop exactly; a local timeout is `Pending`, never a reported failure.
  `accept` rejects an expired quote before sending, signs `quote.comboPositionId()` through the
  V3 path with `context.withBuilder(quote.builderCode())` (the official rule: "order.builder
  must equal the returned builder_code"), and never throws or replays on transport failure — a
  connection loss becomes `Unknown(rfqId, ...)`, the durable handle for a later status poll.
  `comboPositionId` itself is read from the first field name the gateway recognises: the fixture
  names the concept ("the returned Combo YES position ID") without pinning its wire key. Combo
  market/PositionId discovery is the caller supplying `PositionId` values it already holds — no
  CTF computation, and no unverified Gamma "list combo markets" endpoint invented without a
  pinned fixture. Implemented by `com.polymarket.internal.rfq.RfqGateway`.
- `AsyncTrading`/`AsyncRfq` (issue #27) — thin `CompletableFuture` decorators, one per method,
  living in `com.polymarket.trading`/`com.polymarket.rfq` alongside the capabilities they wrap.
  Only these two get an async form. Each future completes on the caller-supplied `Executor`
  (default `ForkJoinPool.commonPool()`); a checked `IOException` surfaces as
  `UncheckedIOException` inside the future's `ExecutionException`, matching every typed
  disposition (`SubmissionOutcome`/`BatchSubmissionOutcome`/`CancellationOutcome`/
  `ReconciliationOutcome`/`RfqOutcome`) unchanged from the synchronous call. Neither wrapper
  exposes the underlying sync capability or its `Executor` — no escape hatch back to a
  synchronous call or a place to hang extra retries.
- `com.polymarket.streaming` (issues #21, #22) — both CLOB WebSocket channels behind one shared
  `StreamTransport` **port** (one OkHttp client, one scheduler): market (book/price-change/
  last-trade-price/tick-size-change, credential-free) and user (order/trade, L2). Ported from
  the proven 1.0 `WsClient` reconnect/backoff/heartbeat algorithm, replacing its public OkHttp
  `WebSocket`/Jackson `JsonNode` surface with immutable records and `Registration` handles.
  Handlers register locally before any subscribe frame; `subscribeUser` fails fast on missing L2
  credentials before a socket opens; each channel keeps its own connection generation, so a user
  reconnect bumps only `userGeneration()` and leaves the market channel untouched. The initial
  user frame carries the nested `auth` object once — a dynamic update on an already-authenticated
  socket never repeats it. Implemented by `com.polymarket.internal.streaming.StreamingGateway`/
  `ChannelConnection`.
- `Rtds` (issue #23) — a second capability in `com.polymarket.streaming`, over the separate,
  unauthenticated RTDS host (`wss://ws-live-data.polymarket.com`, 5 s text `PING` — distinct
  from CLOB's 10 s): Binance/Chainlink price events and comment-created/removed/reaction-
  created/removed events with the documented `parentEntityID`/`parentEntityType` filters. Kept
  as a sibling of `Streaming` rather than folded into its types — genuinely different wire
  envelope and no auth — but mirrors its lifecycle contract exactly (register-before-subscribe,
  closeable `Registration`, per-connection generation, callback isolation) via a parallel
  `RtdsChannelConnection` porting the same proven reconnect/backoff/heartbeat algorithm.
  Implemented by `com.polymarket.internal.streaming.RtdsGateway`.
- Heartbeat (issue #24) — `Polymarket.startHeartbeat()`/`startHeartbeat(Duration)`/
  `stopHeartbeat()`/`isHeartbeatActive()` own the CLOB dead-man-switch `POST /v1/heartbeats`
  tick; nothing ticks on construction. The first tick sends `{"heartbeat_id":""}` (the 1.0
  facade string-concatenates a `null` id into the literal text `"null"` on its first call — a
  real bug, not the documented contract); every later tick chains the `heartbeat_id` the
  previous response returned. A failed tick is logged and stays scheduled — only
  `stopHeartbeat()`/`close()` cancels it, and both are idempotent. Implemented by
  `com.polymarket.internal.operations.HeartbeatGateway`.

Rules that later tickets inherit: no OkHttp/Jackson/Web3j type in a public signature, no transport
import inside a public domain package (the gateway does the mapping), and **capabilities depend on
domain-declared ports, never on an internal adapter type**. Only `Polymarket`, the composition root,
wires the two sides together. Secrets redact in `toString`, and absent authority throws
`AuthenticationRequiredException` before anything reaches the wire.

`PublicBoundaryTest` enforces those rules with ArchUnit (issue #6), scoped to the 2.0 packages so
legacy violations do not block migration. Two exemptions are deliberate: `Polymarket` (composition
root) may import `internal`, and `PrivateKeySigner`/`Addresses` may use Web3j because the JDK has no
secp256k1 or keccak — they stay bound by the public-signature rule. Each rule is proven to fail
against a test-only fixture in `src/test/java/com/polymarket/operations/*Leak.java`. Add a new 2.0
package to `PUBLIC_PACKAGES` when you create one.

**No direct-chain behavior (issue #7).** The SDK authorizes routed API requests and never broadcasts a Polygon
transaction: no RPC, CTF client or ID computation, gas/receipt models, split/merge/redeem, or collateral return.
Web3j is a signing-only dependency (`org.web3j:crypto`, no `core`); reintroducing `org.web3j.protocol` or
`org.web3j.tx` fails `DirectChainSurfaceTest`.

## Architecture (1.0 facade)

This Java SDK stays compatible with Polymarket signing behavior from the upstream TypeScript and Rust
reference SDKs — `Polymarket/clob-client` (TS) and `Polymarket/rs-clob-client-v2` (Rust) on GitHub.
(These are external references; they are not vendored into this repo.)

**Package layout:**

- `com.polymarket.client` — Core API classes (`PolymarketClient`, `AsyncPolymarketClient`, `OrderBuilder`, `HttpClient`,
  `L1Eip712Signer`, `L2HmacSigner`, `ApiKeyCreds`, `PolymarketEndpoints`, `GammaClient`, `RfqClient`,
  `DataClient`, `HeartbeatManager`)
- `com.polymarket.model` — Immutable data models (`SignedOrder`, `PostOrderPayload`, `UserOrder`, `UserMarketOrder`, `OrderData`, `Side`, `OrderType`, `SignatureType`, `Chain`, `OrderStatusType`, `TradeStatusType`, `TraderSide`, `Token`, `ApiKeyRaw`, `HeartbeatResponse`, `OpenOrderParams`, `TradeParams`, `RfqRequestOrderCreationPayload`, and 40+ more)
- `com.polymarket.model.data` — Data API request/response models (`DataTrade`, `DataTradesRequest`, `DataSide`,
  `FilterType`)
- `com.polymarket.model.gamma` — GammaClient request + response models (45 classes)
- `com.polymarket.ws` — WebSocket live-feed client (`WsClient`, `WsMessageListener`, `ChannelType`, `ConnectionState`)
- `com.polymarket.ws.model` — WS message types (`WsMessage`, `BookUpdate`, `PriceChange`, `TradeMessage`, `OrderMessage`, `MidpointUpdate`, etc.)
- `com.polymarket.util` — `PriceUtils` (tick rounding, decimal math, order-book hash, `decimalPlaces`, `orderToJson`), `WalletUtils` (CREATE2 proxy/safe wallet derivation), `OrderUtils` (standalone EIP-712 order builder)
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

**`WalletUtils`** derives CREATE2 proxy and safe wallet addresses from an EOA address, matching the Rust SDK's `derive_proxy_wallet` / `derive_safe_wallet` exactly. Returns `Optional.empty()` for unsupported chain IDs. Pure local computation — no RPC.

**Configuration is caller-supplied only (Ticket #8).** The SDK loads no property file, reads no secret file, prints no
configuration, and ships no HTTP proxy support — credentials reach the client through `PolymarketClient.Builder` (1.0)
or `PolymarketConfig` / `SigningAuthority` (2.0) and nowhere else.

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
- Verified baseline on Java 21 (2026-08-16): `mvn clean verify` → **1024 tests, 0 failures, 0 skipped**.
  `mvn -Plive test` selects the 14 live checks, all skipped without `POLYMARKET_LIVE=1`.

