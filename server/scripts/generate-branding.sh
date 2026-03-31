#!/usr/bin/env bash
# Generates optimized branding assets from the cropped source logo.
# The source image (branding/spela-logo-original-cropped.png) is the source of truth.
# Re-run this script after updating it.
#
# Requires: ImageMagick (magick) — install via `brew install imagemagick`
#
# Usage: ./server/scripts/generate-branding.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SOURCE="$REPO_ROOT/branding/spela-logo-original-cropped.png"

if ! command -v magick &>/dev/null; then
    echo "Error: ImageMagick is required. Install with: brew install imagemagick"
    exit 1
fi

if [[ ! -f "$SOURCE" ]]; then
    echo "Error: Source logo not found at $SOURCE"
    exit 1
fi

echo "Source: $SOURCE"
echo "  $(magick identify "$SOURCE")"
echo ""

# Backend static asset — served at /api/branding/logo
DEST_BACKEND="$REPO_ROOT/server/internal/api/static/branding/spela-logo.png"
mkdir -p "$(dirname "$DEST_BACKEND")"
magick "$SOURCE" -resize 512x -strip PNG32:"$DEST_BACKEND"
echo "  Backend logo: $DEST_BACKEND ($(du -h "$DEST_BACKEND" | cut -f1))"

# Player app drawable resource
DEST_PLAYER="$REPO_ROOT/player/shared/src/commonMain/composeResources/drawable/spela_logo.png"
magick "$SOURCE" -resize 512x -strip PNG32:"$DEST_PLAYER"
echo "  Player logo:  $DEST_PLAYER ($(du -h "$DEST_PLAYER" | cut -f1))"

# README logo (smaller for GitHub display)
DEST_README="$REPO_ROOT/docs/spela-logo.png"
mkdir -p "$(dirname "$DEST_README")"
magick "$SOURCE" -resize 400x -strip PNG32:"$DEST_README"
echo "  README logo:  $DEST_README ($(du -h "$DEST_README" | cut -f1))"

echo ""
echo "Done. All branding assets regenerated."
