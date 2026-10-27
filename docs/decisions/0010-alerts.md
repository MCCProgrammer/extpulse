# 0010. Alerts through an outbox, by email and Discord

**Date:** 2026-10-27 · **Status:** accepted

## Context

Users want to know about a bad review, a spike or a new version without opening the
app, by email or in a Discord channel. Checks run after every collection, and a
collection can run several times a day. Sending can fail (a mail server down, Discord
rate limits), and the same thing must never be announced twice.

## Decision

- **Rules** (`alert_rules`): a type (`LOW_RATING`, `INSTALL_SPIKE`, `NEW_VERSION`,
  `COLLECTION_FAILING`, `WEEKLY_SUMMARY`), an optional threshold, a channel (`EMAIL`,
  `DISCORD`) and a target.
- **Detection in SQL, words in Java.** Each check returns rows with a key naming what
  they are about (`review:42`, `version:7:0.3.1`, `spike:7:2026-10-26`,
  `summary:2026-10-19`). Checks look back further than one run, so a check that didn't
  run is made up for.
- **An outbox** (`alert_events`, unique per rule and key): an alert is written first
  and sent after. A repeated detection hits the unique key and adds nothing; a failed
  send stays unsent and is retried every 5 minutes, up to 5 attempts.
- **Decoupled from the collection:** the collection publishes a `CollectionFinished`
  application event; the alerts package listens. The collection doesn't know alerts exist.
- **Email** through Spring Mail (Mailpit in development, any SMTP in production).
  **Discord** through webhooks: the URL must start with `https://discord.com/api/webhooks/`
  (or `discordapp.com`), checked when saving and again when sending, because the server
  calls a URL a user typed; messages go with `allowed_mentions: {parse: []}`, so a
  review saying `@everyone` pings no one; the webhook token is hidden in API answers.
- **A mail server that is down doesn't make the app unhealthy** (`management.health.mail.enabled: false`).

## Consequences

- Alerts arrive right after the collection that found them, and at most once per rule
  and key, even if collections or checks run again.
- Delivery is at least once: if the app stops between sending and recording it, that
  alert can go out twice. Rare, and acceptable for notifications.
- With more than one instance, two could send the same pending alert; claiming rows
  with `SELECT ... FOR UPDATE SKIP LOCKED` is the next step then.

## Alternatives considered

- **Sending inside the check:** simpler, but a failed send is lost and a repeated check
  sends again.
- **A message broker (Kafka, RabbitMQ):** the right tool between services; inside one
  application the outbox table does the job with the database already there. The v3
  plan moves these events to Kafka.
- **Slack:** same idea as Discord (incoming webhooks); Discord came first because it's
  what the developers this is for use.
