# 0013. Infrastructure on Oracle Cloud Always Free, with Terraform

**Date:** 2026-10-27 · **Status:** accepted

## Context

ExtPulse must run all the time (the collection runs at 06:00) and should cost nothing.
Free tiers that sleep (Render) would miss the daily run, and their free databases
expire; AWS gives credits for six months, then the account closes. Oracle Cloud's
Always Free tier includes Arm (Ampere A1) virtual machines for the life of the account.

## Decision

- **Oracle Cloud Always Free, home region Madrid** (`eu-madrid-1`, the closest to Portugal).
- **Everything in Terraform** (`infra/oracle`): a VCN with one public subnet, an internet
  gateway, a security list that lets in only TCP 22, 80 and 443, one
  `VM.Standard.A1.Flex` instance (1 OCPU, 6 GB, 50 GB boot volume, Ubuntu 24.04 Arm),
  and a budget with an alert on the first cent charged.
- **cloud-init** for the first boot: Docker, fail2ban, unattended upgrades, a `deploy`
  user for CI, SSH without passwords or root, and the host firewall opened for the web
  (Oracle's Ubuntu images reject all but SSH in iptables).
- **1 OCPU and 6 GB, not the whole free allowance:** Oracle may reclaim free instances
  that look idle (CPU, network and memory under 20% for 7 days); the app, PostgreSQL and
  monitoring keep 6 GB above that line.
- **Credentials stay out of the repository:** the provider reads `~/.oci/config` (an API
  signing key); real values are in an ignored `terraform.tfvars`; the state is local.
- **CI checks formatting and validity**; plan and apply run from the maintainer's machine.

## Consequences

- The server can be rebuilt from code; the data needs its own backups.
- Free A1 capacity is limited: creating the instance can fail with "Out of host
  capacity" and has to be retried. Upgrading the account to Pay As You Go usually ends
  that, while Always Free resources stay free.
- Local state means one maintainer; remote state (Object Storage) comes with a second one.

## Alternatives considered

- **Creating the server by hand in the console:** quicker once, but undocumented and
  not repeatable.
- **AWS (EC2 or ECS + RDS):** the most asked-for cloud, planned for v2 on the six-month credits.
- **The AMD micro shape (1 GB):** also Always Free, too small for the JVM and PostgreSQL.
