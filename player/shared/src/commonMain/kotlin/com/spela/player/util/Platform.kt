package com.spela.player.util

/**
 * Returns the platform identifier used by the Spela server for core downloads.
 * Values: "android", "macos", "windows", "linux"
 */
expect fun currentPlatform(): String

/**
 * Returns the CPU architecture identifier for libretro buildbot URLs.
 * Values: "arm64-v8a" (Android), "arm64" / "x86_64" (macOS), "x86_64" (Linux/Windows)
 */
expect fun currentArch(): String

/**
 * Returns true when running on an emulator/simulator rather than physical hardware.
 * Used to apply compatibility workarounds (e.g., software renderer fallback).
 */
expect fun isEmulator(): Boolean
