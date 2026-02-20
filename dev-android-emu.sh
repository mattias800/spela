#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Spela Android Player (Emulator) ==="
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
    exit 1
fi

# Check for emulator
if ! command -v emulator &> /dev/null; then
    echo "Error: emulator not found in PATH."
    echo "  Install Android SDK Emulator."
    echo "  Hint: Add \$ANDROID_HOME/emulator to your PATH."
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

# Function to get online emulator device ID
get_emulator_device() {
    adb devices | grep "emulator-" | grep "device" | awk '{print $1}' | head -n 1
}

# Function to check if emulator is running
is_emulator_running() {
    [ -n "$(get_emulator_device)" ]
}

# Function to start emulator
start_emulator() {
    echo "Starting Android emulator..."

    avd_list=$(emulator -list-avds)

    if [ -z "$avd_list" ]; then
        echo "Error: No Android Virtual Devices (AVDs) found."
        echo "  Create an AVD using Android Studio or avdmanager."
        exit 1
    fi

    avd_name=$(echo "$avd_list" | head -n 1)
    echo "  Using AVD: $avd_name"

    emulator -avd "$avd_name" -audio auto -keyboard &
    emulator_pid=$!

    echo "  Emulator starting (PID: $emulator_pid)..."
    echo "  Waiting for device to boot..."

    adb wait-for-device

    while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        echo "  Still booting..."
        sleep 2
    done

    echo "  Emulator ready!"
}

# Check if emulator is already running
if is_emulator_running; then
    echo "Emulator already running."
else
    start_emulator
fi

cd "$ROOT_DIR/player"

echo ""
echo "Building APK..."
./gradlew :android:assembleDebug

echo ""
echo "Installing APK on emulator..."
emulator_device=$(get_emulator_device)
if [ -z "$emulator_device" ]; then
    echo "Error: No online emulator found."
    exit 1
fi
adb -s "$emulator_device" install -r android/build/outputs/apk/debug/android-debug.apk

echo ""
echo "Launching Spela on emulator..."
adb -s "$emulator_device" shell am start -n com.spela.player/com.spela.player.android.MainActivity

echo ""
echo "Done! Spela is now running on the emulator."
echo ""
echo "Useful commands:"
echo "  View logs:       adb logcat | grep Spela"
echo "  Stop emulator:   adb emu kill"
echo "  Uninstall:       adb uninstall com.spela.player"
