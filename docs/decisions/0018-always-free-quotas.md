# 0018. Quotas: the account only creates what Always Free covers

**Date:** 2026-10-28 · **Status:** accepted

## Context

To get past "Out of host capacity" for the Ampere A1 instance, the account may be
upgraded to Pay As You Go. A paid account charges for anything beyond the Always Free
allowances, and Oracle Cloud has no spending cap: the budget (ADR 0013) emails on the
first cent but stops nothing. ExtPulse must cost nothing, so a mistake should fail
instead of being charged.

## Decision

- **A tenancy-wide quota policy** (`extpulse-always-free`, `infra/oracle/quotas.tf`),
  managed by Terraform with the rest:
  - compute: every core and memory quota zeroed, then Ampere A1 given back up to the free
    4 OCPUs and 24 GB, per availability domain and per region;
  - block and boot volumes: the free 200 GB and 5 backups;
  - Object Storage: 10 GB, half the free 20 GB;
  - databases and load balancers: zero.
- **Order matters:** within a quota, a later statement overrides an earlier one, so each
  family is zeroed first and the free allowance set after.
- **The budget alert stays:** quotas cover the services where a charge is likely, not
  every one of Oracle's services.

## Consequences

- Creating a paid shape, a bigger A1, more disk or a database fails with a quota error,
  whether from Terraform, the console or a stolen key.
- Growing past Always Free is a deliberate change to this file, reviewed in a PR.
- The Always Free AMD micro instances and Autonomous Databases are blocked too; ExtPulse
  doesn't use them.

## Alternatives considered

- **Only the budget alert:** tells about a charge after it happens.
- **A function that stops resources when the budget fires:** complex, and stopping an
  instance doesn't stop the charge for its disk, or for whatever else caused it.
- **A prepaid or virtual card with a low limit:** a refused payment doesn't cancel the
  debt, and the upgrade's card check needs about 100 USD available.
