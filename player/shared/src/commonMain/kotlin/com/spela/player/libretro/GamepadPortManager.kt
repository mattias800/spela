package com.spela.player.libretro

import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.presentation.ui.gamepad.InputMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * Manages mapping of physical gamepad devices to libretro player ports.
 *
 * Each connected gamepad is assigned a unique port (0..MAX_PORTS-1).
 * Disconnected devices free their port for reuse. Each port maintains
 * its own key mapping, loaded from [KeyMappingRepository].
 *
 * Thread-safe: all mutation goes through synchronized blocks.
 */
class GamepadPortManager(
    private val keyMappingRepository: KeyMappingRepository,
    private val scope: CoroutineScope? = null,
) {
    companion object {
        const val MAX_PORTS = 8
        const val ACTIVITY_TIMEOUT_MS = 300L
        const val ACTIVITY_REFRESH_INTERVAL_MS = 100L
    }

    /**
     * Represents a device assigned to a port.
     */
    data class PortAssignment(
        val deviceId: Int,
        val port: Int,
        val deviceName: String = "",
    )

    /** Current port assignments, keyed by device ID. */
    private val deviceToPort = LinkedHashMap<Int, PortAssignment>()

    /** Tracks which ports are occupied. */
    private val occupiedPorts = BooleanArray(MAX_PORTS)

    /** Per-port key mapping: keyCode -> retroButtonId. Loaded from repository. */
    private val portKeyMappings = Array<Map<Int, Int>?>(MAX_PORTS) { null }

    /** Fallback mapping used when a port-specific mapping hasn't been loaded yet. */
    private var fallbackKeyMapping: Map<Int, Int>? = null

    /** Per-port last-input timestamps (epoch milliseconds). */
    private val lastActivityMs = LongArray(MAX_PORTS)

    /** Observable activity map: port -> last activity timestamp. */
    private val _portActivity = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val portActivity: StateFlow<Map<Int, Long>> = _portActivity.asStateFlow()

    /** Observable list of current port assignments. */
    private val _assignments = MutableStateFlow<List<PortAssignment>>(emptyList())
    val assignments: StateFlow<List<PortAssignment>> = _assignments.asStateFlow()

    /** Current input mode: TOUCH when touch input was last used, GAMEPAD when D-pad/buttons were. */
    private val _inputMode = MutableStateFlow(InputMode.TOUCH)
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    /** Observable controller status for UI indicators. */
    private val _controllerStatus = MutableStateFlow(ControllerStatusState.Empty)
    val controllerStatus: StateFlow<ControllerStatusState> = _controllerStatus.asStateFlow()

    /** Coroutine that periodically refreshes activity flags. */
    private var activityRefreshJob: Job? = null

    fun setInputMode(mode: InputMode) {
        _inputMode.value = mode
    }

    /**
     * Assigns a device to the next available port.
     * Returns the assigned port, or -1 if all ports are full.
     * If the device is already assigned, returns its existing port.
     */
    @Synchronized
    fun connectDevice(deviceId: Int, deviceName: String = ""): Int {
        // Already assigned?
        deviceToPort[deviceId]?.let { return it.port }

        // Find the first available port
        val port = occupiedPorts.indexOfFirst { !it }
        if (port == -1) return -1

        occupiedPorts[port] = true
        val assignment = PortAssignment(deviceId = deviceId, port = port, deviceName = deviceName)
        deviceToPort[deviceId] = assignment
        _assignments.value = deviceToPort.values.toList()
        emitControllerStatus()
        startActivityRefreshIfNeeded()
        return port
    }

    /**
     * Records input activity on a port. Updates the per-port timestamp
     * and emits a new activity map for UI observation.
     */
    @Synchronized
    fun reportActivity(port: Int) {
        if (port < 0 || port >= MAX_PORTS) return
        if (!occupiedPorts[port]) return
        lastActivityMs[port] = Clock.System.now().toEpochMilliseconds()
        _portActivity.value = buildActivityMap()
        emitControllerStatus()
    }

    /**
     * Disconnects a device and frees its port for reuse.
     */
    @Synchronized
    fun disconnectDevice(deviceId: Int) {
        val assignment = deviceToPort.remove(deviceId) ?: return
        occupiedPorts[assignment.port] = false
        portKeyMappings[assignment.port] = null
        lastActivityMs[assignment.port] = 0L
        _assignments.value = deviceToPort.values.toList()
        _portActivity.value = buildActivityMap()
        emitControllerStatus()
        stopActivityRefreshIfNotNeeded()
    }

    /**
     * Returns the port assigned to a device, or -1 if not assigned.
     */
    @Synchronized
    fun getPort(deviceId: Int): Int {
        return deviceToPort[deviceId]?.port ?: -1
    }

    /**
     * Returns the key mapping for a port (keyCode -> retroButtonId).
     * Returns null if no mapping is loaded for that port.
     */
    @Synchronized
    fun getKeyMapping(port: Int): Map<Int, Int>? {
        if (port < 0 || port >= MAX_PORTS) return null
        return portKeyMappings[port]
    }

    /**
     * Loads the key mapping for a specific port from the repository.
     * The mapping is stored as retroButtonId -> platformKeyCode,
     * but we reverse it to keyCode -> retroButtonId for fast lookup.
     *
     * @param port The port to load the mapping for
     * @param consoleId The console to load mappings for
     */
    suspend fun loadMappingForPort(port: Int, consoleId: String) {
        if (port < 0 || port >= MAX_PORTS) return
        val retroToKey = keyMappingRepository.getEffectiveMapping(consoleId, port)
        val keyToRetro = retroToKey.entries.associate { (retro, key) -> key to retro }
        synchronized(this) {
            portKeyMappings[port] = keyToRetro
        }
    }

    /**
     * Loads the key mapping for all currently assigned ports.
     * Also loads a fallback mapping (for port 0) used when a device connects
     * before its port-specific mapping is ready.
     */
    suspend fun loadAllMappings(consoleId: String) {
        // Always load fallback so it's available for newly-connected devices
        val retroToKey = keyMappingRepository.getEffectiveMapping(consoleId, 0)
        val keyToRetro = retroToKey.entries.associate { (retro, key) -> key to retro }
        synchronized(this) {
            fallbackKeyMapping = keyToRetro
        }

        val ports = synchronized(this) {
            deviceToPort.values.map { it.port }
        }
        for (port in ports) {
            loadMappingForPort(port, consoleId)
        }
    }

    /**
     * Loads per-game key mapping for a specific port, with fallback chain:
     * per-game -> per-console -> global default -> hardcoded defaults.
     */
    suspend fun loadGameMappingForPort(port: Int, gameId: String, consoleId: String) {
        if (port < 0 || port >= MAX_PORTS) return
        val retroToKey = keyMappingRepository.getEffectiveMappingForGame(gameId, consoleId, port)
        val keyToRetro = retroToKey.entries.associate { (retro, key) -> key to retro }
        synchronized(this) {
            portKeyMappings[port] = keyToRetro
        }
    }

    /**
     * Loads per-game key mapping for all currently assigned ports.
     * Also loads a fallback mapping (for port 0) used when a device connects
     * before its port-specific mapping is ready.
     */
    suspend fun loadAllGameMappings(gameId: String, consoleId: String) {
        // Always load fallback so it's available for newly-connected devices
        val retroToKey = keyMappingRepository.getEffectiveMappingForGame(gameId, consoleId, 0)
        val keyToRetro = retroToKey.entries.associate { (retro, key) -> key to retro }
        synchronized(this) {
            fallbackKeyMapping = keyToRetro
        }

        val ports = synchronized(this) {
            deviceToPort.values.map { it.port }
        }
        for (port in ports) {
            loadGameMappingForPort(port, gameId, consoleId)
        }
    }

    /**
     * Maps a platform key code to a libretro button ID for a given port.
     * If the port has a loaded mapping, uses it exclusively.
     * Falls back to the general fallback mapping only when no port-specific
     * mapping has been loaded yet (covers the race between device connection
     * and async mapping load).
     */
    @Synchronized
    fun mapKeyToLibretro(port: Int, keyCode: Int): Int? {
        if (port < 0 || port >= MAX_PORTS) return null
        val portMapping = portKeyMappings[port]
        return if (portMapping != null) {
            portMapping[keyCode]
        } else {
            fallbackKeyMapping?.get(keyCode)
        }
    }

    /**
     * Returns the number of currently connected devices.
     */
    @Synchronized
    fun connectedDeviceCount(): Int = deviceToPort.size

    /**
     * Swaps device assignments, key mappings, and activity timestamps
     * between two ports. No-op if either port is out of range.
     */
    @Synchronized
    fun swapPorts(portA: Int, portB: Int) {
        if (portA < 0 || portA >= MAX_PORTS) return
        if (portB < 0 || portB >= MAX_PORTS) return
        if (portA == portB) return

        // Swap occupied flags
        val tmpOccupied = occupiedPorts[portA]
        occupiedPorts[portA] = occupiedPorts[portB]
        occupiedPorts[portB] = tmpOccupied

        // Swap key mappings
        val tmpMapping = portKeyMappings[portA]
        portKeyMappings[portA] = portKeyMappings[portB]
        portKeyMappings[portB] = tmpMapping

        // Swap activity timestamps
        val tmpActivity = lastActivityMs[portA]
        lastActivityMs[portA] = lastActivityMs[portB]
        lastActivityMs[portB] = tmpActivity

        // Update device-to-port assignments
        val devicesOnA = deviceToPort.entries.filter { it.value.port == portA }.map { it.key }
        val devicesOnB = deviceToPort.entries.filter { it.value.port == portB }.map { it.key }

        for (devId in devicesOnA) {
            val old = deviceToPort[devId]!!
            deviceToPort[devId] = old.copy(port = portB)
        }
        for (devId in devicesOnB) {
            val old = deviceToPort[devId]!!
            deviceToPort[devId] = old.copy(port = portA)
        }

        _assignments.value = deviceToPort.values.toList()
        _portActivity.value = buildActivityMap()
    }

    /**
     * Clears all device assignments and mappings.
     */
    @Synchronized
    fun clear() {
        deviceToPort.clear()
        occupiedPorts.fill(false)
        portKeyMappings.fill(null)
        fallbackKeyMapping = null
        lastActivityMs.fill(0L)
        _assignments.value = emptyList()
        _portActivity.value = emptyMap()
        emitControllerStatus()
        stopActivityRefresh()
    }

    private fun emitControllerStatus() {
        val now = Clock.System.now().toEpochMilliseconds()
        _controllerStatus.value = ControllerStatusState.fromPortData(
            occupiedPorts = occupiedPorts.copyOf(),
            lastActivityMs = lastActivityMs.copyOf(),
            nowMs = now,
            activityTimeoutMs = ACTIVITY_TIMEOUT_MS,
        )
    }

    private fun startActivityRefreshIfNeeded() {
        if (activityRefreshJob?.isActive == true) return
        if (deviceToPort.size < 2) return
        val currentScope = scope ?: return
        activityRefreshJob = currentScope.launch {
            while (isActive) {
                delay(ACTIVITY_REFRESH_INTERVAL_MS)
                synchronized(this@GamepadPortManager) {
                    emitControllerStatus()
                }
            }
        }
    }

    private fun stopActivityRefreshIfNotNeeded() {
        if (deviceToPort.size >= 2) return
        stopActivityRefresh()
    }

    private fun stopActivityRefresh() {
        activityRefreshJob?.cancel()
        activityRefreshJob = null
    }

    /**
     * Builds an activity map from the current timestamps,
     * only including occupied ports with non-zero timestamps.
     */
    private fun buildActivityMap(): Map<Int, Long> {
        val map = mutableMapOf<Int, Long>()
        for (i in 0 until MAX_PORTS) {
            if (occupiedPorts[i] && lastActivityMs[i] > 0L) {
                map[i] = lastActivityMs[i]
            }
        }
        return map
    }
}
