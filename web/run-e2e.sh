#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ── Parse arguments ──

FILTER=""
UI=false

while [ $# -gt 0 ]; do
  case "$1" in
    --ui) UI=true; shift ;;
    *) FILTER="$1"; shift ;;
  esac
done

# ── Run tests ──

if $UI; then
  echo "Opening Playwright UI..."
  npx playwright test --ui
elif [ -n "$FILTER" ]; then
  echo "Running Playwright E2E tests matching: $FILTER"
  npx playwright test "$FILTER"
else
  echo "Running all Playwright E2E tests..."
  npx playwright test
fi
