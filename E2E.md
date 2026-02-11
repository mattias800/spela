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

- `00-establish-session.yaml` runs first (alphabetical order) and establishes a persisted login session
- 27 tests use `setup/start-logged-in.yaml` — lightweight restart, no login needed
- 4 tests use `setup/login-player.yaml` — full login for session/sync verification
- 2 tests have inline setup for specific startup scenarios

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
- Gamepad button tests (BUTTON_A, BUTTON_B, L1/R1) require manual QA with a physical controller. See `.maestro/MANUAL-QA-gamepad.md`.
- D-pad navigation tests use Maestro's `Remote Dpad` keys which send standard Android DPAD keycodes.
