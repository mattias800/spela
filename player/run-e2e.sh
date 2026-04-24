#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "Error: $ENV_FILE not found. Create it with ADB_SERIAL and DEVICE_PIN."
  exit 1
fi

source "$ENV_FILE"

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

cleanup_after_tests() {
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
  # Run a specific test class or method
  # Usage: ./run-e2e.sh com.spela.player.android.EmulationTest#playCastlevania
  #    or: ./run-e2e.sh com.spela.player.android.EmulationTest
  echo "Running test: $1"
  ANDROID_SERIAL="$ADB_SERIAL" "$SCRIPT_DIR/gradlew" :android:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class="$1"
else
  echo "Running all E2E tests..."
  ANDROID_SERIAL="$ADB_SERIAL" "$SCRIPT_DIR/gradlew" :android:connectedDebugAndroidTest
fi
