#!/bin/sh
# Copies the dumps in /backups (backup.sh) to the Object Storage bucket, off this
# server: when the container starts and every day at 03:30 UTC, after the 03:00 backup.
# Only what the bucket doesn't have yet goes up, so a day that failed is caught up the
# next. The server may add and read objects but not replace or delete them; the bucket
# deletes them after 30 days (infra/oracle/backups.tf). `sh /offsite.sh now` copies once
# and exits.
# After each copy, the time of the newest dump and of the last copy that worked go to the
# Pushgateway, where Prometheus reads them (alerts BackupLate and OffsiteBackupLate).
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

# push <copied dumps, or nothing when the copy failed>
push() {
	newest=0
	for dump in /backups/extpulse-*.dump; do
		[ -f "$dump" ] || continue
		modified=$(date -r "$dump" +%s)
		if [ "$modified" -gt "$newest" ]; then
			newest=$modified
		fi
	done
	{
		if [ "$newest" -gt 0 ]; then
			echo "# TYPE extpulse_backup_newest_dump_timestamp_seconds gauge"
			echo "extpulse_backup_newest_dump_timestamp_seconds $newest"
		fi
		if [ -n "${1:-}" ]; then
			echo "# TYPE extpulse_offsite_last_success_timestamp_seconds gauge"
			echo "extpulse_offsite_last_success_timestamp_seconds $(date -u +%s)"
			echo "# TYPE extpulse_offsite_dumps gauge"
			echo "extpulse_offsite_dumps $1"
		fi
	} >/tmp/metrics
	# POST replaces only the metrics sent: a failed copy keeps the last success's time.
	if [ -s /tmp/metrics ] && ! wget -q -O /dev/null --post-file=/tmp/metrics "$PUSHGATEWAY_URL/metrics/job/offsite"; then
		echo "could not send the backup times to the Pushgateway" >&2
	fi
}

copy() {
	if rclone copy /backups "offsite:$OFFSITE_BUCKET" --include 'extpulse-*.dump' --size-only --immutable; then
		dumps=$(rclone lsf "offsite:$OFFSITE_BUCKET" | wc -l)
		echo "offsite copy done: $dumps dumps in $OFFSITE_BUCKET"
		push "$dumps"
	else
		echo "offsite copy FAILED" >&2
		push
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
