package com.spela.player.util

import java.io.File

actual fun revealInFileManager(path: String): Boolean {
    val file = File(path)
    // getLocalGamePath returns the game file; open its containing folder.
    val folder = (if (file.isDirectory) file else file.parentFile) ?: return false
    if (!folder.exists()) return false

    val os = System.getProperty("os.name").lowercase()
    val cmd = when {
        os.contains("win") -> listOf("explorer.exe", folder.absolutePath)
        os.contains("mac") -> listOf("open", folder.absolutePath)
        else -> listOf("xdg-open", folder.absolutePath)
    }
    return try {
        ProcessBuilder(cmd).start()
        true
    } catch (e: Exception) {
        println("[FileManager] failed to open $folder: ${e.message}")
        false
    }
}
