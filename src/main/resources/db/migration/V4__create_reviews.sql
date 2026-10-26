-- Reviews from every store, collected with the daily snapshots.

CREATE TABLE reviews (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    listing_id      bigint      NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    -- The review's ID on the store; on Open VSX, which has none, the author (github:login).
    external_id     text        NOT NULL,
    rating          smallint    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    -- NULL for a rating without a comment.
    text            text,
    author          text        NOT NULL,
    product_version text,
    -- When the store says it was written or last edited.
    posted_at       timestamptz NOT NULL,
    -- The publisher's reply, on stores that have replies (the Marketplace).
    reply           text,
    -- When ExtPulse first saw it, which is what makes a review "new" for alerts.
    first_seen_at   timestamptz NOT NULL DEFAULT now(),
    -- Set when the user marks it as read in the inbox.
    read_at         timestamptz,
    UNIQUE (listing_id, external_id)
);
