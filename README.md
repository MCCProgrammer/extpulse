# ExtPulse

[![CI](https://github.com/MCCProgrammer/extpulse/actions/workflows/ci.yml/badge.svg)](https://github.com/MCCProgrammer/extpulse/actions/workflows/ci.yml)
[![CodeQL](https://github.com/MCCProgrammer/extpulse/actions/workflows/codeql.yml/badge.svg)](https://github.com/MCCProgrammer/extpulse/actions/workflows/codeql.yml)

Daily stats, reviews and alerts for VS Code and Chrome extensions, across the
VS Code Marketplace, Open VSX and the Chrome Web Store.

> **Status:** in development. Accounts, publishers, stats, a reviews inbox and alerts (email and Discord) work through the REST API and a server-rendered dashboard, with an opt-in public page and README badges. The daily collection runs on its own. The production setup and automatic deploys are ready; the server is being created.

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
| Security | Spring Security: BCrypt passwords; stateless JWT for the API (OAuth2 resource server); form login with a session, CSRF and a Content Security Policy for the website |
| Website | Thymeleaf pages with charts drawn as SVG on the server (no JavaScript) |
| API docs | springdoc-openapi (OpenAPI 3, Swagger UI at `/swagger-ui.html`) |
| Database | PostgreSQL 17, schema managed by Flyway |
| Build | Maven (through the Maven Wrapper, `./mvnw`) |
| Store clients | Spring RestClient (JDK HttpClient), jsoup for the Chrome Web Store page |
| Resilience | Resilience4j (retries with backoff, one request per second per store) |
| Alerts | Spring Mail (Mailpit in development), Discord webhooks, an outbox table |
| Tests | JUnit 6, Spring Boot Test, Testcontainers, WireMock; JaCoCo coverage gate in CI |
| Container | Multi-stage Docker image (Temurin 25 JRE, layered, non-root, arm64), published to GitHub Container Registry |
| Local services | Docker Compose |
| Infrastructure | Terraform: Oracle Cloud Always Free (network, firewall, an Arm VM with cloud-init, a budget alert) |
| Production | Docker Compose on the VM, Caddy (automatic HTTPS, Let's Encrypt), daily `pg_dump` backups |
| CI/CD | GitHub Actions: build and tests, coverage gate, Arm image, secret scan (gitleaks), deploy over SSH with health check and rollback |
| Code quality | Spring Java Format (checked in the build), CodeQL, SonarQube Cloud, Dependabot |

Why each choice was made: [docs/decisions](docs/decisions).

## Getting started

**You need:** Java 25 and a running Docker. Maven is not needed: `./mvnw` downloads it.

```bash
# Run the app. Spring Boot starts PostgreSQL from compose.yaml and stops it on exit.
./mvnw spring-boot:run

# Check that it is up and connected to the database
curl http://localhost:8080/actuator/health   # {"status":"UP"}

# Run all tests (Testcontainers starts a throwaway PostgreSQL; WireMock stands in for the stores)
./mvnw verify

# The build fails on code that isn't formatted; this formats it
./mvnw spring-javaformat:apply

# Only the tests against the real stores (left out of the normal build)
./mvnw test -Dgroups=live -DexcludedGroups=
```

### Run the production image

```bash
docker build -t extpulse .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/extpulse \
  -e SPRING_DATASOURCE_USERNAME=extpulse -e SPRING_DATASOURCE_PASSWORD=extpulse \
  -e EXTPULSE_AUTH_JWT_SECRET="$(openssl rand -base64 48)" \
  extpulse
```

CI publishes every build of `main` as `ghcr.io/mccprogrammer/extpulse:latest` and
`:sha-<commit>`. All configuration comes from environment variables; nothing secret is
in the image.

### Use the website

With the app running, open <http://localhost:8080>: create an account at `/register`
and sign in. The dashboard is at `/app`; Settings turns on the public page
(`/p/<address>`) and its installs badges.

### Try the API

The full description is at <http://localhost:8080/swagger-ui.html> (log in, then
"Authorize" with the token). With `curl`:

```bash
# Create an account and log in; copy the token from the answer
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email": "you@example.com", "password": "at least 10 characters"}'
curl -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email": "you@example.com", "password": "at least 10 characters"}'
TOKEN=...

# Track a publisher: finds its extensions on the Marketplace and Open VSX
curl -X POST localhost:8080/api/publishers -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name": "MCCProgrammer"}'

# Your extensions with today's installs and this week's gain; one extension over time
curl localhost:8080/api/extensions -H "Authorization: Bearer $TOKEN"
curl "localhost:8080/api/extensions/1/stats?from=2026-10-01&granularity=week" -H "Authorization: Bearer $TOKEN"
```

| Method | Path | What it does |
| --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login` | Create an account; get a token (no login needed) |
| GET | `/api/me` | The account the token belongs to |
| POST, GET | `/api/publishers` | Track a publisher on every store; list yours |
| POST | `/api/extensions` | Add an extension by hand (for Chrome extensions) |
| POST | `/api/extensions/{id}/listings` | Add a listing by its store ID (for example a Chrome item ID) |
| GET | `/api/extensions` | Installs today and gain over 7 days, per extension and store |
| GET | `/api/extensions/{id}/stats` | Installs over time: `from`, `to` (ISO dates), `granularity=day\|week` |
| GET | `/api/reviews` | Reviews from every store, newest first: `unread`, `unanswered`, `maxRating`, `store`, `extensionId`, `limit`, `offset` |
| PATCH | `/api/reviews/{id}` | Mark a review as read: `{"read": true}` |
| GET, POST | `/api/alerts` | Your alerts; add one: `{"type": "LOW_RATING", "channel": "DISCORD", "target": "<webhook URL>"}` |
| DELETE | `/api/alerts/{id}` | Remove an alert |
| POST | `/api/alerts/{id}/test` | Send a test message to the alert's email or channel |
| POST | `/api/admin/collect` | Run the collection now (admins) |

Alert emails in development land in Mailpit, at <http://localhost:8025>. If PostgreSQL
was already running when the app started, Spring Boot doesn't start the other services
in `compose.yaml`; run `docker compose up -d` once.

Errors come back as [Problem Details](https://www.rfc-editor.org/rfc/rfc9457) JSON.
In production, set `EXTPULSE_AUTH_JWT_SECRET` (32+ bytes); without it, tokens stop
working when the app restarts.

## Project structure

```
src/main/java/com/mccprogrammer/extpulse/
├── ExtpulseApplication.java   entry point
├── catalog/      publishers, extensions and their listings on each store
├── store/        adapters for each store (the only code that calls them)
├── collection/   the daily scheduled collection
├── stats/        totals and gains read from the snapshots
├── reviews/      reviews from every store
├── alerts/       alert rules and sending
├── publicpage/   home page, public page and badges
└── auth/         accounts, JWT for the API, sign-in for the website
(each feature package also has its pages: CatalogPages, DashboardPages, ...)
src/main/resources/
├── application.yaml           configuration
├── db/migration/              Flyway migrations (V1__..., V2__...)
├── templates/                 Thymeleaf pages, one folder per feature
└── static/                    the stylesheet and the icon
compose.yaml                   local PostgreSQL, and Mailpit for alert emails
.github/workflows/ci.yml       build, tests, image and deploy on every push
.github/workflows/live-stores.yml  weekly test against the real stores
infra/oracle/                  Terraform for the server (Oracle Cloud Always Free)
deploy/                        what runs on the server, and how to operate it
docs/decisions/                architecture decision records
```

Each package has a `package-info.java` saying what belongs there.
