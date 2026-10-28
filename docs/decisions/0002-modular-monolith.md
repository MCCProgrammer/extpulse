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

Since 2026-10-28 this is checked, not only intended: Spring Modulith treats each
top-level package as a module, and `ModularityTests` fails on a cycle between modules
or on a dependency a module's `package-info.java` doesn't allow
(`@ApplicationModule(allowedDependencies = ...)`). `store` and `auth` depend on no other
module; the others list theirs.

## Consequences

- One thing to build, test, deploy and debug.
- The packages can become separate services later if one part needs to scale on its own.
- Packaging by feature (not by layer: `controllers/`, `services/`, `repositories/`)
  keeps everything about reviews in one place.
- A new dependency between features is a one-line change to a `package-info.java`,
  visible in review, instead of an import nobody notices.

## Alternatives considered

- **Microservices:** separate deploys, network calls and data consistency problems,
  with no benefit at this size.
- **Packages by layer:** common in tutorials, but a change to one feature touches
  every folder.
