# E2E Testing Instructions

## Setup

Create `player/.env` (gitignored) with your device credentials:

```
ADB_SERIAL=<your-device-serial>
DEVICE_PIN=<your-device-pin>
```

Find your device serial with `adb devices`.

## Running E2E Tests

The `player/run-e2e.sh` script handles device unlock, APK build, and test execution.

```bash
# Run all tests
cd player
./run-e2e.sh

# Run a single test
./run-e2e.sh emulation-overlay.yaml
```

The script will:
1. Check if the device is locked and unlock it if needed
2. Build and install the debug APK
3. Run the specified test(s)

### Verify device is unlocked:

```bash
adb -s "$ADB_SERIAL" shell dumpsys window | grep mInputRestricted
# Should show: mInputRestricted=false
```

## Test Suite Structure

### Android E2E Tests (`player/android/src/androidTest/`)
- 8 test classes using Espresso + Compose UI Test + JUnit4
- `EstablishSessionTest` handles login session setup
- `TestHelpers.kt` provides shared test utilities

### Desktop E2E Tests (`player/desktop/src/desktopTest/.../e2e/`)
- 15 test files using Compose UI Test with `SpelaTestHarness`
- `SpelaTestHarness.kt` provides fake backend injection for isolated testing
- `TestFakes.kt` provides test doubles for repositories

## Using the Emulator in Landscape Mode

The Odin 2 has a landscape (horizontal) screen. To emulate this on an Android emulator, set the emulator to landscape orientation:

```bash
# Disable auto-rotation and set landscape orientation
adb -s emulator-5554 shell settings put system accelerometer_rotation 0
adb -s emulator-5554 shell settings put system user_rotation 1
```

- `user_rotation 1` = landscape (90° counter-clockwise)
- `user_rotation 0` = portrait (default)

This should be done after the emulator boots, before running tests.

## Notes

- The Odin 2 has a horizontal (landscape) screen — the keyboard covers the entire screen. All test flows must include `hideKeyboard` after every `inputText` step.
- When using the emulator, set it to landscape mode (see above) to match the Odin 2's screen orientation.
- Gamepad button tests (BUTTON_A, BUTTON_B, L1/R1) require manual QA with a physical controller.
- D-pad navigation tests use standard Android DPAD keycodes via UiAutomator.
