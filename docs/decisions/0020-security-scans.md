# 0020. Security scans: Trivy on the image, an SBOM, and ZAP on the live site

**Date:** 2026-10-28 · **Status:** accepted

## Context

CodeQL reads the code and Dependabot watches the declared dependencies (ADR 0012), but
nothing looked at what actually ships: the image's operating system packages, the jars
inside it, or what the live site answers. A first ZAP scan found two real things: the
Swagger UI that springdoc brings had a DOMPurify with known vulnerabilities, and no
response set a `Permissions-Policy`.

## Decision

- **Trivy in the `image` job**, on the image before it is published:
  - HIGH and CRITICAL findings, OS and libraries, are reported; from `main` they go to
    Security, Code scanning (SARIF).
  - Only a CRITICAL vulnerability with a fix, in a library the app brings, fails the
    build. That one is ours to fix with a version bump; a base image waiting for its
    own update doesn't block deploys.
- **An SBOM:** Trivy writes a CycloneDX list of everything in the image (the run's
  artifact), and the image pushed from `main` carries BuildKit's SBOM and a provenance
  attestation (which commit, which workflow).
- **OWASP ZAP baseline** every Thursday against <https://extpulse.mccprogrammer.com>:
  passive, it only reads pages any visitor gets. `.zap/rules.tsv` says which findings
  fail the run (known-vulnerable scripts, a missing `Permissions-Policy`), which are
  warnings (Swagger UI without the CSP, ADR 0015) and which are noise.
- **What the scan found, fixed:** Swagger UI pinned to 5.33.2 (DOMPurify 3.4.16) until
  springdoc brings it, and Caddy sets `Permissions-Policy` for features the site never
  uses.

## Consequences

- A vulnerable library shipped in the image or a header lost in a Caddy change is
  noticed within a week at most, and a fixable critical one never reaches production.
- The image job takes longer: Trivy's database download and three scans, about a
  minute.
- The Trivy and ZAP images are pinned in the workflows, where Dependabot doesn't look;
  they are updated by hand.

## Alternatives considered

- **Failing on every HIGH or CRITICAL:** most come from the base image and are fixed
  upstream; deploys would stop for problems the project can't fix.
- **ZAP's full (active) scan:** it attacks forms and parameters, which on production
  means junk accounts, alert rules and load; the baseline's passive checks are the
  part that belongs on a live site.
- **Grype or Docker Scout instead of Trivy:** similar results; Trivy also writes the
  SBOM and is the one most often met in CI pipelines.
