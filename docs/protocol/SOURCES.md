# Official protocol sources — reviewed inventory

Source of truth for the 2.0 redesign (parent issue #1). Everything an implementation ticket asserts
against must trace back to a row here. **Review date for every row: 2026-08-23** (issue #3, refresh).

Machine-readable companions live in `src/test/resources/protocol/` and are enforced by
`ProtocolContractsTest`:

| Fixture | Contents |
|---|---|
| `signing-vectors.json` | V2/V3/Deposit Wallet typed data, encodeType, typeHash, domain separator, struct hash, digest, signature |
| `constraints.json` | Contracts, collateral, signature types, order struct, GTD, batch limits, pagination, settlement, tick grid + precision table, minimum-size conflict |
| `builder-gateway.json` | Requester RFQ endpoints, auth headers, HMAC recipe, Account Signer vs Trading Wallet address roles, create/accept/status shapes, status machine, HTTP status table |
| `fees.json` | Taker fee formula, per-category rates, 5-decimal precision rule, independently derived fee examples |
| `trades.json` | CLOB paginated trade envelope, full trade-status vocabulary, required filters, Data API trade feed contrast |
| `builder-trades.json` | Required `builder_code`, `before`/`after` continuation, cursor sentinels, unix vs ISO timestamp units |
| `heartbeat.json` | Bodyless `POST /heartbeats` contract and the unlisted id-chaining variant |
| `combo-markets.json` | Combo markets catalog endpoint, query bounds, YES/NO index alignment, cursor semantics |
| `order-submission.json` | `SendOrderResponse` required/optional fields incl. `transactionsHashes`, `POST /orders` array shape, `CancelOrdersResponse` contract, order-identifier syntax evidence |
| `streams.json` | CLOB market and user channel frames and field lists from the two AsyncAPI documents, RTDS topics, subscribe shapes, heartbeat intervals, and the comment fields no official example gives a shape for |

`ProtocolContractsTest` enumerates this directory rather than a list, so a fixture added without a
`reviewedOn` date or citing anything but official documentation fails the build on the day it lands.

## 1. Pinned source revisions

Every document below was fetched with `curl` on **2026-08-23** and hashed. The raw documents are
deliberately **not** committed; the SHA-256 of the fetched bytes plus `info.version` is the pin.
Re-fetch, re-hash, and if a hash moved, re-read that page before trusting any fixture that cites it.

| URL | fetchedOn | sha256 | version |
|---|---|---|---|
| `https://docs.polymarket.com/api-spec/clob-openapi.yaml` | 2026-08-23 | `82529177635db366c31a08777355b4b95c392a427298c3ba68904b937d4594da` | openapi 3.1.0, `info.version` 1.0.0 |
| `https://docs.polymarket.com/api-spec/data-openapi.yaml` | 2026-08-23 | `9d5d62b302bced648b7212e6e8c589a741b258d38bb8769a1cd57c5224ecc1fe` | openapi 3.0.3, `info.version` 1.0.0 |
| `https://docs.polymarket.com/api-spec/gamma-openapi.yaml` | 2026-08-30 | `285a58c10c9bffd3888768ff25fbf1e7f31df59db72c35a0c3b115cecb39a3c9` | openapi 3.0.3, `info.version` 1.0.0 |
| `https://docs.polymarket.com/trading/fees.md` | 2026-08-23 | `8e246189f6ca85b8b8782e1d76a769cf98672a98c4c63d8bbe6150d659db8d7c` | prose (no version field) |
| `https://docs.polymarket.com/market-data/market-details.md` | 2026-08-23 | `6996afeeb98c76da4f983cca1d0256708600d213fbad41a62a17e3bef5f74e90` | prose |
| `https://docs.polymarket.com/trading/combos/builders.md` | 2026-08-23 | `117c3990aa64d0691e617f45d21074bd5ea626838584fca733e6fbc85e622ff8` | prose |
| `https://docs.polymarket.com/trading/combos/market-makers.md` | 2026-08-23 | `2ebce652431e732c07356c207c623181d880d3e9c1beb61a77790357837d2232` | prose |
| `https://docs.polymarket.com/trading/wallets-auth.md` | 2026-08-23 | `34095970a28c384375127aa481b3f928c3e3c2337414aa3d3af4a8b8bd43e8f5` | prose |
| `https://docs.polymarket.com/trading/place-orders.md` | 2026-08-23 | `a3426c3ac3c04c96a4e988009cd6e603d22622a31e1b3c02a453ce3ac22d7563` | prose |
| `https://docs.polymarket.com/resources/contracts.md` | 2026-08-23 | `ed59020bd28a24cbca9dbd2f92624a2a8ad7e403f0f08b6ff1529e33860c99a6` | prose |
| `https://docs.polymarket.com/changelog/predictions.md` | 2026-08-23 | `630e2f885b355a5a34dc1d0e09c2ac033ee83af4595ba283b217273e6f5ac73b` | latest entry Aug 17, 2026 |
| `https://docs.polymarket.com/api-reference/trade/get-builder-trades.md` | 2026-08-23 | `54aa9529cdad068a53e2531144fce969385768fa30527d9d85b3bf7321f297e9` | rendered from clob-openapi.yaml |
| `https://docs.polymarket.com/api-reference/trade/get-trades.md` | 2026-08-23 | `a7b88859fbca99a55bb5c2d43fc21dedf1a44701dba9abc5383478df8883e3fb` | rendered from clob-openapi.yaml |
| `https://docs.polymarket.com/api-reference/trade/send-heartbeat.md` | 2026-08-23 | `983e93c10d697ba4cb19d98a8bf9d2fff19d19d5fac2c98ad0bd73ea382014b0` | rendered from clob-openapi.yaml |
| `https://docs.polymarket.com/llms.txt` | 2026-08-23 | `e80d08b8d48451104ba53d603eb46b6e507e5335184be4c4620840a62888d420` | documentation index, 313 lines |
| `https://combos-rfq-api.polymarket.com/v1/rfq/combo-markets` | 2026-08-24 | live response, not hashed | unversioned; observed shape pinned in `combo-markets.json` |
| `https://docs.polymarket.com/api-reference/trade/post-a-new-order.md` | 2026-08-24 | `6c1924f515da4d960337a2db67b37c3d43965dbaa5b8616bd02d95a0a789e8f5` | rendered from clob-openapi.yaml |
| `https://docs.polymarket.com/api-reference/trade/cancel-multiple-orders.md` | 2026-08-24 | `41f701ad7f4503a0b4a17d9452aa4eb2e70517d7e5658d493417e806b0705983` | rendered from clob-openapi.yaml |
| `https://docs.polymarket.com/trading/manage-orders.md` | 2026-08-24 | `e4a0238db31d5137b4d0da0d4333b1fb90be8f7c7b47d92968edfd993c8c4482` | prose |

Re-fetched on **2026-08-24** for issues #14/#17: `clob-openapi.yaml` still hashes to
`82529177635db366c31a08777355b4b95c392a427298c3ba68904b937d4594da`, so the 2026-08-23 pin above
still covers every schema `order-submission.json` cites.

Unsupported protocol specs remain out of scope: Combos RFQ OpenAPI (quoter/maker only), Relayer,
Bridge, and Perps. The supported stream documents are inventoried by `streams.json`.

## 2. Which fixture each fact came from

| Fact | URL |
|---|---|
| Taker fee formula, per-category rates, 5-decimal precision | `https://docs.polymarket.com/trading/fees.md` |
| Per-market `feeSchedule` (rate, exponent, takerOnly, rebateRate) | `https://docs.polymarket.com/market-data/market-details.md#trading-fees` |
| Paginated trade envelope, trade-status enum, required `maker_address` | `https://docs.polymarket.com/api-spec/clob-openapi.yaml` |
| Data API `/trades` bare array, limit/offset caps, `start`/`end` window | `https://docs.polymarket.com/api-spec/data-openapi.yaml` |
| Required `builder_code`, `before`/`after`, unix vs ISO timestamps | `https://docs.polymarket.com/api-spec/clob-openapi.yaml` (`GET /builder/trades`) |
| Bodyless `POST /heartbeats` | `https://docs.polymarket.com/api-spec/clob-openapi.yaml`, indexed by `llms.txt` |
| Builder Gateway requester flow, address roles, HTTP status table | `https://docs.polymarket.com/trading/combos/builders.md` (Direct API tab) |
| `signer_address` / `maker_address` per wallet type | `https://docs.polymarket.com/trading/combos/market-makers.md` (Resolve Quoter Identity) |
| Combo markets catalog, YES/NO index alignment, `next_cursor` | `https://docs.polymarket.com/trading/combos/market-makers.md` (Get Combo Markets) |
| Exchange V2 typed data, GTD rules, side encoding, tick precision table | `https://docs.polymarket.com/trading/place-orders.md` |
| Exchange V3 typed data, ERC-7739 wrapping | `https://docs.polymarket.com/trading/combos/market-makers.md` |
| `SendOrderResponse` fields incl. `transactionsHashes`, `CancelOrdersResponse` contract, absence of an order-ID pattern | `https://docs.polymarket.com/api-spec/clob-openapi.yaml` |
| Real 32-byte order-hash example (`OpenOrder.id`) | `https://docs.polymarket.com/trading/manage-orders.md` |
| `POLY_ADDRESS` is "Ethereum address associated with the API key"; `maker_address` is "Maker address to filter trades" | `https://docs.polymarket.com/api-spec/clob-openapi.yaml` (`securitySchemes.polyAddress`, `GET /data/trades`) |
| Contract addresses | `https://docs.polymarket.com/resources/contracts.md` |
| Dated behaviour changes | `https://docs.polymarket.com/changelog/predictions.md` |

### Changelog entries that drive this release

| Date | Change |
|---|---|
| 2026-03-30 | Fee Structure V2: per-category rates; Geopolitics stays fee-free |
| 2026-03-31 | Fees are read from the market's `feeSchedule` object |
| 2026-04-17 | CLOB V2: `nonce`/`feeRateBps`/`taker` removed, `timestamp`/`metadata`/`builder` added; EIP-712 domain version `1` → `2`; pUSD replaces USDC.e; fees set at match time; builder attribution via `builderCode` |
| 2026-04-28 | CLOB V2 live; **no V1 compatibility**; open orders wiped |
| 2026-05-18 | `builderCode` added to `/v1/builders/leaderboard` and `/v1/builders/volume` |
| 2026-06-15 | `DELETE /orders` max batch 1000 |
| 2026-07-02 | `0.0025` tick size in use (World Cup markets) |
| 2026-07-10 | Sports taker fee `0.03` → `0.05`; sports maker rebate 25% → 15% |
| 2026-07-17 | `POST /order`/`POST /orders` return `tradeIDs`, **not** `transactionHashes` |
| 2026-08-10 | Data API: per-outcome `REDEEM` rows; optional `grossInitialValue`/`entryFeesUsdc` on `/positions`; `includeArchived` filter |

## 3. Endpoint support matrix

Matches the scope decisions in issue #1.

| Domain | Supported | Not supported |
|---|---|---|
| Markets | Gamma events/markets/tags/series/sports/search; CLOB books, market info | — |
| Trading | V2 token orders, V3 Combo position orders, batch post/cancel, heartbeats | V1 orders, Amoy, direct CTF |
| Portfolio | Data positions/activity/trades, notifications | — |
| Rewards | Market rewards, scoring, earnings | — |
| Builders | Credentials, attribution, builder trades | — |
| Social | Profiles, comments, search | — |
| RFQ | Builder Gateway **requester** flow | Quoter REST + Quoter Gateway WebSocket |
| Streaming | CLOB market + user channels; RTDS crypto prices + comments | RTDS legacy topics, `clob_auth`, Perps channels |
| — | — | Relayer, Bridge, Perps, CTF split/merge/redeem, Polygon RPC |

## 4. Contradictions — kept separate, never merged

1. **Minimum order size.** CLOB `/book.min_order_size` is normalized **shares** and is authoritative
   for signing. Gamma's `orderMinSize` is labelled **USDC notional** and is discovery metadata only.
   Official sources conflict; preserve both.
2. **Order-book level ordering.** Sources disagree on wire ordering of bids/asks. The CLOB OpenAPI
   documents "bids sorted by price descending, asks ascending", but every `GET /book` response
   sampled on 2026-08-16 sent the **exact reverse**. Sort numerically before any depth calculation.
3. **Order `timestamp` units.** V2 documents unix **milliseconds**; V3 documents unix **seconds**.
   Both are official. Do not normalize into one type.
4. **Neg Risk Adapter.** `0xd91E80cF2E7be2e162c6513ceD06f1dD0dA35296` is still on the contracts page,
   now explicitly labelled "CLOB v1, deprecated"; the 2026-07-14 changelog directs pUSD actions to
   `0xadA2005600Dec949baf300f4C6120000bDB6eAab`. Adapter operations are out of scope, so this is
   recorded, not resolved.
5. **Fee-rate units.** `GET /fee-rate` returns `base_fee` as an integer in **basis points**
   (example `30`). The fee page and Gamma's `feeSchedule.rate` express the same coefficient as a
   **decimal** (`0.04`–`0.07`, i.e. 400–700 bps). The two are not interchangeable and the spec's
   example value matches neither published category rate. Pinned in `fees.json.unitContradiction`.
6. **Builder trade timestamps.** One `BuilderTrade` row carries `matchTime` as a unix-seconds
   **string** and `createdAt`/`updatedAt` as **ISO-8601** date-times. One parser cannot read both.
7. **Two Heartbeat contracts.** `clob-openapi.yaml` documents both a bodyless `POST /heartbeats`
   and an id-chaining `POST /v1/heartbeats`. Only the bodyless one is listed in `llms.txt`, so it
   is treated as current and the other as an unlisted alternative.
8. **`signer_address` naming.** On the Builder Gateway, `signer_address` carries the **Trading
   Wallet** for Deposit Wallets (signature type 3) and the **Account Signer** for Proxy/Safe/EOA.
   Only the `POLY_ADDRESS` header always carries the Account Signer. **Resolved by issue #26**:
   the same rule governs the signed order struct, because signature type 3 is verified through
   the wallet's own ERC-1271 check, so the wallet is the signer the exchange resolves. The
   deposit-wallet vectors previously fed the Account Signer into `order.signer` — an input choice
   in `gen-vectors.js`, not an official value — and have been regenerated against the documented
   table. `SigningIdentity.orderSigner()` is where the rule now lives, and
   `constraints.json.signatureTypes.walletAddressRoles` pins the wallet/`maker_address`/
   `order_signer_address` table verbatim from `trading/place-orders`, so the core trading page and
   the Builder Gateway page agree: only type 3 puts the wallet in both. That page also publishes
   the six-field `TypedDataSign` payload with no `uint256[] extensions`, pinned as
   `depositWalletTypedDataSignFields`.

### Superseded by this refresh

- The tick grid's provenance is no longer "MIXED / this repo's own live verification". The
  `place-orders` "Choose a Price and Size" table now publishes all six ticks together with their
  price/size/amount decimals. `constraints.json.tickGrid.officiallyPublished` is `true`.
- `builder-gateway.json` previously listed `submission_deadline` as a millisecond timestamp field.
  No such field exists anywhere in the current requester documentation; it has been removed.

## 5. Provenance of the derived values

Polymarket publishes the **typed data** (domain, types, primary type, example messages) but does
**not** publish digests or signatures. `signing-vectors.json` therefore pins:

- **Directly official** — domain, `types`, `primaryType`, message shape, signature-type table,
  exchange addresses. Re-read on 2026-08-23 against `place-orders.md` and `market-makers.md`; the
  published typed data is unchanged (`salt` 479249096354, `makerAmount` 5200000, `takerAmount`
  10000000 for V2; 450000 / 1000000 for V3).
- **Derived by an independent implementation** — `encodeType`, `typeHash`, `domainSeparator`,
  `structHash`, `digest`, `signature`, produced with **ethers v6.17.0** over the official typed data
  using the repo's well-known test key
  `ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80`. No Java SDK code was involved.

Regenerate with `docs/protocol/gen-vectors.js` (needs `npm i ethers@6`). Verified on 2026-08-23:
its only dependency is `ethers`, it performs no filesystem or repository reads, and re-running it
reproduces `signing-vectors.json` **byte for byte** apart from the `reviewedOn` field.

The independence is checked two ways in `ProtocolContractsTest`: each vector's `encodeType` must
equal the encodeType string Polymarket publishes verbatim (TC-PC-001), and each signature must
recover to its stated signer over its stated digest (TC-PC-002). Both still hold.

`fees.json` is generated by the sibling script `docs/protocol/gen-fee-vectors.js`, which evaluates
the official formula with exact `BigInt` decimal arithmetic — no floating point and no Java SDK
code. Each example also carries Polymarket's own published `publishedUsdc` table value, and
TC-PC-012 asserts the derived exact fee rounds to it. That published column is the independent
check on the derivation; the Java assertion is a third implementation of the same formula, so a
mistake in any one of the three shows up as a failure rather than as silent agreement.

`combo-markets.json` keeps its two kinds of evidence apart by name: `documentedExampleResponse` is
Polymarket's own published example, while `observedResponse` and the `query.limit` bounds are a live
probe of `combos-rfq-api.polymarket.com` on 2026-08-24 — behaviour the documentation does not state.
`order-submission.json` is entirely official: every field traces to `clob-openapi.yaml`.

## 6. Discrepancies against current production code

Recorded here for the tickets that own the code. **No production code was changed by issue #3.**
In every case the fixture states the OFFICIAL value. Entries struck through have since been
repaired by the ticket named; they are kept because the fixture that caught them is still the
evidence the repair is checked against.

1. ~~`trading/FeeRate.feeOn` charges `notional × bps / 10000`, with no `p × (1 - p)` price-curve
   factor.~~ **Repaired (issue #11.)** `exactFeeOn` applies the official price curve, and
   `FeeRateTest` reproduces the published table. See `fees.json`.
2. ~~`internal/builders/BuildersGateway.query(...)` never sends `builder_code`, the only
   **required** parameter of `GET /builder/trades`.~~ **Repaired (issue #19.)** The code is
   required by `BuilderTradeQuery` and always sent. See `builder-trades.json.requiredQuery`.
3. ~~`BuildersGateway.trade(...)` parses `matchTime` with `Instant::parse`.~~ **Repaired (issue
   #19.)** `matchTime` is read as unix seconds and `createdAt`/`updatedAt` as ISO-8601, per
   `builder-trades.json.timestampUnits`.
4. ~~`BuilderTradeQuery` exposes no `before`/`after`.~~ **Repaired (issue #19.)** Both windows are
   sent as unix seconds. See `builder-trades.json.continuation`.
5. ~~`internal/trading/TradeReaderGateway` iterates the `GET /data/trades` body as if it were an
   array and omits the required `maker_address`.~~ **Repaired (issue #16.)** Rows are read from
   `data`, the walk follows `next_cursor`, and the Trading Wallet filter is always sent. See
   `trades.json.clobTradePage`.
6. ~~`internal/operations/HeartbeatGateway` posts the id-chaining `POST /v1/heartbeats`.~~
   **Repaired (issue #24.)** Each tick is the bodyless `POST /heartbeats`, and the 5 s default is
   documented in code as a local choice with no published source. See `heartbeat.json`.
7. ~~`internal/rfq/RfqGateway.quoted(...)` reads `expires_at` and `builder_code` from inside
   `quote` and `leg_position_ids` from the top level.~~ **Repaired (issue #25.)** Both are read
   from the top level and the legs from `request`. See `builder-gateway.json.createResponse`.
8. ~~The same method looks for the Combo position id under `combo_position_id` /
   `comboPositionId` / `position_id` / `tokenId`; none of the four exists.~~ **Repaired (issue
   #25.)** It reads the pinned `request.yes_position_id`.
9. ~~`RfqGateway.requestBody(...)` sends `signer_address = identity.signer()` (the Account
   Signer).~~ **Repaired (issue #25.)** `signer_address` follows the wallet type: only signature
   type 3 puts the Trading Wallet there. See `builder-gateway.json.addressRoles`.
10. ~~`RfqGateway.accept(...)` uses `signedOrder.signer()` as `POLY_ADDRESS`.~~ **Repaired (issue
    #26.)** The Signing Identity is passed through, so `POLY_ADDRESS` is always the Account
    Signer EOA that owns the credentials.
11. ~~`internal/portfolio/PortfolioGateway` caps Data API `/trades` at limit 500 / offset 1000.~~
    **Repaired (issue #15.)** Each endpoint carries its own pinned spec bound, and the superseded
    2025-08-26 changelog figures are kept in `constraints.json` only to explain the old ones.

12. `internal/trading/TradeReaderGateway` used ONE caller-supplied address for both the L2
    `POLY_ADDRESS` header and the required `maker_address` trade filter. `POLY_ADDRESS` is the
    "Ethereum address associated with the API key" (the Account Signer); `maker_address` filters on
    the maker of the order (the Trading Wallet). They coincide only for an EOA, so a Proxy, Safe or
    Deposit Trading Wallet could not reconcile at all. **Fixed by issue #14**: the port now takes a
    `SigningIdentity`.
13. `SubmissionOutcome.Accepted` dropped `transactionsHashes`, a documented `SendOrderResponse`
    field. `constraints.json.settlement` (changelog Jul 17, 2026) and the live `clob-openapi.yaml`
    contradict each other on whether `POST /order` still returns it; both are official. **Fixed by
    issue #14**: the field is carried when present and never required. See
    `order-submission.json.sendOrderResponse.settlementContradiction`.
14. Order-identifier syntax is **not** constrained by the official spec. Every `orderID` /
    `order_id` / `order_ids` field in `clob-openapi.yaml` is a bare `type: string` with no
    `pattern`, while sibling fields in the same schemas do carry patterns. The published examples
    disagree on length: `SendOrderResponse.orderID` and `CancelOrderPayload.orderID` show 0x + 40
    hex, while `GET /data/order/{orderID}`, `GET /order-scoring` and `manage-orders.md` show
    0x + 64 hex. Issue #17 therefore enforces only the 0x-hex **shape** every official example
    shares, never a length. See `order-submission.json.orderIdentifierSyntax`.
