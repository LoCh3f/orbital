#!/bin/sh
set -eu
# Seed once. Sentinel must be able to atomically replace its runtime configuration.
if [ ! -s /data/sentinel.conf ]; then
  master_ip=''
  until [ -n "$master_ip" ]; do
    master_ip=$(getent hosts redis | awk 'NR == 1 { print $1 }')
    [ -n "$master_ip" ] || sleep 1
  done
  sed "s/sentinel monitor mymaster redis /sentinel monitor mymaster $master_ip /" \
    /seed/sentinel.conf > /data/sentinel.conf
fi
# Monitor numeric addresses: Docker removes stopped containers from DNS, and a blocking
# DNS lookup in Sentinel's event loop otherwise repeatedly triggers TILT instead of failover.
exec redis-sentinel /data/sentinel.conf
