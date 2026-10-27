#!/bin/sh
# A pg_dump of the database every day at 03:00 UTC (before the 06:00 collection) into
# /backups, the server's /opt/extpulse/backups. Keeps 14 days. Custom format: restore
# with pg_restore. `sh /backup.sh now` makes one backup and exits.
set -u

backup() {
	stamp=$(date -u +%Y-%m-%dT%H%MZ)
	file="/backups/extpulse-$stamp.dump"
	if pg_dump --format=custom --no-owner --file="$file.partial"; then
		mv "$file.partial" "$file"
		echo "backup $file ($(du -h "$file" | cut -f1))"
	else
		rm -f "$file.partial"
		echo "backup FAILED at $stamp" >&2
	fi
	find /backups -name 'extpulse-*.dump' -mtime +14 -delete
}

if [ "${1:-}" = "now" ]; then
	backup
	exit 0
fi

while true; do
	now=$(date -u +%s)
	next=$(date -u -d '03:00' +%s)
	if [ "$next" -le "$now" ]; then
		next=$(date -u -d 'tomorrow 03:00' +%s)
	fi
	sleep $((next - now))
	backup
done
