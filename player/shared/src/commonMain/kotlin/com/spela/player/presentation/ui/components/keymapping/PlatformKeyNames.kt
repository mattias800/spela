package com.spela.player.presentation.ui.components.keymapping

/**
 * Converts a platform key code (as stored in the key mapping) to a human-readable key name.
 * Platform-specific: Desktop uses Compose Key codes, Android uses KeyEvent codes.
 */
expect fun platformKeyName(keyCode: Int): String
