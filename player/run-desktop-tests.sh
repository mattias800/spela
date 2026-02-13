#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Parse arguments ──

FILTER=""
RERUN=false

while [ $# -gt 0 ]; do
  case "$1" in
    --rerun) RERUN=true; shift ;;
    *) FILTER="$1"; shift ;;
  esac
done

# ── Build gradle args ──

GRADLE_ARGS=":shared:desktopTest :desktop:desktopTest"

if [ -n "$FILTER" ]; then
  GRADLE_ARGS=":shared:desktopTest --tests $FILTER :desktop:desktopTest --tests $FILTER"
  echo "Running desktop tests matching: $FILTER"
else
  echo "Running all desktop tests..."
fi

if $RERUN; then
  GRADLE_ARGS="$GRADLE_ARGS --rerun-tasks"
fi

# ── Run tests with console output ──

"$SCRIPT_DIR/gradlew" $GRADLE_ARGS \
  -Dkotlin.test.verbose=true \
  --console=plain \
  2>&1

# ── Show summary ──

echo ""
echo "── Test Results ──"
for RESULT_DIR in "$SCRIPT_DIR/shared/build/test-results/desktopTest" "$SCRIPT_DIR/desktop/build/test-results/desktopTest"; do
if [ -d "$RESULT_DIR" ]; then
  TOTAL=0
  FAILURES=0
  ERRORS=0
  for f in "$RESULT_DIR"/TEST-*.xml; do
    [ -f "$f" ] || continue
    t=$(xmllint --xpath "string(/testsuite/@tests)" "$f" 2>/dev/null || echo 0)
    fl=$(xmllint --xpath "string(/testsuite/@failures)" "$f" 2>/dev/null || echo 0)
    e=$(xmllint --xpath "string(/testsuite/@errors)" "$f" 2>/dev/null || echo 0)
    time=$(xmllint --xpath "string(/testsuite/@time)" "$f" 2>/dev/null || echo "?")
    name=$(basename "$f" .xml | sed 's/TEST-//')
    TOTAL=$((TOTAL + t))
    FAILURES=$((FAILURES + fl))
    ERRORS=$((ERRORS + e))
    if [ "$fl" -gt 0 ] || [ "$e" -gt 0 ]; then
      echo "  FAIL  $name ($t tests, ${fl} failures, ${e} errors) ${time}s"
    else
      echo "  PASS  $name ($t tests) ${time}s"
    fi
  done
  echo ""
  echo "Total: $TOTAL tests, $FAILURES failures, $ERRORS errors"
fi
done
