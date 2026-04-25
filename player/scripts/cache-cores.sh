#!/usr/bin/env bash
# Pre-cache one or more libretro cores onto the connected device at
# /data/local/tmp/, so KoinResetRule.preCacheCores() can copy them
# into the app's cores dir at test startup. Skipping the libretro
# buildbot fetch on every test run cuts setup time by 5-10s per
# class and avoids hammering the buildbot.
#
# Usage:
#   cache-cores.sh nestopia mupen64plus_next ...
#
# Resolution order (per core):
#   1. If the core is already on the device → no-op.
#   2. If a host-local cache at player/.e2e-cores/ has it → adb push only.
#   3. Otherwise, curl from libretro buildbot once, save locally, push.
#
# Opt out via SPELA_E2E_REAL_CORE_DOWNLOAD=1 — in that case the
# device is left empty and the first test to start a game exercises
# the real first-download flow.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLAYER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
HOST_CACHE_DIR="$PLAYER_DIR/.e2e-cores"

ADB_SERIAL="${ADB_SERIAL:-}"
if [ -z "$ADB_SERIAL" ]; then
  echo "cache-cores: ADB_SERIAL not set — skipping." >&2
  exit 0
fi

if [ "${SPELA_E2E_REAL_CORE_DOWNLOAD:-0}" = "1" ]; then
  echo "cache-cores: SPELA_E2E_REAL_CORE_DOWNLOAD=1 — leaving device empty so tests exercise real download."
  exit 0
fi

if [ "$#" -eq 0 ]; then
  echo "cache-cores: no cores specified" >&2
  exit 1
fi

mkdir -p "$HOST_CACHE_DIR"

cache_one_core() {
  local core_name="$1"
  local core_file="${core_name}_libretro_android.so"
  local device_path="/data/local/tmp/$core_file"
  local host_cache="$HOST_CACHE_DIR/$core_file"
  local buildbot_url="https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/${core_file}.zip"

  # 1. Already on device?
  if adb -s "$ADB_SERIAL" shell "[ -f $device_path ]" 2>/dev/null; then
    echo "cache-cores: $device_path already present."
    return 0
  fi

  # 2. Host-local cache?
  if [ ! -f "$host_cache" ]; then
    echo "cache-cores: downloading $core_file from libretro buildbot ($buildbot_url)…"
    local tmp_zip="$HOST_CACHE_DIR/.${core_name}.download.zip"
    trap 'rm -f "$tmp_zip"' EXIT
    curl -fsSL -o "$tmp_zip" "$buildbot_url"
    unzip -oq "$tmp_zip" -d "$HOST_CACHE_DIR"
    rm -f "$tmp_zip"
    trap - EXIT
    if [ ! -f "$host_cache" ]; then
      echo "cache-cores: download unpacked but $host_cache is missing — aborting." >&2
      return 1
    fi
    echo "cache-cores: cached at $host_cache."
  fi

  # 3. Push to device.
  adb -s "$ADB_SERIAL" push "$host_cache" "$device_path" >/dev/null
  echo "cache-cores: pushed $core_file to $device_path."
}

for core in "$@"; do
  cache_one_core "$core"
done
