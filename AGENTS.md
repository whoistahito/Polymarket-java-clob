# Polymarket Java API Client — Instructions

## Build & Test

```bash
# Compile
mvn clean compile

# Run all tests (deterministic, offline — this is what CI runs)
mvn clean verify

# Run the opt-in read-only live checks against the real production API (see the note below)
mvn -Plive test
mvn -Plive test -Dtest=LiveReadOnlyTest

# Run a single test class
mvn test -Dtest=TradingTest

# Run a single test method
mvn test -Dtest=TradingTest#coherentSuccessIsAccepted

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

The 2.0 live checks are `com.polymarket.live.LiveReadOnlyTest` (issue #30): server time, service
health, geoblock, Gamma discovery, one `GET /book`, and one market-stream connect-and-receive. They
take **no credentials** and perform only documented public GET/subscribe operations — never an order,
an RFQ acceptance, a cancellation, or a derived private key. `LiveCheckGatingTest` (deterministic) is
the guard: it fails the build if a live check loses its `@Tag("live")` or if a live tag appears outside
`com.polymarket.live`.

## Architecture (2.0)

The 2.0 redesign (issue #1) is now the only architecture — issue #28 deleted the superseded 1.0
facade (`com.polymarket.client`/`model`/`util`/`ws`/`rtds`) with no forwarding adapter. This repo is
a pure SDK (library) with no application entry point; trading strategies live in separate projects
that depend on the artifact. Domain packages are public, transport lives behind `internal`:

- `com.polymarket` — `Polymarket` (entry point, thread-safe, `AutoCloseable`), `PolymarketConfig`
  (JDK `URI`/`Duration` only), `ReadRetryPolicy`.
- `com.polymarket.operations` — operator value objects: `ServerTime`, `ServiceHealth`,
  `PolymarketService`, `GeoblockStatus`.
- `com.polymarket.internal.http` — `HttpRuntime`, `HttpOutcome`. **Retry is keyed on the
  operation's idempotency, not on client configuration**: `get` retries within the budget and
  honours `Retry-After`; `post` executes exactly once, so no read budget can replay an order.
- `com.polymarket.authentication` — `Authentication` capability, `SigningAuthority`,
  `PrivateKeySigner` (holds the key, exposes only `address()` and `sign(digest)`), `ApiCredentials`,
  `ApiKey` (a listed key, redacted in `toString` like `ApiCredentials` — issue #5),
  `SigningIdentity` (sealed: `Eoa`/`ProxyWallet`/`SafeWallet`/`DepositWallet`, valid by
  construction, each carrying its official signature type and its `orderSigner()` — the address the
  exchange resolves for an order, which is the Trading Wallet under signature type 3 because that
  wallet ERC-1271-verifies its own orders, and the Account Signer otherwise), typed outcomes, and the
  `ApiKeyDirectory` **port**. `SigningIdentity.deriveProxyWallet`/`.deriveSafeWallet` compute the
  CREATE2 Proxy/Safe address for an EOA locally (no RPC), matching the documented factories.
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
  `SignedOrder`. **`OrderSigner` has one method and it is priced** (issue #10): it takes the
  Protected Price, enforces the snapshot and derives the pUSD leg, so no leg-only overload can imply
  a price the snapshot never saw. The one legitimate leg-based case, a Builder Gateway Combo quote,
  has its own `com.polymarket.rfq.ComboQuoteSigner` port taking a `PositionId`.
  `SignedOrder` is valid by construction — addresses, unsigned fields, positive legs, an official
  signature type and a hex signature — and carries `signer` (the exchange's resolved signer) and
  `accountSigner` (POLY_ADDRESS) separately, because type 3 splits them.
  Routing is the asset's sealed type alone — a `TokenId` signs against Exchange V2,
  a `PositionId` against V3 — proven byte-for-byte against the official vectors in
  `Eip712OrderSignerVectorTest`. A Deposit Wallet (signature type 3) signs the ERC-7739
  `TypedDataSign` wrapper under the exchange's own domain, not the wallet's. Implemented by
  `com.polymarket.internal.trading.Eip712OrderSigner`; signing is offline and takes no port.
  `Trading` (issue #14) adds `submit`/`place` over the `OrderSubmitter` **port**. Both take the
  resolved Order Intent, and `OrderPlacement.forIntent` derives order type, Maker-Only and GTD
  expiration from it, so a hand-built placement that contradicts its intent sends nothing.
  Every `POST /order` outcome is classified as
  `SubmissionOutcome.Accepted`/`Rejected`/`Unknown` — a documented
  4xx/duplicate/5xx-"order timed out" is a definitive `Rejected`, transport loss and a
  contradictory or malformed success are `Unknown` (a 200 body that is not an object carrying a
  boolean `success` states nothing, so it is never a rejection), and nothing is ever silently replayed
  (`HttpRuntime.post` executes exactly once). A `PositionId` order is rejected before any request:
  V3 Combo orders route through the RFQ Builder Gateway (issues #25/#26), not `POST /order`.
  Implemented by `com.polymarket.internal.trading.TradingGateway`.
  Immediate planning measures affordability at the price the order is actually signed at, not at
  blended book prices, so a BUY's encoded leg plus quoted fee can never exceed its budget; share
  quantities truncate to the tick profile's documented size decimals, and `ImmediatePlan.cost` is the
  leg the order carries (the most a BUY spends, the least a SELL receives) on both sides.
  `GoodTilDateOrder` is a final class, not a record: `expiringAt(..., clock)` is the only way to
  build one, so an unvalidated lifetime cannot exist.
  `Trading.reconcile` (issue #16) takes a `SigningIdentity`, because the L2 `POLY_ADDRESS` header
  carries the Account Signer while the required `maker_address` filter carries the Trading Wallet —
  they coincide only for an EOA. It polls `GET /data/trades?id=` (one request per trade ID — the
  filter has no batch form) via the `TradeReader` **port** until every ID reaches the terminal
  `TradeStatus.Known.CONFIRMED`/`FAILED`, so a delayed transaction hash just shows up on a later
  poll. The deadline is passed into `TradeReader.byIds`, so a multi-page, multi-id walk stops when
  the caller's time is spent (the first attempt always goes out), and `Pending` reports the records
  the last read observed, keeping missing, MATCHED, MINED, RETRYING and an unrecognised status
  distinguishable. A missing or non-terminal record keeps polling; an unrecognised status is kept as its
  `raw()` text and treated as non-terminal. A local deadline yields `ReconciliationOutcome.Pending`
  (order and trade IDs preserved) rather than a reported failure. Implemented by
  `com.polymarket.internal.trading.TradeReaderGateway`.
  `Trading.submitBatch`/`cancel` (issue #17) add the `OrderBatch` **port**: official limits
  (15 orders, 1000 cancel IDs) and blank/duplicate cancel IDs are rejected before any request, one
  logical batch is exactly one `POST /orders`/`DELETE /orders`, and a batch is never silently
  chunked. Per-item outcomes are attached **positionally** — the wire array carries no per-item
  ID — and only when the response is a same-length array *and every element is a documented order
  object*; any mismatch, malformed element, unparseable body, or transport failure is
  `BatchSubmissionOutcome.Indeterminate` rather than inventing which item succeeded.
  `CancellationOutcome` is sealed `Completed`/`Uncertain`: transport loss, a non-success status and
  a malformed success are uncertain rather than thrown, and `Completed` keeps `canceled`,
  `notCanceled` (server-stated) and `unaccounted` (never mentioned) as three distinct facts.
  Cancel IDs are checked for the 0x-hex shape the official examples share, but not for a length —
  the spec pins no `pattern` and its own examples disagree (40 vs 64 hex).
  Also implemented by `com.polymarket.internal.trading.TradingGateway`.
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
  `parentEntityType` degrades to `Optional.empty()` rather than failing the read. A comment thread
  has no documented ceiling either, so `commentsById` takes a `CommentPage` like every other read. `Markets.search()`
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
  `status`/`accept`/`awaitSettlement` over the `RfqDirectory` **port**, plus Combo leg discovery
  through the `ComboMarketCatalog` **port**, reached at a caller-supplied
  gateway host (issued per builder onboarding, so it is not one of `PolymarketConfig`'s fixed
  hosts). `request` and `accept` each sign with two independent HMAC header sets — account
  (`L2Attestation`) and builder (`POLY_BUILDER_*`, the same HMAC primitive, now `public` on
  `L2Attestation`). `RfqOutcome` is sealed (`Quoted`/`Confirmed`/`Waiting`/`Failed`/`Expired`/
  `Canceled`/`Pending`/`Unknown`/`NotYetAccepted`/`Rejected`); a business failure (no quote, maker
  decline, execution
  failure) is wire-indistinguishable as anything but status `FAILED` with a free-text nested
  error, so those three stay one `Failed(reason)` case rather than an invented error-code
  schema, and `CONFIRMED`/`FILLED` collapse into one `Confirmed` case matching how the official
  fixture itself groups them as `"success"`. The Quote arrives inline on the create
  response; a status read *before* acceptance is the gateway's documented HTTP 409, surfaced as
  `NotYetAccepted`. `awaitSettlement` therefore polls only after acceptance, mirroring
  `Trading.reconcile`'s injected-`Clock` loop; a local timeout is `Pending`, never a reported
  failure. HTTP validation (`Rejected`) and the business state machine stay separate axes, and the
  durable `rfqId` survives every uncertain path.
  `accept` takes no caller-supplied signer — the root wires the SDK's own — and refuses a
  `SigningContext` whose identity is not the `requestedBy` the Quote was priced for. It rejects an
  expired quote before sending, signs `quote.comboPositionId()` through the
  V3 path with `context.withBuilder(quote.builderCode())` (the official rule: "order.builder
  must equal the returned builder_code"), checks the signed order against the quote before sending,
  and never throws or replays on transport failure — a
  connection loss becomes `Unknown(rfqId, ...)`, the durable handle for a later status poll.
  Direction, amounts, Combo position and deadline all ride on `RfqOutcome.Quoted`, so acceptance
  cannot contradict the request; a payload missing any of them — or the quote id, builder code,
  expiry or legs — is `Unknown`, never a guessed BUY, a zero amount or an epoch-zero deadline.
  `awaitSettlement` clamps its sleep to the remaining time like `Trading.reconcile`, and a
  `Confirmed` keeps the status read's `tx_hash` alongside the acceptance's `taker_order_hash`. `expires_at` and `builder_code` are read top level and the Combo position from
  `request.yes_position_id`, as `builder-gateway.json` pins them. `signer_address` follows the
  wallet type (Trading Wallet only for signature type 3); `POLY_ADDRESS` is always the Account
  Signer. Leg discovery reads the official `GET /v1/rfq/combo-markets` catalog — no local CTF
  computation. Implemented by `com.polymarket.internal.rfq.RfqGateway` and `ComboMarketGateway`.
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
  Every RTDS event carries `observedAt`, the envelope time the stream saw it, distinct from the
  payload's own timestamp. `RtdsTransport` is `AutoCloseable`, so closing the capability releases
  the scheduler, dispatcher and connection pool behind the socket, and dispatch delivers nothing
  once closed. Both WebSocket hosts and the connect timeout come from `PolymarketConfig`
  (`streamHost`, `rtdsHost`), never hardcoded in the root.
  Implemented by `com.polymarket.internal.streaming.RtdsGateway`.
- Heartbeat (issue #24) — the interval is checked in milliseconds, the unit the schedule is
  expressed in, so a positive but sub-millisecond `Duration` is refused before any state changes and
  a scheduling failure restores the inactive state rather than stranding the flag.
  `Polymarket.startHeartbeat()`/`startHeartbeat(Duration)`/
  `stopHeartbeat()`/`isHeartbeatActive()` own the CLOB dead-man-switch tick; nothing ticks on
  construction. Each tick is a **bodyless** L2-signed `POST /heartbeats` (`sendHeartbeat` declares
  no `requestBody`) and a successful status is the whole acknowledgement — there is no
  `heartbeat_id` chain. That chain belongs to `POST /v1/heartbeats` (`sendHeartbeatV1`), which is
  in `clob-openapi.yaml` but absent from `llms.txt`; `heartbeat.json` pins both and marks the
  bodyless form current. No official page publishes a beat interval, so the SDK's 5 s default is a
  local choice. Start, stop and close are idempotent; a failed tick is logged and stays scheduled —
  dropping the schedule would have the exchange cancel the caller's open orders. Implemented by
  `com.polymarket.internal.operations.HeartbeatGateway`.

Rules that later tickets inherit: no OkHttp/Jackson/Web3j type in a public signature, no transport
import inside a public domain package (the gateway does the mapping), and **capabilities depend on
domain-declared ports, never on an internal adapter type**. Only `Polymarket`, the composition root,
wires the two sides together. Secrets redact in `toString`, and absent authority throws
`AuthenticationRequiredException` before anything reaches the wire.

Every reference component of every shipped public record carries Lombok `@NonNull`, so a public
model is valid by construction: a malformed wire frame is dropped rather than mapped into an event
with null required fields, and absence is always `Optional`, never null.

`PublicBoundaryTest` enforces those rules with ArchUnit (issue #6). With the 1.0 facade gone there is
nothing left to exempt, so the rules cover **everything outside `com.polymarket.internal..`** rather
than a hand-maintained package list — a new bounded context is guarded the day you create it. Two
exemptions are deliberate: `Polymarket` (composition root) may import `internal`, and
`PrivateKeySigner`/`Addresses` may use Web3j because the JDK has no secp256k1 or keccak — they stay
bound by the public-signature rule. Each rule is proven to fail against a test-only fixture in
`src/test/java/com/polymarket/operations/*Leak.java`.

**No direct-chain behavior (issue #7).** The SDK authorizes routed API requests and never broadcasts a Polygon
transaction: no RPC, CTF client or ID computation, gas/receipt models, split/merge/redeem, or collateral return.
Web3j is a signing-only dependency (`org.web3j:crypto`, no `core`); reintroducing `org.web3j.protocol` or
`org.web3j.tx` fails `DirectChainSurfaceTest`.

**Dependency contract (issue #29).** 2.0.0 ships only OkHttp, Jackson, `org.web3j:crypto` and `slf4j-api` at compile
scope and imposes no logging backend — logback is `test` scope, and `commons-lang3` plus the dead direct
`bcprov-jdk15on` are gone (web3j already brings `bcprov-jdk18on`). `maven-enforcer-plugin` gates `verify` with
`dependencyConvergence`/`banDuplicatePomDependencyVersions`; conflicts are fixed by `<dependencyManagement>` pins,
never exclusions.

## Configuration

**Configuration is caller-supplied only (issue #8).** The SDK loads no property file, reads no secret
file, prints no configuration, and ships no HTTP proxy support — hosts and timeouts reach the SDK
through `PolymarketConfig` and credentials through `SigningAuthority`/`ApiCredentials`, and nowhere
else. Construction performs no network call and derives no credential. Every `PolymarketConfig`
mutator rejects null, and the WebSocket hosts (`streamHost`, `rtdsHost`) sit there beside the REST
ones. `HttpRuntime` refuses a request once closed, so a capability handed out before
`Polymarket.close()` cannot outlive the root's transport, and it honours both legal `Retry-After`
forms — delay-seconds and an HTTP-date measured from when the response arrived.

**Two-level CLOB authentication.** Both header sets are built in
`com.polymarket.internal.authentication` and are never public:
1. **L1 (EIP-712)** — `L1Attestation` — API-key derivation/creation. Signs the fixed message
   `"This message attests that I control the given wallet"` under domain `ClobAuthDomain v1`.
   Headers: `POLY_ADDRESS`, `POLY_SIGNATURE`, `POLY_TIMESTAMP`, `POLY_NONCE`.
2. **L2 (HMAC-SHA256)** — `L2Attestation` — every authenticated read and write. Headers:
   `POLY_ADDRESS`, `POLY_SIGNATURE`, `POLY_TIMESTAMP`, `POLY_API_KEY`, `POLY_PASSPHRASE`. The same
   HMAC primitive signs the Builder Gateway's `POLY_BUILDER_*` set.

Both are held to digests and HMACs computed independently in `AttestationVectorTest`.

## Key Conventions

### Null rejection
- Lombok `@NonNull` on the parameter or record component, never `Objects.requireNonNull` in a body.
  The two exceptions are `BaseUnits.require` and `Social.requireNotBlank`, whose message names the
  caller's field rather than the parameter — the generated check would lose that name.

### Decimal arithmetic
- All prices and amounts use `BigDecimal`. Never use `double`/`float` for financial values.
- Collateral and share amounts use **6 decimal places** (pUSD standard); `BaseUnits.toBaseUnits`
  multiplies by `10^6` and uses `longValueExact()`, so a value that does not fit cannot be sent.
- **2.0 rejects rather than rounds.** A silently moved price or size is a different order, so there is
  no rounding helper on the public surface:
  - `BaseUnits.require` throws when a `PusdAmount`/`ShareQuantity` needs more than 6 decimals.
  - `MarketRules.requireOnGrid` throws for an off-grid price instead of snapping it to a tick.
  - `MarketRules.requireAtLeastMinimum` throws below the live CLOB minimum.
- **Tick sizes:** the supported grid is `0.1`, `0.01`, `0.005`, `0.0025`, `0.001`, `0.0001`, matched by
  numeric value so `"0.010"` resolves like `"0.01"`. There is NO fallback profile — an unrecognised tick throws
  before any amount is calculated, because signing against the wrong grid mis-prices every order on a
  `0.005`/`0.0025` market.
- **Minimum order size** comes from live CLOB `/book.min_order_size` in NORMALIZED SHARES and is
  compared against the normalized share quantity, never the caller's raw size. Gamma's documented
  minimum order **notional** is discovery metadata only and must never be substituted for it — the
  official sources still disagree on units.

### Model classes
- Prefer Java records for immutable data carriers; use `Optional` for a semantically absent singular
  value so missing stays distinct from zero, `false`, and empty text.
- Collections returned from public types are immutable.
- Public capability classes are `final`; public packages expose only SDK and JDK types.

### Chain IDs
| Network | ID |
|---|---|
| Polygon Mainnet | 137 |

Mainnet only. Amoy and any other undocumented signing network are out of scope for 2.0 (issue #1).

### Order types
`GTC` (resting), `GTD` (expires by date), `FOK` (all-or-nothing immediate), `FAK` (fill what's available).

### Signing compatibility

2.0 signs Exchange **V2** token orders and Exchange **V3** Combo position orders only; V1 signing and
dynamic version resolution are removed. Routing is the sealed `AssetId` type alone — never a string
heuristic. When touching `Eip712OrderSigner`, `L1Attestation`, or `L2Attestation`, verify against the
pinned official fixtures in `src/test/resources/protocol/` (`signing-vectors.json`,
`constraints.json`, `builder-gateway.json`) via `Eip712OrderSignerVectorTest`,
`ProtocolContractsTest`, and `AttestationVectorTest`. Those fixtures come from Polymarket's published
documentation and an independent signer — never from this SDK's own code.

### Testing conventions
- Framework: JUnit 5 + Mockito
- Test IDs follow `TC-XX-NNN` in `@DisplayName` (e.g., `TC-PC-001`)
- Unit tests use a well-known test private key: `ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80`
  — the same key the pinned protocol vectors are generated against.
- Prefer the highest seam: drive a public capability against MockWebServer and assert the typed
  outcome plus the exact outbound method, path, query, headers, and body. Keep a pure domain test
  only where no network seam can exercise the invariant.
- Verified baseline on Java 21 after the issue-#1 repair wave: `mvn clean verify` → **597 tests,
  0 failures, 0 errors, 0 skipped**, with the dependency gate clean and the README examples compiled
  against the packaged jar. The earlier 328-test baseline predates the
  2.0 repair waves. The drop from 1179 is
  the deleted 1.0 facade suite, not lost coverage of 2.0 behavior; the deletion also uncovered a
  dropped capability (CREATE2 wallet derivation), restored with its own golden-vector tests.
  `mvn -Plive test` selects the 6 checks in `LiveReadOnlyTest` and nothing else; each probes a
  documented, credential-free endpoint (`GET clob /time`, `GET gamma /tags?limit=1`,
  `GET data /trades?limit=1`), never an unpublished liveness path.

## Companion documents (issue #30)

- `docs/API_COVERAGE.md` — supported / not supported / out of scope per endpoint, by capability, with
  official doc URLs and a last-reviewed date. Update it in the same change that moves a line.
- `docs/MIGRATION.md` — the 1.0 → 2.0 map. No compatibility adapters exist, so this is the only bridge.
- `docs/WORKFLOW.md` — contributor workflow (GitHub issues + this file + the TDD conventions above).
- `README.md` — 2.0 only. Its examples live in `src/examples/java`, are compiled against the
  packaged JAR and its runtime dependencies during `verify` (not as test sources, and without
  Lombok, so they build the way a consumer's code does), and `ReadmeExamplesTest` asserts the README
  still shows exactly that source. `MigrationDocTest` checks every 2.0 replacement `MIGRATION.md`
  names actually exists, so a rename cannot silently break the migration map.

