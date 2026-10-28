# 0019. Trusted certificate authorities follow Ubuntu's list

**Date:** 2026-10-28 · **Status:** accepted

## Context

On 19 October 2026 the private keys of eight intermediate certificates issued by
eMudhra were disclosed as compromised. Anyone holding them could impersonate an HTTPS
site to a client that still trusts eMudhra's roots. Ubuntu's list had already dropped
the root concerned (emSign ECC Root CA - G3), but the newest Temurin JDK still shipped
its own list with it, and so did the images of Alertmanager, Grafana and rclone.
ExtPulse calls out over HTTPS to the stores, Discord and Oracle Cloud.

## Decision

- **The app image** replaces the JDK's `cacerts` with Ubuntu's trusted authorities
  (`trust extract`), at build time. Each rebuild picks up the base image's security
  updates.
- **Alertmanager, Grafana and the `offsite` container** read the server's own
  `/etc/ssl/certs/ca-certificates.crt`, mounted read-only; unattended-upgrades keeps
  it current.
- Prometheus and Caddy keep their images' lists: Prometheus 3.15.1 dropped the root
  itself, and Caddy only talks to Let's Encrypt.

## Consequences

- A root that Ubuntu distrusts stops being trusted by ExtPulse with the next build or
  restart, without waiting for each project's release.
- Compared with the JDK's list: emSign ECC Root CA - G3, Atos TrustedRoot 2011 and
  Chunghwa's ePKI root are gone, five newer roots are in. Every site ExtPulse calls
  still connects (checked from the image), and a collection of all 12 listings
  succeeded in it.
- A change to the server's list reaches a running container at its next restart
  (every deploy restarts them).
- The production Compose file now expects an Ubuntu (Debian-style) host: running it on
  a Mac needs that mount removed.

## Alternatives considered

- **Deleting only the eMudhra entries with `keytool`:** fixes this case, and the next
  one needs another edit.
- **Waiting for new JDK and image releases:** the JDK's list changes with its releases,
  days or months after the distributions.
- **`USE_SYSTEM_CA_CERTS` in the Temurin image:** adds Ubuntu's authorities to the
  JDK's, but removes none.
