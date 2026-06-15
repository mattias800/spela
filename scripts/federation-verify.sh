#!/usr/bin/env bash
#
# Live two-server federation verification.
#
# Federation is hard to exercise by hand (it needs two paired servers), so this
# script stands up two real Spela servers on localhost, pairs them over HTTP via
# the actual invite -> accept -> /pair-callback handshake, sets per-friend
# catalog policy through the admin API, seeds a covered game on server B, and
# verifies that server A discovers it — WITH its cover — through the real
# catalog-federation flow. It's the manual companion to the in-process
# integration test in server/internal/api/federation_twoserver_test.go.
#
# Requires: go, curl, jq, sqlite3. Runs the servers in debug mode (no real
# secrets). Cleans up its temp dir and processes on exit.
#
# Usage:  ./scripts/federation-verify.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/spela-fed.XXXXXX")"
BIN="$WORK/spela-server"
COVER="https://images.igdb.com/igdb/image/upload/t_cover_big/co1abc.jpg"

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
    SPELA_PUBLIC_URL="http://localhost:$port" SPELA_JWT_SECRET="fedverify-secret-key-$name-0123456789abcdef" \
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

echo "== registering owner accounts =="
TOKB=$(curl -fsS -X POST http://localhost:8090/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"adminb","email":"b@example.com","password":"Federation-Verify-9281x"}' | jq -r .accessToken)
TOKA=$(curl -fsS -X POST http://localhost:8091/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"admina","email":"a@example.com","password":"Federation-Verify-9281x"}' | jq -r .accessToken)
[ -n "$TOKB" ] && [ -n "$TOKA" ] && [ "$TOKB" != null ] && [ "$TOKA" != null ] || { echo "!! auth failed"; exit 1; }

echo "== seeding a covered game on B =="
CID=$(sqlite3 "$WORK/B/spela.db" "SELECT id FROM consoles LIMIT 1;")
sqlite3 "$WORK/B/spela.db" "INSERT INTO games (console_id,title,file_name,file_path,scraper_id,igdb_cover_url,created_at,updated_at) VALUES ($CID,'Chrono Trigger','ct.sfc','/tmp/ct.sfc','igdb:1022','$COVER',datetime('now'),datetime('now'));"

echo "== pairing: B issues invite, A accepts (real /pair callback over HTTP) =="
INVITE=$(curl -fsS -X POST http://localhost:8090/api/admin/federation/invite -H "Authorization: Bearer $TOKB" | jq -r .invite)
curl -fsS -X POST http://localhost:8091/api/admin/federation/peers/accept -H "Authorization: Bearer $TOKA" \
  -H 'Content-Type: application/json' -d "$(jq -n --arg inv "$INVITE" '{invite:$inv,name:"Server B"}')" >/dev/null

BFP=$(curl -fsS http://localhost:8091/api/admin/federation/peers -H "Authorization: Bearer $TOKA" | jq -r '.peers[0].fingerprint')
AFP=$(curl -fsS http://localhost:8090/api/admin/federation/peers -H "Authorization: Bearer $TOKB" | jq -r '.peers[0].fingerprint')
echo "   A sees peer B = $BFP"
echo "   B sees peer A = $AFP"

echo "== setting policies: B shares catalog with A, A consumes from B =="
curl -fsS -X PUT "http://localhost:8090/api/admin/federation/peers/$AFP/policy" -H "Authorization: Bearer $TOKB" \
  -H 'Content-Type: application/json' -d '{"sharePolicy":{"catalog":true},"consumePolicy":{}}' >/dev/null
curl -fsS -X PUT "http://localhost:8091/api/admin/federation/peers/$BFP/policy" -H "Authorization: Bearer $TOKA" \
  -H 'Content-Type: application/json' -d '{"sharePolicy":{},"consumePolicy":{"catalog":true}}' >/dev/null

echo "== A refreshes its catalog from B =="
curl -fsS -X POST http://localhost:8091/api/admin/federation/catalog/refresh -H "Authorization: Bearer $TOKA"; echo

echo "== A queries connected-server games (remoteOnly) =="
RESULT=$(curl -fsS "http://localhost:8091/api/federation/catalog/available?remoteOnly=true" -H "Authorization: Bearer $TOKA")
echo "$RESULT" | jq .

if echo "$RESULT" | jq -e --arg cover "$COVER" '.games | length==1 and .[0].title=="Chrono Trigger" and .[0].cover==$cover and .[0].local==false' >/dev/null; then
  echo "PASS: A discovered B's game WITH its cover, over real HTTP federation."
else
  echo "FAIL"; exit 1
fi
