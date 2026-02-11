package com.spela.player.libretro

import com.spela.player.domain.repository.KeyMappingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
) {
    companion object {
        const val MAX_PORTS = 8
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

    /** Observable list of current port assignments. */
    private val _assignments = MutableStateFlow<List<PortAssignment>>(emptyList())
    val assignments: StateFlow<List<PortAssignment>> = _assignments.asStateFlow()

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
        return port
    }

    /**
     * Disconnects a device and frees its port for reuse.
     */
    @Synchronized
    fun disconnectDevice(deviceId: Int) {
        val assignment = deviceToPort.remove(deviceId) ?: return
        occupiedPorts[assignment.port] = false
        portKeyMappings[assignment.port] = null
        _assignments.value = deviceToPort.values.toList()
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
     */
    suspend fun loadAllMappings(consoleId: String) {
        val ports = synchronized(this) {
            deviceToPort.values.map { it.port }
        }
        for (port in ports) {
            loadMappingForPort(port, consoleId)
        }
    }

    /**
     * Maps a platform key code to a libretro button ID for a given port.
     * Returns null if the key is not mapped.
     */
    @Synchronized
    fun mapKeyToLibretro(port: Int, keyCode: Int): Int? {
        if (port < 0 || port >= MAX_PORTS) return null
        return portKeyMappings[port]?.get(keyCode)
    }

    /**
     * Returns the number of currently connected devices.
     */
    @Synchronized
    fun connectedDeviceCount(): Int = deviceToPort.size

    /**
     * Clears all device assignments and mappings.
     */
    @Synchronized
    fun clear() {
        deviceToPort.clear()
        occupiedPorts.fill(false)
        portKeyMappings.fill(null)
        _assignments.value = emptyList()
    }
}
