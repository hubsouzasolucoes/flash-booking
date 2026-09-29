#!/bin/sh
set -eu
BASE_URL=${BASE_URL:-http://localhost:8080}; ATTEMPTS=${ATTEMPTS:-50}
starts_at=$(date -u -d '+1 day' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -v+1d '+%Y-%m-%dT%H:%M:%SZ')
event=$(curl --fail-with-body -sS -X POST "$BASE_URL/events" -H 'Content-Type: application/json' -d "{\"name\":\"Concurrency demo\",\"startsAt\":\"$starts_at\",\"capacity\":10}")
id=$(printf '%s' "$event" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
export BASE_URL id tmp
seq "$ATTEMPTS" | xargs -P "$ATTEMPTS" -I N sh -c 'curl -sS -o /dev/null -w "%{http_code}\n" -X POST "$BASE_URL/events/$id/reservations" -H "Content-Type: application/json" -H "Idempotency-Key: concurrency-$id-N" -d "{\"quantity\":1}" > "$tmp/N"'
success=$(cat "$tmp"/* | awk '$1==201 {n++} END {print n+0}')
[ "$success" -le 10 ] || { echo "Oversell detected: $success" >&2; exit 1; }
until view=$(curl --fail -sS "$BASE_URL/events/$id" 2>/dev/null) && printf '%s' "$view" | grep -q '"availableTickets":0'; do sleep 1; done
printf 'Attempts=%s successful=%s (capacity=10)\nProjection=%s\n' "$ATTEMPTS" "$success" "$view"
