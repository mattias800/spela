package com.spela.player.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildbotUrlTest {

    @Test
    fun macosArm64Url() {
        val url = buildbotCoreUrl("nestopia", "macos", "arm64")
        assertEquals(
            "https://buildbot.libretro.com/nightly/apple/osx/arm64/latest/nestopia_libretro.dylib.zip",
            url,
        )
    }

    @Test
    fun macosX86Url() {
        val url = buildbotCoreUrl("snes9x", "macos", "x86_64")
        assertEquals(
            "https://buildbot.libretro.com/nightly/apple/osx/x86_64/latest/snes9x_libretro.dylib.zip",
            url,
        )
    }

    @Test
    fun androidArm64Url() {
        val url = buildbotCoreUrl("nestopia", "android", "arm64-v8a")
        assertEquals(
            "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/nestopia_libretro_android.so.zip",
            url,
        )
    }

    @Test
    fun linuxX86Url() {
        val url = buildbotCoreUrl("mupen64plus_next", "linux", "x86_64")
        assertEquals(
            "https://buildbot.libretro.com/nightly/linux/x86_64/latest/mupen64plus_next_libretro.so.zip",
            url,
        )
    }

    @Test
    fun windowsX86Url() {
        val url = buildbotCoreUrl("mgba", "windows", "x86_64")
        assertEquals(
            "https://buildbot.libretro.com/nightly/windows/x86_64/latest/mgba_libretro.dll.zip",
            url,
        )
    }

    @Test
    fun macosFileName() {
        assertEquals("nestopia_libretro.dylib", coreFileName("nestopia", "macos"))
    }

    @Test
    fun androidFileName() {
        assertEquals("nestopia_libretro_android.so", coreFileName("nestopia", "android"))
    }

    @Test
    fun linuxFileName() {
        assertEquals("nestopia_libretro.so", coreFileName("nestopia", "linux"))
    }

    @Test
    fun windowsFileName() {
        assertEquals("nestopia_libretro.dll", coreFileName("nestopia", "windows"))
    }

    @Test
    fun linuxArm64Url() {
        val url = buildbotCoreUrl("nestopia", "linux", "arm64")
        assertEquals(
            "https://buildbot.libretro.com/nightly/linux/aarch64/latest/nestopia_libretro.so.zip",
            url,
        )
    }

    @Test
    fun linuxArm64FileName() {
        assertEquals("nestopia_libretro.so", coreFileName("nestopia", "linux"))
    }

    // resolveDownloadUrl — Azahar-style GitHub release templates

    private val azaharTemplate =
        "https://github.com/azahar-emu/azahar/releases/download/2125.0-alpha4/azahar-libretro-2125.0-alpha4-{platform}.zip"

    @Test
    fun resolveDownloadUrlAndroid() {
        assertEquals(
            "https://github.com/azahar-emu/azahar/releases/download/2125.0-alpha4/azahar-libretro-2125.0-alpha4-android-arm64.zip",
            resolveDownloadUrl(azaharTemplate, "android", "arm64"),
        )
    }

    @Test
    fun resolveDownloadUrlLinux() {
        assertEquals(
            "https://github.com/azahar-emu/azahar/releases/download/2125.0-alpha4/azahar-libretro-2125.0-alpha4-linux-x86_64.zip",
            resolveDownloadUrl(azaharTemplate, "linux", "x86_64"),
        )
    }

    @Test
    fun resolveDownloadUrlMacosArm64() {
        assertEquals(
            "https://github.com/azahar-emu/azahar/releases/download/2125.0-alpha4/azahar-libretro-2125.0-alpha4-macos-arm64.zip",
            resolveDownloadUrl(azaharTemplate, "macos", "arm64"),
        )
    }

    @Test
    fun resolveDownloadUrlWindows() {
        assertEquals(
            "https://github.com/azahar-emu/azahar/releases/download/2125.0-alpha4/azahar-libretro-2125.0-alpha4-windows-x86_64.zip",
            resolveDownloadUrl(azaharTemplate, "windows", "x86_64"),
        )
    }
}
