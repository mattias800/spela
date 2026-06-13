package com.spela.player.libretro

/**
 * Represents the state of a connected gamepad as returned by the native SDL3 poller.
 *
 * @param controllerId SDL instance ID for this controller
 * @param name Human-readable controller name (e.g. "Xbox Controller")
 * @param buttons Button states indexed by libretro button ID (0..15)
 * @param axes Axis values: [LX, LY, RX, RY, TriggerL, TriggerR] in SDL range (-32768..32767)
 */
data class GamepadState(
    val controllerId: Int,
    val name: String,
    val buttons: BooleanArray,
    val axes: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GamepadState) return false
        return controllerId == other.controllerId &&
            name == other.name &&
            buttons.contentEquals(other.buttons) &&
            axes.contentEquals(other.axes)
    }

    override fun hashCode(): Int {
        var result = controllerId
        result = 31 * result + name.hashCode()
        result = 31 * result + buttons.contentHashCode()
        result = 31 * result + axes.contentHashCode()
        return result
    }
}
