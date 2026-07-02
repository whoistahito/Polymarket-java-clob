# Session Workflow — implementing v2-alignment tickets

How a future session should pick up and implement work here. Follow it top to bottom.

## 0. Orient (read first)
- **`AGENTS.md`** (repo root) — build/test commands, architecture, conventions.
- **v2 Rust SDK** at `rs-clob-client/` (`Polymarket/rs-clob-client-v2`) — the **wire/protocol ground
  truth**: endpoints, HTTP methods, payloads, signing, data semantics. It is *guidance, not a
  template* — this SDK is domain-driven, so package/type structure may differ on purpose. Mirror the
  **wire behavior**, never the code structure. Where docs and Rust disagree, Rust wins.
- **`docs/api-reference/`** — the OpenAPI docs (endpoint shapes, params, examples). Secondary to Rust.

## 1. Understand current state
- **`docs/tickets/README.md`** — the hub: board (scope + status), current state, and out-of-scope.
- **`docs/ENDPOINT_AUDIT.md`** — per-endpoint audit evidence behind the gaps.

## 2. Pick a ticket
- Tickets live in **`docs/tickets/`**. Start at **`README.md`** — the board (ID, priority, status,
  parent) and the **recommended sequence**.
- Take the highest-priority `🔵 Todo` ticket unless told otherwise. Each ticket file has the summary,
  evidence (file refs), tasks, and a **Gherkin Definition of Done**.
- Set its status to `🟡 In progress` in the board.

## 3. Implement — test-driven + domain-driven
- **TDD:** write the failing test first from the ticket's Gherkin DoD (one scenario → one test),
  then write the minimum code to pass. Tests use JUnit 5 + Mockito; client tests use `MockWebServer`
  (see `BulkMarketDataTest`, `AuditFixesTest` for the pattern). Test IDs: `TC-XX-NNN` in `@DisplayName`.
- **DDD:** model the domain, not the transport — e.g. results keyed by the domain identity
  (`Map<tokenId, …>`), `Side` enum over strings, `BigDecimal` for all money (never `double`/`float`).
  Don't add abstractions a ticket doesn't need.
- Cross-check the exact wire shape against `rs-clob-client/` before changing a currently-working path;
  tickets marked **verify on live** must be confirmed against the running API first.
- Never regress V1 order signing.

## 4. Verify
- `mvn test` (full suite). Known pre-existing failure: `ExecutionEngineTest
  .testBudgetCapBlocksOverspendForSingleBot` depends on unfinished bot WIP — not your regression
  unless your diff touches it. Everything else must stay green.
- A ticket is Done only when **every** Gherkin scenario passes (and live-verified where stated).

## 5. Close out
- Flip the ticket to `🟢 Done` in `docs/tickets/README.md`; update its "Current state" if capabilities changed.
- Commit per ticket: `feat(area): PMK-NNN <summary>` for code, `docs(tickets): …` for ticket/board
  edits. Commit only when asked; if pushing on `main`, confirm first.

## Guardrails
- Rust wire behavior is ground truth; DDD structure is ours.
- `BigDecimal` only for prices/amounts; rounding `HALF_UP`; token amounts × 10^6.
- EIP-712 signing is signing-critical — for the V2 epic (PMK-001), cross-check hashes against the Rust
  SDK and do it on its own focused branch.
- Shortest working diff; delete dead code the change orphans.
