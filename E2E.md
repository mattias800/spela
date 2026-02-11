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

## Notes

- The Odin 2 has a horizontal (landscape) screen — the keyboard covers the entire screen. All test flows must include `hideKeyboard` after every `inputText` step.
- Gamepad button tests (BUTTON_A, BUTTON_B, L1/R1) require manual QA with a physical controller. See `.maestro/MANUAL-QA-gamepad.md`.
- D-pad navigation tests use Maestro's `Remote Dpad` keys which send standard Android DPAD keycodes.
