# Testing Guide

> New to Spela development? See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for setup instructions.

This document describes how to run all tests across the Spela project. A feature is **not done** until all tests pass with zero regressions.

## Quick Reference

| Suite | Command | Duration |
|-------|---------|----------|
| Go backend unit tests | `cd server && go test ./...` | ~10s |
| Web unit tests (Vitest) | `cd web && npx vitest run` | ~4s |
| Web E2E tests (Playwright) | See [Web E2E](#web-e2e-tests-playwright) | ~3-5min |
| Player shared + desktop unit tests | `cd player && ./run-desktop-tests.sh` | ~2min |
| Player Android unit tests | `cd player && ./run-android-tests.sh` | ~30s |
| Player Android E2E tests | `cd player && ./run-e2e.sh` | ~5-10min |

## Backend Tests (Go)

```bash
cd server && go test ./...
```

- Uses in-memory SQLite + `httptest` + `testify`
- Table-driven tests in `*_test.go` files
- No external dependencies needed

## Web Unit Tests (Vitest)

```bash
cd web && npx vitest run
```

- React Testing Library + Vitest
- Tests hooks, components, and utility functions
- No server or browser needed

## Web E2E Tests (Playwright)

**Important:** Always start the Docker E2E environment first.

```bash
# 1. Start the E2E Docker environment (server + web dev server)
docker compose -f docker-compose.e2e.yml up -d --build --wait

# 2. Run all Playwright tests
cd web && npx playwright test

# 3. Run a specific test file
cd web && npx playwright test social-features.spec.ts

# 4. Run with UI mode (interactive debugging)
cd web && npx playwright test --ui

# 5. When done, stop the environment
docker compose -f docker-compose.e2e.yml down
```

### E2E Docker Environment

The `docker-compose.e2e.yml` starts:
- **server** — Go backend on port 8080 with seeded test data (`SPELA_SEED=true`)
- **web** — Vite dev server on port 5173 proxying to the backend

Test ROMs must be in `testdata/roms/` for games to appear.

### Playwright Configuration

- Config: `web/playwright.config.ts`
- Test dir: `web/e2e/`
- Base URL: `http://localhost:5173`
- `global-setup.spec.ts` runs first to authenticate and save session state
- All other tests use the saved auth state (no login per test)
- Tests use `page.route()` to mock API responses for isolated component testing

### Test Files

| File | Coverage |
|------|----------|
| `global-setup.spec.ts` | Authentication setup |
| `emulator.spec.ts` | Browser emulator (mocked) |
| `emulator-real.spec.ts` | Browser emulator (real server) |
| `emulator-saves.spec.ts` | Save state management |
| `key-mapping.spec.ts` | Keyboard mapping |
| `preferences.spec.ts` | User preferences + shader preview |
| `retroachievements.spec.ts` | RetroAchievements integration |
| `social-features.spec.ts` | Activity feed, ratings, shared saves, online status |

## Player Unit Tests

### Cross-platform + Desktop (runs on JVM)

```bash
cd player && ./run-desktop-tests.sh

# Run with filter
cd player && ./run-desktop-tests.sh "SocialViewModel"

# Run with rerun of failed tests
cd player && ./run-desktop-tests.sh --rerun
```

Runs two Gradle tasks:
- `:shared:desktopTest` — Cross-platform unit tests (`commonTest` + `desktopTest`)
- `:desktop:desktopTest` — Desktop E2E tests (Compose UI Test with `SpelaTestHarness`)

### Android local unit tests

```bash
cd player && ./run-android-tests.sh

# Run with filter
cd player && ./run-android-tests.sh "com.spela.player.android.GamepadMappingTest"
```

Runs `:android:testDebugUnitTest` — local JVM tests, no device needed.

## Player Android E2E Tests (Espresso + Compose UI Test)

**Requires:** A connected Android device or emulator.

### Setup

1. Create `player/.env` with device credentials:
   ```
   ADB_SERIAL=emulator-5554
   DEVICE_PIN=1234
   ```

2. Find your device serial: `adb devices`

3. Start the backend server with seeded data:
   ```bash
   cd server
   go run cmd/seed/main.go
   SPELA_GAME_DIRS=./games go run cmd/server/main.go
   ```

### Running

```bash
cd player

# Run all Android E2E tests
./run-e2e.sh

# Run a specific test class
./run-e2e.sh com.spela.player.android.NavigationTest

# Run a specific test method
./run-e2e.sh com.spela.player.android.EmulationTest#playCastlevania
```

The script:
1. Checks if the device is locked and unlocks it
2. Builds and installs the debug APK
3. Runs `gradle :android:connectedDebugAndroidTest`

### Test Files

Located in `player/android/src/androidTest/java/com/spela/player/android/`:

| File | Coverage |
|------|----------|
| `EstablishSessionTest.kt` | Login session establishment |
| `NavigationTest.kt` | App navigation flows |
| `EmulationTest.kt` | Gameplay and emulation |
| `GamepadNavigationTest.kt` | Gamepad input |
| `SessionTest.kt` | Session management |
| `SettingsTest.kt` | Settings screen |
| `TouchControlsTest.kt` | Touch controls |

## Player Desktop E2E Tests (Compose UI Test)

Desktop E2E tests run as part of `./run-desktop-tests.sh` (the `:desktop:desktopTest` task).

Located in `player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/`:

- Uses `runComposeUiTest { }` for full Compose UI testing
- `SpelaTestHarness` provides fake backend injection (no real server needed)
- `TestFakes.kt` provides fake repository implementations

## Running Everything

To verify the full project has zero regressions:

```bash
# 1. Backend
cd server && go test ./...

# 2. Web unit tests
cd web && npx vitest run

# 3. Web E2E tests
docker compose -f docker-compose.e2e.yml up -d --build --wait
cd web && npx playwright test
docker compose -f docker-compose.e2e.yml down

# 4. Player unit + desktop E2E
cd player && ./run-desktop-tests.sh

# 5. Player Android E2E (requires device + running server)
cd player && ./run-e2e.sh
```
