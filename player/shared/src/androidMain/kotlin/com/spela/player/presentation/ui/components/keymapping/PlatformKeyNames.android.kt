package com.spela.player.presentation.ui.components.keymapping

import android.view.KeyEvent

/**
 * Android implementation: maps Android KeyEvent key codes to human-readable names.
 */
actual fun platformKeyName(keyCode: Int): String {
    return KeyEvent.keyCodeToString(keyCode)
        .removePrefix("KEYCODE_")
        .replace("_", " ")
        .lowercase()
        .replaceFirstChar { it.uppercase() }
}
