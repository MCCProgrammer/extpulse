# 0006. The daily collection

**Date:** 2026-10-26 · **Status:** accepted

## Context

Once a day, ExtPulse asks the stores for every listing's numbers and saves a
snapshot. Stores fail now and then, the job can run twice (a retry, a manual run, a
restart), and a day is a day in Lisbon, not in UTC.

## Decision

- **Idempotent writes.** Snapshots are saved with `INSERT ... ON CONFLICT (listing_id,
  taken_on) DO UPDATE`. Running the collection again on the same day updates that
  day's numbers; it never adds a row or fails on the unique constraint.
- **Isolated failures.** Each listing is collected on its own. A failure is recorded in
  `collection_run_errors` and the run moves on. A run ends as `SUCCEEDED`, `PARTIAL` or
  `FAILED`.
- **Retries and pacing** with Resilience4j, used directly (the `retry` and
  `ratelimiter` modules, no annotations): up to 4 attempts with waits of 1 s, 4 s and
  16 s, and at most one request per second to each store. A listing the store doesn't
  have is not retried.
- **No transaction around the run.** Store calls can take seconds; each snapshot and
  the run record are saved in their own short transactions, so no database
  connection is held during network calls.
- **JPA for entities, SQL where SQL is the point.** `Listing` and `CollectionRun` are
  JPA entities; the upsert is plain SQL through `JdbcClient`.
- **One `Clock` bean** in `extpulse.zone` (Europe/Lisbon) decides what "today" is, and
  the schedule runs in the same zone.
- A new `catalog` package holds publishers, extensions and listings, which several
  packages read.

## Consequences

- Retrying a whole day is safe: run it again.
- A run cut short (the app stopped) stays `RUNNING`; that is visible in the table.
- With more than one instance, each would run the job. ShedLock (a lock in the
  database) comes with the deploy that needs it.

## Alternatives considered

- **Resilience4j's Spring Boot starter with `@Retry` annotations:** less code, but it
  works through proxies (a call from inside the same class silently skips it) and
  ties the version to Spring Boot's; two small objects are easier to read and to test.
- **Spring Framework 7's own `@Retryable`:** new and built in, but it has no rate
  limiter, and Resilience4j is what most teams already use.
- **Errors as a JSON column:** one table less, but "which listings failed this week"
  becomes JSON querying instead of a join.
