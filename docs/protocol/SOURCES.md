# Official protocol sources — reviewed inventory

Source of truth for the 2.0 redesign (parent issue #1). Everything an implementation ticket asserts
against must trace back to a row here. **Review date for every row: 2026-08-16.**

Machine-readable companions live in `src/test/resources/protocol/` and are enforced by
`ProtocolContractsTest`:

| Fixture | Contents |
|---|---|
| `signing-vectors.json` | V2/V3/Deposit Wallet typed data, encodeType, typeHash, domain separator, struct hash, digest, signature |
| `constraints.json` | Contracts, collateral, signature types, order struct, GTD, batch limits, pagination, settlement, minimum-size conflict |
| `builder-gateway.json` | Requester RFQ endpoints, auth headers, HMAC recipe, bodies, status machine |

## 1. Specifications

| Source | URL | Revision |
|---|---|---|
| CLOB OpenAPI | `https://docs.polymarket.com/api-spec/clob-openapi.yaml` | openapi 3.1.0, `info.version` 1.0.0 |
| Combos RFQ OpenAPI | `https://docs.polymarket.com/api-spec/combos-rfq-openapi.yaml` | openapi 3.1.0 — **quoter/maker endpoints only** |
| Gamma OpenAPI | `https://docs.polymarket.com/api-spec/gamma-openapi.yaml` | openapi 3.0.3, `info.version` 1.0.0 |
| Data OpenAPI | `https://docs.polymarket.com/api-spec/data-openapi.yaml` | openapi 3.0.3, `info.version` 1.0.0 |
| Relayer / Bridge / Perps OpenAPI | `https://docs.polymarket.com/api-spec/{relayer,bridge,perps}-openapi.*` | out of scope (issue #1) |
| AsyncAPI — market, user, rfq, sports, perps | `https://docs.polymarket.com/asyncapi{,-user,-rfq,-sports,-perps}.json` | market + user in scope; rfq is the quoter gateway (out of scope) |
| Documentation index | `https://docs.polymarket.com/llms.txt` | 308 lines |

The requester-side Builder Gateway is **not** in `combos-rfq-openapi.yaml`; that spec states it covers
quoter commands. The requester contract is prose-only, at
`https://docs.polymarket.com/trading/combos/builders` (Direct API tab).

## 2. Prose sources behind specific fixtures

| Fact | URL |
|---|---|
| Exchange V2 typed data, GTD rules, side encoding | `https://docs.polymarket.com/trading/place-orders` |
| Exchange V3 typed data, ERC-7739 wrapping, signature-type table | `https://docs.polymarket.com/trading/combos/market-makers` |
| Builder Gateway requester flow | `https://docs.polymarket.com/trading/combos/builders` |
| Contract addresses | `https://docs.polymarket.com/resources/contracts` |
| Dated behaviour changes | `https://docs.polymarket.com/changelog/predictions` |

### Changelog entries that drive this release

| Date | Change |
|---|---|
| 2026-04-17 | CLOB V2: `nonce`/`feeRateBps`/`taker` removed, `timestamp`/`metadata`/`builder` added; EIP-712 domain version `1` → `2`; pUSD replaces USDC.e; fees set at match time; builder attribution via `builderCode` |
| 2026-04-28 | CLOB V2 live; **no V1 compatibility**; open orders wiped |
| 2026-06-15 | `DELETE /orders` max batch 1000 |
| 2026-07-02 | `0.0025` tick size in use (World Cup markets) |
| 2026-07-17 | `POST /order`/`POST /orders` return `tradeIDs`, **not** `transactionHashes` |

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
2. **Order-book level ordering.** Sources disagree on wire ordering of bids/asks. Sort numerically
   before any depth calculation.
3. **Order `timestamp` units.** V2 documents unix **milliseconds**; V3 documents unix **seconds**.
   Both are official. Do not normalize into one type.
4. **Neg Risk Adapter.** `0xd91E80cF2E7be2e162c6513ceD06f1dD0dA35296` is still on the contracts page,
   but the 2026-07-14 changelog deprecates it for CLOB v1 and directs pUSD actions to
   `0xadA2005600Dec949baf300f4C6120000bDB6eAab`. Adapter operations are out of scope, so this is
   recorded, not resolved.

## 5. Provenance of the signing vectors

Polymarket publishes the **typed data** (domain, types, primary type, example messages) but does
**not** publish digests or signatures. `signing-vectors.json` therefore pins:

- **Directly official** — domain, `types`, `primaryType`, message shape, signature-type table,
  exchange addresses.
- **Derived by an independent implementation** — `encodeType`, `typeHash`, `domainSeparator`,
  `structHash`, `digest`, `signature`, produced with **ethers v6.17.0** over the official typed data
  using the repo's well-known test key
  `ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80`. No Java SDK code was involved.

The independence is checked two ways in `ProtocolContractsTest`: each vector's `encodeType` must
equal the encodeType string Polymarket publishes verbatim, and each signature must recover to its
stated signer over its stated digest.

Regenerate with `docs/protocol/gen-vectors.js` (needs `npm i ethers@6`); it reads nothing from
`src/main`.
