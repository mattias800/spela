#!/usr/bin/env bash
# ============================================================================
# run-e2e.sh — One-command E2E test runner for Spela
# ============================================================================
#
# This script handles EVERYTHING needed to run E2E tests reliably:
#   1. Kills any existing processes on test ports (8080, 5173)
#   2. Installs npm dependencies if needed
#   3. Removes stale auth state from previous runs
#   4. Builds the Go server from source
#   5. Seeds a fresh database in a temp directory
#   6. Starts the server and waits for health check
#   7. Triggers a game scan and waits for it to complete
#   8. Starts the Vite dev server
#   9. Runs Playwright E2E tests
#  10. Cleans everything up on exit
#
# Usage:
#   ./run-e2e.sh                    # run all E2E tests
#   ./run-e2e.sh admin-settings     # run tests matching "admin-settings"
#   ./run-e2e.sh --ui               # open Playwright UI mode
#   ./run-e2e.sh --headed           # run tests with visible browser
#   ./run-e2e.sh --debug            # run with Playwright debug mode
#
# Environment variables:
#   SKIP_BUILD=1    — skip Go server build (reuse cached binary)
#   SKIP_INSTALL=1  — skip npm install check
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/server"
WEB_DIR="$SCRIPT_DIR/web"

SERVER_PORT=8080
VITE_PORT=5173

# ── Parse arguments ──────────────────────────────────────────────────────

PLAYWRIGHT_ARGS=()
UI_MODE=false

while [ $# -gt 0 ]; do
    case "$1" in
        --ui)
            UI_MODE=true
            shift
            ;;
        --headed|--debug)
            PLAYWRIGHT_ARGS+=("$1")
            shift
            ;;
        *)
            PLAYWRIGHT_ARGS+=("$1")
            shift
            ;;
    esac
done

# ── Helpers ──────────────────────────────────────────────────────────────

kill_port() {
    local port=$1
    local pids
    pids=$(lsof -ti :"$port" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        echo "Killing process(es) on port $port (PIDs: $pids)..."
        echo "$pids" | xargs kill -9 2>/dev/null || true
        for _ in $(seq 1 20); do
            if ! lsof -ti :"$port" >/dev/null 2>&1; then break; fi
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

# ── Cleanup on exit ──────────────────────────────────────────────────────

SERVER_PID=""
VITE_PID=""
E2E_DIR=""

cleanup() {
    local exit_code=$?
    echo ""
    echo "==> Cleaning up..."
    [ -n "$VITE_PID" ] && kill "$VITE_PID" 2>/dev/null || true
    [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null || true
    sleep 0.5
    [ -n "$VITE_PID" ] && kill -9 "$VITE_PID" 2>/dev/null || true
    [ -n "$SERVER_PID" ] && kill -9 "$SERVER_PID" 2>/dev/null || true
    [ -n "$E2E_DIR" ] && rm -rf "$E2E_DIR"
    echo "Cleanup complete."
    exit $exit_code
}
trap cleanup EXIT INT TERM

# ── Step 1: Kill existing processes on test ports ────────────────────────

echo "==> Step 1/8: Clearing test ports..."
kill_port $SERVER_PORT
kill_port $VITE_PORT

# ── Step 2: Install npm dependencies ────────────────────────────────────

if [ "${SKIP_INSTALL:-}" != "1" ]; then
    echo "==> Step 2/8: Checking npm dependencies..."
    if [ ! -d "$WEB_DIR/node_modules" ]; then
        echo "  Installing npm dependencies..."
        (cd "$WEB_DIR" && npm install)
    fi
    # Ensure Playwright browsers are installed
    if ! npx --prefix "$WEB_DIR" playwright --version >/dev/null 2>&1; then
        echo "  Installing Playwright browsers..."
        (cd "$WEB_DIR" && npx playwright install chromium)
    fi
else
    echo "==> Step 2/8: Skipping npm install (SKIP_INSTALL=1)"
fi

# ── Step 3: Remove stale auth state ─────────────────────────────────────

echo "==> Step 3/8: Removing stale auth state..."
rm -f "$WEB_DIR/e2e/.auth/storage-state.json"

# ── Step 4: Build and seed ───────────────────────────────────────────────

E2E_DIR=$(mktemp -d)
DB_PATH="$E2E_DIR/spela.db"
mkdir -p "$E2E_DIR/saves" "$E2E_DIR/cores" "$E2E_DIR/images" "$E2E_DIR/bios" "$E2E_DIR/dats"

BUILD_CACHE="$SCRIPT_DIR/.e2e-cache"
mkdir -p "$BUILD_CACHE"

if [ "${SKIP_BUILD:-}" != "1" ]; then
    echo "==> Step 4/8: Building server..."
    (cd "$SERVER_DIR" && CGO_ENABLED=1 go build -o "$BUILD_CACHE/spela-server" ./cmd/server)
    (cd "$SERVER_DIR" && CGO_ENABLED=1 go build -o "$BUILD_CACHE/spela-seed" ./cmd/seed)
else
    echo "==> Step 4/8: Skipping build (SKIP_BUILD=1)"
    if [ ! -f "$BUILD_CACHE/spela-server" ] || [ ! -f "$BUILD_CACHE/spela-seed" ]; then
        echo "  No cached binaries — building anyway..."
        (cd "$SERVER_DIR" && CGO_ENABLED=1 go build -o "$BUILD_CACHE/spela-server" ./cmd/server)
        (cd "$SERVER_DIR" && CGO_ENABLED=1 go build -o "$BUILD_CACHE/spela-seed" ./cmd/seed)
    fi
fi

cp "$BUILD_CACHE/spela-server" "$E2E_DIR/spela-server"
cp "$BUILD_CACHE/spela-seed" "$E2E_DIR/spela-seed"

echo "  Seeding database..."
SPELA_DB_PATH="$DB_PATH" "$E2E_DIR/spela-seed"

# ── Step 5: Start server ────────────────────────────────────────────────

echo "==> Step 5/8: Starting server..."
SPELA_PORT=$SERVER_PORT \
SPELA_DB_PATH="$DB_PATH" \
SPELA_JWT_SECRET="e2e-test-secret-that-is-at-least-32-chars-long" \
SPELA_GAME_DIRS="$SCRIPT_DIR/testdata/roms" \
SPELA_SAVE_DIR="$E2E_DIR/saves" \
SPELA_CORE_DIR="$E2E_DIR/cores" \
SPELA_IMAGE_DIR="$E2E_DIR/images" \
SPELA_BIOS_DIR="$E2E_DIR/bios" \
SPELA_DAT_DIR="$E2E_DIR/dats" \
SPELA_CORS_ORIGINS="http://localhost:$VITE_PORT" \
SPELA_ENCRYPTION_KEY="e2e-test-encryption-key-32bytes!" \
SPELA_CHALLENGE_RATE_LIMIT_SEC=0 \
SPELA_TEST_MODE=true \
GIN_MODE=release \
"$E2E_DIR/spela-server" &
SERVER_PID=$!

if ! wait_for_url "http://localhost:$SERVER_PORT/api/health" "server" 30; then
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Server process died unexpectedly"
    fi
    exit 1
fi
echo "  Server ready (PID $SERVER_PID)"

# ── Step 6: Trigger and wait for game scan ───────────────────────────────

echo "==> Step 6/8: Scanning game library..."
TOKEN=$(curl -sf -X POST "http://localhost:$SERVER_PORT/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])" 2>/dev/null) || true

if [ -z "${TOKEN:-}" ]; then
    echo "ERROR: Could not login as admin"
    exit 1
fi

curl -sf -X POST "http://localhost:$SERVER_PORT/api/admin/games/scan" \
  -H "Authorization: Bearer $TOKEN" >/dev/null 2>&1 || true

# Poll scan status until complete
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
        echo "  Scan still running... (${i}s)"
    fi
    sleep 1
done

GAME_COUNT=$(curl -sf "http://localhost:$SERVER_PORT/api/games?limit=1" \
  -H "Authorization: Bearer $TOKEN" 2>/dev/null | \
  python3 -c "import sys,json; print(json.load(sys.stdin).get('total', 0))" 2>/dev/null) || true

echo "  Game scan complete. ${GAME_COUNT:-0} games available."

if [ "${GAME_COUNT:-0}" -eq 0 ]; then
    echo "WARNING: No games found after scan!"
fi

# ── Step 7: Start Vite dev server ────────────────────────────────────────

echo "==> Step 7/8: Starting Vite dev server..."
(cd "$WEB_DIR" && npx vite --port $VITE_PORT) &
VITE_PID=$!

if ! wait_for_url "http://localhost:$VITE_PORT" "Vite" 30; then
    exit 1
fi
echo "  Vite ready (PID $VITE_PID)"

# ── Step 8: Run Playwright tests ────────────────────────────────────────

echo ""
echo "================================================"
echo "  E2E environment ready — running tests"
echo "================================================"
echo "  Server: http://localhost:$SERVER_PORT"
echo "  Web:    http://localhost:$VITE_PORT"
echo "  Games:  ${GAME_COUNT:-0}"
echo "================================================"
echo ""

cd "$WEB_DIR"

# Tell Playwright NOT to use its webServer config (we already started everything)
export E2E_SERVERS_RUNNING=1

if $UI_MODE; then
    echo "Opening Playwright UI..."
    npx playwright test --ui "${PLAYWRIGHT_ARGS[@]+"${PLAYWRIGHT_ARGS[@]}"}"
else
    echo "Running Playwright E2E tests..."
    npx playwright test "${PLAYWRIGHT_ARGS[@]+"${PLAYWRIGHT_ARGS[@]}"}"
fi
