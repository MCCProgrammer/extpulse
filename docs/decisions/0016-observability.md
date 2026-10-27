# 0016. Observability: JSON logs, Prometheus, Grafana and Alertmanager on the server

**Date:** 2026-10-27 · **Status:** accepted

## Context

ExtPulse runs unattended: the collection at 06:00, alert delivery every 5 minutes. A
failure nobody looks at (a store changing its page, the collection not running, the
app restarting in a loop) would go unnoticed for days. It should cost nothing and run
on the same VM.

## Decision

- **Structured logs:** in production the console logs are JSON in the Elastic Common
  Schema (Spring Boot's built-in structured logging), one object per line, kept by
  Docker and read with `jq`. Every line of a collection run carries its run ID (MDC).
- **Metrics with Micrometer, read by Prometheus:** the standard ones (JVM, HTTP
  requests with latency histograms, the connection pool) and ExtPulse's own:
  `extpulse.collection.duration` (by result), `extpulse.collection.listings` (by store
  and outcome), `extpulse.collection.last.success` (read from the database, so a
  restart doesn't reset it) and `extpulse.alerts.delivered` (by channel and outcome).
- **The actuator on its own port** in production (8081), not published and not
  proxied. Only the containers next to the app reach the metrics; the public site has
  no `/actuator` at all, and the deploy's outside check uses the home page.
- **Prometheus** (15 days of data) with six alert rules about ExtPulse itself;
  **Alertmanager** sends them to Discord, with the webhook read from a file that exists
  only on the server; **Grafana** has its data source and dashboard provisioned from
  files in the repository.
- **No public monitoring:** Prometheus, Alertmanager and Grafana listen on the server's
  localhost and are opened through an SSH tunnel.

## Consequences

- A collection that didn't run, a store that started failing or the app being down
  reaches Discord within minutes, without anyone looking.
- About 350 MB more memory on the VM; the 6 GB keep plenty of room.
- Logs stay on the server, bounded by Docker's rotation (5 × 10 MB per service): enough
  to investigate a recent problem, not to search months back.
- The dashboard is code: reviewed in PRs, the same after a rebuild.

## Alternatives considered

- **Grafana Cloud's free tier:** nothing to run, but metrics and logs leave the server,
  and an account and token to manage.
- **Loki (or the ELK stack) for logs:** searchable history across restarts, at the cost
  of another service and its storage; worth it with more than one server.
- **Grafana on a public subdomain behind Caddy:** easier to open, but a login page on
  the internet to protect, for one person who already has SSH.
- **Grafana alerting instead of Alertmanager:** one fewer container, but Prometheus
  rules with Alertmanager are the common pattern, and the rules live in plain files.
