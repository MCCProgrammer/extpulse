# Production

What runs on the server, in `/opt/extpulse`, and how to operate it. Why it is built
this way: [ADR 0014](../docs/decisions/0014-deploy-and-operations.md).

| File | What it does |
| --- | --- |
| `compose.yaml` | The app, PostgreSQL, Caddy (HTTPS) and the daily backup |
| `Caddyfile` | HTTPS for the domain, security headers, proxy to the app |
| `deploy.sh` | Starts one image tag, waits for it to be healthy, rolls back if not |
| `backup.sh` | `pg_dump` every day at 03:00 UTC, keeping 14 days |

On the server only, never in the repository:

| File | What it holds |
| --- | --- |
| `.env` | `POSTGRES_PASSWORD` and `EXTPULSE_AUTH_JWT_SECRET`, created by the first deploy; `EXTPULSE_AUTH_REGISTRATION_ALLOW_LIST`, added by hand |
| `app.env` | Optional settings, such as the SMTP provider (`SPRING_MAIL_*`) |
| `.deployed-tag` | The tag running now |
| `backups/` | The dumps |

## Set up deploys (once)

1. Create the server with Terraform (`infra/oracle`) and point the domain's A record at
   its public IP (DNS only, so Caddy can get the certificate).
2. In the repository's settings, create the environment `production` with two secrets:
   - `DEPLOY_SSH_KEY`: the private key of the `deploy` user;
   - `DEPLOY_KNOWN_HOSTS`: the server's line from `ssh-keyscan -t ed25519 <ip>`,
     checked against the fingerprint the server prints in its console log.
3. Add the repository variable `DEPLOY_HOST` with the IP. The next push to `main` deploys.
4. After the first deploy, open registration for the maintainer only, and restart:
   ```sh
   echo 'EXTPULSE_AUTH_REGISTRATION_ALLOW_LIST=you@example.com' >> .env
   ./deploy.sh "$(cat .deployed-tag)"
   ```

## Everyday

All from `/opt/extpulse`, as `deploy`. Compose needs the tag, so set it first:

```sh
export EXTPULSE_IMAGE_TAG="$(cat .deployed-tag)"
docker compose ps
docker compose logs -f app
```

Deploy or roll back to any published tag by hand (the last week's images are kept):

```sh
./deploy.sh sha-1a2b3c4
```

## Backups

A backup now, and the list:

```sh
docker compose exec backup sh /backup.sh now
ls -lh backups/
```

Restore one (this replaces the data; stop the app so it doesn't write meanwhile):

```sh
docker compose stop app
docker compose exec -T postgres pg_restore -U extpulse -d extpulse --clean --if-exists --no-owner \
  < backups/extpulse-2026-10-28T0300Z.dump
docker compose start app
```

To look inside a dump without touching production, restore it into another database:
`createdb -U extpulse restore_test`, then the same `pg_restore` with `-d restore_test`.
