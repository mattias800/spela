#!/bin/bash
# Compile GLSL shaders to SPIR-V and generate C header with embedded bytecode.
# Requires glslangValidator (from Vulkan SDK or package manager).

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SHADER_DIR="$SCRIPT_DIR"
OUTPUT_DIR="$SCRIPT_DIR/../src"
HEADER_FILE="$OUTPUT_DIR/gpu_shaders_spirv.h"

# Check for glslangValidator
if ! command -v glslangValidator &> /dev/null; then
    echo "Error: glslangValidator not found. Install the Vulkan SDK or run:"
    echo "  brew install glslang  (macOS)"
    echo "  apt install glslang-tools  (Linux)"
    exit 1
fi

echo "Compiling shaders to SPIR-V..."

SHADERS=(
    "fullscreen_quad.vert"
    "passthrough.frag"
    "bilinear.frag"
    "sharp_bilinear.frag"
    "crt_simple.frag"
    "lcd_grid.frag"
    "scanlines.frag"
)

# Compile each shader to SPIR-V
for shader in "${SHADERS[@]}"; do
    echo "  $shader"
    glslangValidator -V "$SHADER_DIR/$shader" -o "$SHADER_DIR/${shader}.spv"
done

# Generate C header
echo "Generating $HEADER_FILE..."

cat > "$HEADER_FILE" << 'HEADER_TOP'
/*
 * Auto-generated SPIR-V shader bytecode.
 * Do not edit manually -- regenerate with shaders/compile_shaders.sh
 */

#ifndef GPU_SHADERS_SPIRV_H
#define GPU_SHADERS_SPIRV_H

#include <stdint.h>
#include <stddef.h>

HEADER_TOP

for shader in "${SHADERS[@]}"; do
    spv_file="$SHADER_DIR/${shader}.spv"
    # Convert filename to C identifier: replace . with _
    var_name="spv_$(echo "$shader" | sed 's/\./_/g')"

    echo "static const uint32_t ${var_name}[] = {" >> "$HEADER_FILE"
    xxd -i < "$spv_file" | sed 's/unsigned char.*\[\] = {//;s/};//;s/0x\([0-9a-f]\{2\}\), 0x\([0-9a-f]\{2\}\), 0x\([0-9a-f]\{2\}\), 0x\([0-9a-f]\{2\}\)/0x\4\3\2\1/g' >> "$HEADER_FILE" 2>/dev/null || {
        # Fallback: use od to convert binary to uint32_t array
        od -A n -t x4 -v < "$spv_file" | sed 's/^ *//;s/ *$//;s/ /, 0x/g;s/^/    0x/;s/$/,/' >> "$HEADER_FILE"
    }
    echo "};" >> "$HEADER_FILE"

    file_size=$(wc -c < "$spv_file" | tr -d ' ')
    echo "static const size_t ${var_name}_size = ${file_size};" >> "$HEADER_FILE"
    echo "" >> "$HEADER_FILE"

    # Clean up .spv file
    rm "$spv_file"
done

echo "#endif /* GPU_SHADERS_SPIRV_H */" >> "$HEADER_FILE"

echo "Done. Generated $HEADER_FILE"
