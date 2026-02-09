package com.spela.player.util

actual fun currentPlatform(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("mac") -> "macos"
        osName.contains("win") -> "windows"
        else -> "linux"
    }
}

actual fun currentArch(): String {
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
        arch.contains("amd64") || arch.contains("x86_64") -> "x86_64"
        else -> "x86_64"
    }
}
