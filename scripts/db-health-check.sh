#!/bin/bash
# ============================================
# DrinkSync Database Health Check
# Runs as a Kubernetes CronJob
# ============================================
# Checks:
#   1. Connection test (can we connect?)
#   2. Query response time (is it slow?)
#   3. Active connections (approaching max?)
#   4. Database size (is it growing unexpectedly?)
#   5. Long-running queries (stuck transactions?)
#   6. Table bloat / dead tuples (needs vacuum?)
#
# Exit codes:
#   0 = all healthy
#   1 = warning (degraded but functional)
#   2 = critical (action needed)
# ============================================

set -uo pipefail

# Configuration (from environment variables)
DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-smarteventbar}"
DB_USER="${DB_USER:-smarteventbar}"
DB_PASSWORD="${DB_PASSWORD:-}"

# Thresholds
MAX_RESPONSE_TIME_MS="${MAX_RESPONSE_TIME_MS:-500}"
MAX_CONNECTIONS_PERCENT="${MAX_CONNECTIONS_PERCENT:-80}"
MAX_DB_SIZE_GB="${MAX_DB_SIZE_GB:-4}"
LONG_QUERY_SECONDS="${LONG_QUERY_SECONDS:-30}"
MAX_DEAD_TUPLES="${MAX_DEAD_TUPLES:-10000}"

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

OVERALL_STATUS=0
REPORT=""

log_ok() {
  REPORT+="[OK]      $1\n"
}

log_warn() {
  REPORT+="[WARNING] $1\n"
  if [ "$OVERALL_STATUS" -lt 1 ]; then OVERALL_STATUS=1; fi
}

log_fail() {
  REPORT+="[CRITICAL] $1\n"
  OVERALL_STATUS=2
}

log_info() {
  REPORT+="[INFO]    $1\n"
}

# Build connection string
export PGPASSWORD="$DB_PASSWORD"
PSQL="psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -A"

echo "============================================"
echo "  DrinkSync DB Health Check"
echo "  $(date '+%Y-%m-%d %H:%M:%S %Z')"
echo "  Target: $DB_HOST:$DB_PORT/$DB_NAME"
echo "============================================"

# ============================================
# Check 1: Connection Test
# ============================================
CONNECTION_START=$(date +%s%N)
if $PSQL -c "SELECT 1;" > /dev/null 2>&1; then
  CONNECTION_END=$(date +%s%N)
  CONNECTION_MS=$(( (CONNECTION_END - CONNECTION_START) / 1000000 ))
  log_ok "Connection successful (${CONNECTION_MS}ms)"
else
  log_fail "Cannot connect to database at $DB_HOST:$DB_PORT"
  echo -e "$REPORT"
  echo ""
  echo "OVERALL STATUS: CRITICAL"
  exit 2
fi

# ============================================
# Check 2: Query Response Time
# ============================================
QUERY_START=$(date +%s%N)
$PSQL -c "SELECT count(*) FROM orders;" > /dev/null 2>&1
QUERY_END=$(date +%s%N)
QUERY_MS=$(( (QUERY_END - QUERY_START) / 1000000 ))

if [ "$QUERY_MS" -lt "$MAX_RESPONSE_TIME_MS" ]; then
  log_ok "Query response time: ${QUERY_MS}ms (threshold: ${MAX_RESPONSE_TIME_MS}ms)"
else
  log_warn "Slow query response: ${QUERY_MS}ms (threshold: ${MAX_RESPONSE_TIME_MS}ms)"
fi

# ============================================
# Check 3: Active Connections
# ============================================
ACTIVE_CONNS=$($PSQL -c "SELECT count(*) FROM pg_stat_activity WHERE datname = '$DB_NAME';")
MAX_CONNS=$($PSQL -c "SHOW max_connections;")
CONN_PERCENT=$(( ACTIVE_CONNS * 100 / MAX_CONNS ))

if [ "$CONN_PERCENT" -lt "$MAX_CONNECTIONS_PERCENT" ]; then
  log_ok "Active connections: $ACTIVE_CONNS / $MAX_CONNS (${CONN_PERCENT}%)"
else
  log_warn "High connection usage: $ACTIVE_CONNS / $MAX_CONNS (${CONN_PERCENT}%) — threshold: ${MAX_CONNECTIONS_PERCENT}%"
fi

# ============================================
# Check 4: Database Size
# ============================================
DB_SIZE_BYTES=$($PSQL -c "SELECT pg_database_size('$DB_NAME');")
DB_SIZE_MB=$(( DB_SIZE_BYTES / 1024 / 1024 ))
DB_SIZE_GB_FLOAT=$(echo "scale=2; $DB_SIZE_BYTES / 1024 / 1024 / 1024" | bc 2>/dev/null || echo "0")

if [ "$DB_SIZE_MB" -lt $(( MAX_DB_SIZE_GB * 1024 )) ]; then
  log_ok "Database size: ${DB_SIZE_MB}MB (threshold: ${MAX_DB_SIZE_GB}GB)"
else
  log_warn "Database size growing: ${DB_SIZE_MB}MB (threshold: ${MAX_DB_SIZE_GB}GB)"
fi

# ============================================
# Check 5: Long-Running Queries
# ============================================
LONG_QUERIES=$($PSQL -c "
  SELECT count(*)
  FROM pg_stat_activity
  WHERE state = 'active'
    AND query NOT LIKE '%pg_stat_activity%'
    AND now() - query_start > interval '${LONG_QUERY_SECONDS} seconds';
")

if [ "$LONG_QUERIES" -eq 0 ]; then
  log_ok "No long-running queries (threshold: >${LONG_QUERY_SECONDS}s)"
else
  log_warn "$LONG_QUERIES queries running longer than ${LONG_QUERY_SECONDS}s"
  # Show the long queries
  LONG_QUERY_DETAILS=$($PSQL -c "
    SELECT pid, now() - query_start AS duration, left(query, 80)
    FROM pg_stat_activity
    WHERE state = 'active'
      AND query NOT LIKE '%pg_stat_activity%'
      AND now() - query_start > interval '${LONG_QUERY_SECONDS} seconds'
    LIMIT 5;
  ")
  log_info "Long queries: $LONG_QUERY_DETAILS"
fi

# ============================================
# Check 6: Dead Tuples (needs VACUUM?)
# ============================================
DEAD_TUPLES=$($PSQL -c "
  SELECT COALESCE(sum(n_dead_tup), 0)
  FROM pg_stat_user_tables;
")

if [ "$DEAD_TUPLES" -lt "$MAX_DEAD_TUPLES" ]; then
  log_ok "Dead tuples: $DEAD_TUPLES (threshold: $MAX_DEAD_TUPLES)"
else
  log_warn "High dead tuple count: $DEAD_TUPLES — consider running VACUUM ANALYZE"
fi

# ============================================
# Check 7: Replication Status (if applicable)
# ============================================
IS_REPLICA=$($PSQL -c "SELECT pg_is_in_recovery();" 2>/dev/null || echo "f")
if [ "$IS_REPLICA" = "t" ]; then
  REPLICATION_LAG=$($PSQL -c "
    SELECT EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp()))::int;
  ")
  if [ "$REPLICATION_LAG" -lt 30 ]; then
    log_ok "Replication lag: ${REPLICATION_LAG}s"
  else
    log_warn "Replication lag: ${REPLICATION_LAG}s (>30s)"
  fi
else
  log_info "Not a replica — replication check skipped"
fi

# ============================================
# Check 8: Table-specific checks (DrinkSync)
# ============================================
ORDER_COUNT=$($PSQL -c "SELECT count(*) FROM orders;" 2>/dev/null || echo "0")
SESSION_COUNT=$($PSQL -c "SELECT count(*) FROM session;" 2>/dev/null || echo "0")
NOTIFICATION_LOG_COUNT=$($PSQL -c "SELECT count(*) FROM notification_log;" 2>/dev/null || echo "0")

log_info "Table stats: orders=$ORDER_COUNT, sessions=$SESSION_COUNT, notifications=$NOTIFICATION_LOG_COUNT"

# ============================================
# Report
# ============================================
echo ""
echo -e "$REPORT"
echo ""
echo "============================================"
case $OVERALL_STATUS in
  0) echo -e "  OVERALL STATUS: ${GREEN}HEALTHY${NC}" ;;
  1) echo -e "  OVERALL STATUS: ${YELLOW}WARNING${NC}" ;;
  2) echo -e "  OVERALL STATUS: ${RED}CRITICAL${NC}" ;;
esac
echo "============================================"

exit $OVERALL_STATUS
