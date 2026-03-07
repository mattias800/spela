#!/usr/bin/env bash
#
# Netplay E2E Test — Two-Process Screenshot Verification
#
# Spins up the E2E docker backend, launches two headless desktop player
# instances in netplay mode, and verifies cross-player input propagation
# by comparing screenshots.
#
# ROM: Chip 'n Dale: Rescue Rangers (NES)
# Test flow:
#   Phase 1 (title screen): P1 presses Select → P2's screen changes
#   Phase 2 (in-game):      P2 presses A (jump) → P1's screen changes
#
# Usage: ./run-netplay-e2e.sh [--skip-docker] [--skip-build]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PLAYER_DIR="$SCRIPT_DIR"
OUTPUT_DIR="$PLAYER_DIR/build/netplay-e2e"

# Parse flags
SKIP_DOCKER=false
SKIP_BUILD=false
for arg in "$@"; do
    case "$arg" in
        --skip-docker) SKIP_DOCKER=true ;;
        --skip-build)  SKIP_BUILD=true ;;
    esac
done

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${YELLOW}[netplay-e2e]${NC} $1"; }
pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }

HOST_PID=""
CLIENT_PID=""

cleanup() {
    log "Cleaning up..."
    [[ -n "$HOST_PID" ]] && kill "$HOST_PID" 2>/dev/null || true
    [[ -n "$CLIENT_PID" ]] && kill "$CLIENT_PID" 2>/dev/null || true
    [[ -n "$HOST_PID" ]] && wait "$HOST_PID" 2>/dev/null || true
    [[ -n "$CLIENT_PID" ]] && wait "$CLIENT_PID" 2>/dev/null || true
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# 1. Ensure docker E2E environment is running
# ---------------------------------------------------------------------------
if [[ "$SKIP_DOCKER" == "false" ]]; then
    log "Starting E2E docker environment..."
    docker compose -f "$PROJECT_ROOT/docker-compose.e2e.yml" up -d --build --wait
else
    log "Skipping docker setup (--skip-docker)"
fi

SERVER_URL="http://localhost:8080"

# Wait for server to be ready
log "Waiting for server at $SERVER_URL..."
for i in $(seq 1 30); do
    if curl -sf "$SERVER_URL/api/health" > /dev/null 2>&1; then
        break
    fi
    if [[ $i -eq 30 ]]; then
        fail "Server did not become ready in 30 seconds"
        exit 1
    fi
    sleep 1
done
log "Server is ready"

# ---------------------------------------------------------------------------
# 2. Find ROM and core
# ---------------------------------------------------------------------------
ROM_PATH="$PROJECT_ROOT/testdata/roms/nes/Chip 'n Dale - Rescue Rangers (Europe).nes"
if [[ ! -f "$ROM_PATH" ]]; then
    # Try alternate names
    ROM_PATH=$(find "$PROJECT_ROOT/testdata/roms/nes" -iname "*chip*dale*" -o -iname "*rescue*ranger*" 2>/dev/null | head -1)
    if [[ -z "$ROM_PATH" || ! -f "$ROM_PATH" ]]; then
        fail "Could not find Chip 'n Dale ROM in testdata/roms/nes/"
        exit 1
    fi
fi
log "ROM: $ROM_PATH"

# Find or download nestopia core
CORE_DIR="$HOME/Library/Application Support/Spela/cores"
if [[ "$(uname)" == "Linux" ]]; then
    CORE_DIR="$HOME/.local/share/spela/cores"
fi
CORE_PATH=""
for ext in dylib so dll; do
    candidate="$CORE_DIR/nestopia_libretro.$ext"
    if [[ -f "$candidate" ]]; then
        CORE_PATH="$candidate"
        break
    fi
done

if [[ -z "$CORE_PATH" ]]; then
    log "Nestopia core not found locally, downloading..."
    mkdir -p "$CORE_DIR"
    OS_NAME="$(uname -s)"
    case "$OS_NAME" in
        Darwin) CORE_URL="https://buildbot.libretro.com/nightly/apple/osx/arm64/latest/nestopia_libretro.dylib.zip"
                CORE_EXT="dylib" ;;
        Linux)  CORE_URL="https://buildbot.libretro.com/nightly/linux/x86_64/latest/nestopia_libretro.so.zip"
                CORE_EXT="so" ;;
        *)      fail "Unsupported OS: $OS_NAME"; exit 1 ;;
    esac
    CORE_PATH="$CORE_DIR/nestopia_libretro.$CORE_EXT"
    curl -L "$CORE_URL" -o /tmp/nestopia_core.zip
    unzip -o /tmp/nestopia_core.zip -d "$CORE_DIR"
    rm -f /tmp/nestopia_core.zip
    if [[ ! -f "$CORE_PATH" ]]; then
        fail "Failed to download nestopia core"
        exit 1
    fi
fi
log "Core: $CORE_PATH"

# ---------------------------------------------------------------------------
# 3. Build the desktop app (includes native library)
# ---------------------------------------------------------------------------
if [[ "$SKIP_BUILD" == "false" ]]; then
    log "Building desktop app and native library..."
    cd "$PLAYER_DIR"
    ./gradlew :desktop:desktopJar :desktop:buildNativeLibrary --quiet 2>&1 | tail -5
    cd "$PROJECT_ROOT"
else
    log "Skipping build (--skip-build)"
fi

# ---------------------------------------------------------------------------
# 4. Prepare output directory
# ---------------------------------------------------------------------------
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# ---------------------------------------------------------------------------
# 5. Build the classpath and JVM args for the test runner
# ---------------------------------------------------------------------------
NATIVE_LIB_DIR="$PLAYER_DIR/desktop/build/native"
GRADLE_RUN="cd $PLAYER_DIR && ./gradlew -q :desktop:runNetplayTestRunner"

# We'll use the Gradle task to run each process. To pass different args to each,
# we use the --args flag. But since we need two concurrent processes, we'll
# invoke Gradle twice in parallel. However, that's slow.
#
# Better approach: run the JVM directly using the jar + classpath from Gradle.
# Let's find the classpath by running a helper.

log "Resolving classpath..."
DESKTOP_JAR=$(find "$PLAYER_DIR/desktop/build/libs" -name "*.jar" | head -1)
if [[ -z "$DESKTOP_JAR" ]]; then
    fail "Desktop JAR not found. Run build first."
    exit 1
fi

# Build a classpath from the Gradle cache — collect all runtime JARs
CLASSPATH_FILE="$OUTPUT_DIR/classpath.txt"
cd "$PLAYER_DIR"
./gradlew -q :desktop:printRuntimeClasspath 2>/dev/null > "$CLASSPATH_FILE" || true
cd "$PROJECT_ROOT"

# If the printRuntimeClasspath task doesn't exist, fall back to Gradle exec
if [[ ! -s "$CLASSPATH_FILE" ]]; then
    log "Using Gradle JavaExec for test runners (slower)..."
    USE_GRADLE=true
else
    CLASSPATH=$(cat "$CLASSPATH_FILE")
    USE_GRADLE=false
fi

# ---------------------------------------------------------------------------
# 6. Launch host process
# ---------------------------------------------------------------------------
log "Launching host process..."
HOST_STDIN="$OUTPUT_DIR/host_stdin"
HOST_STDOUT="$OUTPUT_DIR/host_stdout"
HOST_STDERR="$OUTPUT_DIR/host_stderr"
mkfifo "$HOST_STDIN"
: > "$HOST_STDOUT"

# Launch java reading from FIFO. The FIFO open blocks until we open the write end.
# We launch java in background, then immediately open the write end.
java \
    -Djava.library.path="$NATIVE_LIB_DIR" \
    -cp "$CLASSPATH" \
    com.spela.player.desktop.NetplayTestRunnerKt \
    --server-url "$SERVER_URL" \
    --username admin \
    --password admin123 \
    --role host \
    --core-path "$CORE_PATH" \
    --rom-path "$ROM_PATH" \
    --input-delay 1 \
    < "$HOST_STDIN" > "$HOST_STDOUT" 2>"$HOST_STDERR" &
HOST_PID=$!

# Open write end of FIFO (unblocks java's stdin)
exec 3>"$HOST_STDIN"

# Wait for INVITE_CODE from host
log "Waiting for host to create session..."
INVITE_CODE=""
for i in $(seq 1 60); do
    INVITE_CODE=$(grep "^INVITE_CODE " "$HOST_STDOUT" 2>/dev/null | head -1 | awk '{print $2}' || true)
    if [[ -n "$INVITE_CODE" ]]; then
        break
    fi
    # Check if host process died
    if ! kill -0 "$HOST_PID" 2>/dev/null; then
        fail "Host process exited prematurely"
        cat "$HOST_STDERR" 2>/dev/null || true
        exit 1
    fi
    sleep 1
done

if [[ -z "$INVITE_CODE" ]]; then
    fail "Host did not produce an invite code within 60 seconds"
    cat "$HOST_STDERR" 2>/dev/null || true
    exit 1
fi
log "Got invite code: $INVITE_CODE"

# ---------------------------------------------------------------------------
# 7. Launch client process
# ---------------------------------------------------------------------------
log "Launching client process..."
CLIENT_STDIN="$OUTPUT_DIR/client_stdin"
CLIENT_STDOUT="$OUTPUT_DIR/client_stdout"
CLIENT_STDERR="$OUTPUT_DIR/client_stderr"
mkfifo "$CLIENT_STDIN"
: > "$CLIENT_STDOUT"

java \
    -Djava.library.path="$NATIVE_LIB_DIR" \
    -cp "$CLASSPATH" \
    com.spela.player.desktop.NetplayTestRunnerKt \
    --server-url "$SERVER_URL" \
    --username player \
    --password player123 \
    --role client \
    --invite-code "$INVITE_CODE" \
    --core-path "$CORE_PATH" \
    --rom-path "$ROM_PATH" \
    --input-delay 1 \
    < "$CLIENT_STDIN" > "$CLIENT_STDOUT" 2>"$CLIENT_STDERR" &
CLIENT_PID=$!

exec 4>"$CLIENT_STDIN"

# ---------------------------------------------------------------------------
# 8. Wait for both to be READY
# ---------------------------------------------------------------------------
wait_for_ready() {
    local name="$1"
    local stdout_file="$2"
    local timeout=60
    for i in $(seq 1 $timeout); do
        if grep -q "^READY" "$stdout_file" 2>/dev/null; then
            log "$name is READY"
            return 0
        fi
        sleep 1
    done
    fail "$name did not become ready within ${timeout}s"
    return 1
}

wait_for_ready "Host" "$HOST_STDOUT"
wait_for_ready "Client" "$CLIENT_STDOUT"

# Helper: send a command to a process and wait for OK
# (defined before "go" so it's available)
send_cmd() {
    local name="$1"
    local fd="$2"
    local stdout_file="$3"
    local cmd="$4"

    local before_count
    before_count=$(wc -l < "$stdout_file" 2>/dev/null || echo 0)

    echo "$cmd" >&"$fd"

    # Wait for response line
    for i in $(seq 1 30); do
        local current_count
        current_count=$(wc -l < "$stdout_file" 2>/dev/null || echo 0)
        if [[ "$current_count" -gt "$before_count" ]]; then
            local last_line
            last_line=$(tail -1 "$stdout_file")
            if [[ "$last_line" == OK* ]]; then
                return 0
            elif [[ "$last_line" == ERROR* ]]; then
                fail "$name command '$cmd' failed: $last_line"
                return 1
            fi
        fi
        sleep 0.5
    done
    fail "$name command '$cmd' timed out"
    return 1
}

# ---------------------------------------------------------------------------
# 9. Start emulation on both sides simultaneously
# ---------------------------------------------------------------------------
log "Starting emulation on both processes..."
send_cmd "Host" 3 "$HOST_STDOUT" "go"
send_cmd "Client" 4 "$CLIENT_STDOUT" "go"

# ---------------------------------------------------------------------------
# 10. Execute test sequence
# ---------------------------------------------------------------------------
log ""
log "=========================================="
log " Phase 1: Title Screen Input Test (P1→P2)"
log "=========================================="
log ""

# Wait for title screen to render
log "Waiting 3s for title screen..."
send_cmd "Host" 3 "$HOST_STDOUT" "wait 3000"
send_cmd "Client" 4 "$CLIENT_STDOUT" "wait 3000"

# P2 takes screenshot of title screen (cursor on "1 PLAYER")
log "P2: taking 'before' screenshot (cursor on 1 PLAYER)..."
send_cmd "Client" 4 "$CLIENT_STDOUT" "screenshot $OUTPUT_DIR/phase1_before.png"

# P1 presses Select (toggles cursor from "1 PLAYER" to "2 Players")
log "P1: pressing Select to switch to 2 Players..."
send_cmd "Host" 3 "$HOST_STDOUT" "press select"

# Wait for input to propagate through netplay
log "Waiting 2s for input to propagate..."
send_cmd "Client" 4 "$CLIENT_STDOUT" "wait 2000"

# P2 takes screenshot (cursor should now be on "2 Players")
log "P2: taking 'after' screenshot..."
send_cmd "Client" 4 "$CLIENT_STDOUT" "screenshot $OUTPUT_DIR/phase1_after.png"

# Compare: cursor position should differ
PHASE1_RESULT="FAIL"
if [[ -f "$OUTPUT_DIR/phase1_before.png" && -f "$OUTPUT_DIR/phase1_after.png" ]]; then
    if ! cmp -s "$OUTPUT_DIR/phase1_before.png" "$OUTPUT_DIR/phase1_after.png"; then
        PHASE1_RESULT="PASS"
        pass "Phase 1: P1's Select input propagated to P2 (cursor moved)"
    else
        fail "Phase 1: Screenshots identical — P1's Select did not reach P2"
    fi
else
    fail "Phase 1: Screenshot files missing"
fi

log ""
log "=========================================="
log " Phase 2: In-Game Input Test (P2→P1)"
log "=========================================="
log ""

# Start the game in 2-player mode
log "P1: pressing Start to begin 2-player game..."
send_cmd "Host" 3 "$HOST_STDOUT" "press start"

# Wait for cutscene to appear, then press Start to skip it
log "Waiting 1s for cutscene..."
send_cmd "Host" 3 "$HOST_STDOUT" "wait 1000"
log "P1: pressing Start to skip cutscene..."
send_cmd "Host" 3 "$HOST_STDOUT" "press start"

# We might be on the map/zone select screen — press A to enter the zone
log "Waiting 1s, then pressing A to enter zone..."
send_cmd "Host" 3 "$HOST_STDOUT" "wait 1000"
send_cmd "Host" 3 "$HOST_STDOUT" "press a"

# Wait for level to load
log "Waiting 3s for in-game..."
send_cmd "Host" 3 "$HOST_STDOUT" "wait 3000"
send_cmd "Client" 4 "$CLIENT_STDOUT" "wait 3000"

# Verify game is running: P1 presses Right (moves Chip). If screen doesn't
# change, game is paused — press Start to unpause and try again.
log "Verifying game is running..."
send_cmd "Host" 3 "$HOST_STDOUT" "screenshot $OUTPUT_DIR/verify_a.png"
send_cmd "Host" 3 "$HOST_STDOUT" "press right"
send_cmd "Host" 3 "$HOST_STDOUT" "wait 500"
send_cmd "Host" 3 "$HOST_STDOUT" "screenshot $OUTPUT_DIR/verify_b.png"

if cmp -s "$OUTPUT_DIR/verify_a.png" "$OUTPUT_DIR/verify_b.png"; then
    log "Game is paused — pressing Start to unpause..."
    send_cmd "Host" 3 "$HOST_STDOUT" "press start"
    send_cmd "Host" 3 "$HOST_STDOUT" "wait 500"
    # Verify again
    send_cmd "Host" 3 "$HOST_STDOUT" "screenshot $OUTPUT_DIR/verify_c.png"
    send_cmd "Host" 3 "$HOST_STDOUT" "press right"
    send_cmd "Host" 3 "$HOST_STDOUT" "wait 500"
    send_cmd "Host" 3 "$HOST_STDOUT" "screenshot $OUTPUT_DIR/verify_d.png"
    if cmp -s "$OUTPUT_DIR/verify_c.png" "$OUTPUT_DIR/verify_d.png"; then
        log "WARNING: Game still not responding after unpause"
    else
        log "Game is now running"
    fi
else
    log "Game is running"
fi

# P1 takes screenshot of gameplay
log "P1: taking 'before' screenshot..."
send_cmd "Host" 3 "$HOST_STDOUT" "screenshot $OUTPUT_DIR/phase2_before.png"

# P2 presses A (jump as Dale). The press command blocks for ~200ms (hold time).
# During that time the host keeps emulating. We need to catch the screenshot
# WHILE Dale is mid-jump, so we fire the press without waiting for completion,
# then immediately tell the host to wait a bit and screenshot.
log "P2: pressing A (jump as Dale)..."
echo "press a" >&4  # Fire and forget to client

# The A press needs ~200ms to register + propagate through netplay.
# Wait 400ms then screenshot — Dale should be mid-jump at this point.
log "Waiting 400ms to catch mid-jump on host..."
send_cmd "Host" 3 "$HOST_STDOUT" "wait 400"

log "P1: taking 'after' screenshot..."
send_cmd "Host" 3 "$HOST_STDOUT" "screenshot $OUTPUT_DIR/phase2_after.png"

# Now wait for client press to finish (it will have printed OK by now)
sleep 0.5

# Compare screenshots: Dale's jump should change the screen
PHASE2_RESULT="FAIL"
if [[ -f "$OUTPUT_DIR/phase2_before.png" && -f "$OUTPUT_DIR/phase2_after.png" ]]; then
    if ! cmp -s "$OUTPUT_DIR/phase2_before.png" "$OUTPUT_DIR/phase2_after.png"; then
        PHASE2_RESULT="PASS"
        pass "Phase 2: P2's A input propagated to P1 (Dale jumped)"
    else
        fail "Phase 2: Screenshots are identical — P2's A did not reach P1"
    fi
else
    fail "Phase 2: Screenshot files missing"
fi

# ---------------------------------------------------------------------------
# 10. Quit both processes
# ---------------------------------------------------------------------------
log ""
log "Sending quit commands..."
send_cmd "Host" 3 "$HOST_STDOUT" "quit" || true
send_cmd "Client" 4 "$CLIENT_STDOUT" "quit" || true

# Close stdin pipes
exec 3>&-
exec 4>&-

# Wait for processes to exit
wait "$HOST_PID" 2>/dev/null || true
wait "$CLIENT_PID" 2>/dev/null || true
HOST_PID=""
CLIENT_PID=""

# ---------------------------------------------------------------------------
# 11. Report results
# ---------------------------------------------------------------------------
log ""
log "=========================================="
log " Results"
log "=========================================="
log ""
log "  Phase 1 (title select, P1→P2): $PHASE1_RESULT"
log "  Phase 2 (in-game, P2→P1):      $PHASE2_RESULT"
log ""
log "  Screenshots: $OUTPUT_DIR/"
log "  Host stderr: $HOST_STDERR"
log "  Client stderr: $CLIENT_STDERR"
log ""

if [[ "$PHASE1_RESULT" == "PASS" && "$PHASE2_RESULT" == "PASS" ]]; then
    pass "All netplay E2E tests passed!"
    exit 0
else
    fail "Some netplay E2E tests failed"
    exit 1
fi
