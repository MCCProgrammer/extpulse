-- What each user wants to be told, and where.
CREATE TABLE alert_rules (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       text        NOT NULL CHECK (type IN ('LOW_RATING', 'INSTALL_SPIKE', 'NEW_VERSION',
                                                    'COLLECTION_FAILING', 'WEEKLY_SUMMARY')),
    -- LOW_RATING: the most stars that still alert (default 2). INSTALL_SPIKE: the
    -- smallest day's gain that can be a spike (default 10). Unused by the others.
    threshold  integer,
    channel    text        NOT NULL CHECK (channel IN ('EMAIL', 'DISCORD')),
    -- An email address, or a Discord webhook URL.
    target     text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, type, channel, target)
);

-- Every alert to send, written before it is sent (an outbox). The unique key is what
-- makes an alert go out once: evaluating twice finds the same key and adds nothing.
CREATE TABLE alert_events (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_id    bigint      NOT NULL REFERENCES alert_rules (id) ON DELETE CASCADE,
    -- What the alert is about, e.g. review:42, version:7:0.3.1, summary:2026-10-19.
    dedup_key  text        NOT NULL,
    title      text        NOT NULL,
    message    text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    sent_at    timestamptz,
    attempts   integer     NOT NULL DEFAULT 0,
    last_error text,
    UNIQUE (rule_id, dedup_key)
);

-- Delivery looks for what hasn't been sent yet.
CREATE INDEX alert_events_unsent_idx ON alert_events (id) WHERE sent_at IS NULL;
