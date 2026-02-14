# Spela Player

Native game player application built with Kotlin Multiplatform and Compose Multiplatform. Connects to a Spela server to browse, download, and play retro games using libretro cores.

## Supported Platforms

- **Android** (arm64-v8a, armeabi-v7a, x86_64) - minSdk 24
- **Desktop** (Windows, macOS, Linux) - JVM-based

## Prerequisites

- **JDK 17+**
- **Gradle 8.x** (wrapper included)
- **Android SDK** (for Android builds)
  - SDK Platform 34
  - NDK (for native libretro bridge compilation)
  - CMake 3.22.1+
- **A running Spela server** to connect to

## Project Structure

```
player/
├── shared/          # KMP shared module (business logic, networking, UI)
│   └── src/
│       ├── commonMain/    # Domain models, repositories, use cases, ViewModels
│       ├── androidMain/   # Android-specific (OkHttp, JNI, file storage)
│       └── desktopMain/   # Desktop-specific (CIO, JNI, file storage)
├── android/         # Android app module
├── desktop/         # Desktop app module
├── native/          # C libretro bridge (JNI)
│   ├── CMakeLists.txt
│   └── src/
│       ├── libretro_bridge.c   # Core loading & JNI entry points
│       ├── libretro_video.c    # Frame buffer management
│       ├── libretro_audio.c    # Audio ring buffer
│       └── libretro_input.c    # Input state tracking
└── gradle/
    └── libs.versions.toml      # Dependency version catalog
```

## Building

All commands should be run from the `player/` directory.

### Android

Build a debug APK:

```sh
./gradlew :android:assembleDebug
```

The APK will be at `android/build/outputs/apk/debug/android-debug.apk`.

Build a release APK (requires signing configuration):

```sh
./gradlew :android:assembleRelease
```

Install directly to a connected device:

```sh
./gradlew :android:installDebug
```

### Desktop

Run the desktop application:

```sh
./gradlew :desktop:run
```

**Note:** The desktop build requires the native libretro bridge library to be compiled and available on the library path. On desktop, CMake builds the `spela-libretro` shared library using the system JNI headers.

Build distributable packages:

```sh
# macOS (.dmg)
./gradlew :desktop:packageDmg

# Windows (.msi)
./gradlew :desktop:packageMsi

# Linux (.deb)
./gradlew :desktop:packageDeb
```

Distributable outputs are in `desktop/build/compose/binaries/`.

### Native Bridge (Desktop)

For desktop, the C libretro bridge must be compiled separately. From the `native/` directory:

```sh
mkdir build && cd build
cmake ..
make
```

This produces `libspela-libretro.so` (Linux), `libspela-libretro.dylib` (macOS), or `spela-libretro.dll` (Windows). Place it where the JVM can find it (e.g. the working directory or `java.library.path`).

On Android, the native library is built automatically via the `externalNativeBuild` CMake integration in the Android Gradle plugin.

### Running Unit Tests

```sh
./gradlew :shared:allTests
```

### Running E2E Tests (Espresso + Compose UI Test)

The player app uses **Espresso** and the **Jetpack Compose Test API** for end-to-end UI testing. E2E tests are the primary regression prevention tool for the player app. **Any change that affects user-facing behavior must have a corresponding E2E test.**

#### Prerequisites

- **adb** on PATH: `brew install android-platform-tools` (macOS)
- **A connected Android device or emulator**
- **The Spela backend server running** with seeded data (users and scanned games)

#### Starting the backend for E2E tests

From the `server/` directory:

```sh
# Seed the database with users (player/player123, admin/admin123)
go run cmd/seed/main.go

# Start the server (scans game directories on startup)
SPELA_GAME_DIRS=./games go run cmd/server/main.go
```

The server must have ROM files in its game directories for games to appear in the app.

#### Running the tests

```sh
# Run all Android E2E tests
cd player
./run-e2e.sh

# Run a specific test class
./run-e2e.sh com.spela.player.android.EmulationTest

# Run a specific test method
./run-e2e.sh com.spela.player.android.EmulationTest#playCastlevania

# Run desktop E2E tests
./run-desktop-tests.sh
```

#### Test structure

```
player/android/src/androidTest/java/com/spela/player/android/
├── EmulationTest.kt           # Emulation and gameplay tests
├── EstablishSessionTest.kt    # Login session establishment
├── GamepadNavigationTest.kt   # Gamepad navigation tests
├── NavigationTest.kt          # App navigation tests
├── SessionTest.kt             # Session management tests
├── SettingsTest.kt            # Settings screen tests
├── TouchControlsTest.kt       # Touch control tests
└── TestHelpers.kt             # Shared test utilities

player/desktop/src/desktopTest/kotlin/.../e2e/
├── AppLaunchAndConnectionTest.kt
├── GameBrowsingAndSelectionTest.kt
├── SaveLoadStateTest.kt
├── SpelaTestHarness.kt        # Desktop test infrastructure
├── TestFakes.kt               # Test doubles
└── ... (15 test files total)
```

- Android E2E tests use `@RunWith(AndroidJUnit4::class)` with `createComposeRule()` and `UiAutomator`
- Desktop E2E tests use `runComposeUiTest { }` with `SpelaTestHarness` for fake backend injection
- Each test is self-contained and idempotent

#### Writing new E2E tests

1. Create a new test class in the appropriate `androidTest` or `desktopTest` directory.
2. Use `createComposeRule()` (Android) or `runComposeUiTest { }` (desktop) for Compose UI testing.
3. Use `onNodeWithContentDescription()` for element selection — the app has accessibility labels on all interactive elements.
4. Use `waitUntil {}` with reasonable timeouts for assertions after navigation or network operations.
5. Use `performScrollToNode()` to find elements in scrollable lists.

#### Key accessibility labels in the app

| Screen | Element | contentDescription |
|--------|---------|-------------------|
| Home | Console card | `"{consoleName}, {gameCount} games"` |
| Home | Game card | `"{gameTitle}, {consoleName}"` |
| Home | Continue playing card | `"Continue playing {gameTitle} on {consoleName}"` |
| Game Detail | Play button | `"Play {gameTitle}"` |
| Game Detail | Download button | `"Download {gameTitle}"` |
| Game Detail | Favorite button | `"Add to favorites"` / `"Remove from favorites"` |
| In-Game Overlay | Overlay backdrop | `"Game overlay, tap to dismiss"` |
| In-Game Overlay | FPS HUD | `"{fps} FPS, tap to open game menu"` |
| In-Game Overlay | Action buttons | `"Save"`, `"Load"`, `"Screenshot"`, `"Fast"` |

#### Debugging failed tests

```sh
# View Android logcat for the app
adb logcat -d --pid=$(adb shell pidof com.spela.player) | tail -50

# Run with verbose output
ANDROID_SERIAL="$ADB_SERIAL" ./gradlew :android:connectedDebugAndroidTest --info
```

#### Server URL configuration

The E2E tests currently hardcode the server URL as `http://192.168.11.143:8080`. If your machine has a different IP, update the `inputText` step in the test flows. The Android device must be able to reach this URL over the network.

## Architecture

The app follows **Clean Architecture** with **MVI** (Model-View-Intent):

- **Domain** - Models, repository interfaces, use cases (pure Kotlin, no framework dependencies)
- **Data** - Ktor API client, DTOs, repository implementations, SQLDelight local database
- **Presentation** - Compose Multiplatform UI, ViewModels with sealed State/Intent classes

Dependency injection uses **Koin** with a common module and platform-specific modules.

## Key Dependencies

| Library | Purpose |
|---------|---------|
| Compose Multiplatform 1.7.1 | Cross-platform UI |
| Ktor 2.3.12 | HTTP client |
| SQLDelight 2.0.2 | Local database |
| Koin 3.5.6 | Dependency injection |
| Coil 3.0.4 | Image loading |
| kotlinx-serialization 1.7.3 | JSON serialization |
