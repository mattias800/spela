#!/usr/bin/env bash
# Start the E2E test environment locally (replaces docker-compose.e2e.yml).
# Usage:
#   bash web/e2e/start-e2e.sh        # start servers, block until Ctrl+C
#   bash web/e2e/start-e2e.sh --ci   # start servers, exit when ready (for Playwright webServer)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SERVER_DIR="$REPO_ROOT/server"
WEB_DIR="$REPO_ROOT/web"

CI_MODE=false
if [[ "${1:-}" == "--ci" ]]; then
    CI_MODE=true
fi

# Temp directory for E2E data
E2E_DIR=$(mktemp -d)
cleanup() {
    echo "Stopping E2E servers..."
    kill "${SERVER_PID:-}" 2>/dev/null || true
    kill "${VITE_PID:-}" 2>/dev/null || true
    rm -rf "$E2E_DIR"
}
trap cleanup EXIT

DB_PATH="$E2E_DIR/spela.db"
mkdir -p "$E2E_DIR/saves" "$E2E_DIR/cores" "$E2E_DIR/images" "$E2E_DIR/bios" "$E2E_DIR/dats"

# 1. Build the server
echo "Building server..."
(cd "$SERVER_DIR" && CGO_ENABLED=1 go build -o "$E2E_DIR/spela-server" ./cmd/server)
(cd "$SERVER_DIR" && CGO_ENABLED=1 go build -o "$E2E_DIR/spela-seed" ./cmd/seed)

# 2. Seed the database
echo "Seeding database..."
SPELA_DB_PATH="$DB_PATH" "$E2E_DIR/spela-seed"

# 3. Start the server
echo "Starting server..."
SPELA_PORT=8080 \
SPELA_DB_PATH="$DB_PATH" \
SPELA_JWT_SECRET="e2e-test-secret-that-is-at-least-32-chars-long" \
SPELA_GAME_DIRS="$REPO_ROOT/testdata/roms" \
SPELA_SAVE_DIR="$E2E_DIR/saves" \
SPELA_CORE_DIR="$E2E_DIR/cores" \
SPELA_IMAGE_DIR="$E2E_DIR/images" \
SPELA_BIOS_DIR="$E2E_DIR/bios" \
SPELA_DAT_DIR="$E2E_DIR/dats" \
SPELA_CORS_ORIGINS="http://localhost:5173" \
SPELA_ENCRYPTION_KEY="e2e-test-encryption-key-at-least-32-chars" \
SPELA_CHALLENGE_RATE_LIMIT_SEC=0 \
SPELA_TEST_MODE=true \
GIN_MODE=release \
"$E2E_DIR/spela-server" &
SERVER_PID=$!

# 4. Wait for server to be ready
echo "Waiting for server..."
for i in $(seq 1 60); do
    if curl -sf http://localhost:8080/api/health >/dev/null 2>&1; then
        break
    fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Server process died"
        exit 1
    fi
    sleep 0.5
done

if ! curl -sf http://localhost:8080/api/health >/dev/null 2>&1; then
    echo "Server failed to start"
    exit 1
fi
echo "Server ready."

# 5. Trigger game scan
echo "Scanning games..."
TOKEN=$(curl -sf -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])" 2>/dev/null) || true

if [ -n "${TOKEN:-}" ]; then
    SCAN_RESULT=$(curl -sf -X POST http://localhost:8080/api/admin/games/scan \
      -H "Authorization: Bearer $TOKEN" 2>/dev/null) || true
    echo "Scan result: ${SCAN_RESULT:-failed}"
else
    echo "Warning: could not login to trigger game scan"
fi

# 6. Start Vite dev server
echo "Starting Vite dev server..."
(cd "$WEB_DIR" && npx vite --port 5173) &
VITE_PID=$!

# Wait for Vite
for i in $(seq 1 30); do
    if curl -sf http://localhost:5173 >/dev/null 2>&1; then
        break
    fi
    sleep 0.5
done

echo ""
echo "E2E servers are running:"
echo "  Server: http://localhost:8080 (PID $SERVER_PID)"
echo "  Web:    http://localhost:5173 (PID $VITE_PID)"
echo "  DB:     $DB_PATH"
echo "  Test reset: POST http://localhost:8080/api/test/reset"

echo ""
echo "Press Ctrl+C to stop."
wait
