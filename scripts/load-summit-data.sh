#!/usr/bin/env bash
#
# Load the full Summit schema (forms-summit-example/init-summit.sql) into the
# docker-compose Oracle, as the app user `summit`.
#
# Two gotchas this handles (see the summit-dump-load-gotchas note):
#   1. The dump is fully qualified SUMMIT.<obj>, so it must load AS user `summit`
#      (Oracle uppercases that to schema SUMMIT — its own schema).
#   2. Every CREATE TABLE hardcodes TABLESPACE "SYSTEM", where the app user has no
#      quota. We strip the clause so objects default to USERS.
#   Plus: grant CREATE VIEW (not in the RESOURCE role) as SYSTEM beforehand.
#
# The dump leads with DROP statements, so re-running is safe. ~84 residual errors on
# exotic non-slice objects (types with dependents, CLOB tables) are expected/harmless;
# the migration slice (S_ORD/S_ITEM/S_PRODUCT/S_CUSTOMER/S_EMP/...) loads clean.
#
# Usage:  ./scripts/load-summit-data.sh
set -euo pipefail

CONTAINER="summit-oracle"
SERVICE="FREEPDB1"
SYS_PWD="summit_sys"      # ORACLE_PASSWORD in compose.yaml (SYS/SYSTEM)
APP_USER="summit"
APP_PWD="summit"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/forms-summit-example/init-summit.sql"
SANITIZED="$(mktemp -t summit-init.XXXXXX.sql)"
trap 'rm -f "$SANITIZED"' EXIT

# --- 0. Preconditions -------------------------------------------------------
if [[ ! -f "$SRC" ]]; then
  echo "ERROR: dump not found at $SRC" >&2
  exit 1
fi
if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "ERROR: container '$CONTAINER' is not running. Start it first:" >&2
  echo "         docker compose up -d" >&2
  exit 1
fi

echo "Waiting for Oracle to be healthy..."
for i in {1..60}; do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo unknown)"
  [[ "$status" == "healthy" ]] && break
  sleep 5
  [[ $i -eq 60 ]] && { echo "ERROR: Oracle did not become healthy in time (status=$status)." >&2; exit 1; }
done
echo "Oracle is healthy."

# --- 1. Grant CREATE VIEW to the app user (needs SYSTEM) --------------------
echo "Granting CREATE VIEW to $APP_USER..."
docker exec -i "$CONTAINER" sqlplus -S -L "system/${SYS_PWD}@//localhost:1521/${SERVICE}" <<SQL
WHENEVER SQLERROR CONTINUE NONE
GRANT CREATE VIEW TO ${APP_USER};
EXIT
SQL

# --- 2. Sanitize: strip TABLESPACE "SYSTEM" --------------------------------
echo "Sanitizing dump (stripping TABLESPACE \"SYSTEM\")..."
sed -E 's/TABLESPACE +"SYSTEM"//g' "$SRC" > "$SANITIZED"

# --- 3. Load as the app user ------------------------------------------------
# Stream the script over stdin rather than docker cp + @file: a copied file lands
# owned by root and sqlplus (running as the oracle user) can't read it (SP2-0310).
echo "Loading as $APP_USER (this takes a few minutes — 54k inserts)..."
{
  echo "SET DEFINE OFF"
  echo "SET ECHO OFF"
  echo "SET TERMOUT OFF"
  echo "WHENEVER SQLERROR CONTINUE NONE"
  cat "$SANITIZED"
  echo ""
  echo "COMMIT;"
  echo "EXIT"
} | docker exec -i "$CONTAINER" sqlplus -S -L "${APP_USER}/${APP_PWD}@//localhost:1521/${SERVICE}"

# --- 4. Verify the slice populated -----------------------------------------
echo "Row counts for the migration slice:"
docker exec -i "$CONTAINER" sqlplus -S -L "${APP_USER}/${APP_PWD}@//localhost:1521/${SERVICE}" <<'SQL'
SET HEADING OFF FEEDBACK OFF PAGESIZE 0
SELECT 'S_ORD      ' || COUNT(*) FROM s_ord;
SELECT 'S_ITEM     ' || COUNT(*) FROM s_item;
SELECT 'S_PRODUCT  ' || COUNT(*) FROM s_product;
SELECT 'S_CUSTOMER ' || COUNT(*) FROM s_customer;
SELECT 'S_EMP      ' || COUNT(*) FROM s_emp;
EXIT
SQL

echo "Done."
