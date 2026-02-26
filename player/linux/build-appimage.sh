#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ARCH="${1:---arch}"

# Parse args
TARGET_ARCH="x86_64"
while [[ $# -gt 0 ]]; do
    case "$1" in
        --arch) TARGET_ARCH="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

echo "Building AppImage for $TARGET_ARCH..."

# Paths
DIST_DIR="$PROJECT_ROOT/desktop/build/compose/binaries/main/app/Spela"
APPDIR="$PROJECT_ROOT/desktop/build/appimage/Spela.AppDir"
OUTPUT="$PROJECT_ROOT/desktop/build/appimage/Spela-${TARGET_ARCH}.AppImage"

if [ ! -d "$DIST_DIR" ]; then
    echo "Error: Distribution not found at $DIST_DIR"
    echo "Run './gradlew createDistributable' first."
    exit 1
fi

# Clean and create AppDir
rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/bin"
mkdir -p "$APPDIR/usr/lib"
mkdir -p "$APPDIR/usr/share/applications"
mkdir -p "$APPDIR/usr/share/metainfo"
mkdir -p "$APPDIR/usr/share/icons/hicolor/256x256/apps"

# Copy application
cp -r "$DIST_DIR"/* "$APPDIR/usr/"

# Copy native library
NATIVE_LIB="$PROJECT_ROOT/native/build/lib/main/release/shared/libspela-libretro.so"
if [ -f "$NATIVE_LIB" ]; then
    cp "$NATIVE_LIB" "$APPDIR/usr/lib/"
fi

# Desktop integration files
cp "$SCRIPT_DIR/com.spela.player.desktop" "$APPDIR/usr/share/applications/"
cp "$SCRIPT_DIR/com.spela.player.desktop" "$APPDIR/com.spela.player.desktop"
cp "$SCRIPT_DIR/com.spela.player.metainfo.xml" "$APPDIR/usr/share/metainfo/"

# Icon (use existing icon or create placeholder)
ICON_SRC="$PROJECT_ROOT/../web/public/spela-icon.svg"
if [ -f "$ICON_SRC" ]; then
    cp "$ICON_SRC" "$APPDIR/usr/share/icons/hicolor/256x256/apps/com.spela.player.svg"
    cp "$ICON_SRC" "$APPDIR/com.spela.player.svg"
fi

# Create AppRun
cat > "$APPDIR/AppRun" << 'APPRUN'
#!/usr/bin/env bash
SELF_DIR="$(dirname "$(readlink -f "$0")")"
export PATH="$SELF_DIR/usr/bin:$PATH"
export LD_LIBRARY_PATH="$SELF_DIR/usr/lib:${LD_LIBRARY_PATH:-}"
exec "$SELF_DIR/usr/bin/Spela" "$@"
APPRUN
chmod +x "$APPDIR/AppRun"

# Download appimagetool if needed
APPIMAGETOOL="$PROJECT_ROOT/desktop/build/appimage/appimagetool"
if [ ! -x "$APPIMAGETOOL" ]; then
    echo "Downloading appimagetool..."
    TOOL_ARCH="$TARGET_ARCH"
    if [ "$TOOL_ARCH" = "aarch64" ]; then
        TOOL_ARCH="aarch64"
    fi
    curl -fsSL -o "$APPIMAGETOOL" \
        "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-${TOOL_ARCH}.AppImage"
    chmod +x "$APPIMAGETOOL"
fi

# Build AppImage
ARCH="$TARGET_ARCH" "$APPIMAGETOOL" "$APPDIR" "$OUTPUT"

echo "AppImage created: $OUTPUT"
