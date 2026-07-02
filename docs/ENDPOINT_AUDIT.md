# Endpoint Audit — Docs vs Rust SDK vs Java (June 2026)

Audit of every documented Polymarket endpoint against the Java SDK, using the Rust SDK
(`rs-clob-client-v2`) as the protocol/signing reference. Docs were re-pulled from
`docs.polymarket.com` (113 pages) before auditing.

**Ground-truth rule:** where the OpenAPI docs and the Rust SDK disagree (the docs have
several stale/aspirational pages and describe newer separate systems), the Rust SDK wins
for anything touching signing or wire protocol — it is the battle-tested official client.

Legend: ✅ fixed this pass · 🔧 confirmed gap, not yet done · ❓ doc-vs-Rust conflict (left as Rust) · 🆕 net-new surface (no Rust reference)

---

## ✅ Fixed this pass (with tests — `AuditFixesTest`, TC-AUD-001..005)

| Issue | Was | Now | Evidence |
|---|---|---|---|
| Tick-size response field | read `tick_size` | read `minimum_tick_size` (legacy fallback kept) | Rust `client.rs:861` + `get-tick-size.md` |
| Fee-rate response field | read `fee_rate_bps` | read `base_fee` (legacy fallback kept) | Rust `client.rs:645` + `get-fee-rate.md` |
| `areOrdersScoring` body | `{"orderIds":[...]}` | bare JSON array `["id",...]` | Rust `client.rs:2174` `.json(&order_ids)` |
| `getRewardPercentages` type | `Map<String,Double>` | `Map<String,BigDecimal>` | project convention + Rust `Decimal` |
| Mixed `ID`/`Id` JSON casing | `GammaComment.parentEntityID`/`parentCommentID` silently null | global `ACCEPT_CASE_INSENSITIVE_PROPERTIES` on shared mapper | `list-comments.md` casing; `HttpClient.defaultObjectMapper` |

`getMidpoint` reads `mid` — docs say `mid_price`, but **Rust uses `mid`** (`response.rs`), so left unchanged (Rust is ground truth). ❓

---

## 🔧 Confirmed gaps — recommended, not yet implemented

### HIGH

1. **CLOB order protocol V2 signing.** The CLOB server exposes `GET /version` (returns 1 or 2).
   Rust resolves it at runtime (`resolve_version`, `client.rs:692`) and builds **V1**
   (`taker/nonce/feeRateBps`) or **V2** (`timestamp/metadata/builder`) orders, each with a
   distinct EIP-712 domain version (`VERSION_V1`/`VERSION_V2`, name `"Polymarket CTF Exchange"`)
   and verifying contract. **Java `OrderBuilder` only implements V1.** This is signing-critical and
   sizeable (new struct, domain, contract addresses, runtime version negotiation, `Poly1271`
   signer rules) — it deserves its own focused, cross-checked task. *Do not rush; getting EIP-712
   wrong silently breaks all trading.*
2. **`getOpenOrders` response shape.** Java parses the body directly as `List<OpenOrder>`
   (`PolymarketClient.java:558-575`); docs return a paginated wrapper
   `OrdersResponse {limit,next_cursor,count,data}`. Verify against Rust before changing — if Rust
   parses a bare list, the live API likely returns a list and the doc page is stale.
3. **Bulk market-data typing.** `getMidpoints/getPrices/getSpreads` return `List<Map<String,Object>>`
   but the responses are JSON **objects/maps**, not arrays (Rust returns typed
   `MidpointsResponse/PricesResponse/SpreadsResponse`). The array parse can throw at runtime. Retype
   to the documented map shapes. (This is the existing "typed bulk-query results" MEDIUM gap.)

### MED

4. **WS user-channel auth shape** — doc `wss/user.md` nests `{apiKey,secret,passphrase}` under an
   `auth` object with no HMAC; Java flattens them + adds `timestamp`/`signature`
   (`WsClient.java:361-371`). **Verify against Rust `ws` / TS clob-client before changing** — the
   current shape likely matches the live CLOB gateway and the doc describes a newer gateway. ❓
5. **`OrderResponse` missing `tradeIDs`** (`model/OrderResponse.java`) — present in doc
   `SendOrderResponse` for matched orders.
6. **`getLastTradePrice(s)` drop `side`** — doc returns `{price, side}`; Java returns bare
   `BigDecimal` / untyped maps. Add a typed `LastTradePrice` result.
7. **Rewards param coverage** — `/rewards/user/markets`, `/rewards/user/percentages` etc. are missing
   documented optional params (`signature_type`, `maker_address`, `sponsored`, paging). Non-breaking.
8. **`GammaComment`/Gamma models** now deserialize correctly after the casing fix, but several Gamma
   response records still omit many documented fields (harmless — unknown fields are ignored).

### LOW

9. Reward earning models use primitive `double` (`UserEarning`, `TotalUserEarning`) — convention is `BigDecimal`.
10. `BookParams.side` serializes as `"side":null` when omitted (Rust omits via `skip_serializing_none`); add `@JsonInclude(NON_NULL)`.
11. Gamma keyset-pagination variants (`/events/keyset`, `/markets/keyset`) and `series` `exclude_events` param missing.
12. `createOrDeriveApiKey` falls back on empty-key rather than HTTP-status error (Rust semantics).

---

## 🆕 Net-new surfaces — no Rust reference, entirely unbuilt

These appear in the re-pulled docs but **do not exist in the Rust SDK**, so there is no reference
implementation to mirror. Treat as new features, scope separately:

- **Combinatorial RFQ / maker** (`combos-rfq-api.polymarket.com`): `POST /v1/maker/quotes`,
  `/v1/maker/quotes/cancel`, `/v1/maker/confirmations` (`docs/api-reference/maker/*`). The existing
  Java `RfqClient` implements the *legacy* `/rfq/*` CLOB endpoints — a different system.
- **RFQ quoter WebSocket gateway** (`wss://combos-rfq-gateway-quoter.polymarket.com/ws/rfq`,
  `wss/rfq.md`): auth-first handshake, `type`-discriminated messages.
- **Sports WebSocket** (`sports-api.polymarket.com`, `wss/sports.md`): broadcast-only, ping/pong.
- **Combo markets** (`GET /v1/rfq/combo-markets`), **relayer** API (7 pages), **batch-prices-history**,
  **clob-market-info**, **market-by-token** — CLOB/combos host endpoints with no Java client.

Out of scope per `docs/tickets/README.md`: full **Data API** and **Bridge API** parity.

---

## Surfaces that audited clean

Gamma events/markets/tags/series/comments/sports/search/profiles paths and query params match docs
(only `series.exclude_events` missing). CLOB auth (api-key create/derive/delete/get), cancel
(single/multi/market/all), `getOrder`, `isOrderScoring`, notifications, balance-allowance,
server-time, order-book(s), `getSpread`, `getPrice`, `getNegRisk`, builder-trades — all correct.
