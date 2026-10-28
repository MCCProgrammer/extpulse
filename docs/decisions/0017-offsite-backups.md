# 0017. Backups off the server: Object Storage, write-only for the server

**Date:** 2026-10-27 · **Status:** accepted

## Context

The daily `pg_dump` (ADR 0014) is on the same disk as the database. It helps with a bad
migration or a mistake, not with losing the VM, its disk or the account on it. A copy
has to live somewhere else, cost nothing, and survive whoever might take over the
server: an attacker who can delete the database could otherwise delete its copies too.

## Decision

- **A bucket in Oracle Object Storage** (`extpulse-backups`, private, Standard tier),
  created by Terraform with the rest of the infrastructure. Always Free includes 20 GB;
  a month of dumps is a few megabytes.
- **The server signs in as itself:** a dynamic group that matches the instance's OCID
  and an IAM policy for it (instance principal). There is no access key on the server,
  in `.env` or in GitHub, and nothing to rotate. A rebuilt server gets a new OCID and
  Terraform moves the rule to it.
- **Write-only for the server:** the policy allows `OBJECT_CREATE`, `OBJECT_INSPECT` and
  `OBJECT_READ` on that bucket only; not overwriting or deleting. The bucket's lifecycle
  rule deletes objects after 30 days, run by the Object Storage service with its own
  narrow policy.
- **rclone in its own container** (`offsite`), with its remote defined in environment
  variables. It copies what the bucket doesn't have yet: when it starts (so every
  deploy checks the access works) and every day at 03:30 UTC, half an hour after the
  backup. A day that fails is caught up the next; an existing copy is never replaced
  (`--immutable`).
- **Off until configured:** the bucket's namespace goes in the server's `.env` by hand
  once (`OCI_NAMESPACE`); until then the container only logs that copies are off.

## Consequences

- Losing the VM loses at most the day since the last backup; the dumps of the last 30
  days are in the bucket and can be restored on a new server (`deploy/README.md`).
- A compromised server can add junk to the bucket but cannot remove or alter the
  copies already there.
- The copies are encrypted at rest by Oracle, not by ExtPulse: anyone with access to the
  Oracle account can read them, so its sign-in (with MFA) protects them.
- Both copies are in the same region and account. Losing the Oracle account itself
  still loses everything; a second provider would cover it, at the cost of a key to
  store.
- A failed copy used to show only in the `offsite` container's log. Since 2026-10-28
  `offsite.sh` pushes the time of the newest dump and of the last copy that worked to a
  Pushgateway, and Prometheus alerts when either is more than 26 hours old
  (`BackupLate`, `OffsiteBackupLate`).

## Alternatives considered

- **A customer secret key (S3-compatible API) in `.env`:** works with any S3 tool, but
  it is a long-lived secret on the server, tied to a user, with that user's
  permissions.
- **OCI CLI instead of rclone:** Oracle's own tool, but a far larger image, and a
  loop to write by hand to copy only what is missing.
- **Block volume backups (snapshots of the disk):** Always Free allows five, but they
  copy the whole disk, not a consistent database dump, and are restored as a disk.
- **Encrypting the dumps before upload (rclone crypt):** protects them from the
  account too, but the passphrase must then live outside the server, or losing the
  server loses the means to read its backups.
