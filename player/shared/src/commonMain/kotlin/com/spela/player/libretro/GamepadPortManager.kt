package com.spela.player.libretro

import com.spela.player.domain.model.ControllerStyle
import com.spela.player.domain.model.DefaultGamepadMapping
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.domain.repository.ControllerAssignmentRepository
import com.spela.player.domain.repository.GamepadMappingRepository
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

/** Analog stick deflection for the live input tester (#1448). Axes normalized
 *  to -1..1; (0,0) at rest. */
data class GamepadTestSticks(
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
)

class GamepadPortManager(
    private val keyMappingRepository: KeyMappingRepository,
    private val scope: CoroutineScope? = null,
    /**
     * Wall-clock source in epoch milliseconds. Defaults to the system clock.
     * Tests inject a virtual clock (the test scheduler's `currentTime`) so the
     * activity-timeout window is deterministic instead of racing real time.
     */
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /**
     * Source of the device-local/synced gamepad mapping layer (GamepadPosition →
     * RetroPad). Null disables per-console gamepad remapping — consumers fall
     * back to [com.spela.player.domain.model.DefaultGamepadMapping], reproducing
     * the historical fixed behavior. Desktop wires the real repository (#1334).
     */
    private val gamepadMappingRepository: GamepadMappingRepository? = null,
    /**
     * Device-local persistence of which physical controller is which player
     * (#1359). Null disables persistence (tests / in-game keyboard-only setups):
     * connections then auto-claim the lowest free slot per session without
     * remembering. When wired, a controller restores its remembered slot (or its
     * remembered "cleared" state) on reconnect.
     */
    private val controllerAssignmentRepository: ControllerAssignmentRepository? = null,
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
        val style: ControllerStyle = ControllerStyle.Generic,
    )

    /**
     * A connected physical controller and its current player-slot assignment
     * (#1359). [slot] is null when the controller is connected but unassigned
     * (explicitly cleared, or its remembered slot was taken) — it is polled for
     * the input tester but routes no input to any game port.
     */
    data class ConnectedController(
        val deviceId: Int,
        val deviceName: String,
        val stableKey: String,
        val style: ControllerStyle,
        val slot: Int?,
    )

    /** Identity of every connected device, assigned or not, in connect order. */
    private data class DeviceIdentity(
        val deviceId: Int,
        val deviceName: String,
        val stableKey: String,
        val style: ControllerStyle,
    )

    private val connectedDevices = LinkedHashMap<Int, DeviceIdentity>()

    /** Lazily-loaded cache of persisted assignments: stableKey -> slot (null = cleared). */
    private var assignmentCache: MutableMap<String, Int?>? = null

    /** Current port assignments, keyed by device ID (assigned devices only). */
    private val deviceToPort = LinkedHashMap<Int, PortAssignment>()

    /** Tracks which ports are occupied. */
    private val occupiedPorts = BooleanArray(MAX_PORTS)

    /** Per-port key mapping: keyCode -> retroButtonId. Loaded from repository. */
    private val portKeyMappings = Array<Map<Int, Int>?>(MAX_PORTS) { null }

    /** Fallback mapping used when a port-specific mapping hasn't been loaded yet. */
    private var fallbackKeyMapping: Map<Int, Int>? = null

    /** Per-port gamepad mapping: GamepadPosition -> retroButtonId. Loaded from repository. */
    private val portGamepadMappings = Array<Map<GamepadPosition, Int>?>(MAX_PORTS) { null }

    /** Fallback gamepad mapping used before a port-specific one is loaded. */
    private var fallbackGamepadMapping: Map<GamepadPosition, Int>? = null

    /** Per-port last-input timestamps (epoch milliseconds). */
    private val lastActivityMs = LongArray(MAX_PORTS)

    /** Observable activity map: port -> last activity timestamp. */
    private val _portActivity = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val portActivity: StateFlow<Map<Int, Long>> = _portActivity.asStateFlow()

    /** Observable list of current port assignments. */
    private val _assignments = MutableStateFlow<List<PortAssignment>>(emptyList())
    val assignments: StateFlow<List<PortAssignment>> = _assignments.asStateFlow()

    /** Observable list of all connected controllers and their player-slot
     *  assignment (#1359) — the source for the per-controller list/detail UI. */
    private val _connectedControllers = MutableStateFlow<List<ConnectedController>>(emptyList())
    val connectedControllers: StateFlow<List<ConnectedController>> = _connectedControllers.asStateFlow()

    /** Currently-pressed canonical input positions, keyed by device id (the input
     *  layer). Fed by the desktop poller and Android key dispatch; consumed by the
     *  live input tester so a user can press a button and see which GamepadPosition
     *  it normalizes to (#1355). Per-device so the per-controller tester (#1359)
     *  shows only the controller under test, even when it's unassigned. */
    private val pressedByDevice = HashMap<Int, MutableSet<GamepadPosition>>()
    private val _pressedPositions = MutableStateFlow<Set<GamepadPosition>>(emptySet())
    /** Positions held on the controller currently under test ([testCaptureDeviceId]),
     *  or empty when no tester is focused. */
    val pressedPositions: StateFlow<Set<GamepadPosition>> = _pressedPositions.asStateFlow()

    /** Analog stick deflection of the controller under test, for the live tester's
     *  stick indicators (#1448). Each axis is normalized to -1..1; (0,0) at rest. */
    private val _testSticks = MutableStateFlow(GamepadTestSticks())
    val testSticks: StateFlow<GamepadTestSticks> = _testSticks.asStateFlow()

    /** The controller whose buttons the input tester is currently capturing, or
     *  null when no tester element is focused. Non-null = capture active for
     *  exactly that device: the input pipelines (Android key dispatch, desktop
     *  poller) route+consume its non-D-pad buttons and never capture the D-pad,
     *  so navigation is never disrupted by the tester being on screen (#1355/#1359). */
    private val _testCaptureDeviceId = MutableStateFlow<Int?>(null)
    val testCaptureDeviceId: StateFlow<Int?> = _testCaptureDeviceId.asStateFlow()

    /** Set by the tester UI on focus gain/loss (device id while focused, null on
     *  blur). Switching/closing the tester clears stale highlights. */
    @Synchronized
    fun setTestCaptureDevice(deviceId: Int?) {
        if (_testCaptureDeviceId.value == deviceId) return
        _testCaptureDeviceId.value = deviceId
        pressedByDevice.clear()
        _pressedPositions.value = emptySet()
        _testSticks.value = GamepadTestSticks()
    }

    /** Reports the controller-under-test's analog stick deflection for the live
     *  tester (#1448). Ignored unless [deviceId] is the device under test. */
    @Synchronized
    fun reportTestSticks(deviceId: Int, leftX: Float, leftY: Float, rightX: Float, rightY: Float) {
        if (_testCaptureDeviceId.value != deviceId) return
        _testSticks.value = GamepadTestSticks(leftX, leftY, rightX, rightY)
    }

    /** Positions held during a hold-to-bind session, merged across ALL devices
     *  and INCLUDING the D-pad (#1377). Distinct from the input tester, which is
     *  per-device and excludes the D-pad: the binding editor lets the user assign
     *  any physical button — including D-pad directions — using any controller. */
    private val bindPressedByDevice = HashMap<Int, MutableSet<GamepadPosition>>()
    private val _bindPressedPositions = MutableStateFlow<Set<GamepadPosition>>(emptySet())
    val bindPressedPositions: StateFlow<Set<GamepadPosition>> = _bindPressedPositions.asStateFlow()

    /** True while the per-console mapping editor is capturing a hold-to-bind press
     *  (#1377). The input pipelines route every position (incl. D-pad) from any
     *  device into [bindPressedPositions] and suppress navigation, so a press only
     *  feeds the binder. Mutually exclusive with the input tester in practice (they
     *  live on different screens). */
    private val _bindCaptureActive = MutableStateFlow(false)
    val bindCaptureActive: StateFlow<Boolean> = _bindCaptureActive.asStateFlow()

    /** Set by the mapping editor when a hold-to-bind session starts/ends. Clears
     *  any stale held positions on each transition. */
    @Synchronized
    fun setBindCaptureActive(active: Boolean) {
        if (_bindCaptureActive.value == active) return
        _bindCaptureActive.value = active
        bindPressedByDevice.clear()
        _bindPressedPositions.value = emptySet()
    }

    /** Current input mode: TOUCH when touch input was last used, GAMEPAD when D-pad/buttons were. */
    private val _inputMode = MutableStateFlow(InputMode.TOUCH)
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    /** Normalized right-stick vertical position (-1 fully up .. +1 fully down, 0 at
     *  rest after deadzone), published by the platform input layers for continuous
     *  viewport scrolling (#1362). The UI reads the latest value each frame. */
    private val _rightStickScroll = MutableStateFlow(0f)
    val rightStickScroll: StateFlow<Float> = _rightStickScroll.asStateFlow()

    /** Report the right-stick vertical position (already deadzoned by the caller). */
    fun setRightStickScroll(normalizedY: Float) {
        if (_rightStickScroll.value == normalizedY) return
        _rightStickScroll.value = normalizedY
    }

    /** Observable controller status for UI indicators. */
    private val _controllerStatus = MutableStateFlow(ControllerStatusState.Empty)
    val controllerStatus: StateFlow<ControllerStatusState> = _controllerStatus.asStateFlow()

    /** Coroutine that periodically refreshes activity flags. */
    private var activityRefreshJob: Job? = null

    fun setInputMode(mode: InputMode) {
        _inputMode.value = mode
    }

    /**
     * Registers a connected controller and resolves its player slot (#1359):
     * - a controller with a remembered slot restores it (if still free),
     * - a controller remembered as *cleared* stays unassigned,
     * - a never-seen controller auto-claims the lowest free slot (preserving
     *   plug-and-play) and remembers it.
     *
     * Returns the assigned slot, or -1 when the controller is connected but
     * unassigned (cleared, no free slot, or its remembered slot was taken).
     * Idempotent: re-registering an already-connected device returns its current
     * slot without re-claiming.
     *
     * [stableKey] identifies the physical controller for persistence; it defaults
     * to [deviceName] (the desktop identity) — Android passes the device descriptor.
     */
    @Synchronized
    fun connectDevice(
        deviceId: Int,
        deviceName: String = "",
        style: ControllerStyle = ControllerStyle.Generic,
        stableKey: String = deviceName,
    ): Int {
        if (connectedDevices.containsKey(deviceId)) return deviceToPort[deviceId]?.port ?: -1

        ensureCacheLoaded()
        connectedDevices[deviceId] = DeviceIdentity(deviceId, deviceName, stableKey, style)

        val cache = assignmentCache!!
        val targetSlot: Int? = when {
            // A blank key can't reliably identify a physical controller, so don't
            // persist or restore under it — just auto-claim for this session.
            stableKey.isBlank() -> occupiedPorts.indexOfFirst { !it }.takeIf { it >= 0 }
            // Remembered: restore the slot if free; honor an explicit clear (null).
            cache.containsKey(stableKey) -> cache[stableKey]?.takeIf { !occupiedPorts[it] }
            // Never seen: auto-claim the lowest free slot and remember it.
            else -> occupiedPorts.indexOfFirst { !it }.takeIf { it >= 0 }
                ?.also { rememberAssignment(stableKey, it) }
        }
        if (targetSlot != null) assignToPort(deviceId, targetSlot)

        emitConnectedControllers()
        emitControllerStatus()
        startActivityRefreshIfNeeded()
        return targetSlot ?: -1
    }

    /**
     * Assigns [deviceId] to player [slot] (0-based), with move-and-clear conflict
     * resolution (#1359): if another controller holds [slot], it is cleared
     * (unassigned + remembered as cleared) before this one takes the slot. The
     * caller is responsible for confirming the switch with the user first (see
     * [deviceOnSlot]). No-op for an out-of-range slot or unknown device.
     */
    @Synchronized
    fun assignSlot(deviceId: Int, slot: Int) {
        if (slot < 0 || slot >= MAX_PORTS) return
        val identity = connectedDevices[deviceId] ?: return

        deviceToPort.entries.firstOrNull { it.value.port == slot && it.key != deviceId }?.key
            ?.let { clearAssignmentInternal(it) }
        freeAssignedPort(deviceId)

        assignToPort(deviceId, slot)
        rememberAssignment(identity.stableKey, slot)
        _portActivity.value = buildActivityMap()
        emitConnectedControllers()
        emitControllerStatus()
        startActivityRefreshIfNeeded()
    }

    /**
     * Clears [deviceId]'s player assignment (#1359): the controller stays
     * connected (still testable) but routes no input to any game port, and the
     * cleared state is remembered so it stays cleared on reconnect.
     */
    @Synchronized
    fun clearAssignment(deviceId: Int) {
        if (connectedDevices[deviceId] == null) return
        clearAssignmentInternal(deviceId)
        _portActivity.value = buildActivityMap()
        emitConnectedControllers()
        emitControllerStatus()
        stopActivityRefreshIfNotNeeded()
    }

    /** The device currently assigned to [slot], or null when the slot is free. */
    @Synchronized
    fun deviceOnSlot(slot: Int): Int? =
        deviceToPort.entries.firstOrNull { it.value.port == slot }?.key

    /** Loads the persisted-assignment cache on first use. Must hold the monitor. */
    private fun ensureCacheLoaded() {
        if (assignmentCache == null) {
            assignmentCache = controllerAssignmentRepository?.getAll()?.toMutableMap() ?: mutableMapOf()
        }
    }

    /** Remembers [slot] (or null = cleared) for [stableKey]. Must hold the monitor. */
    private fun rememberAssignment(stableKey: String, slot: Int?) {
        ensureCacheLoaded()
        assignmentCache!![stableKey] = slot
        controllerAssignmentRepository?.put(stableKey, slot)
    }

    /** Marks [port] occupied and records the assignment. Must hold the monitor. */
    private fun assignToPort(deviceId: Int, port: Int) {
        val identity = connectedDevices[deviceId] ?: return
        occupiedPorts[port] = true
        deviceToPort[deviceId] = PortAssignment(deviceId, port, identity.deviceName, identity.style)
        _assignments.value = deviceToPort.values.toList()
    }

    /** Frees the device's assigned port (mappings, activity), if any. Must hold the monitor. */
    private fun freeAssignedPort(deviceId: Int) {
        val assignment = deviceToPort.remove(deviceId) ?: return
        occupiedPorts[assignment.port] = false
        portKeyMappings[assignment.port] = null
        portGamepadMappings[assignment.port] = null
        lastActivityMs[assignment.port] = 0L
        _assignments.value = deviceToPort.values.toList()
    }

    /** Frees the port and remembers the device as cleared. Must hold the monitor. */
    private fun clearAssignmentInternal(deviceId: Int) {
        val identity = connectedDevices[deviceId]
        freeAssignedPort(deviceId)
        if (identity != null) rememberAssignment(identity.stableKey, null)
    }

    /** Rebuilds and emits the connected-controllers snapshot. Must hold the monitor. */
    private fun emitConnectedControllers() {
        _connectedControllers.value = connectedDevices.values.map { id ->
            ConnectedController(
                deviceId = id.deviceId,
                deviceName = id.deviceName,
                stableKey = id.stableKey,
                style = id.style,
                slot = deviceToPort[id.deviceId]?.port,
            )
        }
    }

    /**
     * Records input activity on a port. Updates the per-port timestamp
     * and emits a new activity map for UI observation.
     */
    @Synchronized
    fun reportActivity(port: Int) {
        if (port < 0 || port >= MAX_PORTS) return
        if (!occupiedPorts[port]) return
        lastActivityMs[port] = nowMs()
        _portActivity.value = buildActivityMap()
        emitControllerStatus()
    }

    /**
     * Disconnects a device: frees its port (if assigned) for reuse and drops it
     * from the connected list. Its persisted assignment is kept, so reconnecting
     * restores its slot (or cleared state). No-op for an unknown device.
     */
    @Synchronized
    fun disconnectDevice(deviceId: Int) {
        val wasConnected = connectedDevices.remove(deviceId) != null
        freeAssignedPort(deviceId)
        if (pressedByDevice.remove(deviceId) != null && _testCaptureDeviceId.value == deviceId) {
            recomputePressedPositions()
        }
        if (bindPressedByDevice.remove(deviceId) != null && _bindCaptureActive.value) {
            recomputeBindPressed()
        }
        if (!wasConnected) return
        _portActivity.value = buildActivityMap()
        emitConnectedControllers()
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
     * Loads the gamepad mapping layer (GamepadPosition -> retroButtonId) for a
     * port from [gamepadMappingRepository]. No-op when no repository is wired.
     */
    suspend fun loadGamepadMappingForPort(port: Int, consoleId: String) {
        if (port < 0 || port >= MAX_PORTS) return
        val repo = gamepadMappingRepository ?: return
        val mapping = repo.getEffectiveMapping(consoleId, port)
        synchronized(this) {
            portGamepadMappings[port] = mapping
        }
    }

    /**
     * Loads the gamepad mapping layer for all currently assigned ports, plus a
     * port-0 fallback for devices that connect before their port-specific
     * mapping is ready. No-op when no repository is wired.
     */
    suspend fun loadAllGamepadMappings(consoleId: String) {
        val repo = gamepadMappingRepository ?: return
        val fallback = repo.getEffectiveMapping(consoleId, 0)
        synchronized(this) {
            fallbackGamepadMapping = fallback
        }
        val ports = synchronized(this) { deviceToPort.values.map { it.port } }
        for (port in ports) {
            loadGamepadMappingForPort(port, consoleId)
        }
    }

    /**
     * Returns the gamepad mapping (GamepadPosition -> retroButtonId) for a port:
     * the port-specific mapping if loaded, else the fallback, else null (the
     * caller should default to [com.spela.player.domain.model.DefaultGamepadMapping]).
     */
    @Synchronized
    fun getGamepadMapping(port: Int): Map<GamepadPosition, Int>? {
        if (port < 0 || port >= MAX_PORTS) return null
        return portGamepadMappings[port] ?: fallbackGamepadMapping
    }

    /**
     * Maps an Android gamepad key code to a libretro RetroPad id for a port via
     * the two-layer model (#1334): physical key code → canonical
     * [GamepadPosition] (input layer) → RetroPad id (mapping layer). Falls back
     * to [DefaultGamepadMapping] when no per-console mapping is loaded, so the
     * default reproduces the historical behavior. Returns null for non-gamepad
     * key codes or unmapped positions.
     */
    @Synchronized
    fun mapGamepadKeyToLibretro(port: Int, keyCode: Int): Int? {
        if (port < 0 || port >= MAX_PORTS) return null
        val position = AndroidGamepadNormalizer.normalize(keyCode) ?: return null
        val mapping = portGamepadMappings[port] ?: fallbackGamepadMapping ?: DefaultGamepadMapping.POSITION_TO_RETRO
        return mapping[position]
    }

    /**
     * Records that a single canonical [position] was pressed/released on
     * [deviceId] (incremental — used by the Android key dispatch). Feeds
     * [pressedPositions] for the live input tester when [deviceId] is the one
     * under test (#1355/#1359).
     */
    @Synchronized
    fun reportPositionInput(deviceId: Int, position: GamepadPosition, pressed: Boolean) {
        val set = pressedByDevice.getOrPut(deviceId) { mutableSetOf() }
        val changed = if (pressed) set.add(position) else set.remove(position)
        if (changed && _testCaptureDeviceId.value == deviceId) recomputePressedPositions()
    }

    /**
     * Replaces the full set of currently-pressed positions for [deviceId]
     * (wholesale — used by the desktop poller, which has all positions each
     * frame). Only emits when the set for the device under test actually changes,
     * so the 120 Hz poll loop doesn't churn [pressedPositions].
     */
    @Synchronized
    fun reportPressedPositions(deviceId: Int, positions: Set<GamepadPosition>) {
        val set = pressedByDevice.getOrPut(deviceId) { mutableSetOf() }
        if (set == positions) return
        set.clear()
        set.addAll(positions)
        if (_testCaptureDeviceId.value == deviceId) recomputePressedPositions()
    }

    /** Must be called while holding the monitor. */
    private fun recomputePressedPositions() {
        val id = _testCaptureDeviceId.value
        _pressedPositions.value = if (id != null) pressedByDevice[id]?.toSet() ?: emptySet() else emptySet()
    }

    /**
     * Records a hold-to-bind press/release for [deviceId] during a binding session
     * (#1377). Captures from ANY device and INCLUDES the D-pad — fed by the desktop
     * poller and (on Android) the mapping dialog's key handler. No-op unless
     * [bindCaptureActive] is set, so stray presses outside a session are ignored.
     */
    @Synchronized
    fun reportBindPosition(deviceId: Int, position: GamepadPosition, pressed: Boolean) {
        if (!_bindCaptureActive.value) return
        val set = bindPressedByDevice.getOrPut(deviceId) { mutableSetOf() }
        val changed = if (pressed) set.add(position) else set.remove(position)
        if (changed) recomputeBindPressed()
    }

    /**
     * Replaces the full set of held bind positions for [deviceId] (wholesale —
     * used by the desktop poller, which has all positions each frame). Includes
     * the D-pad. No-op unless [bindCaptureActive] is set.
     */
    @Synchronized
    fun reportBindPressedPositions(deviceId: Int, positions: Set<GamepadPosition>) {
        if (!_bindCaptureActive.value) return
        val set = bindPressedByDevice.getOrPut(deviceId) { mutableSetOf() }
        if (set == positions) return
        set.clear()
        set.addAll(positions)
        recomputeBindPressed()
    }

    /** Must be called while holding the monitor. */
    private fun recomputeBindPressed() {
        val merged = mutableSetOf<GamepadPosition>()
        for (positions in bindPressedByDevice.values) merged.addAll(positions)
        _bindPressedPositions.value = merged
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
     * Returns the number of currently connected devices (assigned or not).
     */
    @Synchronized
    fun connectedDeviceCount(): Int = connectedDevices.size

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

        // Swap gamepad mappings
        val tmpGamepadMapping = portGamepadMappings[portA]
        portGamepadMappings[portA] = portGamepadMappings[portB]
        portGamepadMappings[portB] = tmpGamepadMapping

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
        emitConnectedControllers()
        emitControllerStatus()
    }

    /**
     * Clears all device assignments and mappings.
     */
    @Synchronized
    fun clear() {
        deviceToPort.clear()
        connectedDevices.clear()
        occupiedPorts.fill(false)
        portKeyMappings.fill(null)
        portGamepadMappings.fill(null)
        fallbackKeyMapping = null
        fallbackGamepadMapping = null
        lastActivityMs.fill(0L)
        pressedByDevice.clear()
        _pressedPositions.value = emptySet()
        _testCaptureDeviceId.value = null
        bindPressedByDevice.clear()
        _bindPressedPositions.value = emptySet()
        _bindCaptureActive.value = false
        _rightStickScroll.value = 0f
        _assignments.value = emptyList()
        _connectedControllers.value = emptyList()
        _portActivity.value = emptyMap()
        emitControllerStatus()
        stopActivityRefresh()
    }

    private fun emitControllerStatus() {
        val now = nowMs()
        _controllerStatus.value = ControllerStatusState.fromPortData(
            occupiedPorts = occupiedPorts.copyOf(),
            lastActivityMs = lastActivityMs.copyOf(),
            nowMs = now,
            activityTimeoutMs = ACTIVITY_TIMEOUT_MS,
        )
    }

    /** Starts the periodic activity refresh if multiplayer and not already running.
     *  Must be called while holding the monitor (from a @Synchronized method). */
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

    /** Stops the periodic activity refresh if there are fewer than 2 controllers.
     *  Must be called while holding the monitor (from a @Synchronized method). */
    private fun stopActivityRefreshIfNotNeeded() {
        if (deviceToPort.size >= 2) return
        stopActivityRefresh()
    }

    /** Cancels the periodic activity refresh job unconditionally.
     *  Must be called while holding the monitor (from a @Synchronized method). */
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
