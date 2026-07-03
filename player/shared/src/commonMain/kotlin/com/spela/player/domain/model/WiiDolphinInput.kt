package com.spela.player.domain.model

/**
 * Wii input configuration for the dolphin-libretro core.
 *
 * Both helpers gate on the same condition — a Wii session running the
 * dolphin core — and return no-op values otherwise. See
 * DolphinLibretro/Input.cpp in the core source for the mappings referenced
 * below, and epic #1558 for the Wii controls roadmap.
 */

/**
 * The controller device to set on the Wiimote ports after `loadGame`, or
 * null to keep the frontend default (#1534, #1559).
 *
 * The native bridge defaults every port to plain RETRO_DEVICE_JOYPAD — a
 * bare Wiimote — right after `retro_load_game`, which hard-blocks Nunchuk
 * games ("Connect a Nunchuk to Player 1's Wii Remote"). The scheme (the
 * per-game choice, [WiiControlScheme.NUNCHUK] by default) decides which
 * controller type the core attaches instead. The core's Nunchuk scheme
 * (descWiimoteNunchuk): left stick = Nunchuk stick, X = C, Y = Z,
 * L/R = −/+, R2/L2 = shake Wiimote/Nunchuk, right stick stays IR pointer.
 */
fun wiiControllerPortDevice(
    consoleId: String,
    corePath: String,
    scheme: WiiControlScheme,
): Int? =
    if (isWiiWithDolphin(consoleId, corePath)) scheme.portDevice else null

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
fun wiiIrPointerCoreVariables(consoleId: String, corePath: String): List<CoreVariableOverride> =
    if (isWiiWithDolphin(consoleId, corePath)) {
        listOf(
            CoreVariableOverride("dolphin_ir_offset", "10"),
            CoreVariableOverride("dolphin_ir_pitch", "20"),
        )
    } else {
        emptyList()
    }

private fun isWiiWithDolphin(consoleId: String, corePath: String): Boolean {
    val console = consoleId.trim().lowercase().replace("-", "").replace("_", "")
    val core = corePath.substringAfterLast('/').substringBeforeLast('.').lowercase()
    return console == "wii" && core.contains("dolphin")
}
