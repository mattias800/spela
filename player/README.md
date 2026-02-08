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

### Running Tests

```sh
./gradlew :shared:allTests
```

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
