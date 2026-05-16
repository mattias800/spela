package com.spela.player.util

private const val BUILDBOT_BASE = "https://buildbot.libretro.com/nightly"

/**
 * Cores whose Android nightly is packaged without the `_android` suffix.
 *
 * libretro buildbot publishes most Android cores as
 * `<name>_libretro_android.so` inside `<name>_libretro_android.so.zip`, but
 * a handful of newer cores (azahar) ship as `<name>_libretro.so` inside
 * `<name>_libretro.so.zip`. The convention is enforced upstream by the
 * buildbot config, not by anything we control — when in doubt, HEAD the
 * two URLs to confirm.
 */
private val ANDROID_NO_SUFFIX_CORES = setOf("azahar")

/**
 * Builds the libretro buildbot download URL for a given core name.
 *
 * URL patterns per platform:
 *   android: /nightly/android/latest/{arch}/{name}_libretro_android.so.zip
 *   macos:   /nightly/apple/osx/{arch}/latest/{name}_libretro.dylib.zip
 *   linux:   /nightly/linux/{arch}/latest/{name}_libretro.so.zip
 *   windows: /nightly/windows/{arch}/latest/{name}_libretro.dll.zip
 */
fun buildbotCoreUrl(coreName: String, platform: String = currentPlatform(), arch: String = currentArch()): String {
    return when (platform) {
        "android" -> {
            val asset = if (coreName in ANDROID_NO_SUFFIX_CORES) {
                "${coreName}_libretro.so.zip"
            } else {
                "${coreName}_libretro_android.so.zip"
            }
            "$BUILDBOT_BASE/android/latest/$arch/$asset"
        }
        "macos" -> "$BUILDBOT_BASE/apple/osx/$arch/latest/${coreName}_libretro.dylib.zip"
        "windows" -> "$BUILDBOT_BASE/windows/$arch/latest/${coreName}_libretro.dll.zip"
        else -> {
            val buildbotArch = if (arch == "arm64") "aarch64" else arch
            "$BUILDBOT_BASE/linux/$buildbotArch/latest/${coreName}_libretro.so.zip"
        }
    }
}

/**
 * Resolves a URL template for a non-buildbot core download.
 *
 * The template uses `{platform}` as a placeholder, which is replaced with a
 * `{platform}-{arch}` string matching the release asset naming convention used
 * by projects like Azahar (e.g. `android-arm64`, `linux-x86_64`, `macos-arm64`).
 */
fun resolveDownloadUrl(template: String, platform: String = currentPlatform(), arch: String = currentArch()): String {
    val platformSuffix = "$platform-$arch"
    return template.replace("{platform}", platformSuffix)
}

/**
 * Returns the expected filename of the extracted core binary.
 *
 * Mirrors the buildbot asset naming — cores in [ANDROID_NO_SUFFIX_CORES]
 * ship as `<name>_libretro.so` on Android too, so the cached on-disk
 * filename has to match what's inside the zip.
 */
fun coreFileName(coreName: String, platform: String = currentPlatform()): String {
    return when (platform) {
        "android" -> if (coreName in ANDROID_NO_SUFFIX_CORES) {
            "${coreName}_libretro.so"
        } else {
            "${coreName}_libretro_android.so"
        }
        "macos" -> "${coreName}_libretro.dylib"
        "windows" -> "${coreName}_libretro.dll"
        else -> "${coreName}_libretro.so"
    }
}
