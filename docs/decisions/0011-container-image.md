# 0011. The container image: multi-stage, layered, Arm, on GHCR

**Date:** 2026-10-27 · **Status:** accepted

## Context

ExtPulse will run on an Oracle Cloud Always Free virtual machine (Ampere A1, Arm) with
Docker Compose, at no cost. The image must be small to update, safe to run on a
server on the internet, and configured without rebuilding it.

## Decision

- **A multi-stage Dockerfile:** the first stage compiles with the JDK and the Maven
  Wrapper; the second has only the JRE (`eclipse-temurin:25-jre-noble`) and the app.
  `docker build .` from a clean checkout is enough; tests run in CI before.
- **Layers by how often they change:** the jar is extracted with Spring Boot's
  `jarmode=tools` into dependencies, loader, snapshot dependencies and application. A
  code change replaces the application layer (under 1 MB), not the libraries (68 MB).
- **A user without privileges** (uid 10001), and the heap sized from the container's
  memory limit (`-XX:MaxRAMPercentage=75`); at 512 MB it uses about 300 MB.
- **Arm only for now**, built natively on GitHub's Arm runner (free for public
  repositories), without emulation.
- **GitHub Container Registry**, next to the code: every build of `main` is pushed as
  `sha-<commit>` and `latest`; pull requests build without pushing.
- **All configuration from environment variables**; no secret is baked into the image.

## Consequences

- A deploy names an exact image (`sha-…`), so rolling back is running the previous tag.
- An x86 server would need a second platform in the build (`linux/amd64`).
- The base image tags follow Java 25's latest update, so rebuilding picks up security fixes.

## Alternatives considered

- **Cloud Native Buildpacks (`spring-boot:build-image`):** no Dockerfile to maintain,
  but less visible and harder to adjust; a Dockerfile is what most teams read.
- **Building the jar on the runner and copying it in:** faster in CI, but the image
  would then depend on what the runner built, instead of building anywhere the same way.
- **Docker Hub:** works the same, but needs another account and token; GHCR uses the
  repository's own `GITHUB_TOKEN`.
