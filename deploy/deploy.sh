#!/usr/bin/env bash
# Deploys one image tag on the server: pulls it, restarts what changed, waits for the app
# to report healthy, and goes back to the previous tag if it doesn't.
#   ./deploy.sh sha-1a2b3c4
set -euo pipefail
cd "$(dirname "$0")"

tag="${1:?usage: deploy.sh <image tag>}"

# Secrets are created here, on the server, the first time each is needed; they never
# leave it.
touch .env
chmod 600 .env
secret() {
	if ! grep -q "^$1=" .env; then
		echo "$1=$2" >>.env
		echo "Created $1 in .env"
	fi
}
secret POSTGRES_PASSWORD "$(openssl rand -hex 24)"
secret EXTPULSE_AUTH_JWT_SECRET "$(openssl rand -base64 48 | tr -d '\n')"
secret GRAFANA_ADMIN_PASSWORD "$(openssl rand -base64 24 | tr -d '\n/+=')"
mkdir -p backups
# Alertmanager reads the Discord webhook from this file (deploy/README.md). Empty until
# it is set: alerts then fail to send, and say so in Alertmanager's log. Readable by the
# container's user; the directory around it is closed to other users of the server.
if [ ! -f alertmanager/discord-webhook ]; then
	install -m 644 /dev/null alertmanager/discord-webhook
fi

previous="$(cat .deployed-tag 2>/dev/null || true)"

# Every docker compose command needs the tag (compose.yaml requires it), not only these two.
up() {
	export EXTPULSE_IMAGE_TAG="$1"
	docker compose pull --quiet app
	docker compose up -d --remove-orphans
	# Compose recreates a service when its definition changes, not when a mounted file
	# does: pick up the Caddyfile (a reload, without dropping connections), backup.sh and
	# the monitoring configuration.
	docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null
	docker compose restart backup prometheus alertmanager grafana >/dev/null
}

healthy() {
	for _ in $(seq 1 40); do
		status="$(docker inspect --format '{{.State.Health.Status}}' "$(docker compose ps -q app)" 2>/dev/null || true)"
		if [ "$status" = "healthy" ]; then
			return 0
		fi
		sleep 5
	done
	return 1
}

up "$tag"
if healthy; then
	echo "$tag" >.deployed-tag
	# Old images go, but the last week's stay, for a quick rollback by hand.
	docker image prune --all --force --filter "until=168h" >/dev/null
	echo "Deployed $tag"
	exit 0
fi

echo "$tag did not become healthy; last app logs:"
docker compose logs --tail 80 app
if [ -n "$previous" ] && [ "$previous" != "$tag" ]; then
	echo "Rolling back to $previous"
	up "$previous"
	healthy && echo "Rolled back to $previous" || echo "Rollback to $previous is not healthy either"
fi
exit 1
