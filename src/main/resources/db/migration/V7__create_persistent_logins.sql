-- "Keep me signed in" on the website: Spring Security's persistent remember-me tokens.
-- The columns are the ones its JdbcTokenRepositoryImpl reads and writes. A login is a
-- series (one per browser that ticked the box) whose token changes every time it is
-- used; a stolen cookie used after the real one stops working, and signing out
-- deletes the account's series.
CREATE TABLE persistent_logins (
    series    text        PRIMARY KEY,
    username  text        NOT NULL,
    token     text        NOT NULL,
    last_used timestamptz NOT NULL
);

CREATE INDEX persistent_logins_username_idx ON persistent_logins (username);
