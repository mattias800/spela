#!/usr/bin/env bash
# Fast iteration loop for a single Android E2E test class.
#
# Assumes one-time setup is already done (see docs/e2e-testing.md
# "Iterating on a single Android E2E test"):
#
#   1. AVD running          : emulator -avd spela-test -no-snapshot-load &
#   2. docker-compose up    : docker compose -f docker-compose.e2e.yml up -d --build --wait
#
# Then iterate with:
#
#   ./player/scripts/e2e-android-quick.sh CloneSessionSmokeTest
#   ./player/scripts/e2e-android-quick.sh com.spela.player.android.CloneSessionSmokeTest
#
# Both forms work — the FQCN is auto-prefixed when missing. End-to-end
# wall time is ~2 min after the first run instead of ~25 min via PR CI.
#
# This wrapper:
#   * validates the AVD is online
#   * validates the backend is healthy (no recreate / reset — keeps
#     state warm across iterations)
#   * delegates to run-e2e.sh with --emulator --skip-reset so the heavy
#     lifting (adb reverse, animation disable, core pre-cache,
#     diagnostics) stays in one place
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLAYER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PLAYER_DIR/.." && pwd)"

if [ "$#" -lt 1 ]; then
  cat <<EOF >&2
usage: $0 <TestClass | FullyQualifiedClassName>

Examples:
  $0 CloneSessionSmokeTest
  $0 com.spela.player.android.CoreDecisionFlagsSmokeTest

Prereqs (one-time per machine):
  emulator -avd spela-test -no-snapshot-load &
  docker compose -f docker-compose.e2e.yml up -d --build --wait

See docs/e2e-testing.md "Iterating on a single Android E2E test" for
the full setup walkthrough.
EOF
  exit 64
fi

TEST_CLASS="$1"
case "$TEST_CLASS" in
  *.*) FQCN="$TEST_CLASS" ;;
  *) FQCN="com.spela.player.android.$TEST_CLASS" ;;
esac

# Preflight: emulator running?
if ! adb devices | awk '/^emulator-[0-9]+/ && $2 == "device" {found=1} END{exit !found}'; then
  cat <<'EOF' >&2
error: no running emulator detected.

Start the spela-test AVD:
  emulator -avd spela-test -no-snapshot-load &

Then re-run this script.
EOF
  exit 1
fi

# Preflight: backend healthy?
if ! curl -fs --max-time 2 http://localhost:8080/api/health >/dev/null; then
  cat <<EOF >&2
error: backend not responding on localhost:8080.

Bring up the E2E stack (one-time per session):
  docker compose -f $REPO_ROOT/docker-compose.e2e.yml up -d --build --wait

Then re-run this script.
EOF
  exit 1
fi

echo "── Quick iterate: $FQCN ──"
exec "$PLAYER_DIR/run-e2e.sh" --emulator --skip-reset "$FQCN"
