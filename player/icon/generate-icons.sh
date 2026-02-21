#!/usr/bin/env bash
#
# Generate all platform-specific icon assets from the canonical SVG.
# Prefers Chrome headless for accurate SVG gradient rendering, falls back
# to rsvg-convert or ImageMagick.
#
# Optional: iconutil (macOS-only, for .icns)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SVG="$SCRIPT_DIR/spela-icon.svg"
PLAYER_DIR="$(dirname "$SCRIPT_DIR")"

if [ ! -f "$SVG" ]; then
  echo "ERROR: Cannot find $SVG"
  exit 1
fi

# Find Chrome binary
find_chrome() {
  if command -v google-chrome &>/dev/null; then
    echo "google-chrome"
  elif command -v chromium &>/dev/null; then
    echo "chromium"
  elif [ -x "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ]; then
    echo "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
  else
    echo ""
  fi
}

CHROME_BIN="$(find_chrome)"

# Render SVG to a high-res master PNG using Chrome (best gradient support)
MASTER_PNG=""
if [ -n "$CHROME_BIN" ]; then
  MASTER_PNG="$SCRIPT_DIR/_master_1024.png"
  # Create a temporary HTML wrapper that fills the SVG to the viewport
  WRAPPER_HTML="$SCRIPT_DIR/_render.html"
  cat > "$WRAPPER_HTML" <<'HTMLEOF'
<!DOCTYPE html>
<html><head><style>
  * { margin: 0; padding: 0; }
  body { width: 1024px; height: 1024px; overflow: hidden; }
  img { width: 1024px; height: 1024px; }
</style></head><body>
<img src="spela-icon.svg">
</body></html>
HTMLEOF
  "$CHROME_BIN" --headless --disable-gpu --screenshot="$MASTER_PNG" \
    --window-size=1024,1024 "file://$WRAPPER_HTML" 2>/dev/null
  rm -f "$WRAPPER_HTML"
  echo "  Using Chrome for SVG rendering (best gradient support)"
fi

# SVG-to-PNG: resize from master if available, otherwise fall back to direct conversion
svg_to_png() {
  local size="$1" input="$2" output="$3"
  if [ -n "$MASTER_PNG" ] && [ -f "$MASTER_PNG" ] && command -v magick &>/dev/null; then
    magick "$MASTER_PNG" -resize "${size}x${size}" "$output"
  elif command -v rsvg-convert &>/dev/null; then
    rsvg-convert -w "$size" -h "$size" "$input" -o "$output"
  elif command -v magick &>/dev/null; then
    magick -background none -density 300 "$input" -resize "${size}x${size}" "$output"
  elif command -v convert &>/dev/null; then
    convert -background none -density 300 "$input" -resize "${size}x${size}" "$output"
  else
    echo "ERROR: No SVG converter found. Install one of:"
    echo "  Google Chrome (for best results)"
    echo "  rsvg-convert: brew install librsvg (macOS) / apt install librsvg2-bin (Linux)"
    echo "  ImageMagick:  brew install imagemagick (macOS) / apt install imagemagick (Linux)"
    exit 1
  fi
}

echo "=== Generating icons from $SVG ==="

# --- Android mipmap PNGs ---
for entry in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  density="${entry%%:*}"
  size="${entry##*:}"
  out_dir="$PLAYER_DIR/android/src/main/res/mipmap-$density"
  mkdir -p "$out_dir"
  svg_to_png "$size" "$SVG" "$out_dir/ic_launcher.png"
  echo "  Android mipmap-$density: ${size}x${size} -> $out_dir/ic_launcher.png"
done

# --- Desktop classpath resource (SVG copy) ---
desktop_res="$PLAYER_DIR/desktop/src/desktopMain/resources"
mkdir -p "$desktop_res"
cp "$SVG" "$desktop_res/spela-icon.svg"
echo "  Desktop resource: $desktop_res/spela-icon.svg"

# --- macOS .icns (requires iconutil, macOS only) ---
if command -v iconutil &>/dev/null; then
  ICONSET_DIR="$SCRIPT_DIR/spela-icon.iconset"
  mkdir -p "$ICONSET_DIR"

  for size in 16 32 128 256 512; do
    svg_to_png "$size" "$SVG" "$ICONSET_DIR/icon_${size}x${size}.png"
    double=$((size * 2))
    svg_to_png "$double" "$SVG" "$ICONSET_DIR/icon_${size}x${size}@2x.png"
  done

  iconutil -c icns "$ICONSET_DIR" -o "$SCRIPT_DIR/spela-icon.icns"
  rm -rf "$ICONSET_DIR"
  echo "  macOS: $SCRIPT_DIR/spela-icon.icns"
else
  echo "  macOS: skipped .icns (iconutil not found — macOS only)"
fi

# --- Windows .ico (requires ImageMagick) ---
if command -v magick &>/dev/null || command -v convert &>/dev/null; then
  ICO_PNGS=()
  for size in 16 32 48 64 128 256; do
    png="$SCRIPT_DIR/ico_${size}.png"
    svg_to_png "$size" "$SVG" "$png"
    ICO_PNGS+=("$png")
  done
  if command -v magick &>/dev/null; then
    magick "${ICO_PNGS[@]}" "$SCRIPT_DIR/spela-icon.ico"
  else
    convert "${ICO_PNGS[@]}" "$SCRIPT_DIR/spela-icon.ico"
  fi
  rm -f "${ICO_PNGS[@]}"
  echo "  Windows: $SCRIPT_DIR/spela-icon.ico"
else
  echo "  Windows: skipped .ico (ImageMagick not found)"
fi

# --- Linux 512x512 PNG ---
svg_to_png 512 "$SVG" "$SCRIPT_DIR/spela-icon-512.png"
echo "  Linux: $SCRIPT_DIR/spela-icon-512.png"

# Clean up master PNG
if [ -n "$MASTER_PNG" ] && [ -f "$MASTER_PNG" ]; then
  rm -f "$MASTER_PNG"
fi

echo "=== Done ==="
