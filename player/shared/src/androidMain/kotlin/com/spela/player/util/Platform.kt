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

actual fun isEmulator(): Boolean {
    return android.os.Build.FINGERPRINT.startsWith("generic") ||
        android.os.Build.FINGERPRINT.startsWith("unknown") ||
        android.os.Build.MODEL.contains("google_sdk") ||
        android.os.Build.MODEL.contains("Emulator") ||
        android.os.Build.MODEL.contains("Android SDK built for x86") ||
        android.os.Build.MANUFACTURER.contains("Genymotion") ||
        android.os.Build.BRAND.startsWith("generic") ||
        android.os.Build.DEVICE.startsWith("generic") ||
        android.os.Build.PRODUCT.contains("sdk") ||
        android.os.Build.HARDWARE.contains("goldfish") ||
        android.os.Build.HARDWARE.contains("ranchu")
}
