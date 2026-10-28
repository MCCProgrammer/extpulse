# Production

What runs on the server, in `/opt/extpulse`, and how to operate it. Why it is built
this way: [ADR 0014](../docs/decisions/0014-deploy-and-operations.md).

| File | What it does |
| --- | --- |
| `compose.yaml` | The app, PostgreSQL, Caddy (HTTPS) and the daily backup |
| `Caddyfile` | HTTPS for the domain, security headers, proxy to the app |
| `deploy.sh` | Starts one image tag, waits for it to be healthy, rolls back if not |
| `backup.sh` | `pg_dump` every day at 03:00 UTC, keeping 14 days |
| `offsite.sh` | Copies the dumps to Object Storage, off the server, at 03:30 UTC |
| `prometheus/` | What Prometheus reads (the app's metrics, the backup times) and the alert rules, with their tests |
| `alertmanager/` | Where alerts go: a Discord channel |
| `grafana/` | Grafana's data source and the ExtPulse dashboard |

On the server only, never in the repository:

| File | What it holds |
| --- | --- |
| `.env` | `POSTGRES_PASSWORD`, `EXTPULSE_AUTH_JWT_SECRET` and `GRAFANA_ADMIN_PASSWORD`, created by deploys; `EXTPULSE_AUTH_REGISTRATION_ALLOW_LIST` and `OCI_NAMESPACE`, added by hand |
| `alertmanager/discord-webhook` | The Discord webhook URL for monitoring alerts; empty until set |
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

5. Send monitoring alerts to Discord: in the channel's settings, Integrations →
   Webhooks → New webhook, copy its URL, then on the server:
   ```sh
   printf '%s\n' 'https://discord.com/api/webhooks/...' > alertmanager/discord-webhook
   docker compose restart alertmanager
   ```

   Alerts set up in the app (low ratings, spikes, the weekly summary) can also go by
   email, through any SMTP service. With Resend: verify the sending domain, create an
   API key with sending access, then on the server write the settings and pipe the key
   from the clipboard on your machine:
   ```sh
   cat >> app.env <<'SETTINGS'
   SPRING_MAIL_HOST=smtp.resend.com
   SPRING_MAIL_PORT=587
   SPRING_MAIL_USERNAME=resend
   SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
   SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true
   EXTPULSE_ALERTS_MAIL_FROM=ExtPulse <alerts@your-domain>
   SETTINGS
   chmod 600 app.env
   # on your machine, with the API key copied:
   pbpaste | ssh deploy@<ip> 'cd /opt/extpulse && { printf "SPRING_MAIL_PASSWORD="; cat; echo; } >> app.env && ./deploy.sh "$(cat .deployed-tag)"'
   ```
   Then send a test from an email alert in the dashboard (Alerts, "Send test").

6. Turn on the copies to Object Storage: the namespace is
   `terraform output -raw backup_namespace` in `infra/oracle`. Then on the server:
   ```sh
   echo 'OCI_NAMESPACE=<namespace>' >> .env
   ./deploy.sh "$(cat .deployed-tag)"
   docker compose logs offsite
   ```
   The log ends with `offsite copy done: N dumps in extpulse-backups`. Right after the
   server is created, its permission can take a few minutes to apply.

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

Logs are JSON, one object per line; `jq` makes them readable and filterable:

```sh
docker compose logs --no-log-prefix app | grep '^{' | jq -r '"\(.["@timestamp"]) \(.log.level) \(.message)"'
docker compose logs --no-log-prefix app | grep '^{' | jq 'select(.log.level == "WARN")'
```

## Monitoring

Prometheus, Alertmanager and Grafana listen on the server's localhost only. Open them
through an SSH tunnel from your machine:

```sh
ssh -i ~/.ssh/extpulse_admin -N -L 3000:localhost:3000 -L 9090:localhost:9090 -L 9093:localhost:9093 ubuntu@<ip>
```

Then <http://localhost:3000> is Grafana (user `admin`, the password is
`GRAFANA_ADMIN_PASSWORD` in `.env`), <http://localhost:9090> Prometheus (targets, alert
rules) and <http://localhost:9093> Alertmanager (alerts firing, silences).

| Alert | When |
| --- | --- |
| ExtPulseDown | Prometheus can't read the app for 5 minutes |
| CollectionLate | No collection has worked for 26 hours |
| ListingsFailing | A store's listings failed in the last collection |
| AlertDeliveryFailing | The app's own alerts (email, Discord) couldn't be sent |
| ServerErrors | Server errors (5xx) for 10 minutes |
| HeapAlmostFull | The JVM heap over 90% for 15 minutes |
| BackupLate | The newest dump on the server is more than 26 hours old |
| OffsiteBackupLate | No copy has reached Object Storage for 26 hours (or nothing was pushed) |

The backup jobs run once a night, so they have no endpoint for Prometheus to read:
`offsite.sh` pushes the time of the newest dump and of the last copy that worked to the
Pushgateway (`pushgateway`, internal only), which Prometheus reads. To test a change
to the rules: `promtool test rules prometheus/alerts.test.yml`.

The dashboard is `grafana/dashboards/extpulse.json`: change it there, not in Grafana
(it is read-only in Grafana and comes back from the file).

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

### Copies off the server

Every dump also goes to the bucket `extpulse-backups` in Object Storage, kept 30 days
([ADR 0017](../docs/decisions/0017-offsite-backups.md)). The server can add and read them,
not replace or delete them. A copy now, and the list in the bucket:

```sh
docker compose exec offsite sh /offsite.sh now
docker compose exec offsite rclone lsl offsite:extpulse-backups
```

If the server is lost, create a new one with Terraform (the permission follows the new
server), deploy, and bring the newest dump back before restoring it as above:

```sh
docker compose run --rm --no-deps -v ./backups:/restore --entrypoint rclone offsite \
  copy offsite:extpulse-backups/extpulse-2026-10-28T0300Z.dump /restore
```

Without a server, any dump can also be downloaded from the Oracle Cloud console:
Storage → Buckets → `extpulse-backups`.
