# 0004. Docker Compose for development, Testcontainers for tests

**Date:** 2026-10-21 · **Status:** accepted

## Context

The application needs PostgreSQL to start. Installing PostgreSQL by hand differs on
every machine, and tests against an in-memory database (H2) behave differently from
PostgreSQL (window functions, `ON CONFLICT`, types).

## Decision

- **Development:** `compose.yaml` describes PostgreSQL 17. With
  `spring-boot-docker-compose`, `./mvnw spring-boot:run` starts it, connects to it
  and stops it on exit; no datasource settings are needed.
- **Tests:** Testcontainers starts a throwaway PostgreSQL 17 for the test run
  (`TestcontainersConfiguration`, `@ServiceConnection`).
- Both use the same image version.

## Consequences

- Docker must be running to start the app or run the tests.
- Tests run against the real database engine, so what passes in tests works in production.
- CI needs nothing extra: GitHub's Ubuntu runners have Docker.

## Alternatives considered

- **H2 in memory:** faster, but not PostgreSQL; the queries this project needs would
  behave differently or not work.
- **A PostgreSQL installed on the machine:** works, but every developer and the CI
  must set it up by hand.
