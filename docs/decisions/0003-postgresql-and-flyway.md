# 0003. PostgreSQL, with the schema owned by Flyway

**Date:** 2026-10-21 · **Status:** accepted

## Context

The data is relational (users, publishers, extensions, listings, daily snapshots,
reviews) and the most important rule is "one snapshot per listing per day", which
the database should enforce. Daily gains need window functions (`LAG`).

## Decision

PostgreSQL 17. The schema is created and changed only by Flyway migrations in
`src/main/resources/db/migration` (`V1__...sql`, `V2__...sql`). Hibernate runs with
`ddl-auto: validate`: it checks that the entities match the tables and never changes them.

## Consequences

- Every schema change is a reviewed SQL file in Git, applied the same way in
  development, tests, CI and production.
- A migration that has run is never edited; a fix is a new migration.
- Unique constraints and checks live in the database, so a bug in the code can't
  break them.

## Alternatives considered

- **`ddl-auto: update`:** quick at the start, but it guesses changes, can't drop or
  rename safely and leaves no history.
- **Liquibase:** same idea as Flyway with XML/YAML changelogs; plain SQL is simpler to read.
- **MongoDB:** the data has relations and needs constraints across tables.
