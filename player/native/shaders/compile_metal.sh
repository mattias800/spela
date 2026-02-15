#!/bin/bash
# Compile Metal shaders to .metallib for macOS.
# Requires Xcode command line tools (xcrun metal/metallib).

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SHADER_DIR="$SCRIPT_DIR"
OUTPUT_DIR="$SCRIPT_DIR/../src"

# Check for xcrun
if ! command -v xcrun &> /dev/null; then
    echo "Error: xcrun not found. Install Xcode command line tools."
    exit 1
fi

echo "Compiling Metal shaders..."

METAL_SHADERS=(
    "passthrough.metal"
    "bilinear.metal"
    "sharp_bilinear.metal"
    "crt_simple.metal"
    "lcd_grid.metal"
    "scanlines.metal"
)

AIR_FILES=()

for shader in "${METAL_SHADERS[@]}"; do
    air_file="${SHADER_DIR}/${shader%.metal}.air"
    echo "  $shader -> $(basename "$air_file")"
    xcrun -sdk macosx metal -c "$SHADER_DIR/$shader" -o "$air_file"
    AIR_FILES+=("$air_file")
done

echo "Linking to metallib..."
xcrun -sdk macosx metallib "${AIR_FILES[@]}" -o "$OUTPUT_DIR/shaders.metallib"

# Clean up .air files
for air_file in "${AIR_FILES[@]}"; do
    rm "$air_file"
done

echo "Done. Generated $OUTPUT_DIR/shaders.metallib"
