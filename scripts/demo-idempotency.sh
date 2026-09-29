#!/bin/sh
set -eu
BASE_URL=${BASE_URL:-http://localhost:8080}
starts_at=$(date -u -d '+1 day' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -v+1d '+%Y-%m-%dT%H:%M:%SZ')
event=$(curl --fail-with-body -sS -X POST "$BASE_URL/events" -H 'Content-Type: application/json' -d "{\"name\":\"Idempotency demo\",\"startsAt\":\"$starts_at\",\"capacity\":5}")
id=$(printf '%s' "$event" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
key="idempotency-demo-$id"
a=$(curl --fail-with-body -sS -X POST "$BASE_URL/events/$id/reservations" -H 'Content-Type: application/json' -H "Idempotency-Key: $key" -d '{"quantity":1}')
b=$(curl --fail-with-body -sS -X POST "$BASE_URL/events/$id/reservations" -H 'Content-Type: application/json' -H "Idempotency-Key: $key" -d '{"quantity":1}')
printf 'First:  %s\nReplay: %s\n' "$a" "$b"
status=$(curl -sS -o /tmp/flash-booking-conflict.json -w '%{http_code}' -X POST "$BASE_URL/events/$id/reservations" -H 'Content-Type: application/json' -H "Idempotency-Key: $key" -d '{"quantity":2}')
[ "$status" = 409 ] || { cat /tmp/flash-booking-conflict.json >&2; exit 1; }
printf 'Different payload: HTTP 409 (%s)\n' "$(cat /tmp/flash-booking-conflict.json)"
rm -f /tmp/flash-booking-conflict.json
