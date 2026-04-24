#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
E2E_COMPOSE="$REPO_ROOT/docker-compose.e2e.yml"
ENV_FILE="$SCRIPT_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "Error: $ENV_FILE not found. Create it with ADB_SERIAL and DEVICE_PIN."
  exit 1
fi

source "$ENV_FILE"

# ── Clean-slate backend reset ──
# Every E2E run starts with a fresh backend state so results are fully
# idempotent — no test mutates a DB row, plays a game, or creates a save
# that leaks into the next run. We teardown named volumes with `down -v`,
# then rebuild and wait for health.
#
# Opt-out with --skip-reset when iterating fast on a single test and you
# know the state is already clean (e.g. you just ran a reset five minutes
# ago). Default is always: reset before every run.
RESET_BACKEND=true
ARGS=()
for arg in "$@"; do
  case "$arg" in
    --skip-reset) RESET_BACKEND=false ;;
    *) ARGS+=("$arg") ;;
  esac
done
set -- "${ARGS[@]+"${ARGS[@]}"}"

if [ "$RESET_BACKEND" = true ]; then
  echo "── Resetting backend (docker compose down -v) ──"
  docker compose -f "$E2E_COMPOSE" down -v --remove-orphans >/dev/null 2>&1 || true
else
  echo "── Skipping backend reset (--skip-reset) — keeping existing volumes ──"
fi

echo "── Ensuring backend is up (docker compose up -d --build --wait) ──"
docker compose -f "$E2E_COMPOSE" up -d --build --wait
echo "Backend up and healthy."

# ── Unlock device if locked ──

LOCKED=$(adb -s "$ADB_SERIAL" shell dumpsys window | grep mInputRestricted | head -1)
if echo "$LOCKED" | grep -q "true"; then
  echo "Device is locked — unlocking..."
  adb -s "$ADB_SERIAL" shell input keyevent KEYCODE_WAKEUP
  sleep 0.5
  adb -s "$ADB_SERIAL" shell input swipe 540 1800 540 800 300
  sleep 0.5
  adb -s "$ADB_SERIAL" shell input text "$DEVICE_PIN"
  sleep 0.3
  adb -s "$ADB_SERIAL" shell input keyevent KEYCODE_ENTER
  sleep 1
  echo "Device unlocked."
else
  echo "Device is already unlocked."
fi

# ── Set up reverse port forwarding (device localhost:8080 → host localhost:8080) ──
# This works on both emulators and physical devices, so tests can use 127.0.0.1:8080.
adb -s "$ADB_SERIAL" reverse tcp:8080 tcp:8080
echo "Reverse port forwarding set up (device:8080 → host:8080)."

# ── Suspend third-party accessibility services ──
# Some devices (notably the AYN Thor via `com.odin.gameassistant`) register an
# accessibility service that monopolises the accessibility bridge. Android
# only allows one bridge consumer at a time, so UiAutomator's `dump()` and
# element lookups fail with "null root node returned by
# UiTestAutomationBridge" — which cascades through every helper that falls
# back to UiAutomator (back button, focus checks, fullscreen IME detection).
# We snapshot the list here and restore it in the cleanup trap so the user's
# normal accessibility setup comes back when tests finish.
PREV_A11Y=$(adb -s "$ADB_SERIAL" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')
if [ -n "$PREV_A11Y" ] && [ "$PREV_A11Y" != "null" ]; then
  adb -s "$ADB_SERIAL" shell "settings put secure enabled_accessibility_services ''" >/dev/null
  echo "Disabled accessibility services for test run (was: $PREV_A11Y)."
fi

# ── Keep screen on during tests ──
# The Gradle build can take 1-2 minutes. Without this, physical devices go back
# to sleep before the test APK is installed, causing the Activity to pause immediately.
PREV_TIMEOUT=$(adb -s "$ADB_SERIAL" shell settings get system screen_off_timeout 2>/dev/null || echo "")
adb -s "$ADB_SERIAL" shell settings put system screen_off_timeout 600000
echo "Screen timeout set to 10 minutes for test duration."
adb -s "$ADB_SERIAL" shell input keyevent KEYCODE_WAKEUP

# ── Preferred launch display ──
# Some devices (notably the AYN Thor with its 1240x1080 secondary
# "Screen-2" presentation display) route activities to a non-primary
# display by default. ActivityScenario / Compose UI Test / UiAutomator
# all target display 0, so tests then look at the launcher instead of
# the app and every assertion times out.
#
# Force one explicit launch on display 0 before the test runner takes
# over — Android caches the launch display per-task, and subsequent
# `ActivityScenario.launch()` calls inherit it. Cheap, side-effect
# free, non-destructive (we force-stop the app right after).
if adb -s "$ADB_SERIAL" shell pm list packages 2>/dev/null | grep -q com.spela.player; then
  adb -s "$ADB_SERIAL" shell am start --display 0 -n com.spela.player/.android.MainActivity >/dev/null 2>&1 || true
  sleep 1
  adb -s "$ADB_SERIAL" shell am force-stop com.spela.player >/dev/null 2>&1 || true
  echo "Primed activity on display 0 (multi-display devices)."
fi

# ── Test failure artifacts (host-side path) ──
# FailureDiagnosticsRule writes a per-failure dir on the device under
# /sdcard/spela-test-failures/. We pull the whole tree to here on every
# exit (success or fail) and print the absolute paths so an agent can
# locate them without scrolling through gradle output.
HOST_FAILURES_DIR="$SCRIPT_DIR/build/test-failures"
DEVICE_FAILURES_DIR="/sdcard/spela-test-failures"

# Clear stale failures from previous runs so the dir reflects only this
# run's artefacts.
adb -s "$ADB_SERIAL" shell "rm -rf $DEVICE_FAILURES_DIR" 2>/dev/null || true
rm -rf "$HOST_FAILURES_DIR"
mkdir -p "$HOST_FAILURES_DIR"

cleanup_after_tests() {
  # Pull failure artefacts BEFORE restoring device state — the screen
  # going to sleep can race with adb pull on slow USB.
  if adb -s "$ADB_SERIAL" shell "[ -d $DEVICE_FAILURES_DIR ]" 2>/dev/null; then
    adb -s "$ADB_SERIAL" pull "$DEVICE_FAILURES_DIR/." "$HOST_FAILURES_DIR/" >/dev/null 2>&1 || true
    if [ -n "$(ls -A "$HOST_FAILURES_DIR" 2>/dev/null)" ]; then
      echo
      echo "── Test failure artefacts ──"
      for d in "$HOST_FAILURES_DIR"/*/; do
        echo "  ${d%/}"
        for f in "$d"*; do
          [ -f "$f" ] && echo "    - $(basename "$f")"
        done
      done
      echo
    fi
  fi

  if [ -n "$PREV_TIMEOUT" ] && [ "$PREV_TIMEOUT" != "null" ]; then
    adb -s "$ADB_SERIAL" shell settings put system screen_off_timeout "$PREV_TIMEOUT" 2>/dev/null || true
    echo "Screen timeout restored to ${PREV_TIMEOUT}ms."
  fi
  if [ -n "$PREV_A11Y" ] && [ "$PREV_A11Y" != "null" ]; then
    adb -s "$ADB_SERIAL" shell "settings put secure enabled_accessibility_services '$PREV_A11Y'" 2>/dev/null || true
    echo "Accessibility services restored."
  fi
  # Turn off screen after tests to protect OLED from burn-in
  adb -s "$ADB_SERIAL" shell input keyevent KEYCODE_SLEEP 2>/dev/null || true
  echo "Screen turned off to protect OLED."
}
trap cleanup_after_tests EXIT

# ── Run Compose instrumented tests ──

if [ $# -gt 0 ]; then
  # Run a specific test class or method.
  # Usage:
  #   ./run-e2e.sh com.spela.player.android.EmulationTest#playCastlevania
  #   ./run-e2e.sh com.spela.player.android.EmulationTest
  echo "Running test: $1"
  ANDROID_SERIAL="$ADB_SERIAL" "$SCRIPT_DIR/gradlew" :android:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class="$1"
  exit $?
fi

# ── Fail-fast batch run ──
# Default: discover all `androidTest` test classes and run them one at a
# time, aborting on the first class that has any failure. Cuts the
# "watch 80 doomed tests time out" pattern down to "see the first
# failure with full diagnostics."
#
# Pass `--no-fail-fast` to run every class regardless (e.g. for a
# nightly all-tests-summary).
FAIL_FAST=true
for arg in "$@"; do
  case "$arg" in
    --no-fail-fast) FAIL_FAST=false ;;
  esac
done

ANDROID_TEST_DIR="$SCRIPT_DIR/android/src/androidTest/java/com/spela/player/android"
TEST_CLASSES=$(find "$ANDROID_TEST_DIR" -name '*Test.kt' -maxdepth 1 \
  | xargs -I{} basename {} .kt \
  | sort \
  | sed 's|^|com.spela.player.android.|')

if [ -z "$TEST_CLASSES" ]; then
  echo "No test classes found under $ANDROID_TEST_DIR"
  exit 1
fi

echo "Running E2E test classes ($([ "$FAIL_FAST" = true ] && echo 'fail-fast' || echo 'continue-on-failure')):"
echo "$TEST_CLASSES" | sed 's|^|  - |'
echo

OVERALL_RESULT=0
for cls in $TEST_CLASSES; do
  echo "════════ $cls ════════"
  ANDROID_SERIAL="$ADB_SERIAL" "$SCRIPT_DIR/gradlew" :android:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class="$cls" || {
    OVERALL_RESULT=$?
    echo "✗ $cls failed."
    if [ "$FAIL_FAST" = true ]; then
      echo "Stopping (fail-fast). Pass --no-fail-fast to run all classes regardless."
      exit "$OVERALL_RESULT"
    fi
  }
done

exit "$OVERALL_RESULT"
