package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.ControllerStyle
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.domain.repository.ControllerStyleOverrideRepository
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GamepadConfigState(
    val portAssignments: List<PortAssignmentUi> = emptyList(),
    val selectedPort: Int? = null,
    /** Canonical input positions currently held down on the controller under
     *  test — drives the live input tester (#1355/#1359). */
    val pressedPositions: Set<GamepadPosition> = emptySet(),
    /** All connected controllers and their player-slot assignment (#1359) — the
     *  source for the per-controller list/detail UI in Settings → Controls. */
    val controllers: List<ControllerUi> = emptyList(),
    /** The controller whose detail subscreen is open, or null for the list (#1359). */
    val selectedDeviceId: Int? = null,
    /** A pending player-slot conflict awaiting the user's switch/cancel (#1359). */
    val conflict: SlotConflict? = null,
)

/**
 * A connected controller for the per-controller list/detail UI (#1359). [slot] is
 * the assigned 0-based player port, or null when the controller is connected but
 * unassigned (cleared) — testable, but routing no input to any game port.
 */
data class ControllerUi(
    val deviceId: Int,
    val deviceName: String,
    val slot: Int?,
    val isActive: Boolean,
    /** Effective controller style: the user's override if set, else detection. */
    val style: ControllerStyle = ControllerStyle.Generic,
    /** Raw detected style, independent of any override (labels the "Auto" choice). */
    val detectedStyle: ControllerStyle = ControllerStyle.Generic,
    /** The user's explicit style override, or null when Auto (defer to detection). */
    val styleOverride: ControllerStyle? = null,
)

/**
 * A pending player-slot assignment that collides with another controller (#1359).
 * The UI confirms the switch: confirming moves [slot] to [deviceId] and clears
 * [currentDeviceId]; cancelling leaves everything as-is.
 */
data class SlotConflict(
    val deviceId: Int,
    val slot: Int,
    val currentDeviceId: Int,
    val currentDeviceName: String,
)

data class PortAssignmentUi(
    val port: Int,
    val deviceName: String,
    val deviceId: Int,
    val isActive: Boolean,
    val hasCustomMapping: Boolean,
    /** Effective controller style: the user's override if set, else detection. */
    val style: ControllerStyle = ControllerStyle.Generic,
    /** Raw detected style, independent of any override (labels the "Auto" choice). */
    val detectedStyle: ControllerStyle = ControllerStyle.Generic,
    /** The user's explicit style override, or null when Auto (defer to detection). */
    val styleOverride: ControllerStyle? = null,
)

sealed interface GamepadConfigIntent {
    data class SwapPorts(val portA: Int, val portB: Int) : GamepadConfigIntent
    data class SelectPortForMapping(val port: Int) : GamepadConfigIntent
    data object DeselectPort : GamepadConfigIntent

    /**
     * Set (or, with style == null, clear back to Auto) the device-local
     * controller-style override for the controller on [port].
     */
    data class SetStyleOverride(val port: Int, val style: ControllerStyle?) : GamepadConfigIntent

    // --- Per-controller list/detail (#1359) ---

    /** Open the detail subscreen for [deviceId]. */
    data class SelectController(val deviceId: Int) : GamepadConfigIntent

    /** Close the detail subscreen back to the controller list. */
    data object CloseDetail : GamepadConfigIntent

    /** Assign [deviceId] to player [slot] (0-based). Raises a [SlotConflict] when
     *  another controller holds the slot, instead of assigning immediately. */
    data class AssignPlayer(val deviceId: Int, val slot: Int) : GamepadConfigIntent

    /** Confirm the pending [SlotConflict]: move the slot (clearing the old controller). */
    data object ConfirmConflict : GamepadConfigIntent

    /** Dismiss the pending [SlotConflict] without changing anything. */
    data object DismissConflict : GamepadConfigIntent

    /** Clear [deviceId]'s player assignment (it stays connected, routes no input). */
    data class ClearPlayer(val deviceId: Int) : GamepadConfigIntent

    /** Set (or clear, with style == null) the style override for the controller
     *  identified by [deviceId] — works for unassigned controllers too. */
    data class SetStyleOverrideForController(val deviceId: Int, val style: ControllerStyle?) : GamepadConfigIntent

    /** Toggle input-test capture for the open controller's detail (tester focus). */
    data class SetInputTestActive(val active: Boolean) : GamepadConfigIntent
}

class GamepadConfigViewModel(
    private val gamepadPortManager: GamepadPortManager,
    private val styleOverrideRepository: ControllerStyleOverrideRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    /**
     * Wall-clock source in epoch milliseconds. Defaults to the system clock.
     * Tests inject a virtual clock so the activity-timeout window is
     * deterministic rather than racing real time against the test scheduler.
     */
    private val nowMs: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) {
    companion object {
        private const val ACTIVITY_TIMEOUT_MS = 500L
        private const val REFRESH_INTERVAL_MS = 200L
    }

    private val _state = MutableStateFlow(GamepadConfigState())
    val state: StateFlow<GamepadConfigState> = _state.asStateFlow()

    /**
     * In-memory cache of device-local style overrides, keyed by controller
     * device name. Loaded lazily when a controller first appears (so the
     * 200 ms refresh loop never touches the DB) and updated on
     * [GamepadConfigIntent.SetStyleOverride].
     */
    private val _overrides = MutableStateFlow<Map<String, ControllerStyle>>(emptyMap())

    /** Device names whose override load has been requested. Only mutated from
     *  the single refresh coroutine, so it needs no synchronization. */
    private val requestedLoads = mutableSetOf<String>()

    private var refreshJob: Job? = null

    init {
        startRefreshing()
        // Mirror the live pressed-positions signal into state (event-driven, not
        // the 200 ms poll) so the input tester highlights feel immediate.
        scope.launch(dispatchers.default) {
            gamepadPortManager.pressedPositions.collect { positions ->
                _state.update { it.copy(pressedPositions = positions) }
            }
        }
    }

    fun onIntent(intent: GamepadConfigIntent) {
        when (intent) {
            is GamepadConfigIntent.SwapPorts -> {
                gamepadPortManager.swapPorts(intent.portA, intent.portB)
                refreshState()
            }
            is GamepadConfigIntent.SelectPortForMapping -> {
                _state.update { it.copy(selectedPort = intent.port) }
            }
            GamepadConfigIntent.DeselectPort -> {
                _state.update { it.copy(selectedPort = null) }
            }
            is GamepadConfigIntent.SetStyleOverride -> {
                val deviceName = gamepadPortManager.assignments.value
                    .find { it.port == intent.port }
                    ?.deviceName
                    ?.takeIf { it.isNotEmpty() }
                    ?: return
                persistStyleOverride(deviceName, intent.style)
            }
            is GamepadConfigIntent.SelectController -> {
                _state.update { it.copy(selectedDeviceId = intent.deviceId) }
            }
            GamepadConfigIntent.CloseDetail -> {
                gamepadPortManager.setTestCaptureDevice(null)
                _state.update { it.copy(selectedDeviceId = null) }
            }
            is GamepadConfigIntent.AssignPlayer -> {
                val occupant = gamepadPortManager.deviceOnSlot(intent.slot)
                if (occupant != null && occupant != intent.deviceId) {
                    val name = gamepadPortManager.connectedControllers.value
                        .find { it.deviceId == occupant }?.deviceName.orEmpty()
                    _state.update {
                        it.copy(
                            conflict = SlotConflict(
                                deviceId = intent.deviceId,
                                slot = intent.slot,
                                currentDeviceId = occupant,
                                currentDeviceName = name.ifEmpty { "another controller" },
                            ),
                        )
                    }
                } else {
                    gamepadPortManager.assignSlot(intent.deviceId, intent.slot)
                    refreshState()
                }
            }
            GamepadConfigIntent.ConfirmConflict -> {
                val conflict = _state.value.conflict ?: return
                gamepadPortManager.assignSlot(conflict.deviceId, conflict.slot)
                _state.update { it.copy(conflict = null) }
                refreshState()
            }
            GamepadConfigIntent.DismissConflict -> {
                _state.update { it.copy(conflict = null) }
            }
            is GamepadConfigIntent.ClearPlayer -> {
                gamepadPortManager.clearAssignment(intent.deviceId)
                refreshState()
            }
            is GamepadConfigIntent.SetStyleOverrideForController -> {
                val deviceName = gamepadPortManager.connectedControllers.value
                    .find { it.deviceId == intent.deviceId }
                    ?.deviceName
                    ?.takeIf { it.isNotEmpty() }
                    ?: return
                persistStyleOverride(deviceName, intent.style)
            }
            is GamepadConfigIntent.SetInputTestActive -> {
                gamepadPortManager.setTestCaptureDevice(
                    if (intent.active) _state.value.selectedDeviceId else null,
                )
            }
        }
    }

    /** Writes the device-local style override and reflects it in the in-memory cache. */
    private fun persistStyleOverride(deviceName: String, style: ControllerStyle?) {
        scope.launch(dispatchers.io) {
            styleOverrideRepository.setOverride(deviceName, style)
            _overrides.update { current ->
                if (style == null) current - deviceName
                else current + (deviceName to style)
            }
        }
    }

    private fun startRefreshing() {
        refreshJob?.cancel()
        refreshJob = scope.launch(dispatchers.default) {
            while (isActive) {
                refreshState()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun refreshState() {
        val assignments = gamepadPortManager.assignments.value
        val activity = gamepadPortManager.portActivity.value
        val overrides = _overrides.value
        val now = nowMs()

        // Lazily load the override for any controller we haven't looked up yet.
        // The result lands in [_overrides] and is reflected on the next tick.
        for (assignment in assignments) {
            val name = assignment.deviceName
            if (name.isNotEmpty() && requestedLoads.add(name)) {
                scope.launch(dispatchers.io) {
                    val stored = styleOverrideRepository.getOverride(name) ?: return@launch
                    _overrides.update { it + (name to stored) }
                }
            }
        }

        val portAssignments = assignments.map { assignment ->
            val lastActivity = activity[assignment.port] ?: 0L
            val isActive = lastActivity > 0L && (now - lastActivity) < ACTIVITY_TIMEOUT_MS
            val override = overrides[assignment.deviceName]
            PortAssignmentUi(
                port = assignment.port,
                deviceName = assignment.deviceName.ifEmpty { "Controller ${assignment.port + 1}" },
                deviceId = assignment.deviceId,
                isActive = isActive,
                hasCustomMapping = gamepadPortManager.getKeyMapping(assignment.port) != null,
                style = override ?: assignment.style,
                detectedStyle = assignment.style,
                styleOverride = override,
            )
        }.sortedBy { it.port }

        // Per-controller list (#1359): every connected controller, assigned or not.
        val connected = gamepadPortManager.connectedControllers.value
        for (controller in connected) {
            val name = controller.deviceName
            if (name.isNotEmpty() && requestedLoads.add(name)) {
                scope.launch(dispatchers.io) {
                    val stored = styleOverrideRepository.getOverride(name) ?: return@launch
                    _overrides.update { it + (name to stored) }
                }
            }
        }
        val controllers = connected.map { controller ->
            val lastActivity = controller.slot?.let { activity[it] } ?: 0L
            val isActive = lastActivity > 0L && (now - lastActivity) < ACTIVITY_TIMEOUT_MS
            val override = overrides[controller.deviceName]
            ControllerUi(
                deviceId = controller.deviceId,
                deviceName = controller.deviceName.ifEmpty { "Controller" },
                slot = controller.slot,
                isActive = isActive,
                style = override ?: controller.style,
                detectedStyle = controller.style,
                styleOverride = override,
            )
        }

        _state.update { it.copy(portAssignments = portAssignments, controllers = controllers) }
    }
}
