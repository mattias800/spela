package com.spela.player.domain.model

/**
 * Where the Wiimote IR pointer gets its input (#1560, phase 3 of epic
 * #1558). Orthogonal to [WiiControlScheme] — any scheme can use either
 * source. Maps to the dolphin-libretro `dolphin_ir_mode` core option (see
 * [wiiIrPointerCoreVariables]). Persisted device-locally per game, like the
 * scheme; [RIGHT_STICK] is the default and stores no row.
 */
enum class WiiIrSource(
    val storageId: String,
    val displayName: String,
    val description: String,
) {
    RIGHT_STICK(
        storageId = "right_stick",
        displayName = "Right Stick",
        description = "Default. Aim the pointer with the right stick.",
    ),
    TOUCH_POINTER(
        storageId = "touch_pointer",
        displayName = "Touch Pointer",
        description = "Aim by touching the screen — the second display on the AYN Thor, or an overlay on the game screen.",
    );

    companion object {
        fun fromStorageId(id: String?): WiiIrSource? =
            entries.find { it.storageId == id }
    }
}
