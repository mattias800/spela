package com.spela.player.util

/**
 * Shows a native folder picker and returns the chosen directory's absolute
 * path, or null if the user cancelled or the platform has no picker.
 *
 * Blocking — call from a background dispatcher. Unsupported on Android
 * (returns null); used on desktop to choose where to download a game Spela
 * can't emulate (#1257).
 */
expect fun pickDirectory(title: String): String?
