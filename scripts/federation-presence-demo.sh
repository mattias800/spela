#!/usr/bin/env bash
#
# Live two-server cross-mesh PRESENCE verification ("friends playing now").
#
# Presence can't be exercised with one backend — it surfaces players active on
# *connected* servers. This stands up two real Spela servers, marks a user on
# server B as currently playing a game (via the real play-time heartbeat, which
# feeds the websocket hub's live presence), pairs B->A with presence consent,
# and shows server A pulling B's live presence over real signed HTTP through
# GET /api/federation/presence/aggregated — exactly what the web/player
# "Playing now across connected servers" widget consumes.
#
# Companion to server/internal/api/federation_presence_test.go (in-process) and
# scripts/federation-verify.sh (catalog). Requires: go, curl, jq, sqlite3.
# Runs servers in debug mode on :8090/:8091 (does NOT touch your dev :8080).
#
# Usage:  ./scripts/federation-presence-demo.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/spela-presence.XXXXXX")"
BIN="$WORK/spela-server"

echo "== building server binary =="
( cd "$ROOT/server" && go build -o "$BIN" ./cmd/server )

start_server() {
  local name=$1
  local port=$2
  local d="$WORK/$name"
  mkdir -p "$d"/{games,saves,cores,images,bios,dats}
  SPELA_PORT=$port SPELA_DB_PATH="$d/spela.db" \
    SPELA_GAME_DIRS="$d/games" SPELA_SAVE_DIR="$d/saves" SPELA_CORE_DIR="$d/cores" \
    SPELA_IMAGE_DIR="$d/images" SPELA_BIOS_DIR="$d/bios" SPELA_DAT_DIR="$d/dats" \
    SPELA_PUBLIC_URL="http://localhost:$port" SPELA_JWT_SECRET="presence-demo-secret-key-$name-0123456789abcdef" \
    SPELA_DISABLE_CORE_POLLER=1 \
    "$BIN" > "$d/server.log" 2>&1 &
  echo $! > "$d/pid"
}

wait_health() {
  local port=$1
  for _ in $(seq 1 80); do
    curl -fsS "http://localhost:$port/api/health" >/dev/null 2>&1 && return 0
    sleep 0.5
  done
  echo "!! server on $port never became healthy"; cat "$WORK"/*/server.log; return 1
}

cleanup() {
  for n in A B; do [ -f "$WORK/$n/pid" ] && kill "$(cat "$WORK/$n/pid")" 2>/dev/null || true; done
  rm -rf "$WORK"
}
trap cleanup EXIT

echo "== starting servers B(8090) and A(8091) =="
start_server B 8090
start_server A 8091
wait_health 8090
wait_health 8091

echo "== registering owner accounts (public profiles by default) =="
TOKB=$(curl -fsS -X POST http://localhost:8090/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"bob","email":"b@example.com","password":"Presence-Demo-9281x"}' | jq -r .accessToken)
TOKA=$(curl -fsS -X POST http://localhost:8091/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"admina","email":"a@example.com","password":"Presence-Demo-9281x"}' | jq -r .accessToken)
[ -n "$TOKB" ] && [ -n "$TOKA" ] && [ "$TOKB" != null ] && [ "$TOKA" != null ] || { echo "!! auth failed"; exit 1; }

echo "== seeding a cross-identifiable game on B and marking bob as PLAYING it =="
CID=$(sqlite3 "$WORK/B/spela.db" "SELECT id FROM consoles LIMIT 1;")
sqlite3 "$WORK/B/spela.db" "INSERT INTO games (console_id,title,file_name,file_path,scraper_id,created_at,updated_at) VALUES ($CID,'Chrono Trigger','ct.sfc','/tmp/ct.sfc','igdb:1022',datetime('now'),datetime('now'));"
GID=$(sqlite3 "$WORK/B/spela.db" "SELECT id FROM games WHERE scraper_id='igdb:1022';")
# Real play-time heartbeat -> server B's websocket hub now reports bob playing live.
curl -fsS -X POST "http://localhost:8090/api/games/$GID/play-time" -H "Authorization: Bearer $TOKB" \
  -H 'Content-Type: application/json' -d '{"seconds":60}' >/dev/null
echo "   bob is now playing Chrono Trigger (game id $GID) on B"

echo "== pairing: B issues invite, A accepts (real /pair callback over HTTP) =="
INVITE=$(curl -fsS -X POST http://localhost:8090/api/admin/federation/invite -H "Authorization: Bearer $TOKB" | jq -r .invite)
curl -fsS -X POST http://localhost:8091/api/admin/federation/peers/accept -H "Authorization: Bearer $TOKA" \
  -H 'Content-Type: application/json' -d "$(jq -n --arg inv "$INVITE" '{invite:$inv,name:"Server B"}')" >/dev/null

BFP=$(curl -fsS http://localhost:8091/api/admin/federation/peers -H "Authorization: Bearer $TOKA" | jq -r '.peers[0].fingerprint')
AFP=$(curl -fsS http://localhost:8090/api/admin/federation/peers -H "Authorization: Bearer $TOKB" | jq -r '.peers[0].fingerprint')
echo "   A sees peer B = $BFP"
echo "   B sees peer A = $AFP"

echo "== setting policies: B shares PRESENCE with A, A consumes PRESENCE from B =="
curl -fsS -X PUT "http://localhost:8090/api/admin/federation/peers/$AFP/policy" -H "Authorization: Bearer $TOKB" \
  -H 'Content-Type: application/json' -d '{"sharePolicy":{"presence":true},"consumePolicy":{}}' >/dev/null
curl -fsS -X PUT "http://localhost:8091/api/admin/federation/peers/$BFP/policy" -H "Authorization: Bearer $TOKA" \
  -H 'Content-Type: application/json' -d '{"sharePolicy":{},"consumePolicy":{"presence":true}}' >/dev/null

echo "== A queries who's playing now across connected servers (LIVE pull from B) =="
RESULT=$(curl -fsS "http://localhost:8091/api/federation/presence/aggregated" -H "Authorization: Bearer $TOKA")
echo "$RESULT" | jq .

if echo "$RESULT" | jq -e '.presence | map(select(.hops>=1)) | length==1 and .[0].username=="bob" and .[0].gameKey=="igdb:1022" and .[0].gameTitle=="Chrono Trigger" and .[0].serverName=="Server B"' >/dev/null; then
  echo "PASS: A sees bob playing Chrono Trigger on Server B, live over real HTTP federation."
  echo "      (This is exactly what the 'Playing now across connected servers' widget renders.)"
else
  echo "FAIL"; exit 1
fi

echo "== negative check: revoke presence consent -> A sees nobody =="
curl -fsS -X PUT "http://localhost:8091/api/admin/federation/peers/$BFP/policy" -H "Authorization: Bearer $TOKA" \
  -H 'Content-Type: application/json' -d '{"sharePolicy":{},"consumePolicy":{}}' >/dev/null
RESULT2=$(curl -fsS "http://localhost:8091/api/federation/presence/aggregated" -H "Authorization: Bearer $TOKA")
if echo "$RESULT2" | jq -e '.presence | map(select(.hops>=1)) | length==0' >/dev/null; then
  echo "PASS: with consume consent removed, A pulls no remote presence (consent gate works)."
else
  echo "FAIL: presence leaked without consent"; echo "$RESULT2" | jq .; exit 1
fi
