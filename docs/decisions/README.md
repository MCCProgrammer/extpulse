# Architecture decision records

Each file records one decision: the context, what was decided, what it costs and
what else was considered. They are numbered and never rewritten; a decision that
changes gets a new record that replaces the old one.

| # | Decision |
| --- | --- |
| [0001](0001-java-25-and-spring-boot-4.md) | Java 25 and Spring Boot 4.1 |
| [0002](0002-modular-monolith.md) | One application, split into modules by feature |
| [0003](0003-postgresql-and-flyway.md) | PostgreSQL, with the schema owned by Flyway |
| [0004](0004-docker-compose-and-testcontainers.md) | Docker Compose for development, Testcontainers for tests |
| [0005](0005-store-adapters.md) | Store adapters, and reading the Chrome Web Store page |
| [0006](0006-daily-collection.md) | The daily collection: idempotent, isolated failures, retries and pacing |
| [0007](0007-accounts-and-jwt.md) | Accounts and stateless login with self-issued JWTs |
| [0008](0008-catalog-and-stats.md) | The catalog API and how stats are computed |
| [0009](0009-reviews.md) | Reviews: one inbox for every store |
