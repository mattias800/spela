#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Load environment variables from .env
set -a
source "$SCRIPT_DIR/.env"
set +a

# Resolve relative paths from .env against the project root
export SPELA_GAME_DIRS="${SPELA_GAME_DIRS/#.\//$SCRIPT_DIR/}"
export SPELA_BIOS_DIR="${SPELA_BIOS_DIR/#.\//$SCRIPT_DIR/}"

cd "$SCRIPT_DIR/server"
echo "Starting backend server..."
echo "  Game dirs: $SPELA_GAME_DIRS"
echo "  BIOS dir:  $SPELA_BIOS_DIR"
go run ./cmd/server
