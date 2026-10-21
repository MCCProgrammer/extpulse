# ExtPulse

Daily stats, reviews and alerts for VS Code and Chrome extensions, across the
VS Code Marketplace, Open VSX and the Chrome Web Store.

> **Status:** project setup. Nothing is collected yet.

## Why

Each store shows only its own numbers, with no history. If you publish the same
extension on the Marketplace and on Open VSX, nobody tells you the total, how many
installs you gained this week or that a 1★ review just arrived. ExtPulse saves one
snapshot per extension, per store, per day, and builds stats and alerts on top.

## Planned (v1)

- Find all extensions of a publisher on the three stores
- Daily collection at 06:00 (Europe/Lisbon), one snapshot per listing per day
- Combined totals, daily and weekly gains, split by store
- One inbox for the reviews of every store
- Alerts: low ratings, install spikes, new versions, weekly summary
- Public portfolio page and a combined installs badge for READMEs

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator) |
| Database | PostgreSQL 17, schema managed by Flyway |
| Build | Maven (through the Maven Wrapper, `./mvnw`) |
| Tests | JUnit 5, Spring Boot Test, Testcontainers |
| Local services | Docker Compose |
| CI | GitHub Actions |

Why each choice was made: [docs/decisions](docs/decisions).

## Getting started

**You need:** Java 25 and a running Docker. Maven is not needed: `./mvnw` downloads it.

```bash
# Run the app. Spring Boot starts PostgreSQL from compose.yaml and stops it on exit.
./mvnw spring-boot:run

# Check that it is up and connected to the database
curl http://localhost:8080/actuator/health   # {"status":"UP"}

# Run all tests (Testcontainers starts a throwaway PostgreSQL)
./mvnw verify
```

## Project structure

```
src/main/java/com/mccprogrammer/extpulse/
├── ExtpulseApplication.java   entry point
├── store/        adapters for each store (the only code that calls them)
├── collection/   the daily scheduled collection
├── stats/        totals and gains read from the snapshots
├── reviews/      reviews from every store
├── alerts/       alert rules and sending
├── publicpage/   public page and badge
└── auth/         accounts and JWT
src/main/resources/
├── application.yaml           configuration
└── db/migration/              Flyway migrations (V1__..., V2__...)
compose.yaml                   local PostgreSQL
.github/workflows/ci.yml       build and tests on every push
docs/decisions/                architecture decision records
```

Each package has a `package-info.java` saying what belongs there.
