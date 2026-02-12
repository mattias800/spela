#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ── Parse arguments ──

FILTER=""
VERBOSE=false

while [ $# -gt 0 ]; do
  case "$1" in
    -v|--verbose) VERBOSE=true; shift ;;
    *) FILTER="$1"; shift ;;
  esac
done

# ── Build go test args ──

ARGS=()
if $VERBOSE; then
  ARGS+=("-v")
fi

if [ -n "$FILTER" ]; then
  echo "Running Go tests matching: $FILTER"
  ARGS+=("-run" "$FILTER")
else
  echo "Running all Go tests..."
fi

# ── Run tests ──

go test "${ARGS[@]}" ./...
