#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_DIR="$PROJECT_ROOT/desktop/build/flatpak"
MANIFEST="$SCRIPT_DIR/com.spela.player.yml"
REPO_DIR="$BUILD_DIR/repo"
BUNDLE_PATH="$BUILD_DIR/spela.flatpak"

echo "Building Flatpak..."

# Check prerequisites
if ! command -v flatpak-builder &> /dev/null; then
    echo "Error: flatpak-builder is not installed."
    echo "Install with: sudo apt install flatpak-builder"
    exit 1
fi

# Ensure runtime is installed
flatpak install -y --noninteractive flathub org.freedesktop.Platform//24.08 org.freedesktop.Sdk//24.08 || true

# Build
flatpak-builder --force-clean "$BUILD_DIR/build" "$MANIFEST" --repo="$REPO_DIR"

# Create bundle
flatpak build-bundle "$REPO_DIR" "$BUNDLE_PATH" com.spela.player

echo "Flatpak bundle created: $BUNDLE_PATH"
echo "Install with: flatpak install $BUNDLE_PATH"
