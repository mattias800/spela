#!/usr/bin/env bash
# Converts console logo SVGs to PNGs for native app compatibility.
# SVGs remain the source of truth; re-run this script after any SVG changes.
#
# Requires: librsvg (rsvg-convert) — install via `brew install librsvg`
#           ImageMagick (magick) — install via `brew install imagemagick`
#
# Usage: ./scripts/generate-logo-pngs.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SVG_DIR="$SCRIPT_DIR/../internal/api/static/console-logos"
PNG_DIR="$SCRIPT_DIR/../internal/api/static/console-logos-png"

if ! command -v rsvg-convert &>/dev/null; then
    echo "Error: rsvg-convert is required. Install with: brew install librsvg"
    exit 1
fi

if ! command -v magick &>/dev/null; then
    echo "Error: ImageMagick is required. Install with: brew install imagemagick"
    exit 1
fi

mkdir -p "$PNG_DIR"

count=0
failed=()
for svg in "$SVG_DIR"/*.svg; do
    name="$(basename "$svg" .svg)"
    png="$PNG_DIR/${name}.png"

    # Skip if PNG is newer than SVG
    if [[ -f "$png" && "$png" -nt "$svg" ]]; then
        continue
    fi

    echo "Converting ${name}.svg → ${name}.png"
    # Strip enable-background (can confuse some SVG renderers), convert with rsvg-convert,
    # then produce clean 32-bit RGBA PNG without bKGD chunk.
    sed 's/style="enable-background[^"]*"//g' "$svg" \
        | rsvg-convert -w 800 --background-color none \
        | magick png:- -alpha set -define png:exclude-chunk=bKGD PNG32:"$png"
    ((count++))
done

if [[ $count -eq 0 ]]; then
    echo "All PNGs up to date."
else
    echo "Converted $count logo(s)."
fi

# Validate: every logo PNG must have at least some transparent pixels.
# A logo with 0% transparency likely has a baked-in opaque background.
echo ""
echo "Validating transparency..."
errors=0
for png in "$PNG_DIR"/*.png; do
    name="$(basename "$png" .png)"
    # Count fully transparent pixels (alpha == 0)
    transparent=$(magick "$png" -channel A -separate -format '%[fx:mean]' info: 2>/dev/null)
    # mean=1.0 → all opaque, mean=0.0 → all transparent
    # If mean is exactly 1.0, there are zero transparent pixels
    if [[ "$transparent" == "1" ]]; then
        echo "  FAIL: ${name}.png has no transparent pixels (likely opaque background)"
        ((errors++))
    else
        # Calculate percentage of transparent pixels
        pct=$(magick "$png" -channel A -separate -format '%[fx:100*(1-mean)]' info: 2>/dev/null)
        echo "  OK: ${name}.png — ${pct}% transparent"
    fi
done

if [[ $errors -gt 0 ]]; then
    echo ""
    echo "WARNING: $errors logo(s) have no transparency. These need manual SVG fixes."
    exit 1
else
    echo ""
    echo "All logos have transparency. ✓"
fi
