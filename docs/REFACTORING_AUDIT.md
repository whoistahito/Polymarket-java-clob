# Refactoring Merge Audit

Reviewed: 2026-08-30

## Merge Status

- Local `develop` at `1957790` contains every non-`main` remote branch.
- `main` remains unchanged at `54533cd`.
- Issue #3 conflicts were resolved using the aggregate branch's newer protocol evidence.

## Verification

`mvn clean verify` passes: **609 tests, 0 failures, 0 errors, 0 skipped**. The dependency and
packaging gates pass, and the README examples compile against the packaged JAR. `mvn -Plive test`
selects the 6 read-only live checks and passes against the production API.

## Ticket Status

Every ticket's follow-up comment has been worked. The release blockers below are closed:

1. #1/#5: listed API keys are a redacted `ApiKey` value type; a blank wire entry is a read failure.
2. #10-#12: the public signing seam is priced and singular, so no leg pair can imply an off-grid
   price; immediate BUY affordability is measured at the price the order is signed at.
3. #14: a success whose `orderID` or `status` is not text is Unknown, and `SignedOrder` refuses a
   value the signer could not have produced.
4. #23: RTDS carries the envelope observation time and the documented comment/reaction fields;
   closing releases the transport and stops delivery.
5. #24: a sub-millisecond interval is refused before any state changes, and a scheduling failure
   restores the inactive state.
6. #26/#28: the Deposit Wallet signer contract is resolved in favour of the documented Resolve
   Quoter Identity table, and acceptance is bound to the requesting identity and the SDK's signer.

## Cross-cutting repairs

- Every reference component of every shipped public record rejects null, guarded by an ArchUnit
  rule proven against a test-only fixture.
- Reconciliation and RFQ polling are bounded by their local deadlines, including the work in
  flight, and `ReconciliationOutcome.Pending` reports what was last observed.
- Batch and cancellation responses are refused rather than coerced when malformed.
- Streaming and RTDS hosts and timeouts come from `PolymarketConfig`.
- `MIGRATION.md`, `SOURCES.md`, `API_COVERAGE.md` and `README.md` describe the code that exists,
  and `MigrationDocTest` plus the fixture-directory enumeration keep them from drifting again.
- The final PR review also binds hand-built immediate plans to their intents, rejects RFQ responses
  that contradict their requests, validates every documented successful order field, refuses
  contradictory cancellation facts, checks GTD lifetime again at submission, and cancels HTTP work
  already in flight when the root closes.

## Known limits

- `streams.json` names `media`, `reactions` and `tradeAsset` on an RTDS comment, but no official
  example gives them a shape. They are recorded as deliberately unmapped rather than invented.
