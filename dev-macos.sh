#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Spela macOS Desktop Player ==="
echo ""

# Check for JAVA_HOME
if [ -z "${JAVA_HOME:-}" ]; then
    # Try to auto-detect on macOS
    if /usr/libexec/java_home &> /dev/null; then
        export JAVA_HOME=$(/usr/libexec/java_home)
        echo "Auto-detected JAVA_HOME: $JAVA_HOME"
    else
        echo "Error: JAVA_HOME is not set and could not be auto-detected."
        echo "  Install a JDK (e.g. brew install openjdk@21) and set JAVA_HOME."
        exit 1
    fi
fi

# Check for CMake
if ! command -v cmake &> /dev/null; then
    # Check Android SDK location
    cmake_android="${HOME}/Library/Android/sdk/cmake/3.22.1/bin/cmake"
    if [ ! -x "$cmake_android" ]; then
        echo "Error: CMake not found."
        echo "  Install via: brew install cmake"
        echo "  Or install CMake 3.22.1 via Android Studio SDK Manager."
        exit 1
    fi
fi

# Check for Xcode command-line tools (needed for macOS frameworks)
if ! xcode-select -p &> /dev/null; then
    echo "Error: Xcode command-line tools not found."
    echo "  Install via: xcode-select --install"
    exit 1
fi

echo "All dependencies OK."
echo ""

cd "$ROOT_DIR/player"
echo "Building and starting macOS desktop player..."
./gradlew :desktop:run
