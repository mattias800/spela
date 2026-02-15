#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/web"
echo "Starting Vite dev server..."
npm run dev
