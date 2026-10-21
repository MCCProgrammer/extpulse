# 0002. One application, split into modules by feature

**Date:** 2026-10-21 · **Status:** accepted

## Context

ExtPulse has a few clear parts: talking to the stores, the daily collection,
stats, reviews, alerts, the public page and accounts. It runs for one person at
first, then for a small number of developers.

## Decision

One Spring Boot application (a monolith), with one package per feature:
`store`, `collection`, `stats`, `reviews`, `alerts`, `publicpage`, `auth`.
Each package owns its classes; others use it only through its public services.

## Consequences

- One thing to build, test, deploy and debug.
- The packages can become separate services later if one part needs to scale on its own.
- Packaging by feature (not by layer: `controllers/`, `services/`, `repositories/`)
  keeps everything about reviews in one place.

## Alternatives considered

- **Microservices:** separate deploys, network calls and data consistency problems,
  with no benefit at this size.
- **Packages by layer:** common in tutorials, but a change to one feature touches
  every folder.
