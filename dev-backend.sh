#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

export SPELA_GAME_DIRS="$SCRIPT_DIR/testdata/roms"
export SPELA_BIOS_DIR="$SCRIPT_DIR/testdata/bios"

cd "$SCRIPT_DIR/server"
echo "Starting backend server..."
echo "  Game dirs: $SPELA_GAME_DIRS"
echo "  BIOS dir:  $SPELA_BIOS_DIR"
go run ./cmd/server
