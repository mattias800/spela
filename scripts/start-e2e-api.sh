#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SERVER_DIR="$REPO_ROOT/server"

PORT="${SPELA_E2E_PORT:-8080}"
STATE_DIR="${SPELA_E2E_API_DIR:-${RUNNER_TEMP:-/tmp}/spela-e2e-api}"
BUILD_CACHE="${SPELA_E2E_BUILD_CACHE:-$REPO_ROOT/.e2e-cache}"

wait_for_url() {
  local url=$1
  local label=$2
  local attempts=${3:-60}

  for _ in $(seq 1 "$attempts"); do
    if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done

  echo "ERROR: $label did not become ready" >&2
  return 1
}

server_alive() {
  local pid=$1
  kill -0 "$pid" >/dev/null 2>&1
}

join_by_comma() {
  local IFS=,
  echo "$*"
}

if curl -fsS --max-time 2 "http://localhost:$PORT/api/health" >/dev/null 2>&1; then
  echo "ERROR: port $PORT already has a healthy Spela API server; refusing to reuse stale state." >&2
  exit 1
fi

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR"/{data,saves,cores,images,bios} "$BUILD_CACHE"

if [ ! -x "$BUILD_CACHE/spela-server" ] || [ ! -x "$BUILD_CACHE/spela-seed" ]; then
  echo "Building E2E API binaries..."
  (cd "$SERVER_DIR" && CGO_ENABLED=1 go build -ldflags "-X main.version=dev" -o "$BUILD_CACHE/spela-server" ./cmd/server)
  (cd "$SERVER_DIR" && CGO_ENABLED=1 go build -o "$BUILD_CACHE/spela-seed" ./cmd/seed)
else
  echo "Using cached E2E API binaries."
fi

cp "$BUILD_CACHE/spela-server" "$STATE_DIR/spela-server"
cp "$BUILD_CACHE/spela-seed" "$STATE_DIR/spela-seed"

DB_PATH="$STATE_DIR/data/spela.db"
SPELA_DB_PATH="$DB_PATH" "$STATE_DIR/spela-seed"

game_dirs=()
if [ -d "$REPO_ROOT/testdata/roms" ]; then
  game_dirs+=("$REPO_ROOT/testdata/roms")
fi
if [ -d "$REPO_ROOT/testdata-public" ]; then
  game_dirs+=("$REPO_ROOT/testdata-public")
fi
if [ "${#game_dirs[@]}" -eq 0 ]; then
  echo "ERROR: no E2E game directories found." >&2
  exit 1
fi
GAME_DIRS="$(join_by_comma "${game_dirs[@]}")"

echo "Starting E2E API server on port $PORT..."
(
  export SPELA_PORT="$PORT"
  export SPELA_DB_PATH="$DB_PATH"
  export SPELA_JWT_SECRET="e2e-test-secret-that-is-at-least-32-chars-long"
  export SPELA_GAME_DIRS="$GAME_DIRS"
  export SPELA_SAVE_DIR="$STATE_DIR/saves"
  export SPELA_CORE_DIR="$STATE_DIR/cores"
  export SPELA_IMAGE_DIR="$STATE_DIR/images"
  export SPELA_BIOS_DIR="$STATE_DIR/bios"
  export SPELA_DAT_DIR="$SERVER_DIR/dats"
  export SPELA_CORS_ORIGINS="http://localhost:5173"
  export SPELA_ENCRYPTION_KEY="e2e-test-encryption-key-32bytes!"
  export SPELA_CHALLENGE_RATE_LIMIT_SEC=0
  export SPELA_TEST_MODE=true
  export GIN_MODE=debug

  nohup "$STATE_DIR/spela-server" >"$STATE_DIR/server.log" 2>&1 &
  echo "$!" >"$STATE_DIR/server.pid"
)

SERVER_PID="$(cat "$STATE_DIR/server.pid")"
if ! wait_for_url "http://localhost:$PORT/api/health" "E2E API server" 60; then
  if ! server_alive "$SERVER_PID"; then
    echo "Server process exited early. Last log lines:" >&2
    tail -200 "$STATE_DIR/server.log" >&2 || true
  fi
  exit 1
fi

LOGIN_RESP="$(curl -fsS -X POST "http://localhost:$PORT/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}')"
ADMIN_TOKEN="$(printf '%s' "$LOGIN_RESP" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
if [ -z "$ADMIN_TOKEN" ]; then
  echo "ERROR: admin login failed while preparing E2E API server." >&2
  exit 1
fi

echo "Scanning E2E game library..."
curl -fsS -X POST "http://localhost:$PORT/api/admin/games/scan" \
  -H "Authorization: Bearer $ADMIN_TOKEN" >/dev/null

scan_deadline=$(( $(date +%s) + 90 ))
while [ "$(date +%s)" -lt "$scan_deadline" ]; do
  SCAN_STATUS="$(curl -fsS -H "Authorization: Bearer $ADMIN_TOKEN" \
    "http://localhost:$PORT/api/admin/games/scan/status" 2>/dev/null || true)"
  if printf '%s' "$SCAN_STATUS" | grep -q '"active":false'; then
    break
  fi
  sleep 0.5
done
if [ "$(date +%s)" -ge "$scan_deadline" ]; then
  echo "ERROR: E2E game scan still active after 90s." >&2
  tail -200 "$STATE_DIR/server.log" >&2 || true
  exit 1
fi

GAMES_RESP="$(curl -fsS "http://localhost:$PORT/api/games?limit=1" \
  -H "Authorization: Bearer $ADMIN_TOKEN" 2>/dev/null || true)"
GAME_COUNT="$(printf '%s' "$GAMES_RESP" | sed -n 's/.*"total":\([0-9][0-9]*\).*/\1/p')"
if [ -z "$GAME_COUNT" ] || [ "$GAME_COUNT" -eq 0 ]; then
  echo "ERROR: E2E game scan found no games." >&2
  tail -200 "$STATE_DIR/server.log" >&2 || true
  exit 1
fi

echo "E2E API server ready: pid=$SERVER_PID games=$GAME_COUNT state=$STATE_DIR"
