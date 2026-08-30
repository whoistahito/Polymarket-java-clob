# API Coverage — 2.0.0

What this SDK actually calls, grouped by 2.0 capability. Anything not marked **Supported** is not
reachable through the public API: there is no raw-HTTP escape hatch to work around a gap.

- **Last reviewed: 2026-08-24.** Every row was re-derived from `src/main/java/com/polymarket/` and its
  URL checked against `https://docs.polymarket.com/sitemap.xml`; the credential-free reads were
  confirmed against production by `mvn -Plive test`. The official inventory this release was designed
  against was pinned on 2026-08-16 (issue #1).
- **Status:** `Supported` — a public capability method calls it · `Not supported` — a real documented
  endpoint left unimplemented · `Out of scope` — on issue #1's Out of Scope list, so not coming in 2.x.
- Hosts come from `PolymarketConfig`: CLOB `https://clob.polymarket.com`, Gamma
  `https://gamma-api.polymarket.com`, Data `https://data-api.polymarket.com`, geoblock
  `https://polymarket.com`. The RFQ Builder Gateway host is issued per builder onboarding, so the
  caller supplies it.
- Endpoints with no dedicated documentation page are cited against the canonical spec,
  `https://docs.polymarket.com/api-spec/clob-openapi.yaml`.

## Authentication — `Polymarket.authentication()`

| Operation | Endpoint | Docs | Status |
|---|---|---|---|
| `createApiKey()` | `POST clob /auth/api-key` (L1) | https://docs.polymarket.com/api-spec/clob-openapi.yaml | Supported |
| `deriveApiKey()` | `GET clob /auth/derive-api-key` (L1) | https://docs.polymarket.com/api-spec/clob-openapi.yaml | Supported |
| `apiKeys()` | `GET clob /auth/api-keys` (L1) | https://docs.polymarket.com/api-spec/clob-openapi.yaml | Supported |
| `validate()` | `GET clob /auth/ban-status/closed-only` (L2) | https://docs.polymarket.com/api-spec/clob-openapi.yaml | Supported |
| `deleteApiKey()` | `DELETE clob /auth/api-key` (L2) | https://docs.polymarket.com/api-spec/clob-openapi.yaml | Supported |
| `SigningIdentity.deriveProxyWallet` / `.deriveSafeWallet` | none — local CREATE2, no RPC | https://docs.polymarket.com/resources/contracts | Supported (offline) |
| Signing-identity background (EOA / Proxy / Safe / Deposit) | — | https://docs.polymarket.com/trading/wallets-auth | Supported (all four) |
| Relayer API keys | `GET /relayer-api-keys` | https://docs.polymarket.com/api-reference/relayer-api-keys/get-all-relayer-api-keys | Out of scope (Relayer) |
| Proxy wallet create/delete | `POST/DELETE /proxy` | https://docs.polymarket.com/api-reference/create-proxy | Out of scope (wallet deployment) |

## Markets — `Polymarket.markets()`

| Operation | Endpoint | Docs | Status |
|---|---|---|---|
| `events(EventQuery)` | `GET gamma /events` | https://docs.polymarket.com/api-reference/events/list-events | Supported |
| `eventBySlug(slug)` | `GET gamma /events/slug/{slug}` | https://docs.polymarket.com/api-reference/events/get-event-by-slug | Supported |
| `markets(MarketQuery)` | `GET gamma /markets` | https://docs.polymarket.com/api-reference/markets/list-markets | Supported |
| `market(id)` | `GET gamma /markets/{id}` | https://docs.polymarket.com/api-reference/markets/get-market-by-id | Supported |
| `tags(limit)` | `GET gamma /tags?limit=` | https://docs.polymarket.com/api-reference/tags/list-tags | Supported |
| `series(limit)` | `GET gamma /series?limit=` | https://docs.polymarket.com/api-reference/series/list-series | Supported |
| `sports()` | `GET gamma /sports` | https://docs.polymarket.com/api-reference/sports/get-sports-metadata-information | Supported |
| `search(query)` | `GET gamma /public-search?q=` — events and tags | https://docs.polymarket.com/api-reference/search/search-markets-events-and-profiles | Supported |
| Event by id / event tags / keyset events | `GET gamma /events/{id}`, `/events/{id}/tags`, `/events/keyset` | https://docs.polymarket.com/api-reference/events/get-event-by-id | Not supported |
| Market by slug / by token / market tags / keyset markets | `GET gamma /markets/slug/{slug}` and friends | https://docs.polymarket.com/api-reference/markets/get-market-by-slug | Not supported |
| Tag by id/slug, related tags | `GET gamma /tags/{id}`, `/tags/{id}/related-tags` | https://docs.polymarket.com/api-reference/tags/get-tag-by-id | Not supported |
| Teams, valid sports market types | `GET gamma /teams`, `/sports-market-types` | https://docs.polymarket.com/api-reference/sports/list-teams | Not supported |
| Series by id | `GET gamma /series/{id}` | https://docs.polymarket.com/api-reference/series/get-series-by-id | Not supported |
| Price history | `GET clob /prices-history`, `POST /batch-prices-history` | https://docs.polymarket.com/api-reference/markets/get-prices-history | Not supported |
| CLOB market listings | `GET clob /clob-markets/{condition_id}`, `/simplified-markets`, `/sampling-markets`, `/sampling-simplified-markets` | https://docs.polymarket.com/api-reference/markets/get-clob-market-info | Not supported — Gamma is the single discovery source (issue #1: one semantic market model) |
| Combo market discovery | `GET combos-rfq /v1/rfq/combo-markets` | https://docs.polymarket.com/trading/combos/market-makers | Supported — on `Rfq.comboMarkets(...)`, not on `Markets` |

## Order books — `Polymarket.orderBooks()`

| Operation | Endpoint | Docs | Status |
|---|---|---|---|
| `book(TokenId)` | `GET clob /book?token_id=` | https://docs.polymarket.com/api-reference/market-data/get-order-book | Supported — the single authority for tick size, minimum shares and neg-risk |
| `books(List<TokenId>)` | `GET clob /books?token_ids=` | https://docs.polymarket.com/api-spec/clob-openapi.yaml | Supported — the GET form deliberately, so the read keeps its retry budget (the documented POST form would not) |
| Midpoint / market price / spread (single and batch) | `GET clob /midpoint`, `/price`, `/spread`, `/midpoints`, `/prices`, `/spreads` | https://docs.polymarket.com/api-reference/market-data/get-market-price | Not supported — derivable from the book already fetched for signing |
| Tick size / fee rate / neg-risk lookups | `GET clob /tick-size`, `/fee-rate`, `/neg-risk` | https://docs.polymarket.com/api-reference/market-data/get-tick-size | Not supported — one `GET /book` already returns all three, so a second source could disagree with it |
| Last trade price | `GET clob /last-trade-price`, `/last-trades-prices` | https://docs.polymarket.com/api-reference/market-data/get-last-trade-price | Not supported as a REST read — carried on `OrderBookSnapshot.lastTradePrice()` and streamed as `LastTradePriceEvent` |

## Trading — `Polymarket.trading()`

| Operation | Endpoint | Docs | Status |
|---|---|---|---|
| `sign(asset, side, price, shares, rules, context)` | none — local EIP-712, Exchange V2 for `TokenId`, V3 for `PositionId` | https://docs.polymarket.com/trading/place-orders | Supported (offline) |
| `submit(...)` / `place(...)` | `POST clob /order` (L2, executed exactly once, never replayed) | https://docs.polymarket.com/api-reference/trade/post-a-new-order | Supported |
| `submitBatch(...)` | `POST clob /orders` (official limit: 15) | https://docs.polymarket.com/api-reference/trade/post-multiple-orders | Supported |
| `cancel(...)` | `DELETE clob /orders` (official limit: 1000 ids) | https://docs.polymarket.com/api-reference/trade/cancel-multiple-orders | Supported |
| `reconcile(...)` | `GET clob /data/trades?id=` per trade id (L2) | https://docs.polymarket.com/api-reference/trade/get-trades | Supported — takes a `SigningIdentity`: the header carries the Account Signer, the `maker_address` filter the Trading Wallet |
| `ImmediatePlanner` | none — pure depth walk over a live book | https://docs.polymarket.com/trading/place-orders | Supported (offline) |
| `AsyncTrading` | the same endpoints, `CompletableFuture` decorator | — | Supported |
| Open-order reads | `GET clob /data/orders` (L2) | https://docs.polymarket.com/api-reference/trade/get-user-orders | Supported — on `Portfolio.openOrders(...)`, not on `Trading` |
| Cancel single order | `DELETE clob /order` | https://docs.polymarket.com/api-reference/trade/cancel-single-order | Not supported — `cancel(...)` covers one id as a batch of one |
| Cancel all / cancel by market | `DELETE clob /cancel-all`, `/cancel-market-orders` | https://docs.polymarket.com/api-reference/trade/cancel-all-orders | Not supported — an unbounded write whose per-order outcome cannot be reported |
| Order scoring | `GET clob /order-scoring`, `/orders-scoring` | https://docs.polymarket.com/api-reference/trade/get-order-scoring-status | Not supported |
| Balance / allowance | `GET clob /balance-allowance` (L2) | https://docs.polymarket.com/api-spec/clob-openapi.yaml | Supported (read) — on `Portfolio.collateralBalance()` / `.conditionalBalance(tokenId)`; the `POST` update is not supported |
| Maker rebates | `GET clob /rebates/current` | https://docs.polymarket.com/api-reference/rebates/get-current-rebated-fees-for-a-maker | Not supported |
| V1 order signing, dynamic protocol-version resolution | — | — | Out of scope |
| Split / merge / redeem / approval / collateral return | — | https://docs.polymarket.com/trading/combos/collateral-return | Out of scope |
| Direct Polygon RPC, CTF ID computation, Relayer envelopes | — | https://docs.polymarket.com/api-reference/relayer/submit-a-transaction | Out of scope |
| Amoy or any other undocumented signing network | — | — | Out of scope |

## Portfolio — `Polymarket.portfolio()`

| Operation | Endpoint | Docs | Status |
|---|---|---|---|
| `positions(PositionQuery[, PageCursor])` | `GET data /positions` | https://docs.polymarket.com/api-reference/core/get-current-positions-for-a-user | Supported |
| `trades(TradeQuery[, PageCursor])` | `GET data /trades` | https://docs.polymarket.com/api-reference/core/get-trades-for-a-user-or-markets | Supported |
| `activity(ActivityQuery[, PageCursor])` | `GET data /activity` | https://docs.polymarket.com/api-reference/core/get-user-activity | Supported |
| `notifications()` | `GET clob /notifications?signature_type=` (L2) | https://docs.polymarket.com/api-reference/get-notifications | Supported |
| Mark notifications read | `POST clob /notifications/read` | https://docs.polymarket.com/api-reference/mark-notifications-read | Not supported |
| Closed positions | `GET data /closed-positions` | https://docs.polymarket.com/api-reference/core/get-closed-positions-for-a-user | Not supported |
| Market positions / top holders | `GET data /v1/market-positions`, `/holders` | https://docs.polymarket.com/api-reference/core/get-top-holders-for-markets | Not supported |
| Portfolio value / P&L | `GET data /value`, `/pnl` | https://docs.polymarket.com/api-reference/core/get-total-value-of-a-users-positions | Not supported |
| Trader leaderboard | `GET data /v1/leaderboard` | https://docs.polymarket.com/api-reference/core/get-trader-leaderboard-rankings | Not supported |
| `comboPositions(ComboPositionQuery[, PageCursor])` | `GET data /v1/positions/combos` | https://docs.polymarket.com/api-reference/core/get-user-combo-positions | Supported — absolute snapshots, never accumulated deltas |
| Combo activity | `GET data /v1/activity/combos` | https://docs.polymarket.com/api-reference/core/get-user-combo-positions | Not supported |
| Accounting snapshot, open interest, live volume, markets traded | `GET data /v1/accounting-snapshot`, `/oi`, `/live-volume`, `/traded` | https://docs.polymarket.com/api-reference/misc/get-open-interest | Not supported |

## Rewards — `Polymarket.rewards()`

| Operation | Endpoint | Docs | Status |
|---|---|---|---|
| `marketRewards(conditionId[, cursor])`, `allMarketRewards(conditionId)` | `GET clob /rewards/markets/{condition_id}` | https://docs.polymarket.com/api-reference/rewards/get-raw-rewards-for-a-specific-market | Supported |
| `currentRewards([cursor])` | `GET clob /rewards/markets/current` | https://docs.polymarket.com/api-reference/rewards/get-current-active-rewards-configurations | Supported |
| `rewardedMarkets([cursor])` | `GET clob /rewards/markets/multi` | https://docs.polymarket.com/api-reference/rewards/get-multiple-markets-with-rewards | Supported |
| `earnings(date[, cursor])` | `GET clob /rewards/user?date=` (L2) | https://docs.polymarket.com/api-reference/rewards/get-earnings-for-user-by-date | Supported |
| `totalEarnings(date)` | `GET clob /rewards/user/total?date=` (L2) | https://docs.polymarket.com/api-reference/rewards/get-total-earnings-for-user-by-date | Supported |
| `rewardPercentages()` | `GET clob /rewards/user/percentages` (L2) | https://docs.polymarket.com/api-reference/rewards/get-reward-percentages-for-user | Supported |
| `userRewardedMarkets(date[, cursor])` | `GET clob /rewards/user/markets?date=` (L2) | https://docs.polymarket.com/api-reference/rewards/get-user-earnings-and-markets-configuration | Supported |

Cursors travel in the documented `next_cursor` **query** parameter, never a header.

## Builders — `com.polymarket.builders.Builders`

Reached through the root: `sdk.builders()`.

| Operation | Endpoint | Docs | Status |
|---|---|---|---|
| `createCredentials()` | `POST clob /auth/builder-api-key` (L2) | https://docs.polymarket.com/api-spec/clob-openapi.yaml | Supported |
| `listCredentials()` | `GET clob /auth/builder-api-key` (L2) | https://docs.polymarket.com/api-spec/clob-openapi.yaml | Supported |
| `revokeCredentials()` | `DELETE clob /auth/builder-api-key` (L2) | https://docs.polymarket.com/api-spec/clob-openapi.yaml | Supported |
| `trades([query][, cursor])` | `GET clob /builder/trades` (L2) | https://docs.polymarket.com/api-reference/trade/get-builder-trades | Supported — filters by builder code, id, market, asset, `before` and `after` |
| Builder attribution on an order | carried by `SigningContext.withBuilder(...)` into `POST /order` | https://docs.polymarket.com/programs/builders/overview | Supported |
| Builder leaderboard, daily builder volume | `GET data /v1/builders-leaderboard`, `/v1/builders-volume` | https://docs.polymarket.com/api-reference/builders/get-aggregated-builder-leaderboard | Not supported |

## Social — `com.polymarket.social.Social`

Reached through the root: `sdk.social()`.

| Operation | Endpoint | Docs | Status |
|---|---|---|---|
| `profile(address)` | `GET gamma /public-profile?address=` | https://docs.polymarket.com/api-reference/profiles/get-public-profile-by-wallet-address | Supported |
| `comments(CommentQuery)` | `GET gamma /comments` | https://docs.polymarket.com/api-reference/comments/list-comments | Supported |
| `commentsById(id, page[, includePositions])` | `GET gamma /comments/{id}` | https://docs.polymarket.com/api-reference/comments/get-comments-by-comment-id | Supported |
| `commentsByUserAddress(address, page)` | `GET gamma /comments/user_address/{address}` | https://docs.polymarket.com/api-reference/comments/get-comments-by-user-address | Supported |
| `search(SearchQuery)` | `GET gamma /public-search` — profile matches only | https://docs.polymarket.com/api-reference/search/search-markets-events-and-profiles | Supported |
| Posting, editing or reacting to comments | — | https://docs.polymarket.com/api-reference/comments/list-comments | Not supported — the capability is read-only by design |

## RFQ — `com.polymarket.rfq.Rfq`

Requester side only. Reached through the root at the Builder Gateway host issued during builder
onboarding: `sdk.rfq(gatewayHost)`. The root owns each one and closes it with itself.

| Operation | Endpoint | Docs | Status |
|---|---|---|---|
| `request(...)` | `POST gateway /v1/builder/rfq/requests` (account + builder HMAC) | https://docs.polymarket.com/trading/combos/builders | Supported |
| `status(rfqId, ...)` | `GET gateway /v1/builder/rfq/requests/{rfqId}` | https://docs.polymarket.com/trading/combos/builders | Supported |
| `awaitSettlement(...)` | polls `status` after acceptance against an injected `Clock` | https://docs.polymarket.com/trading/combos/builders | Supported |
| `comboMarkets(ComboMarketQuery)` | `GET combos-rfq /v1/rfq/combo-markets` | https://docs.polymarket.com/trading/combos/market-makers | Supported — credential-free; the official source of leg Position IDs |
| `accept(quote, ...)` | `POST gateway /v1/builder/rfq/requests/{rfqId}/accept` | https://docs.polymarket.com/trading/combos/builders | Supported — direction, amounts, Combo position and deadline all come from the Quote |
| `AsyncRfq` | the same endpoints, `CompletableFuture` decorator | — | Supported |
| Result reconciliation | `Portfolio.comboPositions(...)` absolute snapshots, or `Trading.reconcile(..., rfqId, ...)` | https://docs.polymarket.com/trading/combos/overview | Supported — no Polygon RPC involved |
| Maker quote submit / cancel / last-look confirm | `POST /v1/maker/quotes`, `/v1/maker/quotes/cancel`, `/v1/maker/confirmations` | https://docs.polymarket.com/api-reference/maker/submit-a-quote | Out of scope |
| Quoter Gateway WebSocket | `wss` RFQ channel | https://docs.polymarket.com/api-reference/wss/rfq | Out of scope |
| Legacy CLOB `/rfq/*` endpoints | — | https://docs.polymarket.com/trading/combos/market-makers | Not supported — superseded by the Builder Gateway |

## Streaming — `Polymarket.streaming()` and `com.polymarket.streaming.Rtds`

| Operation | Endpoint | Docs | Status |
|---|---|---|---|
| `subscribeMarket(assetIds)` → `BookEvent`, `PriceChangeEvent`, `LastTradePriceEvent`, `TickSizeChangeEvent` | `wss://ws-subscriptions-clob.polymarket.com/ws/market` | https://docs.polymarket.com/api-reference/wss/market | Supported — credential-free |
| `subscribeUser(markets)` → `OrderEvent`, `TradeEvent` | `wss://ws-subscriptions-clob.polymarket.com/ws/user` (L2) | https://docs.polymarket.com/api-reference/wss/user | Supported |
| `Rtds` Binance / Chainlink price events | `wss://ws-live-data.polymarket.com` | https://docs.polymarket.com/market-data/realtime-data | Supported |
| `Rtds` comment and reaction created/removed events | `wss://ws-live-data.polymarket.com` | https://docs.polymarket.com/market-data/realtime-data | Supported |
| Sports WebSocket channel | `wss` sports channel | https://docs.polymarket.com/api-reference/wss/sports | Not supported |
| `enableCustomMarketEvents()` → `BestBidAskEvent`, `NewMarketEvent`, `MarketResolvedEvent` | CLOB market channel | https://docs.polymarket.com/api-reference/wss/market | Supported — opt-in, because these frames have no counterpart in the current channel description |
| Midpoint frames | CLOB market channel | https://docs.polymarket.com/api-reference/wss/market | Not supported — derivable from the book |
| Raw socket access, arbitrary JSON frames | — | — | Out of scope |

## Operations — `Polymarket` itself

| Operation | Endpoint | Docs | Status |
|---|---|---|---|
| `serverTime()` | `GET clob /time` | https://docs.polymarket.com/api-reference/data/get-server-time | Supported |
| `health()` | probes `GET clob /time`, `GET gamma /tags?limit=1`, `GET data /trades?limit=1` | https://docs.polymarket.com/api-reference/tags/list-tags | Supported — every probe is a documented credential-free read; an unreachable service is reported, not thrown |
| `geoblock()` | `GET https://polymarket.com/api/geoblock` | https://docs.polymarket.com/api-reference/geoblock | Supported |
| `startHeartbeat()` / `stopHeartbeat()` | `POST clob /heartbeats` (L2, bodyless) | https://docs.polymarket.com/api-reference/trade/send-heartbeat | Supported — idle until started |
| Automatic geoblock preflight before every action | — | — | Out of scope |

## Product areas excluded from 2.x entirely

From issue #1's Out of Scope section — no replacement, no planned support:

| Area | Docs | Note |
|---|---|---|
| Bridge (deposit, withdraw, quote, status, supported assets) | https://docs.polymarket.com/trading/bridge/deposit | Out of scope |
| Perpetual markets (REST and WebSocket) | https://docs.polymarket.com/api-reference/perps/overview | Out of scope |
| Relayer transactions, nonces, wallet-deployment checks | https://docs.polymarket.com/api-reference/relayer/submit-a-transaction | Out of scope |
| Direct Polygon RPC and transaction broadcasting; CTF condition/collection/position ID computation | https://docs.polymarket.com/resources/blockchain-data | Out of scope — enforced by `DirectChainSurfaceTest` |
| Split, merge, redeem, approval, collateral return, wallet deployment | https://docs.polymarket.com/trading/combos/collateral-return | Out of scope |
| RFQ maker/quoter REST and Quoter Gateway WebSocket | https://docs.polymarket.com/trading/combos/market-makers | Out of scope |
| Amoy or any undocumented signing network; V1 order signing | — | Out of scope — mainnet chain id 137 only |
| Referrals, invite codes, account stats/limits | https://docs.polymarket.com/api-reference/apply-referral-code | Out of scope |
| Raw HTTP, raw maps, generic JSON topics, underlying WebSocket access, public transport-library configuration, HTTP proxy configuration | — | Out of scope |
| Backward-compatible adapters for the 1.0 Java API | [MIGRATION.md](MIGRATION.md) | Out of scope — migrate instead |

Complete coverage of every official Polymarket endpoint is explicitly not a goal. This table is the
promise; the official documentation is not.
