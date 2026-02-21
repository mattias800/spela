package com.spela.player.presentation.viewmodel

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
)

data class PortAssignmentUi(
    val port: Int,
    val deviceName: String,
    val deviceId: Int,
    val isActive: Boolean,
    val hasCustomMapping: Boolean,
)

sealed interface GamepadConfigIntent {
    data class SwapPorts(val portA: Int, val portB: Int) : GamepadConfigIntent
    data class SelectPortForMapping(val port: Int) : GamepadConfigIntent
    data object DeselectPort : GamepadConfigIntent
}

class GamepadConfigViewModel(
    private val gamepadPortManager: GamepadPortManager,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val ACTIVITY_TIMEOUT_MS = 500L
        private const val REFRESH_INTERVAL_MS = 200L
    }

    private val _state = MutableStateFlow(GamepadConfigState())
    val state: StateFlow<GamepadConfigState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        startRefreshing()
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
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()

        val portAssignments = assignments.map { assignment ->
            val lastActivity = activity[assignment.port] ?: 0L
            val isActive = lastActivity > 0L && (now - lastActivity) < ACTIVITY_TIMEOUT_MS
            PortAssignmentUi(
                port = assignment.port,
                deviceName = assignment.deviceName.ifEmpty { "Controller ${assignment.port + 1}" },
                deviceId = assignment.deviceId,
                isActive = isActive,
                hasCustomMapping = gamepadPortManager.getKeyMapping(assignment.port) != null,
            )
        }.sortedBy { it.port }

        _state.update { it.copy(portAssignments = portAssignments) }
    }
}
