package com.spela.player.domain.model

import android.os.Build

/** Android: detect device type and return appropriate preset ID. */
actual fun detectDevicePreset(): String? {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("ayn") -> "xbox-standard" // Ayn Odin/Thor use standard XInput
        manufacturer.contains("nintendo") -> "nintendo-standard"
        else -> "xbox-standard" // Most Android gamepads follow XInput layout
    }
}
