# Contributor workflow

How work on this repo is planned and landed. Read [AGENTS.md](../AGENTS.md) first — it is the
architecture doc, and everything below assumes it.

## 1. Orient

- **[AGENTS.md](../AGENTS.md)** — build and test commands, the 2.0 package map, invariants, testing
  conventions, and the two-line maximum on JavaDoc and comments.
- **[API_COVERAGE.md](API_COVERAGE.md)** — what the SDK supports, what it deliberately does not, and
  what is out of scope. Update it in the same change that moves a line.
- **[MIGRATION.md](MIGRATION.md)** — the 1.0 → 2.0 map, for questions about why something was dropped.
- **`src/test/resources/protocol/*.json`** — pinned official fixtures (`signing-vectors.json`,
  `constraints.json`, `builder-gateway.json`). Each field carries the documentation URL it came from.
  They are wire ground truth; never regenerate them from this SDK's own output.
- Polymarket's published documentation and its OpenAPI specs are the endpoint authority. Where the
  official sources contradict each other (minimum-size units, book level ordering), AGENTS.md records
  which side this SDK takes and why — do not quietly pick the other one.

## 2. Pick up work

Work is tracked as **GitHub issues**, not files in this repo. The 2.0 redesign is issue #1 (the parent
epic, holding the scope, decisions and the Out of Scope list); each unit of work is a sub-issue. An
issue carrying the `ready-for-agent` label is ready to start. Take one issue at a time and keep its
scope: an issue's acceptance criteria are the contract.

## 3. Implement — test-driven, domain-driven

- **TDD at the highest seam.** Drive a public capability against `MockWebServer` and assert the typed
  outcome plus the exact outbound method, path, query, headers and body. Keep a pure domain test only
  where no network seam can exercise the invariant.
- Do not use `@DisplayName`. Name tests `should...When...` and exception tests
  `shouldThrow...When...`; keep necessary test documentation to at most two lines.
- **Model the domain, not the transport.** `BigDecimal` for every financial value, never
  `double`/`float`; sealed types and records over strings and maps; `Optional` so absent stays distinct
  from zero. Reject rather than round.
- Don't add abstractions the issue doesn't need. Mark a deliberate corner-cut with a one-line
  `// ponytail:` comment rather than building the general case.
- Boundaries are enforced by the build: `PublicBoundaryTest` (no transport type or `internal` import in
  a public package), `DirectChainSurfaceTest` (no Polygon RPC surface). If a change fights those rules,
  the design is wrong, not the rule.
- When touching `Eip712OrderSigner`, `L1Attestation` or `L2Attestation`, re-verify against the pinned
  vectors — signing is the one place a green suite can still be wrong.

## 4. Verify

```bash
mvn -o clean verify     # full deterministic suite; must be entirely green, no exclusions
mvn -o clean package    # the release gate
```

The suite is offline by construction, so a test that reaches a real host fails with
`UnknownHostException`. Live checks are the exception: they carry `@Tag("live")`, live in
`com.polymarket.live`, are excluded from every normal run, and are selected only by
`mvn -Plive test`. `LiveCheckGatingTest` fails the build if a live check loses its tag or a live tag
appears outside that package. Live checks stay credential-free and read-only — never an order, an RFQ
acceptance, a cancellation, or a private key.

## 5. Close out

- Update AGENTS.md when architecture or the verified test count changes, and API_COVERAGE.md when
  supported surface changes — in the same change, not a follow-up.
- One commit per logical change, subject in the imperative naming the issue:
  `Add trade-ID settlement reconciliation (#16)`.
- Delete the dead code a change orphans. Prefer removing stale content over leaving a broken pointer.
