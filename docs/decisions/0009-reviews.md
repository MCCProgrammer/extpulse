# 0009. Reviews: one inbox for every store

**Date:** 2026-10-26 · **Status:** accepted

## Context

A 1-star review on one store is easy to miss when each store shows only its own.
The stores differ: the Marketplace has review IDs and publisher replies; Open VSX has
neither, and allows one review per user per extension; the Chrome Web Store shows
reviews on a separate page, without a stable ID per review.

## Decision

- Reviews are collected with the daily snapshots, by a new `fetchReviews` on every
  store adapter (the latest 100 per listing), and saved in a `reviews` table.
- A review is identified by `(listing_id, external_id)`: the review ID on the
  Marketplace, the author (`github:login`) on Open VSX. Saving is an upsert: a review
  seen again is updated (text, stars, reply), never duplicated, and keeps
  `first_seen_at`, the day ExtPulse first saw it, which alerts will use.
- The Marketplace query uses the default filter. `filterOptions=1`, used in the first
  plan, leaves out ratings without text, and a 1-star without a comment is exactly the
  review that matters.
- `read_at` is the user's: `PATCH /api/reviews/{id}` sets it. A review whose stars or
  text change becomes unread again.
- `GET /api/reviews` filters by unread, unanswered (reviews without a reply, on the
  stores that have replies: not Open VSX), stars, store and extension, newest first,
  with `limit`/`offset` and a total.
- Chrome Web Store reviews (added 2026-10-28) come from the item's reviews page, which
  carries them as JSON for its scripts (`AF_initDataCallback`), each with a review ID,
  stars, text, version, last-edit time and the developer's reply. The page lists the 10
  latest reviews in its language, so `extpulse.stores.chrome-web-store-review-languages`
  (English and Portuguese) is one request each. Ratings without a comment aren't listed
  by the store.
- If reading reviews fails, the day's snapshot is still saved; the listing is counted as
  failed with a `reviews:` message.

## Consequences

- One more request per listing per day, paced like the others; a run takes about twice
  as long (12 listings: about 12 s).
- A deleted review stays in the table as it was last seen; the store's deletions are
  not mirrored yet.
- Only the latest 100 reviews per listing are read each day (10 per language on the
  Chrome Web Store), which is enough unless a listing gets more than that in a day.
  Reviews in a language that isn't configured are not read.
- The Chrome Web Store adds one request per configured language per listing.

## Alternatives considered

- **Reading Chrome reviews from the visible text:** there were no stars and no review ID
  in it, so the key would have been author + date, which breaks when a review is
  edited. The JSON the page carries has both.
- **The store's internal data API (`batchexecute`):** returns 100 reviews per call, but
  it is an undocumented endpoint for the site's own scripts; the page is what any
  visitor gets.
- **A JPA entity for reviews:** the writes are upserts and the reads are filtered joins
  across four tables; plain SQL says both more clearly.
