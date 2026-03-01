# E2E Testing Instructions

> For the full testing overview (all suites), see [TESTING.md](TESTING.md).

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

## Testing Strategy: Desktop-Primary, Android-Smoke

The player app uses Compose Multiplatform — all UI composables, ViewModels,
and navigation are 100% shared code. **Do not write the same UI assertion
on both platforms.** Each suite has a distinct purpose:

- **Desktop tests** = Primary UI test suite. Every feature gets thorough
  desktop tests. Fast, no device needed, uses fake repos.
- **Android tests** = Integration smoke tests. Focused on real API
  round-trips, auth flows, platform-specific behavior. Smaller set.

See CLAUDE.md "Player App Testing Strategy" for the full decision matrix.

## Test Suite Structure

### Desktop E2E Tests — Primary UI Suite (`player/desktop/src/desktopTest/.../e2e/`)
- Compose UI Test with `SpelaTestHarness` (fake backend injection)
- `SpelaTestHarness.kt` provides fake backend injection for isolated testing
- `TestFakes.kt` provides test doubles for repositories
- **All feature-level UI tests go here**: rendering, interactions, state, navigation, empty/error states
- Run with: `player/run-desktop-tests.sh`

### Android E2E Tests — Integration Smoke Suite (`player/android/src/androidTest/`)
- Espresso + Compose UI Test + JUnit4 on real device/emulator
- `EstablishSessionTest` handles login session setup
- `TestHelpers.kt` provides shared test utilities
- **Focused on**: real API integration, auth flow, platform-specific behavior, critical flow smoke tests
- **Not for**: duplicating desktop UI assertions on shared composables
- Run with: `player/run-e2e.sh` (requires backend via `docker-compose.e2e.yml`)

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
