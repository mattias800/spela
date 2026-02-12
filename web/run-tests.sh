#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ── Parse arguments ──

FILTER=""

while [ $# -gt 0 ]; do
  case "$1" in
    *) FILTER="$1"; shift ;;
  esac
done

# ── Run tests ──

if [ -n "$FILTER" ]; then
  echo "Running Vitest unit tests matching: $FILTER"
  npx vitest run "$FILTER"
else
  echo "Running all Vitest unit tests..."
  npx vitest run
fi
