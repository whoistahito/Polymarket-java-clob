# Migration — 1.0 → 2.0

2.0.0 is a deliberate break (issue #1). The 1.0 facade was deleted outright in issue #28: there are no
forwarding adapters, no deprecated shims, and no compatibility mode. This page is the map from what you
called to what replaces it. Everything 1.0 shipped is covered below; anything marked **Removed** has no
2.0 equivalent and is not coming back.

The last commit containing the 1.0 source is `352bb64` — `git show 352bb64:src/main/java/com/polymarket/client/PolymarketClient.java`
if you need to read the old behavior while porting.

Packages deleted whole: `com.polymarket.client`, `com.polymarket.model` (and `.model.data`, `.model.gamma`),
`com.polymarket.util`, `com.polymarket.ws` (and `.ws.model`), `com.polymarket.rtds`.

## Entry point

| 1.0 | 2.0 |
|---|---|
| `new PolymarketClient.Builder()…build()` | `Polymarket.withDefaults()` or `Polymarket.with(config, authority)` |
| `AsyncPolymarketClient.wrap(client)` | `AsyncTrading.wrap(sdk.trading()[, executor])` / `AsyncRfq.wrap(rfq[, executor])` — only trading and RFQ get an async form |
| `.clobHost(..)`, `.gammaHost(..)`, `.dataHost(..)`, `.maxRetries(..)` | `PolymarketConfig.defaults().clobHost(URI)…readRetryPolicy(ReadRetryPolicy)` — JDK types only |
| `.privateKey(hex)` / `.credentials(Credentials)` | `SigningAuthority.signing(PrivateKeySigner.of(hex), identity)` — Web3j never appears in a public signature |
| `.apiCreds(ApiKeyCreds)` | `SigningAuthority.apiOnly(new ApiCredentials(key, secret, passphrase))`, or `.withApiCredentials(..)` |
| `.signatureType(SignatureType)` + `.funderAddress(addr)` | `SigningIdentity.eoa/proxyWallet/safeWallet/depositWallet(..)` — the identity carries its own official signature type |
| `.chainId(137)` / `.chainId(Chain)` | Removed — mainnet 137 only |
| `.useServerTime(boolean)`, `.geoBlockToken(..)`, `.httpClient(HttpClient)` | Removed — see *Removed with no replacement* |
| `client.getAddress()` / `getFunderAddress()` / `getSignatureType()` | `authority.identity()` → `SigningIdentity.signer()`/`maker()`/`signatureType()` |
| `client.hasApiCreds()` / `getApiCreds()` | `authority.apiCredentials()` (an `Optional`) |
| `client.gamma()` / `client.data()` / `client.rfq()` | `sdk.markets()` + `Social`, `sdk.portfolio()`, `Rfq` — see the sections below |
| (none — 1.0 had no close) | `Polymarket` is `AutoCloseable`; `close()` stops heartbeats, streams and the HTTP runtime, idempotently |

## Authentication and wallets

| 1.0 | 2.0 |
|---|---|
| `client.createApiKey()` / `createApiKey(nonce)` | `sdk.authentication().createApiKey()` — the nonce overload is gone |
| `client.deriveApiKey()` / `deriveApiKey(nonce)` | `sdk.authentication().deriveApiKey()` |
| `client.createOrDeriveApiKey()` | Removed — call `createApiKey()` or `deriveApiKey()` deliberately |
| `client.getApiKeys()` (raw map) | `sdk.authentication().apiKeys()` → `List<ApiKey>` (redacted) |
| `client.deleteApiKey()` (raw map) | `sdk.authentication().deleteApiKey()` → `ApiKeyDeletion` |
| `client.getClosedOnlyMode()` → `BanStatus` | `sdk.authentication().validate()` → `ApiKeyValidation` |
| `model.ApiKeyCreds` | `com.polymarket.authentication.ApiCredentials` (record; `toString` fully redacts) |
| `model.SignatureType` | `SigningIdentity` (sealed: `Eoa`/`ProxyWallet`/`SafeWallet`/`DepositWallet`) |
| `util.WalletUtils.deriveProxyWallet(eoa, chainId)` | `SigningIdentity.deriveProxyWallet(eoa)` → `ProxyWallet`; chain id is implicit (137) and it returns the identity, not an `Optional<String>` |
| `util.WalletUtils.deriveSafeWallet(eoa, chainId)` | `SigningIdentity.deriveSafeWallet(eoa)` → `SafeWallet` |
| `client.L1Eip712Signer` / `L2HmacSigner` | Internal only (`com.polymarket.internal.authentication.L1Attestation`/`L2Attestation`) — no public sign/hash helpers |
| `client.createReadonlyApiKey()` / `getReadonlyApiKeys()` / `deleteReadonlyApiKey(key)` / `validateReadonlyApiKey(..)` | Removed |

## Market discovery

`GammaClient` split in two: market/event discovery is `Markets`, people-facing reads are `Social`.

| 1.0 | 2.0 |
|---|---|
| `gamma.events(EventsRequest)` | `sdk.markets().events(EventQuery)` |
| `gamma.eventBySlug(EventBySlugRequest)` | `sdk.markets().eventBySlug(slug)` → `Optional<DiscoveredEvent>` |
| `gamma.markets(MarketsRequest)` | `sdk.markets().markets(MarketQuery)` |
| `gamma.marketById(MarketByIdRequest)` | `sdk.markets().market(id)` → `Optional<DiscoveredMarket>` |
| `gamma.tags(TagsRequest)` | `sdk.markets().tags(limit)` — a `limit` is now required |
| `gamma.seriesList(SeriesListRequest)` | `sdk.markets().series(limit)` |
| `gamma.sports()` | `sdk.markets().sports()` |
| `gamma.search(SearchRequest)` | `sdk.markets().search(query)` for events/tags; `Social.search(SearchQuery)` for profiles |
| `gamma.publicProfile(..)` | `Social.profile(address)` |
| `gamma.comments(..)` / `commentsById(..)` / `commentsByUserAddress(..)` | `Social.comments(CommentQuery)` / `commentsById(id, page)` / `commentsByUserAddress(address, page)` |
| `gamma.status()` | `sdk.health()` → `List<ServiceHealth>` |
| `gamma.eventById/eventTags/eventsKeyset/marketBySlug/marketTags/marketsKeyset/seriesById` | Removed |
| `gamma.tagById/tagBySlug/relatedTagsById/relatedTagsBySlug/tagsRelatedToTagById/tagsRelatedToTagBySlug` | Removed |
| `gamma.teams(..)` / `gamma.sportsMarketTypes()` | Removed |
| `client.getGammaMarkets(Map)` | `sdk.markets().markets(MarketQuery)` — no raw maps |
| `model.gamma.*`, `model.Market`, `model.GammaMarket`, `model.Token` | `DiscoveredEvent`, `DiscoveredMarket`, `MarketOutcome`, `MarketState`, `MarketPricing`, `MarketMetadata`, `MarketTag`, `MarketSeries`, `Sport` — one semantic market model |
| `client.getMarkets(cursor)` / `getMarket(conditionId)` / `getSimplifiedMarkets` / `getSamplingMarkets` / `getSamplingSimplifiedMarkets` (CLOB, raw maps) | Removed — Gamma is the single discovery source |

## Order books and market rules

| 1.0 | 2.0 |
|---|---|
| `client.getOrderBook(tokenId)` → `OrderBookSummary` | `sdk.orderBooks().book(new TokenId(id))` → `Optional<OrderBookSnapshot>` |
| `client.getOrderBooks(List<BookParams>)` | `sdk.orderBooks().books(List<TokenId>)` |
| `client.getMarketRules(conditionId)` | `OrderBookSnapshot.rules()` — one `GET /book` supplies tick, minimum shares and neg-risk |
| `client.getTickSize(tokenId)` (+ `clearTickSizeCache`) | `snapshot.rules().tickSize()` — no hidden cache to invalidate |
| `client.getNegRisk(tokenId)` | `snapshot.rules().negativeRisk()` |
| `client.getFeeRateBps(tokenId)` | `FeeRate.ofBasisPoints(bps)` — 1.0 read a per-token endpoint; 2.0 takes the rate as an input to `ImmediateBuy` budgeting |
| `client.getOrderBookHash(..)` / `PriceUtils.generateOrderBookSummaryHash(..)` | `OrderBookSnapshot.hash()` — the exchange's own hash, not a locally recomputed one |
| `client.getMidpoint(s)`, `getPrice(s)`, `getSpread(s)`, `getLastTradePrice(s)` | Removed as REST reads — derive from `OrderBookSnapshot.bestBid()`/`bestAsk()`; last trade is on `snapshot.lastTradePrice()` and streams as `LastTradePriceEvent` |
| `client.getPricesHistory(..)`, `getMarketTradesEvents(..)` | Removed |
| `model.OrderBookSummary`, `OrderSummary`, `BookParams`, `SpreadResult`, `LastTradePriceResult`, `MarketPrice`, `MarketRules` | `OrderBookSnapshot`, `PriceLevel`, `TokenId`, `MarketRules` (in `com.polymarket.markets`) |

## Trading

| 1.0 | 2.0 |
|---|---|
| `client.createOrder(UserOrder, CreateOrderOptions)` | `sdk.trading().sign(assetId, side, price, shares, rules, SigningContext)` |
| `client.createMarketOrder(UserMarketOrder, ..)` | `ImmediatePlanner` over a live book → `ImmediateBuy`/`ImmediateSell`, then `sign` |
| `client.postOrder(SignedOrder, OrderType)` / `postOrder(Map)` / `postOrder(PostOrderPayload)` | `sdk.trading().submit(signedOrder, OrderPlacement.of(credentials, OrderType.GTC))` |
| `client.createAndPostOrder(..)` | `sdk.trading().place(orderExecution, context, credentials)` — the Order Intent carries Maker-Only and GTD, so no placement is restated |
| `client.submitOrder(..)` → `OrderSubmission` | `SubmissionOutcome` (sealed: `Accepted`/`Rejected`/`Unknown`) — the separate throwing `postOrder` is gone |
| `client.postOrders(..)` / `createAndPostOrders(..)` | `sdk.trading().submitBatch(List<BatchItem>)` → `BatchSubmissionOutcome`; the 15-order limit is checked before anything is sent and a batch is never silently split |
| `client.cancelOrder(id)` / `cancelOrders(ids)` (raw maps) | `sdk.trading().cancel(credentials, accountSigner, List.of(ids))` → sealed `CancellationOutcome` (`Completed`/`Uncertain`); it no longer throws on transport loss |
| `client.cancelAll()` / `cancelMarketOrders(..)` | Removed — an unbounded write whose per-order result cannot be reported |
| `client.getOrder(id)` / `getOpenOrders(..)` / `getOpenOrdersPaginated(..)` | Removed |
| `client.getTrades(..)` / `getTradesPaginated(..)` | `sdk.portfolio().trades(TradeQuery[, PageCursor])`; for settling one order use `sdk.trading().reconcile(..)` |
| (1.0 read `transactionHash` off the post response) | `sdk.trading().reconcile(credentials, identity, orderId, tradeIds, timeout, pollInterval)` → `ReconciliationOutcome` — hashes now arrive late, and the `SigningIdentity` separates the Account Signer header from the Trading Wallet filter |
| `client.isOrderScoring(..)` / `areOrdersScoring(..)` | Removed |
| `client.getBalanceAllowance(..)` / `updateBalanceAllowance(..)` | Removed |
| `client.calculateMarketPrice(..)` | `ImmediatePlanner` — pure depth walking with a caller-supplied protection bound |
| `client.resolveVersion()` / `getCachedVersion()` / `clearVersionCache()` / `OrderBuilder.setVersion/getVersion` | Removed — routing is the sealed `AssetId` type: `TokenId` → Exchange V2, `PositionId` → V3 |
| `client.INITIAL_CURSOR` / `END_CURSOR` constants | `PageCursor` / `RewardCursor` / `BuilderCursor` handle start and end internally |
| `model.UserOrder`, `UserMarketOrder`, `CreateOrderOptions`, `OrderData`, `OrderDataV2`, `PostOrderPayload` | `LimitOrder`, `MakerOnlyLimitOrder`, `GoodTilDateOrder`, `ImmediateBuy`, `ImmediateSell`, `SigningContext`, `OrderPlacement` |
| `model.OrderResponse`, `OrderSubmission`, `OrderSubmissionStatus`, `OrderStatusType` | `SubmissionOutcome`, `BatchSubmissionOutcome`, `BatchItem`, `CancellationOutcome` |
| `model.Trade`, `TradeParams`, `TradeStatusType`, `TraderSide` | `SettledTrade`, `TradeStatus`, `TradeQuery`, `TradedSide` |
| `model.Side`, `model.OrderType` | `com.polymarket.trading.Side`, `com.polymarket.trading.OrderType` |
| `model.SignedOrder`, `SignedOrderSerializer` | `com.polymarket.trading.SignedOrder` (serialization is internal) |
| `model.PaginationPayload<T>` | `PortfolioPage<T>` / `RewardPage<T>` / `BuilderTradePage` |

### Order signing helpers

| 1.0 | 2.0 |
|---|---|
| `client.OrderBuilder` | `com.polymarket.trading.OrderSigner` (port) + `SigningContext`; the implementation is `internal.trading.Eip712OrderSigner` |
| `util.OrderUtils.buildSignedOrder(OrderData)` (V1) | Removed — V1 signing is out of scope |
| `util.OrderUtils.buildSignedOrderV2(OrderDataV2, negRisk)` | `sdk.trading().sign(new TokenId(..), ..)` |
| `util.OrderUtils.exchangeAddress/exchangeAddressV2`, `OrderBuilder.resolveVerifyingContract` | Internal — contract selection follows from the asset type and `rules.negativeRisk()` |
| `util.OrderUtils.buildV2DomainHash/buildV2StructHash/signPoly1271` | Internal — no public hash or 1271 helpers |
| `OrderBuilder.getMakerAddress/getSignerAddress/getSignatureTypeValue` | `SigningIdentity.maker()`/`signer()`/`signatureType()` |

### `util.PriceUtils` — removed, and what replaces each piece

`PriceUtils` is gone entirely. 2.0 rejects rather than rounds, so most of it had no honest 2.0 form:

| 1.0 helper | 2.0 |
|---|---|
| `tickRound(price, tickSize, mode)` | `MarketRules.requireOnGrid(Price)` — **throws** instead of snapping; a moved price is a different order |
| `priceValid(price, tickSize)` / `isValidPrice(price)` | `TickSize.isOnGrid(Price)`; `Price.of(..)` already rejects anything outside the probability range |
| `decimalPlaces(tickSize)` / `isTickSizeSmaller(a, b)` | `TickSize.of(..).value()` — a `BigDecimal` you can compare directly; an unrecognised tick throws |
| `feeRateFromBps(bps)` / `calculateRequiredAmount(price, size)` | `FeeRate.ofBasisPoints(bps)` and `FeeRate.feeOn(notional)` |
| `safeBigDecimal(..)` (silent default on bad input) | Removed — `Price`/`PusdAmount`/`ShareQuantity` reject invalid text at construction |
| `round(..)`, `clamp(..)`, `min/max`, `equalWithinTolerance(..)`, `MATH_CONTEXT` | Removed — plain `BigDecimal` API; the SDK never clamps a financial value |
| `formatPrice/formatMoney/formatPercentage` | Removed — presentation is the caller's |
| `generateOrderBookSummaryHash(..)` | `OrderBookSnapshot.hash()` |
| `TEN_THOUSAND` | Removed |
| (1.0 had no base-unit guard) | `PusdAmount.of(..)` / `ShareQuantity.of(..)` reject anything needing more than 6 decimals, so an unsendable amount cannot be constructed |

## Portfolio, rewards, builders

| 1.0 | 2.0 |
|---|---|
| `data.positions(DataPositionsRequest)` / `positionsPaginated(..)` | `sdk.portfolio().positions(PositionQuery[, PageCursor])` → `PortfolioPage<PositionSnapshot>` |
| `data.trades(DataTradesRequest)` | `sdk.portfolio().trades(TradeQuery[, PageCursor])` |
| (1.0 had no activity read) | `sdk.portfolio().activity(ActivityQuery[, PageCursor])` |
| `client.getNotifications()` | `sdk.portfolio().notifications()` |
| `client.dropNotifications(..)` | Removed |
| `model.data.DataPosition/DataTrade/DataSide/FilterType` | `PositionSnapshot`, `PositionValuation`, `TradeRecord`, `ActivityRecord`, `MarketReference`, `Side`, `TradedSide` |
| `client.getCurrentRewards()` | `sdk.rewards().currentRewards([cursor])` |
| `client.getRawRewardsForMarket(conditionId)` | `sdk.rewards().marketRewards(conditionId[, cursor])`, or `allMarketRewards(conditionId)` |
| `client.getEarningsForUserForDay(date)` | `sdk.rewards().earnings(LocalDate[, cursor])` |
| `client.getTotalEarningsForUserForDay(date)` | `sdk.rewards().totalEarnings(LocalDate)` |
| `client.getUserEarningsAndMarketsConfig(..)` | `sdk.rewards().userRewardedMarkets(LocalDate[, cursor])` |
| `client.getRewardPercentages()` | `sdk.rewards().rewardPercentages()` |
| `model.MarketReward`, `UserEarning`, `TotalUserEarning`, `UserRewardsEarning` | `RewardedMarket`, `CurrentMarketRewards`, `RewardConfig`, `ScoringRules`, `MarketMetrics`, `RewardToken`, `UserEarning`, `AssetEarning`, `UserRewardedMarket` |
| `client.createBuilderApiKey()` / `getBuilderApiKeys()` / `revokeBuilderApiKey()` | `Builders.createCredentials()` / `listCredentials()` / `revokeCredentials()` |
| `client.getBuilderTrades(..)` | `Builders.trades([BuilderTradeQuery][, BuilderCursor])` |
| `model.BuilderApiKey`, `BuilderApiKeyResponse`, `BuilderTrade` | `BuilderCredentials` (redacted), `BuilderCredentialSummary`, `BuilderCredentialRevocation`, `BuilderTrade`, `BuilderTradePage` |

`Builders` is on the `Polymarket` root: `sdk.builders()`.

## RFQ

1.0's `RfqClient` spoke the legacy CLOB RFQ endpoints and covered both sides of the auction. 2.0 keeps
only the **requester** flow, over the Builder Gateway (issues #25/#26).

| 1.0 | 2.0 |
|---|---|
| `rfq.createRfqRequest(RfqUserOrder, tickSize)` | `Rfq.request(RfqRequest, SigningIdentity, ..)` → `RfqOutcome` |
| `rfq.getRfqRequests(..)` | `Rfq.status(rfqId, ..)` (valid only after acceptance — before it, the gateway answers 409 as `NotYetAccepted`), or `Rfq.awaitSettlement(..)` for the poll loop |
| `rfq.getRfqBestQuote(..)` / `getRfqRequesterQuotes(..)` | `RfqOutcome.Quoted`, returned inline on `Rfq.request(..)` — there is no quote to poll for |
| `rfq.acceptRfqQuote(AcceptQuoteParams)` | `Rfq.accept(quoted, signer, ..)` — the direction comes from the Quote, and an expired quote is refused before sending |
| `rfq.cancelRfqRequest(..)` / `cancelRfqQuote(..)` / `createRfqQuote(..)` / `getRfqQuoterQuotes(..)` / `approveRfqOrder(..)` / `rfqConfig()` | Removed — maker/quoter behavior is out of scope (issue #1) |
| `AsyncRfqClient` | `com.polymarket.rfq.AsyncRfq` |
| `model.RfqRequest`, `RfqQuote`, `RfqUserOrder`, `RfqUserQuote`, `RfqMatchType`, `RfqPaginatedResponse`, `*Params` | `RfqRequest` (sealed `Buy`/`Sell`), `RfqStatus`, `RfqOutcome` (sealed) |

`Rfq` is on the `Polymarket` root, but takes the gateway host issued per builder onboarding:
`sdk.rfq(gatewayHost)`. The root owns each one and closes it with itself.

## Streaming

| 1.0 | 2.0 |
|---|---|
| `WsClient.builder()…build()` | `sdk.streaming()` |
| `ws.subscribeMarket(assetIds)` / `unsubscribeMarket(..)` | `sdk.streaming().subscribeMarket(List<String>)` / `unsubscribeMarket(..)` |
| `ws.subscribeMarket(assetIds, customFeatures)` | Removed — the flag has no counterpart in the current channel |
| `ws.subscribeUser(markets)` / `unsubscribeUser(..)` | `sdk.streaming().subscribeUser(..)` — fails fast on missing L2 credentials, before a socket opens |
| `ws.onBookUpdate/onPriceChange/onLastTradePrice/onTickSizeChange/onOrder/onTrade` (void) | Same names on `Streaming`, each returning a closeable `Registration` |
| `ws.onMidpointUpdate/onBestBidAsk/onNewMarket/onMarketResolved/onUserEvent` | Removed |
| `ws.getMarketWebSocket()` / `getUserWebSocket()` | Removed — no raw OkHttp `WebSocket` on the public surface |
| `ws.isMarketConnected()` / `isUserConnected()` / `getConnectionState(ChannelType)` | `StreamLifecycleListener` via `addLifecycleListener(..)` |
| `ws.getConnectionGeneration(ChannelType)` | `marketGeneration()` / `userGeneration()` — per channel, so a user reconnect leaves the market channel alone |
| `ws.getSubscribedAssetIds()` / `getSubscribedMarkets()` / `getSubscriptionCount()` | `subscribedAssetIds()` / `subscribedMarkets()` |
| `ws.model.*` (`BookUpdate`, `PriceChange`, `TradeMessage`, …) and Jackson `JsonNode` payloads | `BookEvent`, `BookLevel`, `PriceChangeEvent`, `PriceChangeEntry`, `LastTradePriceEvent`, `TickSizeChangeEvent`, `OrderEvent`, `TradeEvent`, `MakerOrder` — records, no Jackson types |
| `ws.ChannelType` / `ConnectionState` / `WsMessageListener` | `StreamChannel`, `StreamLifecycleListener` |
| `RtdsClient.builder()…build()` | `com.polymarket.streaming.Rtds` |
| `rtds.subscribeCryptoPrices(symbols)` | `rtds.subscribeBinancePrices(symbols)` / `subscribeChainlinkPrices(symbols)` — the two feeds are separate types |
| `rtds.subscribeComments(CommentType)` | `rtds.subscribeComments(CommentEventType[, RtdsEntityType, entityId])` |
| `rtds.subscribeCommentsAuthenticated(..)` | Removed — the authenticated RTDS topics no longer exist upstream |
| `rtds.subscribe(Subscription)` (arbitrary frame) | Removed — no generic JSON topic |
| `RtdsListener`, `RtdsMessage`, `Subscription`, `CommentType` | `RtdsEventSink`, `RtdsLifecycleListener`, `CommentSubscription`, `CommentEventType`, `RtdsEntityType`, and the typed `*Event` records |

## Operations

| 1.0 | 2.0 |
|---|---|
| `client.getServerTime()` → `long` | `sdk.serverTime()` → `ServerTime` (an `Instant`) |
| `client.getOk()` (raw map) | `sdk.health()` → `List<ServiceHealth>` — an unreachable service is reported, not thrown |
| (1.0 had `.geoBlockToken(..)` only) | `sdk.geoblock()` → `GeoblockStatus` |
| `client.startHeartbeats()` / `startHeartbeats(ms)` | `sdk.startHeartbeat()` / `startHeartbeat(Duration)` |
| `client.stopHeartbeats()` / `isHeartbeatsActive()` | `sdk.stopHeartbeat()` / `isHeartbeatActive()` |
| `client.postHeartbeat(id)` | Removed — the id now chains internally. 1.0's first tick sent the literal string `"null"`; 2.0 sends `""`. |
| `client.HttpClient`, `HttpStatusException`, `PolymarketEndpoints` | Internal — no raw HTTP, no public endpoint constants |

## Removed with no replacement

Each of these is on issue #1's Out of Scope list, so it will not return in 2.x. See
[API_COVERAGE.md](API_COVERAGE.md) for the endpoint-level detail.

- **Amoy / testnet.** `model.Chain`, `.chainId(80002)` and every non-137 signing network. Mainnet only.
- **V1 orders.** `OrderUtils.buildSignedOrder(OrderData)`, `model.OrderData`, the V1 `nonce`/`feeRateBps`/
  `taker` fields, and dynamic protocol-version resolution. 2.0 signs Exchange V2 (tokens) and V3 (Combo
  positions) only, routed from the sealed `AssetId` type.
- **Direct chain access.** Any Polygon RPC, CTF condition/collection/position ID computation, gas and
  receipt models, split/merge/redeem, collateral return, and Relayer envelopes. `DirectChainSurfaceTest`
  fails the build if `org.web3j.protocol` or `org.web3j.tx` reappears; Web3j is a signing-only dependency.
- **Wallet deployment and approvals.** A routed Combo request needs an already-deployed, already-approved
  wallet. `SigningIdentity.deriveProxyWallet`/`.deriveSafeWallet` compute an address locally — they do not
  deploy anything.
- **RFQ maker/quoter.** All quote-submitting REST calls and the Quoter Gateway WebSocket.
- **Bridge and Perps APIs.** Never present in 1.0 either; listed here so the boundary is explicit.
- **Transport escape hatches.** Raw HTTP methods, raw `Map` payloads, public endpoint constants, the
  underlying OkHttp `WebSocket`, arbitrary JSON frames, `.httpClient(HttpClient)`, and HTTP proxy support.
- **Ambient configuration.** Property-file and secret-file loading, configuration printing, bot strategy
  config, and `.useServerTime(boolean)`. Everything reaches the SDK through `PolymarketConfig` and
  `SigningAuthority`, and construction performs no network call.
- **`util.JsonEmbeddedListDeserializer`.** Internal parsing detail with no public counterpart.

## Behavior changes worth re-reading your code for

These compile fine after a mechanical port but mean something different at runtime:

1. **Rejection replaces rounding.** An off-grid price or an under-minimum size now throws
   (`MarketRules.requireOnGrid` / `requireAtLeastMinimum`) instead of being adjusted for you.
2. **Order submission is never retried.** `HttpRuntime.post` executes exactly once; a lost response is
   `SubmissionOutcome.Unknown`, not a silent replay. Read retries no longer touch writes.
3. **Fills settle through trade IDs.** A transaction hash is often absent from the `POST /order`
   response — use `Trading.reconcile(..)`.
4. **Batches are one request or none.** Over the official limit, nothing is sent; per-item results are
   positional and become `Indeterminate` rather than being guessed.
5. **Missing stays missing.** Every non-identity field is `Optional`; a zero position size survives
   untouched instead of being clamped away.
6. **Signing rules come from the live book, per placement.** There is no tick-size cache to clear, and
   Gamma's minimum-order notional is never substituted for the CLOB minimum shares.
7. **Nothing ticks until you ask.** Heartbeats start only on `startHeartbeat()`; `close()` stops
   heartbeats and streams idempotently.
