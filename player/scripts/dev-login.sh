#!/usr/bin/env bash
#
# Drive the Spela Android player from any starting state (cold install,
# logged-out, or already at Login) through to a logged-in Home screen.
#
# Useful for manual smoke testing on the AYN Thor when you don't want
# to run the full ./run-e2e.sh suite. Reads the same .env file —
# ADB_SERIAL is required; SPELA_USERNAME, SPELA_PASSWORD, SERVER_NAME,
# SERVER_URL are optional (default: player / player123 / Local /
# http://127.0.0.1:8080).
#
# Workarounds for the AYN Thor:
# - Launches the activity on display 0 (Thor's secondary "Screen-2"
#   presentation display otherwise eats it).
# - Sets up `adb reverse tcp:8080 tcp:8080` so the app can reach a
#   docker-compose backend on the host.
# - Dismisses the soft keyboard between every text input. Gboard's
#   landscape extract-editor mode otherwise stacks every typed char
#   into whichever field was first tapped, ignoring subsequent taps.
# - Clears each text field with KEYCODE_MOVE_END + repeated KEYCODE_DEL
#   before typing, so re-running the script over a partially-filled
#   form doesn't pile garbage onto existing content.
#
# Usage:
#   ./player/scripts/dev-login.sh           # uses .env
#   ./player/scripts/dev-login.sh <serial>  # override ADB_SERIAL
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLAYER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PLAYER_DIR/.env"

if [ -f "$ENV_FILE" ]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

ADB_SERIAL="${1:-${ADB_SERIAL:-}}"
SERVER_NAME="${SERVER_NAME:-Local}"
SERVER_URL="${SERVER_URL:-http://127.0.0.1:8080}"
SPELA_USERNAME="${SPELA_USERNAME:-player}"
SPELA_PASSWORD="${SPELA_PASSWORD:-player123}"

if [ -z "$ADB_SERIAL" ]; then
  ADB_SERIAL="$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
fi
if [ -z "$ADB_SERIAL" ]; then
  echo "No device. Pass serial as arg or set ADB_SERIAL in $ENV_FILE." >&2
  exit 1
fi

ADB="adb -s $ADB_SERIAL"
DUMP=/sdcard/dev-login.dump.xml

echo "── Device $ADB_SERIAL ──"
$ADB shell input keyevent KEYCODE_WAKEUP >/dev/null
sleep 0.3
$ADB reverse tcp:8080 tcp:8080 >/dev/null

# Force-launch on display 0. Thor caches the launch display per task,
# so once the app has been launched on display 0, ActivityScenario
# inherits it. Cheap to repeat each run.
$ADB shell am force-stop com.spela.player >/dev/null 2>&1 || true
sleep 0.4
$ADB shell am start --display 0 -n com.spela.player/.android.MainActivity >/dev/null
sleep 3

# ── UI helpers ──

LOCAL_DUMP="/tmp/dev-login.ui.xml"

dump_ui() {
  $ADB shell uiautomator dump "$DUMP" >/dev/null 2>&1 || true
  $ADB shell cat "$DUMP" >"$LOCAL_DUMP" 2>/dev/null || true
}

# Returns center (x y) of the first node whose text or content-desc
# CONTAINS the needle. Empty output if not found.
#
# Implementation note: previously used `cat | python3 - <<HEREDOC` —
# but the heredoc and the pipe both redirect stdin and bash gives the
# heredoc precedence, so python's sys.stdin returned the script body
# rather than the XML. Now we dump to a host-side file and pass the
# path as argv[1]; needle is argv[2].
ui_center() {
  dump_ui
  python3 -c '
import re, sys
with open(sys.argv[1]) as f: data = f.read()
needle = sys.argv[2]
pattern = (
    r"<node[^>]*?(?:text|content-desc)=\"([^\"]*" + re.escape(needle) +
    r"[^\"]*)\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\""
)
m = re.search(pattern, data)
if not m: sys.exit(0)
x = (int(m.group(2)) + int(m.group(4))) // 2
y = (int(m.group(3)) + int(m.group(5))) // 2
print(f"{x} {y}")
' "$LOCAL_DUMP" "$1"
}

ui_has() {
  dump_ui
  grep -q "$1" "$LOCAL_DUMP" 2>/dev/null
}

# Returns "x y" for the Nth (0-indexed) android.widget.EditText.
edittext_center() {
  local n=$1
  dump_ui
  python3 -c '
import re, sys
with open(sys.argv[1]) as f: data = f.read()
n = int(sys.argv[2])
matches = re.findall(
    r"<node[^>]*?class=\"android\.widget\.EditText\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"",
    data,
)
if n >= len(matches): sys.exit(0)
x1, y1, x2, y2 = map(int, matches[n])
print(f"{(x1+x2)//2} {(y1+y2)//2}")
' "$LOCAL_DUMP" "$n"
}

# Tap field at (x,y), clear, type, dismiss keyboard. Single-arg
# coords to avoid quoting hell.
tap_clear_type() {
  local coords=$1 text=$2
  read -r x y <<<"$coords"
  $ADB shell input tap "$x" "$y"
  sleep 0.7
  $ADB shell input keyevent KEYCODE_MOVE_END >/dev/null
  for _ in $(seq 1 50); do
    $ADB shell input keyevent KEYCODE_DEL >/dev/null
  done
  sleep 0.2
  $ADB shell input text "$(printf %s "$text" | sed 's/ /%s/g')"
  sleep 0.4
  # Dismiss the soft keyboard. KEYCODE_BACK while IME is up closes
  # only the IME on Android, not the activity.
  $ADB shell input keyevent KEYCODE_BACK >/dev/null
  sleep 0.8
}

tap_node() {
  local coords=$1
  read -r x y <<<"$coords"
  $ADB shell input tap "$x" "$y"
}

# ── Drive screens ──

for attempt in $(seq 1 10); do
  # Already on Home? Look for distinctive Home-screen markers.
  if ui_has "Recently Added" || ui_has "Continue Playing" \
      || ui_has "screen_home" || ui_has "Top Rated"; then
    echo "✓ On Home — logged in"
    exit 0
  fi

  # Add Server screen (cold install, no servers yet).
  if ui_has "Server URL" && ui_has "Connect"; then
    echo "── Add Server: $SERVER_NAME → $SERVER_URL ──"
    name_field=$(edittext_center 0)
    url_field=$(edittext_center 1)
    [ -z "$name_field" ] || [ -z "$url_field" ] && {
      echo "Couldn't locate server form fields"; exit 1; }
    tap_clear_type "$name_field" "$SERVER_NAME"
    tap_clear_type "$url_field" "$SERVER_URL"
    connect=$(ui_center "Connect")
    [ -z "$connect" ] && { echo "Connect button not found"; exit 1; }
    tap_node "$connect"
    sleep 3
    continue
  fi

  # Server list (one or more saved servers, no active session).
  # Distinguish from Add Server by presence of a server card (URL text)
  # plus the persistent "Add Server" button below.
  if ui_has "Add Server" && ui_has "$SERVER_URL"; then
    echo "── Server list: tapping $SERVER_NAME ──"
    card=$(ui_center "$SERVER_NAME")
    [ -z "$card" ] && { echo "Server card not found"; exit 1; }
    tap_node "$card"
    sleep 3
    continue
  fi

  # Login screen.
  if ui_has "Username" && ui_has "Sign In"; then
    echo "── Login: signing in as $SPELA_USERNAME ──"
    user_field=$(edittext_center 0)
    pass_field=$(edittext_center 1)
    [ -z "$user_field" ] || [ -z "$pass_field" ] && {
      echo "Couldn't locate login form fields"; exit 1; }
    tap_clear_type "$user_field" "$SPELA_USERNAME"
    tap_clear_type "$pass_field" "$SPELA_PASSWORD"
    signin=$(ui_center "Sign In")
    [ -z "$signin" ] && { echo "Sign In button not found"; exit 1; }
    tap_node "$signin"
    sleep 4
    continue
  fi

  echo "Unknown screen state (attempt $attempt) — waiting…"
  sleep 2
done

echo "Failed to reach Home after 10 attempts." >&2
exit 1
