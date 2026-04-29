#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${VSPICY_DB_HOST:-127.0.0.1}"
DB_PORT="${VSPICY_DB_PORT:-3306}"
DB_NAME="${VSPICY_DB_NAME:-vspicy}"
DB_USER="${VSPICY_DB_USERNAME:-vspicy}"
DB_PASSWORD="${VSPICY_DB_PASSWORD:-}"

if [ -z "$DB_PASSWORD" ]; then
  echo "[WARN] VSPICY_DB_PASSWORD is empty."
  echo 'Set it first: export VSPICY_DB_PASSWORD="your-password"'
  exit 0
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "[WARN] mysql client not found. Use Navicat to run sql/58-vspicy-schema-verify.sql."
  exit 0
fi

ROOT_DIR="$(pwd)"
SQL_FILE=""

if [ -f "$ROOT_DIR/../sql/58-vspicy-schema-verify.sql" ]; then
  SQL_FILE="$ROOT_DIR/../sql/58-vspicy-schema-verify.sql"
elif [ -f "$ROOT_DIR/sql/58-vspicy-schema-verify.sql" ]; then
  SQL_FILE="$ROOT_DIR/sql/58-vspicy-schema-verify.sql"
else
  echo "[MISS] sql/58-vspicy-schema-verify.sql not found."
  exit 1
fi

echo "== VSpicy Schema Verify =="
echo "host=$DB_HOST port=$DB_PORT db=$DB_NAME user=$DB_USER"
echo

mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "-p$DB_PASSWORD" "$DB_NAME" < "$SQL_FILE"
