package com.spela.player.util

/**
 * Formats a play time duration in seconds into a human-readable string (e.g., "2h 15m", "< 1 min").
 */
fun formatPlayTime(seconds: Long): String {
    if (seconds < 60) return "< 1 min"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Formats a byte count into a human-readable string (e.g., "1.5 KB", "3.2 MB").
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}
