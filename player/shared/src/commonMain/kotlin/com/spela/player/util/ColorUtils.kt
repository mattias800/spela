package com.spela.player.util

import androidx.compose.ui.graphics.Color

/**
 * Parses a hex color string (e.g. "#e53e3e" or "#FFe53e3e") into a Compose Color.
 * Returns [fallback] if the string is not a valid hex color.
 */
fun parseHexColor(hex: String, fallback: Color = Color.Unspecified): Color {
    val cleaned = hex.removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> Color(
                red = cleaned.substring(0, 2).toInt(16) / 255f,
                green = cleaned.substring(2, 4).toInt(16) / 255f,
                blue = cleaned.substring(4, 6).toInt(16) / 255f,
            )
            8 -> Color(
                alpha = cleaned.substring(0, 2).toInt(16) / 255f,
                red = cleaned.substring(2, 4).toInt(16) / 255f,
                green = cleaned.substring(4, 6).toInt(16) / 255f,
                blue = cleaned.substring(6, 8).toInt(16) / 255f,
            )
            else -> fallback
        }
    } catch (_: NumberFormatException) {
        fallback
    }
}
