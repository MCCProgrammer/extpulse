# 0012. Quality gates: formatting, static analysis, secrets and dependencies

**Date:** 2026-10-27 · **Status:** accepted

## Context

The repository is public and changes land through pull requests checked by CI. Tests
and the coverage gate (JaCoCo) say the code works; they don't say it
is consistently formatted, free of known security patterns, free of committed secrets,
or on dependencies without known vulnerabilities. Every tool here is free for public
repositories.

## Decision

- **Spring Java Format**, checked in the `validate` phase: unformatted code fails the
  build; `./mvnw spring-javaformat:apply` fixes it. The formatting commit is listed in
  `.git-blame-ignore-revs`.
- **CodeQL** on every pull request, on `main` and weekly, for the Java code and the
  GitHub Actions workflows (`security-and-quality` queries, no build needed).
- **gitleaks** in CI over the whole history, as a pinned binary checked against its
  published SHA-256.
- **SonarQube Cloud** (sonarcloud.io) analysis with JaCoCo coverage, run by CI once the
  `SONAR_TOKEN` secret exists.
- **Dependabot** weekly for Maven, GitHub Actions, the Dockerfile's base images and
  compose.yaml, with minor and patch updates grouped.
- **Branch protection on `main`:** changes through pull requests, with CI passing; no
  force pushes or deletion.

## Consequences

- A pull request shows formatting, tests, coverage, image build, secrets and CodeQL
  before it can merge.
- One reformat touched 59 files once; after that, diffs only show real changes.
- Dependabot opens a few pull requests a week that need merging.

## Alternatives considered

- **Spotless with Palantir or Google Java Format:** widely used too, but they use
  spaces; switching from tabs would rewrite every line. Spring Java Format matches the
  code's Spring style.
- **Checkstyle:** rules on top of formatting (naming, imports); can come later.
- **The gitleaks GitHub Action:** convenient, but needs a licence for organizations; the
  binary does the same with nothing to trust beyond the checksum.
