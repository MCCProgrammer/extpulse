#!/bin/sh
# Copies the dumps in /backups (backup.sh) to the Object Storage bucket, off this
# server: when the container starts and every day at 03:30 UTC, after the 03:00 backup.
# Only what the bucket doesn't have yet goes up, so a day that failed is caught up the
# next. The server may add and read objects but not replace or delete them; the bucket
# deletes them after 30 days (infra/oracle/backups.tf). `sh /offsite.sh now` copies once
# and exits.
set -u

if [ -z "${RCLONE_CONFIG_OFFSITE_NAMESPACE:-}" ]; then
	echo "offsite copies are OFF: set OCI_NAMESPACE in .env (deploy/README.md)" >&2
	if [ "${1:-}" = "now" ]; then
		exit 1
	fi
	while true; do
		sleep 86400
	done
fi

copy() {
	if rclone copy /backups "offsite:$OFFSITE_BUCKET" --include 'extpulse-*.dump' --size-only --immutable; then
		echo "offsite copy done: $(rclone lsf "offsite:$OFFSITE_BUCKET" | wc -l) dumps in $OFFSITE_BUCKET"
	else
		echo "offsite copy FAILED" >&2
		return 1
	fi
}

if [ "${1:-}" = "now" ]; then
	copy
	exit
fi

copy

while true; do
	now=$(date -u +%s)
	next=$((now - now % 86400 + 3 * 3600 + 30 * 60))
	if [ "$next" -le "$now" ]; then
		next=$((next + 86400))
	fi
	sleep $((next - now))
	copy
done
