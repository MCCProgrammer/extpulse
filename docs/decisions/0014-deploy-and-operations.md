# 0014. Deploy and operations: Compose on the server, deploys from CI

**Date:** 2026-10-27 · **Status:** accepted

## Context

The server (ADR 0013) is one Arm VM. It has to run the app, PostgreSQL and HTTPS,
take a new version from every merge to `main` without anyone logging in, recover
from a bad version on its own, and keep copies of the data. The API is public, but
the accounts on this server are only the maintainer's.

## Decision

- **Docker Compose in production** (`deploy/compose.yaml`, project `extpulse-production`):
  the app, PostgreSQL 17 (no published port), Caddy and a backup container. Each one has
  a memory limit and bounded logs (5 × 10 MB).
- **Caddy for HTTPS:** a Let's Encrypt certificate it obtains and renews itself, HTTP
  redirected to HTTPS, HSTS and a few security headers, and the app behind it. The app
  trusts its `X-Forwarded-*` headers.
- **Deploys from CI over SSH:** after the image job publishes `sha-<commit>`, the deploy
  job copies `deploy/` to `/opt/extpulse` and runs `deploy.sh sha-<commit>` as the `deploy`
  user, with its key and the server's host key in the `production` environment's secrets.
  It runs one at a time and is skipped until the `DEPLOY_HOST` variable exists. A final
  check calls `/actuator/health` through the domain.
- **Rollback by health:** `deploy.sh` waits up to about 3 minutes for the container's
  health check; if it fails, it prints the logs and starts the previous tag again.
- **Secrets created on the server:** the first deploy writes `.env` (mode 600) with a
  random database password and JWT key. They are not in GitHub or anywhere else.
- **Registration closed:** `extpulse.auth.registration-open=false` in production, with an
  allow-list for the maintainer's email; anyone else gets 403.
- **Backups:** `pg_dump` in custom format every day at 03:00 UTC, before the collection,
  into `/opt/extpulse/backups`, kept 14 days. Restoring is in `deploy/README.md`.

## Consequences

- A merge to `main` is in production a few minutes later, and a broken one doesn't stay.
- Deploys need a short restart of the app (seconds); with one server there is no
  rolling update.
- Backups are on the same disk as the database: they help with mistakes and bad
  migrations, not with losing the VM. Copies to Object Storage came next (ADR 0017).
- A database migration that the previous version can't run on is not undone by the
  rollback; migrations stay additive.

## Alternatives considered

- **Kubernetes (k3s):** what large teams run, but for one VM and four containers it
  adds more to operate than it solves.
- **Pull-based deploys (Watchtower):** no SSH key in CI, but no health check, rollback
  or record of what was deployed in the pipeline.
- **Nginx and Certbot:** two pieces and a renewal cron where Caddy does both.
- **Secrets in GitHub, copied at each deploy:** more places to leak from, for values
  that never need to leave the server.
