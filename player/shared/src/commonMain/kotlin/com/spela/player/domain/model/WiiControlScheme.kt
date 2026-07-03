package com.spela.player.domain.model

/**
 * dolphin-libretro controller device types for Wii ports — the
 * `retro_set_controller_port_device` subclass encodings from
 * DolphinLibretro/Input.cpp. See epic #1558.
 */
const val RETRO_DEVICE_WIIMOTE: Int = 1
const val RETRO_DEVICE_WIIMOTE_SIDEWAYS: Int = (2 shl 8) or 1
const val RETRO_DEVICE_WIIMOTE_NC: Int = (3 shl 8) or 1
const val RETRO_DEVICE_CLASSIC: Int = (4 shl 8) or 1
const val RETRO_DEVICE_CLASSIC_PRO: Int = (5 shl 8) or 1
const val RETRO_DEVICE_GC_ON_WII: Int = (6 shl 8) or 1

/**
 * Per-game Wii controller scheme (#1559, phase 2 of epic #1558).
 *
 * Selects which emulated controller the dolphin core attaches to the
 * Wiimote ports. [NUNCHUK] is the default (#1534): it matches what most
 * Wii titles expect and is harmless for Wiimote-only games, like real
 * hardware. Persisted device-locally per game as a [storageId] in the
 * DeviceSettingEntity key-value store (same pattern as the per-game
 * widescreen mode).
 */
enum class WiiControlScheme(
    val storageId: String,
    val displayName: String,
    val description: String,
    val portDevice: Int,
) {
    NUNCHUK(
        storageId = "nunchuk",
        displayName = "Wii Remote + Nunchuk",
        description = "Default. Matches most Wii titles.",
        portDevice = RETRO_DEVICE_WIIMOTE_NC,
    ),
    WIIMOTE(
        storageId = "wiimote",
        displayName = "Wii Remote",
        description = "Bare Wii Remote, no extension.",
        portDevice = RETRO_DEVICE_WIIMOTE,
    ),
    WIIMOTE_SIDEWAYS(
        storageId = "wiimote_sideways",
        displayName = "Wii Remote (Sideways)",
        description = "Held sideways, NES-style (e.g. NSMB Wii).",
        portDevice = RETRO_DEVICE_WIIMOTE_SIDEWAYS,
    ),
    CLASSIC_CONTROLLER(
        storageId = "classic",
        displayName = "Classic Controller",
        description = "For Classic Controller titles and VC games.",
        portDevice = RETRO_DEVICE_CLASSIC,
    ),
    CLASSIC_PRO(
        storageId = "classic_pro",
        displayName = "Classic Controller Pro",
        description = "Classic Controller Pro layout.",
        portDevice = RETRO_DEVICE_CLASSIC_PRO,
    ),
    GC_PAD(
        storageId = "gc_pad",
        displayName = "GameCube Controller",
        description = "For GameCube-compatible titles.",
        portDevice = RETRO_DEVICE_GC_ON_WII,
    );

    companion object {
        fun fromStorageId(id: String?): WiiControlScheme? =
            entries.find { it.storageId == id }
    }
}
