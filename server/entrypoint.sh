#!/bin/sh
set -e

# ── Fix ownership on bind-mounted directories ──────────────────────────
# Docker creates host directories as root when they don't exist yet.
# Ensure all data directories are owned by the spela user (uid 1000)
# so the server process can read and write to them.
SPELA_DIRS="/app/data /app/saves /app/cores /app/images /app/bios"
for dir in $SPELA_DIRS; do
    if [ -d "$dir" ]; then
        owner=$(stat -c '%u' "$dir" 2>/dev/null || stat -f '%u' "$dir" 2>/dev/null)
        if [ "$owner" != "$(id -u spela)" ]; then
            echo "Fixing ownership on $dir (was uid=$owner, setting to spela)..."
            chown -R spela:spela "$dir"
        fi
    fi
done

# ── Drop to spela user and run ─────────────────────────────────────────
if [ "$SPELA_SEED" = "true" ]; then
    echo "Seeding database..."
    su-exec spela ./spela-seed

    # Start the server in the background so we can trigger a game scan
    su-exec spela ./spela-server &
    SERVER_PID=$!

    # Wait for the server to be ready
    echo "Waiting for server to be ready..."
    for i in $(seq 1 30); do
        if wget -qO- http://localhost:${SPELA_PORT:-8080}/api/auth/setup-status >/dev/null 2>&1; then
            break
        fi
        sleep 0.5
    done

    # Login as admin to get a token
    echo "Logging in as admin to trigger game scan..."
    TOKEN=$(wget -qO- --post-data='{"username":"admin","password":"admin123"}' \
        --header='Content-Type: application/json' \
        http://localhost:${SPELA_PORT:-8080}/api/auth/login 2>/dev/null \
        | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

    if [ -n "$TOKEN" ]; then
        # Trigger game scan
        echo "Scanning for games..."
        SCAN_RESULT=$(wget -qO- --post-data='' \
            --header="Authorization: Bearer $TOKEN" \
            http://localhost:${SPELA_PORT:-8080}/api/admin/games/scan 2>/dev/null)
        echo "Scan result: $SCAN_RESULT"
    else
        echo "Warning: could not login to trigger game scan"
    fi

    # Wait for server process (keeps container running)
    wait $SERVER_PID
else
    exec su-exec spela ./spela-server
fi
