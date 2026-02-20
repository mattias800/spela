package com.spela.player.domain.model

/**
 * Detects the current device and returns the ID of an appropriate preset.
 * Returns null if no suitable preset can be determined.
 */
expect fun detectDevicePreset(): String?
