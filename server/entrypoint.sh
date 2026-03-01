#!/bin/sh
set -e

# Helper: run a command as the spela user when running as root,
# or directly when already running as non-root (rootless Docker,
# Portainer with --no-new-privileges, etc.).
run_as_spela() {
    if [ "$(id -u)" = "0" ]; then
        su-exec spela "$@"
    else
        "$@"
    fi
}

# ── Root-only setup (skipped in rootless / non-root containers) ────────
if [ "$(id -u)" = "0" ]; then
    # Configure runtime user — set PUID/PGID to match your host directories.
    PUID=${PUID:-1000}
    PGID=${PGID:-1000}

    # Update the spela user/group to match the requested UID/GID.
    if [ "$(id -u spela)" != "$PUID" ] || [ "$(id -g spela)" != "$PGID" ]; then
        echo "Setting spela user to uid=$PUID gid=$PGID"
        sed -i "s/^spela:x:[0-9]*:[0-9]*/spela:x:${PUID}:${PGID}/" /etc/passwd
        sed -i "s/^spela:x:[0-9]*/spela:x:${PGID}/" /etc/group
    fi

    # Fix ownership on bind-mounted directories.
    # Docker creates host directories as root when they don't exist yet.
    SPELA_DIRS="/app/data /app/saves /app/cores /app/images /app/bios /app/dats"
    for dir in $SPELA_DIRS; do
        if [ -d "$dir" ]; then
            owner=$(stat -c '%u' "$dir" 2>/dev/null || stat -f '%u' "$dir" 2>/dev/null)
            if [ "$owner" != "$PUID" ]; then
                echo "Fixing ownership on $dir (was uid=$owner, setting to $PUID:$PGID)..."
                chown -R "$PUID:$PGID" "$dir"
            fi
        fi
    done
fi

# ── Run ────────────────────────────────────────────────────────────────
if [ "$SPELA_SEED" = "true" ]; then
    echo "Seeding database..."
    run_as_spela ./spela-seed

    # Start the server in the background so we can trigger a game scan
    run_as_spela ./spela-server &
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
    if [ "$(id -u)" = "0" ]; then
        exec su-exec spela ./spela-server
    else
        exec ./spela-server
    fi
fi
