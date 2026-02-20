package com.spela.player.domain.model

/** Desktop always defaults to the keyboard arrows preset. */
actual fun detectDevicePreset(): String? = "keyboard-arrows"
