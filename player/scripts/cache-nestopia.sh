#!/usr/bin/env bash
# Ensure nestopia (NES libretro core) is present on the connected device at
# /data/local/tmp/ so KoinResetRule.preCacheCores() can copy it into the
# app's cores dir at test startup. Pre-caching saves every E2E run from
# re-downloading nestopia — real users download on-demand, but thrashing
# libretro buildbot once per CI-style run is antisocial.
#
# Resolution order:
#   1. If the core is already on the device → no-op.
#   2. If a host-local cache at player/.e2e-cores/ has it → adb push only.
#   3. Otherwise, curl from libretro buildbot once, save locally, push.
#
# Opt out via SPELA_E2E_REAL_CORE_DOWNLOAD=1 — in that case the device
# is left empty and the first test to start a game exercises the real
# first-download flow.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLAYER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CORE_FILE="nestopia_libretro_android.so"
DEVICE_PATH="/data/local/tmp/$CORE_FILE"
HOST_CACHE_DIR="$PLAYER_DIR/.e2e-cores"
HOST_CACHE="$HOST_CACHE_DIR/$CORE_FILE"
BUILDBOT_URL="https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/nestopia_libretro_android.so.zip"

ADB_SERIAL="${ADB_SERIAL:-}"
if [ -z "$ADB_SERIAL" ]; then
  echo "cache-nestopia: ADB_SERIAL not set — skipping." >&2
  exit 0
fi

if [ "${SPELA_E2E_REAL_CORE_DOWNLOAD:-0}" = "1" ]; then
  echo "cache-nestopia: SPELA_E2E_REAL_CORE_DOWNLOAD=1 — leaving device empty so tests exercise real download."
  exit 0
fi

# 1. Already on device?
if adb -s "$ADB_SERIAL" shell "[ -f $DEVICE_PATH ]" 2>/dev/null; then
  echo "cache-nestopia: $DEVICE_PATH already present."
  exit 0
fi

mkdir -p "$HOST_CACHE_DIR"

# 2. Host-local cache?
if [ ! -f "$HOST_CACHE" ]; then
  echo "cache-nestopia: downloading once from libretro buildbot ($BUILDBOT_URL)…"
  TMP_ZIP="$HOST_CACHE_DIR/.nestopia.download.zip"
  trap 'rm -f "$TMP_ZIP"' EXIT
  curl -fsSL -o "$TMP_ZIP" "$BUILDBOT_URL"
  unzip -oq "$TMP_ZIP" -d "$HOST_CACHE_DIR"
  rm -f "$TMP_ZIP"
  trap - EXIT
  if [ ! -f "$HOST_CACHE" ]; then
    echo "cache-nestopia: download unpacked but $HOST_CACHE is missing — aborting." >&2
    exit 1
  fi
  echo "cache-nestopia: cached at $HOST_CACHE."
fi

# 3. Push to device.
adb -s "$ADB_SERIAL" push "$HOST_CACHE" "$DEVICE_PATH" >/dev/null
echo "cache-nestopia: pushed $CORE_FILE to $DEVICE_PATH."
