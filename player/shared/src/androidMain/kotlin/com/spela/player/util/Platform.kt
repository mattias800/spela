package com.spela.player.util

actual fun currentPlatform(): String = "android"

actual fun currentArch(): String {
    val abis = android.os.Build.SUPPORTED_ABIS
    return when {
        abis.contains("arm64-v8a") -> "arm64-v8a"
        abis.contains("armeabi-v7a") -> "armeabi-v7a"
        abis.contains("x86_64") -> "x86_64"
        abis.contains("x86") -> "x86"
        else -> abis.firstOrNull() ?: "arm64-v8a"
    }
}
