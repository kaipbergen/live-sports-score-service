#!/usr/bin/env bash
# Demo: play out a match through the API.
# Requires the stack to be up: docker compose up --build -d
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

step() { echo; echo "==> $1"; }

step "1. Create match Zenit vs CSKA"
MATCH=$(curl -sf -X POST "$BASE_URL/matches" \
  -H 'Content-Type: application/json' \
  -d '{"homeTeam": "Zenit", "awayTeam": "CSKA", "startTime": "2026-07-20T18:00:00Z"}')
echo "$MATCH"
MATCH_ID=$(echo "$MATCH" | sed -E 's/.*"id":([0-9]+).*/\1/')
echo "matchId=$MATCH_ID"

step "2. Initial score (0:0, SCHEDULED)"
curl -sf "$BASE_URL/matches/$MATCH_ID/score"; echo

step "3. MATCH_STARTED"
curl -sf -X POST "$BASE_URL/matches/$MATCH_ID/events" \
  -H 'Content-Type: application/json' \
  -d '{"type": "MATCH_STARTED", "minute": 0}'; echo

step "4. GOAL Zenit (Ivanov, 12')"
curl -sf -X POST "$BASE_URL/matches/$MATCH_ID/events" \
  -H 'Content-Type: application/json' \
  -d '{"type": "GOAL", "team": "HOME", "player": "Ivanov", "minute": 12}'; echo

step "5. YELLOW_CARD CSKA (Petrov, 30')"
curl -sf -X POST "$BASE_URL/matches/$MATCH_ID/events" \
  -H 'Content-Type: application/json' \
  -d '{"type": "YELLOW_CARD", "team": "AWAY", "player": "Petrov", "minute": 30}'; echo

step "6. GOAL CSKA (Sidorov, 55')"
curl -sf -X POST "$BASE_URL/matches/$MATCH_ID/events" \
  -H 'Content-Type: application/json' \
  -d '{"type": "GOAL", "team": "AWAY", "player": "Sidorov", "minute": 55}'; echo

sleep 2

step "7. Live score (expect 1:1, LIVE)"
curl -sf "$BASE_URL/matches/$MATCH_ID/score"; echo

step "8. MATCH_FINISHED"
curl -sf -X POST "$BASE_URL/matches/$MATCH_ID/events" \
  -H 'Content-Type: application/json' \
  -d '{"type": "MATCH_FINISHED", "minute": 90}'; echo

sleep 2

step "9. Final score (expect 1:1, FINISHED)"
curl -sf "$BASE_URL/matches/$MATCH_ID/score"; echo

step "10. Event feed"
curl -sf "$BASE_URL/matches/$MATCH_ID/events"; echo

step "11. Event after finish -> 409 Conflict"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST "$BASE_URL/matches/$MATCH_ID/events" \
  -H 'Content-Type: application/json' \
  -d '{"type": "GOAL", "team": "HOME", "player": "Late", "minute": 95}'
