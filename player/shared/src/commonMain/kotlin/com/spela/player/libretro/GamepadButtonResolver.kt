package com.spela.player.libretro

import com.spela.player.domain.model.GamepadPosition

/**
 * Pure resolution of the two-layer gamepad model's mapping step: given which
 * canonical [GamepadPosition]s are currently pressed (the input layer) and a
 * `GamepadPosition` → RetroPad-id mapping (the mapping layer), produce the
 * per-RetroPad-button pressed state to feed the core.
 *
 * **Fan-in safe:** if several positions map to the same RetroPad id, the result
 * is their OR — a press on any contributing position wins, and releasing one
 * mapped position never clobbers another's press. (A naive per-position
 * `setButton` would let a released position turn off a button another position
 * is still holding.)
 */
object GamepadButtonResolver {
    /** RetroPad face/dpad/shoulder/stick button count (ids 0..15). */
    const val RETRO_BUTTON_COUNT = 16

    /**
     * @param positionPressed pressed state indexed by [GamepadPosition.ordinal]
     * @param mapping position → RetroPad id (e.g. an effective console mapping)
     * @return BooleanArray of size [RETRO_BUTTON_COUNT]; index = RetroPad id
     */
    fun resolve(positionPressed: BooleanArray, mapping: Map<GamepadPosition, Int>): BooleanArray {
        val out = BooleanArray(RETRO_BUTTON_COUNT)
        for (position in GamepadPosition.entries) {
            if (positionPressed.getOrNull(position.ordinal) != true) continue
            val retroId = mapping[position] ?: continue
            if (retroId in 0 until RETRO_BUTTON_COUNT) out[retroId] = true
        }
        return out
    }

    /**
     * Resolves analog trigger pressure through the same GamepadPosition -> RetroPad
     * mapping layer used for digital buttons. Null means the platform did not
     * expose that trigger axis; zero means the axis exists and is released.
     *
     * If both trigger positions map to the same RetroPad id, the stronger pressure
     * wins so one released trigger cannot clobber another held trigger.
     */
    fun resolveAnalogTriggerPressures(
        l2: Short?,
        r2: Short?,
        mapping: Map<GamepadPosition, Int>,
    ): Map<Int, Short> {
        val out = LinkedHashMap<Int, Int>()

        fun record(position: GamepadPosition, pressure: Short?) {
            if (pressure == null) return
            val retroId = mapping[position] ?: return
            if (retroId !in 0 until RETRO_BUTTON_COUNT) return
            out[retroId] = maxOf(out[retroId] ?: 0, pressure.toInt())
        }

        record(GamepadPosition.L2, l2)
        record(GamepadPosition.R2, r2)

        return out.mapValues { (_, pressure) -> pressure.toShort() }
    }
}

/**
 * Tracks which RetroPad ids currently receive analog trigger pressure per port.
 * When live mapping changes remove an id, the caller must clear that id in the
 * native analog-button table so RETRO_DEVICE_INDEX_ANALOG_BUTTON can fall back
 * to digital state again.
 */
class AnalogTriggerRouteTracker(private val portCount: Int = 8) {
    companion object {
        fun portsInvalidatedByAssignmentChange(
            previousAssignments: Map<Int, Int>,
            currentAssignments: Map<Int, Int>,
        ): Set<Int> {
            val portsToClear = LinkedHashSet<Int>()

            previousAssignments.forEach { (deviceId, previousPort) ->
                if (currentAssignments[deviceId] != previousPort) portsToClear.add(previousPort)
            }
            currentAssignments.forEach { (deviceId, port) ->
                val previousDeviceOnPort = previousAssignments.entries
                    .firstOrNull { (_, previousPort) -> previousPort == port }
                    ?.key
                if (previousDeviceOnPort != deviceId) {
                    portsToClear.add(port)
                }
            }

            return portsToClear
        }
    }

    private val idsByPort = List(portCount) { LinkedHashSet<Int>() }

    fun update(port: Int, currentIds: Set<Int>): Set<Int> {
        if (port !in 0 until portCount) return emptySet()
        val previous = idsByPort[port]
        val removed = previous.filterTo(LinkedHashSet()) { it !in currentIds }
        previous.clear()
        previous.addAll(currentIds)
        return removed
    }

    fun clearPort(port: Int): Set<Int> {
        if (port !in 0 until portCount) return emptySet()
        val previous = idsByPort[port].toSet()
        idsByPort[port].clear()
        return previous
    }
}
