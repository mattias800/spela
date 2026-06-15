package com.spela.player.libretro

/**
 * Represents the state of a connected gamepad as returned by the native SDL3 poller.
 *
 * @param controllerId SDL instance ID for this controller
 * @param name Human-readable controller name (e.g. "Xbox Controller")
 * @param buttons Button states indexed by [com.spela.player.domain.model.GamepadPosition]
 *   ordinal (0..15) — the input layer's canonical positions, NOT libretro ids.
 *   The L2/R2 trigger slots are filled Kotlin-side from [axes], not by the C bridge.
 *   Kotlin applies the configurable GamepadPosition→RetroPad mapping (#1334).
 * @param axes Axis values: [LX, LY, RX, RY, TriggerL, TriggerR] in SDL range (-32768..32767)
 * @param type SDL_GamepadType (SDL_GetRealGamepadType) integer for the connected pad; 0 = unknown
 * @param serial Per-unit controller serial (SDL_GetGamepadSerial), or "" when the
 *   pad exposes none. Used as the stable persistence key for player-slot
 *   assignments so two identical pads can hold distinct slots (#1361); callers
 *   fall back to [name] when blank.
 */
data class GamepadState(
    val controllerId: Int,
    val name: String,
    val buttons: BooleanArray,
    val axes: IntArray,
    val type: Int,
    val serial: String = "",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GamepadState) return false
        return controllerId == other.controllerId &&
            name == other.name &&
            buttons.contentEquals(other.buttons) &&
            axes.contentEquals(other.axes) &&
            type == other.type &&
            serial == other.serial
    }

    override fun hashCode(): Int {
        var result = controllerId
        result = 31 * result + name.hashCode()
        result = 31 * result + buttons.contentHashCode()
        result = 31 * result + axes.contentHashCode()
        result = 31 * result + type
        result = 31 * result + serial.hashCode()
        return result
    }
}
