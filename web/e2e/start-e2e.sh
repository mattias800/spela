#!/usr/bin/env bash
# Start the E2E test environment locally (replaces docker-compose.e2e.yml).
# Builds server from source, seeds DB, starts server + Vite, waits for
# game scan to complete, then blocks (or exits in CI mode).
#
# Usage:
#   bash web/e2e/start-e2e.sh        # start servers, block until Ctrl+C
#   bash web/e2e/start-e2e.sh --ci   # start servers, exit when ready (for Playwright webServer)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SERVER_DIR="$REPO_ROOT/server"
WEB_DIR="$REPO_ROOT/web"

SERVER_PORT=8080
VITE_PORT=5173

CI_MODE=false
if [[ "${1:-}" == "--ci" ]]; then
    CI_MODE=true
fi

# ── Helpers ──────────────────────────────────────────────────────────────

kill_port() {
    local port=$1
    local pids
    pids=$(lsof -ti :"$port" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        echo "Killing existing process(es) on port $port (PIDs: $pids)..."
        echo "$pids" | xargs kill -9 2>/dev/null || true
        # Wait for port to be released
        for _ in $(seq 1 20); do
            if ! lsof -ti :"$port" >/dev/null 2>&1; then
                break
            fi
            sleep 0.25
        done
    fi
}

wait_for_url() {
    local url=$1
    local label=$2
    local max_attempts=${3:-60}
    for i in $(seq 1 "$max_attempts"); do
        if curl -sf "$url" >/dev/null 2>&1; then
            return 0
        fi
        if [ "$((i % 10))" -eq 0 ]; then
            echo "  Still waiting for $label... (${i}s)"
        fi
        sleep 1
    done
    echo "ERROR: $label did not become ready within ${max_attempts}s"
    return 1
}

# ── Port cleanup ─────────────────────────────────────────────────────────

echo "==> Cleaning up ports..."
kill_port $SERVER_PORT
kill_port $VITE_PORT

# ── Temp directory for E2E data ──────────────────────────────────────────

E2E_DIR=$(mktemp -d)
cleanup() {
    echo ""
    echo "==> Stopping E2E servers..."
    kill "${SERVER_PID:-}" 2>/dev/null || true
    kill "${VITE_PID:-}" 2>/dev/null || true
    # Give processes a moment to die
    sleep 0.5
    # Force kill if still alive
    kill -9 "${SERVER_PID:-}" 2>/dev/null || true
    kill -9 "${VITE_PID:-}" 2>/dev/null || true
    rm -rf "$E2E_DIR"
    echo "Cleanup complete."
}
trap cleanup EXIT

DB_PATH="$E2E_DIR/spela.db"
mkdir -p "$E2E_DIR/saves" "$E2E_DIR/cores" "$E2E_DIR/images" "$E2E_DIR/bios" "$E2E_DIR/dats"

# ── 1. Build the server ─────────────────────────────────────────────────

echo "==> Building server..."
(cd "$SERVER_DIR" && CGO_ENABLED=1 go build -o "$E2E_DIR/spela-server" ./cmd/server)
(cd "$SERVER_DIR" && CGO_ENABLED=1 go build -o "$E2E_DIR/spela-seed" ./cmd/seed)

# ── 2. Seed the database ────────────────────────────────────────────────

echo "==> Seeding database..."
SPELA_DB_PATH="$DB_PATH" "$E2E_DIR/spela-seed"

# ── 3. Start the server ─────────────────────────────────────────────────

echo "==> Starting server on port $SERVER_PORT..."
SPELA_PORT=$SERVER_PORT \
SPELA_DB_PATH="$DB_PATH" \
SPELA_JWT_SECRET="e2e-test-secret-that-is-at-least-32-chars-long" \
SPELA_GAME_DIRS="$REPO_ROOT/testdata/roms" \
SPELA_SAVE_DIR="$E2E_DIR/saves" \
SPELA_CORE_DIR="$E2E_DIR/cores" \
SPELA_IMAGE_DIR="$E2E_DIR/images" \
SPELA_BIOS_DIR="$E2E_DIR/bios" \
SPELA_DAT_DIR="$E2E_DIR/dats" \
SPELA_CORS_ORIGINS="http://localhost:$VITE_PORT" \
SPELA_ENCRYPTION_KEY="e2e-test-encryption-key-32bytes!" \
SPELA_CHALLENGE_RATE_LIMIT_SEC=0 \
SPELA_TEST_MODE=true \
"$E2E_DIR/spela-server" &
SERVER_PID=$!

# ── 4. Wait for server health check ─────────────────────────────────────

echo "==> Waiting for server health check..."
if ! wait_for_url "http://localhost:$SERVER_PORT/api/health" "server" 30; then
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Server process died unexpectedly"
    fi
    exit 1
fi
echo "Server ready."

# ── 5. Trigger game scan and WAIT for it to complete ────────────────────

echo "==> Logging in to trigger game scan..."
TOKEN=$(curl -sf -X POST "http://localhost:$SERVER_PORT/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])" 2>/dev/null) || true

if [ -z "${TOKEN:-}" ]; then
    echo "ERROR: Could not login as admin to trigger game scan"
    exit 1
fi

echo "==> Triggering game scan..."
SCAN_RESPONSE=$(curl -sf -X POST "http://localhost:$SERVER_PORT/api/admin/games/scan" \
  -H "Authorization: Bearer $TOKEN" 2>/dev/null) || true
echo "Scan triggered: ${SCAN_RESPONSE:-failed}"

# Poll scan status until scan is no longer active
echo "==> Waiting for game scan to complete..."
for i in $(seq 1 120); do
    SCAN_STATUS=$(curl -sf "http://localhost:$SERVER_PORT/api/admin/games/scan/status" \
      -H "Authorization: Bearer $TOKEN" 2>/dev/null) || true

    if [ -n "$SCAN_STATUS" ]; then
        IS_ACTIVE=$(echo "$SCAN_STATUS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('active', False))" 2>/dev/null) || true
        if [ "$IS_ACTIVE" = "False" ] || [ "$IS_ACTIVE" = "false" ]; then
            break
        fi
    fi

    if [ "$((i % 5))" -eq 0 ]; then
        echo "  Scan still in progress... (${i}s)"
    fi
    sleep 1
done

# Verify games exist
GAME_COUNT=$(curl -sf "http://localhost:$SERVER_PORT/api/games?limit=1" \
  -H "Authorization: Bearer $TOKEN" 2>/dev/null | \
  python3 -c "import sys,json; print(json.load(sys.stdin).get('total', 0))" 2>/dev/null) || true

if [ "${GAME_COUNT:-0}" -eq 0 ]; then
    echo "WARNING: No games found after scan. Tests that depend on games may fail."
else
    echo "Game scan complete. $GAME_COUNT games available."
fi

# ── 6. Start Vite dev server ────────────────────────────────────────────

echo "==> Starting Vite dev server on port $VITE_PORT..."
(cd "$WEB_DIR" && npx vite --port $VITE_PORT) &
VITE_PID=$!

if ! wait_for_url "http://localhost:$VITE_PORT" "Vite dev server" 30; then
    exit 1
fi
echo "Vite dev server ready."

# ── Ready ────────────────────────────────────────────────────────────────

echo ""
echo "================================================"
echo "  E2E environment is ready!"
echo "================================================"
echo "  Server: http://localhost:$SERVER_PORT (PID $SERVER_PID)"
echo "  Web:    http://localhost:$VITE_PORT (PID $VITE_PID)"
echo "  DB:     $DB_PATH"
echo "  Games:  ${GAME_COUNT:-0}"
echo "  Reset:  POST http://localhost:$SERVER_PORT/api/test/reset"
echo "================================================"
echo ""

if $CI_MODE; then
    echo "CI mode: servers are ready, exiting startup script."
    # In CI mode we need to keep running so Playwright can manage the lifecycle
    # The trap will clean up when Playwright kills us
    wait
else
    echo "Press Ctrl+C to stop."
    wait
fi
