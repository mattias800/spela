#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Spela Android Player (Physical Device) ==="
echo ""

# Check for JAVA_HOME
if [ -z "${JAVA_HOME:-}" ]; then
    if /usr/libexec/java_home &> /dev/null 2>&1; then
        export JAVA_HOME=$(/usr/libexec/java_home)
        echo "Auto-detected JAVA_HOME: $JAVA_HOME"
    else
        echo "Error: JAVA_HOME is not set and could not be auto-detected."
        echo "  Install a JDK (e.g. brew install openjdk@21) and set JAVA_HOME."
        exit 1
    fi
fi

# Check for adb
if ! command -v adb &> /dev/null; then
    echo "Error: adb not found in PATH."
    echo "  Install Android SDK Platform Tools."
    echo "  Download from: https://developer.android.com/studio/releases/platform-tools"
    exit 1
fi

# Check for Android SDK
ANDROID_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${HOME}/Library/Android/sdk}}"
if [ ! -d "$ANDROID_SDK" ]; then
    echo "Error: Android SDK not found at $ANDROID_SDK"
    echo "  Set ANDROID_SDK_ROOT or ANDROID_HOME, or install Android Studio."
    exit 1
fi

# Check for Android NDK
ndk_dir=$(ls -d "$ANDROID_SDK/ndk/"* 2>/dev/null | sort -V | tail -1)
if [ -z "${ndk_dir:-}" ]; then
    echo "Error: Android NDK not found in $ANDROID_SDK/ndk/"
    echo "  Install the NDK via Android Studio SDK Manager or:"
    echo "  sdkmanager --install 'ndk;27.0.12077973'"
    exit 1
fi

# Check for CMake (system or Android SDK)
if ! command -v cmake &> /dev/null; then
    cmake_android=$(ls -d "$ANDROID_SDK/cmake/"*/bin/cmake 2>/dev/null | sort -V | tail -1)
    if [ -z "${cmake_android:-}" ]; then
        echo "Error: CMake not found."
        echo "  Install via Android Studio SDK Manager or: brew install cmake"
        exit 1
    fi
fi

echo "All dependencies OK."
echo ""

# Check for connected devices
echo "Checking for connected Android devices..."
device_count=$(adb devices | grep -v "List of devices" | grep -v "^$" | grep "device$" | wc -l | tr -d ' ')

if [ "$device_count" -eq 0 ]; then
    echo "Error: No Android devices found."
    echo ""
    echo "  1. Connect your Android device via USB"
    echo "  2. Enable USB debugging in Developer Options"
    echo "  3. Accept the USB debugging prompt on your device"
    echo ""
    echo "Current devices:"
    adb devices
    exit 1
fi

if [ "$device_count" -gt 1 ]; then
    echo "Multiple devices connected:"
    adb devices | grep "device$"
    echo ""
    echo "Using the first device. To target a specific device, use:"
    echo "  ANDROID_SERIAL=<device_id> ./dev-android.sh"
fi

device_name=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
android_version=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')

echo "Found device: $device_name (Android $android_version)"

cd "$ROOT_DIR/player"

echo ""
echo "Building APK..."
./gradlew :android:assembleDebug

apk_path="android/build/outputs/apk/debug/android-debug.apk"

if [ ! -f "$apk_path" ]; then
    echo "Error: APK not found at $apk_path"
    exit 1
fi

echo ""
echo "Installing APK on device..."
adb install -r "$apk_path"

echo ""
echo "Launching Spela..."
adb shell am start -n com.spela.player/.MainActivity

echo ""
echo "Done! Spela is now running on your device."
echo ""
echo "Useful commands:"
echo "  View logs:        adb logcat | grep -i spela"
echo "  View logs (tag):  adb logcat -s SpelaPlayer"
echo "  Clear logs:       adb logcat -c"
echo "  Stop app:         adb shell am force-stop com.spela.player"
echo "  Uninstall:        adb uninstall com.spela.player"
echo "  Take screenshot:  adb exec-out screencap -p > screenshot.png"
echo "  Device info:      adb shell getprop | grep -i model"
