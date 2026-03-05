package com.spela.player.util

private const val BUILDBOT_BASE = "https://buildbot.libretro.com/nightly"

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
        "android" -> "$BUILDBOT_BASE/android/latest/$arch/${coreName}_libretro_android.so.zip"
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
 */
fun coreFileName(coreName: String, platform: String = currentPlatform()): String {
    return when (platform) {
        "android" -> "${coreName}_libretro_android.so"
        "macos" -> "${coreName}_libretro.dylib"
        "windows" -> "${coreName}_libretro.dll"
        else -> "${coreName}_libretro.so"
    }
}
