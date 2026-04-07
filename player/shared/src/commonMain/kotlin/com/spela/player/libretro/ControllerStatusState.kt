package com.spela.player.libretro

/**
 * Status of a single controller port.
 */
data class PortStatus(
    val port: Int,
    val connected: Boolean,
    val active: Boolean,
)

/**
 * UI-ready snapshot of all controller port statuses.
 * Derived from [GamepadPortManager] port assignments and activity timestamps.
 */
data class ControllerStatusState(
    val ports: List<PortStatus>,
    val connectedCount: Int,
    val isMultiplayer: Boolean,
) {
    companion object {
        /** Number of ports shown in the UI (most games support up to 4 players). */
        const val DISPLAY_PORTS = 4

        /** Creates the state from raw port data. */
        fun fromPortData(
            occupiedPorts: BooleanArray,
            lastActivityMs: LongArray,
            nowMs: Long,
            activityTimeoutMs: Long = 300L,
        ): ControllerStatusState {
            val ports = (0 until DISPLAY_PORTS).map { port ->
                val connected = port < occupiedPorts.size && occupiedPorts[port]
                val lastActivity = if (port < lastActivityMs.size) lastActivityMs[port] else 0L
                val active = connected && lastActivity > 0L && (nowMs - lastActivity) < activityTimeoutMs
                PortStatus(port = port, connected = connected, active = active)
            }
            val connectedCount = ports.count { it.connected }
            return ControllerStatusState(
                ports = ports,
                connectedCount = connectedCount,
                isMultiplayer = connectedCount >= 2,
            )
        }

        /** Empty state — no controllers connected. */
        val Empty = ControllerStatusState(
            ports = (0 until DISPLAY_PORTS).map { PortStatus(port = it, connected = false, active = false) },
            connectedCount = 0,
            isMultiplayer = false,
        )
    }
}
