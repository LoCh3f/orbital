#!/bin/bash
# UNVERIFIED — drafted without a Docker daemon to test against (see docker-compose.yml's
# postgres-market-replica comment for the full caveat). Review before relying on it.
#
# Runs once, only against a brand-new data directory (docker-entrypoint-initdb.d scripts never
# re-run on a volume that already has a cluster). Creates the replication role the replica
# connects as, and switches on streaming replication in postgresql.conf/pg_hba.conf — both files
# only take effect because this script finishes before the entrypoint's final `exec postgres`.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE ROLE ${REPLICATION_USER:-replicator} WITH REPLICATION LOGIN PASSWORD '${REPLICATION_PASSWORD:?REPLICATION_PASSWORD must be set}';
EOSQL

{
  echo "host replication ${REPLICATION_USER:-replicator} all md5"
} >> "$PGDATA/pg_hba.conf"

{
  echo "wal_level = replica"
  echo "max_wal_senders = 5"
  echo "max_replication_slots = 5"
  echo "hot_standby = on"
} >> "$PGDATA/postgresql.conf"
