#!/bin/sh
set -eu
BASE_URL=${BASE_URL:-http://localhost:8080}
TIMEOUT=${TIMEOUT:-120}
json_id() { printf '%s' "$1" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'; }
request() { curl --fail-with-body --silent --show-error "$@"; }

printf 'Waiting for readiness at %s...\n' "$BASE_URL"
elapsed=0
until curl --fail --silent "$BASE_URL/actuator/health/readiness" >/dev/null 2>&1; do
  [ "$elapsed" -lt "$TIMEOUT" ] || { echo 'Readiness timeout' >&2; exit 1; }
  sleep 2; elapsed=$((elapsed + 2))
done
starts_at=$(date -u -d '+1 day' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -v+1d '+%Y-%m-%dT%H:%M:%SZ')
event=$(request -X POST "$BASE_URL/events" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Demo event\",\"startsAt\":\"$starts_at\",\"capacity\":10}")
event_id=$(json_id "$event"); [ -n "$event_id" ] || { echo "Could not parse event: $event" >&2; exit 1; }
printf 'Created event %s\n' "$event_id"
# The event query is eventually consistent, so poll until its projection exists.
until event_view=$(curl --fail --silent "$BASE_URL/events/$event_id" 2>/dev/null); do sleep 1; done
printf 'Event projection: %s\n' "$event_view"
key="demo-$event_id"
reservation=$(request -X POST "$BASE_URL/events/$event_id/reservations" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $key" -d '{"quantity":2}')
reservation_id=$(json_id "$reservation"); [ -n "$reservation_id" ] || exit 1
printf 'Created reservation: %s\n' "$reservation"
printf 'Reservation query: %s\n' "$(request "$BASE_URL/reservations/$reservation_id")"
replay=$(request -X POST "$BASE_URL/events/$event_id/reservations" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $key" -d '{"quantity":2}')
[ "$(json_id "$replay")" = "$reservation_id" ] || { echo 'Idempotent replay returned another reservation' >&2; exit 1; }
printf 'Idempotent replay returned %s\n' "$reservation_id"
until availability=$(curl --fail --silent "$BASE_URL/events/$event_id" 2>/dev/null) && printf '%s' "$availability" | grep -q '"availableTickets":8'; do sleep 1; done
printf 'Availability after reservation: %s\n' "$availability"
printf 'Cancellation: %s\n' "$(request -X DELETE "$BASE_URL/reservations/$reservation_id")"
until final=$(curl --fail --silent "$BASE_URL/events/$event_id" 2>/dev/null) && printf '%s' "$final" | grep -q '"availableTickets":10'; do sleep 1; done
printf 'Final availability: %s\nFinal reservation: %s\n' "$final" "$(request "$BASE_URL/reservations/$reservation_id")"
