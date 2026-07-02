package com.spela.player.domain.model

/**
 * Core-variable overrides that center the Wiimote IR pointer's rest
 * position (#1524).
 *
 * The dolphin-libretro core maps the right analog stick to the IR pointer
 * (`dolphin_ir_mode` defaults to right-stick absolute) but defaults
 * `dolphin_ir_offset` — Dolphin's "IR Vertical Offset" — to 0, which parks
 * the neutral-stick pointer at the bottom-center of the screen. Standalone
 * Dolphin ships vertical offset 10 with total pitch 20: an offset of half
 * the pitch span is what maps the neutral pose to screen-center. Mirror
 * those values. Total yaw is left at the core default (25), which already
 * matches standalone Dolphin.
 */
fun wiiIrPointerCoreVariables(consoleId: String, corePath: String): List<CoreVariableOverride> {
    val console = consoleId.trim().lowercase().replace("-", "").replace("_", "")
    val core = corePath.substringAfterLast('/').substringBeforeLast('.').lowercase()
    if (console != "wii" || !core.contains("dolphin")) return emptyList()
    return listOf(
        CoreVariableOverride("dolphin_ir_offset", "10"),
        CoreVariableOverride("dolphin_ir_pitch", "20"),
    )
}
