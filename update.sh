#!/bin/sh
#
# Spela — Pull latest code, rebuild, and restart
#
# Usage:
#   ./update.sh
#

set -e

echo ""
echo "========================================="
echo "  Spela Update"
echo "========================================="
echo ""

# ── Check .env exists ────────────────────────────────────────────────
if [ ! -f .env ]; then
    echo "Error: No .env file found. Run ./setup.sh first."
    exit 1
fi

# ── Pull latest code ─────────────────────────────────────────────────
echo "Pulling latest changes from GitHub..."
git pull --ff-only
echo ""

# ── Rebuild and restart ──────────────────────────────────────────────
echo "Rebuilding and restarting..."
docker compose -f docker-compose.qa.yml up --build -d
echo ""

echo "========================================="
echo "  Update complete!"
echo "========================================="
echo ""
echo "To view logs:  docker compose -f docker-compose.qa.yml logs -f"
