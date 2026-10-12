#!/bin/bash
# UNVERIFIED — drafted without a Docker daemon to test against. Review before relying on it.
#
# Wraps the official postgres image's own entrypoint. On a brand-new (empty) data volume, clones
# the primary via pg_basebackup -R (which writes standby.signal + primary_conninfo for us — no
# manual recovery.conf needed on PG12+) before handing off to the normal entrypoint, which then
# sees an already-initialized data directory and starts Postgres straight into standby mode
# instead of running initdb. On a volume that already has a cloned/running standby, this clone
# step is skipped and we just hand off immediately — safe to restart this container repeatedly.
set -euo pipefail

: "${PRIMARY_HOST:?PRIMARY_HOST must be set to the primary service name}"
: "${REPLICATION_PASSWORD:?REPLICATION_PASSWORD must be set}"
REPLICATION_USER="${REPLICATION_USER:-replicator}"
PRIMARY_PORT="${PRIMARY_PORT:-5432}"

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "replica-entrypoint: no existing data directory, waiting for primary '$PRIMARY_HOST'..."
  until pg_isready -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U "$REPLICATION_USER" >/dev/null 2>&1; do
    sleep 2
  done

  echo "replica-entrypoint: cloning from primary via pg_basebackup..."
  PGPASSWORD="$REPLICATION_PASSWORD" pg_basebackup \
    -h "$PRIMARY_HOST" \
    -p "$PRIMARY_PORT" \
    -D "$PGDATA" \
    -U "$REPLICATION_USER" \
    -Fp -Xs -P -R
else
  echo "replica-entrypoint: existing data directory found, skipping clone."
fi

exec docker-entrypoint.sh postgres
