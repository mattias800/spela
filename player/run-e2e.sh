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
