-- One row per run of the daily collection, so failures can be seen and counted.

CREATE TABLE collection_runs (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    started_at         timestamptz NOT NULL,
    finished_at        timestamptz,
    -- RUNNING until it ends; a run that stays RUNNING was cut short (the app stopped).
    status             text        NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    listings_total     integer     NOT NULL DEFAULT 0 CHECK (listings_total >= 0),
    listings_succeeded integer     NOT NULL DEFAULT 0 CHECK (listings_succeeded >= 0),
    listings_failed    integer     NOT NULL DEFAULT 0 CHECK (listings_failed >= 0)
);

-- What failed in a run. A table rather than a JSON column, so "which listings failed
-- this week" is a plain query. store and external_id stay when a listing is deleted.
CREATE TABLE collection_run_errors (
    run_id      bigint NOT NULL REFERENCES collection_runs (id) ON DELETE CASCADE,
    listing_id  bigint REFERENCES listings (id) ON DELETE SET NULL,
    store       text   NOT NULL,
    external_id text   NOT NULL,
    message     text   NOT NULL
);

CREATE INDEX collection_run_errors_run_id_idx ON collection_run_errors (run_id);
CREATE INDEX collection_run_errors_listing_id_idx ON collection_run_errors (listing_id);
