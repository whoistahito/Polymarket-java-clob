# Refactoring Merge Audit

Reviewed: 2026-08-30

## Merge Status

- Local `develop` at `1957790` contains every non-`main` remote branch.
- `main` remains unchanged at `54533cd`.
- Issue #3 conflicts were resolved using the aggregate branch's newer protocol evidence.

## Verification

`mvn clean verify` passed: **554 tests, 0 failures, 0 errors, 0 skipped**. Dependency and packaging gates also passed.

## Ticket Status

- Complete: #2, #6, #7, #8, #15, #29.
- Direct acceptance criteria implemented, but affected by issue #1 cross-cutting gaps: #18, #19, #20, #21, #22.
- Partial or requiring correction: #1, #3, #4, #5, #9-#14, #16, #17, #23-#28, #30.

## Release Blockers

1. #1/#5: listed API keys are exposed as raw strings instead of redacted values.
2. #10-#12: signing can bypass price-grid validation, and immediate BUY execution can exceed its pUSD budget.
3. #14: malformed successful order responses can be classified as accepted.
4. #23: RTDS drops envelope timestamps and documented social-event fields.
5. #24: sub-millisecond heartbeat intervals can leave the SDK reporting an active schedule when none exists.
6. #26/#28: RFQ Deposit Wallet signer rules conflict, and acceptance is not bound to the requesting identity or SDK signer.

## Other Gaps

- Public models and configuration still permit null values where valid-by-construction types are required.
- Reconciliation and RFQ polling can exceed local deadlines or collapse distinct settlement states.
- Batch, cancellation, and RFQ response validation accepts some malformed payloads.
- Streaming configuration and RTDS lifecycle are not fully controlled by the root SDK configuration.
- Migration, protocol, README, and live-check documentation contain stale or unsupported claims.

Fix the release blockers before pushing or closing the affected GitHub issues.
