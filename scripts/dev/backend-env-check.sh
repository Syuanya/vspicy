#!/usr/bin/env bash
set -euo pipefail

echo "== Backend Environment Check =="

echo
echo "-- repo --"
pwd
if [ -d ".git" ]; then
  git branch --show-current || true
  git status --short || true
else
  echo "[WARN] .git not found"
fi

echo
echo "-- required files --"
for file in ".env.example" "config/application-local.example.yml" "pom.xml"; do
  if [ -f "$file" ]; then
    echo "[OK]   $file"
  else
    echo "[MISS] $file"
  fi
done

echo
echo "-- local files --"
for file in ".env" "config/application-local.yml"; do
  if [ -f "$file" ]; then
    echo "[OK]   $file"
  else
    echo "[WARN] $file not found. Copy from example if needed."
  fi
done

echo
echo "-- java / maven --"
if command -v java >/dev/null 2>&1; then
  java -version 2>&1 | head -n 1
else
  echo "[MISS] java"
fi

if [ -f "./mvnw" ]; then
  echo "[OK] Maven wrapper"
elif command -v mvn >/dev/null 2>&1; then
  mvn -version | head -n 1
else
  echo "[MISS] mvn"
fi

echo
echo "-- configured env preview --"
for key in SPRING_PROFILES_ACTIVE VSPICY_DB_HOST VSPICY_DB_NAME VSPICY_REDIS_HOST VSPICY_MINIO_ENDPOINT VSPICY_MINIO_BUCKET VSPICY_ROCKETMQ_NAME_SERVER VSPICY_FFMPEG_PATH; do
  value="${!key:-}"
  if [ -z "$value" ]; then
    echo "[EMPTY] $key"
  else
    echo "[OK]    $key=$value"
  fi
done

echo
echo "Done."
