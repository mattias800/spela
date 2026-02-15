#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/server"
echo "Starting backend server..."
go run ./cmd/server
