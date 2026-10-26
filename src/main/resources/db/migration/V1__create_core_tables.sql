-- Core tables: users own publishers, publishers own extensions, an extension has
-- one listing per store, and a listing has one snapshot per day.

CREATE TABLE users (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         text        NOT NULL,
    password_hash text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);

-- Unique ignoring case: Miguel@x.com and miguel@x.com are the same account.
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));

CREATE TABLE publishers (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);

CREATE TABLE extensions (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    publisher_id bigint      NOT NULL REFERENCES publishers (id) ON DELETE CASCADE,
    display_name text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (publisher_id, display_name)
);

-- The same extension published on several stores has one listing per store.
CREATE TABLE listings (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    extension_id bigint      NOT NULL REFERENCES extensions (id) ON DELETE CASCADE,
    store        text        NOT NULL CHECK (store IN ('VS_MARKETPLACE', 'OPEN_VSX', 'CHROME_WEB_STORE')),
    external_id  text        NOT NULL,
    url          text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (store, external_id)
);

-- PostgreSQL doesn't index foreign keys on its own. The unique constraints above
-- already cover publishers.user_id and extensions.publisher_id; this one doesn't.
CREATE INDEX listings_extension_id_idx ON listings (extension_id);

-- What a store showed for a listing on one day. installs holds installs on the
-- Marketplace, downloads on Open VSX and users on the Chrome Web Store.
CREATE TABLE snapshots (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    listing_id   bigint        NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    taken_on     date          NOT NULL,
    installs     bigint        NOT NULL CHECK (installs >= 0),
    rating_avg   numeric(3, 2) CHECK (rating_avg BETWEEN 0 AND 5),
    rating_count integer       NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
    version      text,
    collected_at timestamptz   NOT NULL DEFAULT now(),
    -- Running the daily collection twice can't add a second row for the same day.
    -- The index behind it also serves "one listing's series, by day".
    UNIQUE (listing_id, taken_on)
);
