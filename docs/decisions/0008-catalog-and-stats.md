# 0008. The catalog API and how stats are computed

**Date:** 2026-10-26 · **Status:** accepted

## Context

A user adds a publisher once and expects every extension it has on every store to
be tracked. The same extension appears on the Marketplace as
`MCCProgrammer.diff-checker` and on Open VSX as `MCCProgrammer/diff-checker`. Stats
must hold up against real data: a store missing on some days, a listing added weeks
after the others, and install counts that go down.

## Decision

- **Adding a publisher discovers everything at once.** `POST /api/publishers` asks
  every store, groups the results by a store-independent key (the VS Code extension
  ID in lowercase, set by each store adapter) and saves one extension with one listing
  per store. The store calls run before the transaction; if any store fails, nothing
  is saved (502). Chrome extensions, which can't be found by publisher, are added with
  `POST /api/extensions` and `POST /api/extensions/{id}/listings` by item ID, after
  checking that the store has it.
- **Stats are computed on read, in SQL,** from `snapshots`. There are no
  pre-aggregated tables to keep in sync. Each lookup is "the latest snapshot of a
  listing on or before a day", which the `(listing_id, taken_on)` index answers directly.
- **A missing day carries the last known number** (`JOIN LATERAL ... taken_on <= day
  ORDER BY taken_on DESC LIMIT 1`), so a failed collection doesn't show as a drop.
- **Gains are per listing, then summed** (`LAG(...) OVER (PARTITION BY listing_id)`).
  A listing's first day has no gain, so adding Open VSX later doesn't count all its
  installs as one day's growth. Gains can be negative.
- **Weeks are ISO weeks** (Monday first), grouped from the daily series in Java.
- **Another user's extension answers 404**, the same as a missing one.

## Consequences

- One account per listing: `listings` is unique by store and ID, so two accounts can't
  track the same extension (409). Sharing would need listings separate from their
  owners, which isn't needed while each developer tracks their own extensions.
- Stats only exist from the first collection on; there is no backfill of history.
- A query per request: if the overview gets slow with many users, a daily summary
  table filled by the collection is the next step.

## Alternatives considered

- **Pre-aggregated daily totals per extension:** faster reads, but one more thing to
  keep correct when a listing is added or a day is re-collected.
- **Computing gains in Java:** possible, but window functions do it in one pass where
  the data is, and it's a good SQL showcase.
- **Treating a missing day as zero or skipping it:** zero shows false drops; skipping
  makes the next day's gain cover two days.
