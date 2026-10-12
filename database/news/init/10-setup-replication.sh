#!/bin/bash
# UNVERIFIED — see database/market/init/10-setup-replication.sh (identical script, mirrored here
# because docker-compose mounts one init directory per Postgres instance) for the full caveat.
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
