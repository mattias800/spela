package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.ButtonInfo
import com.spela.player.domain.model.DefaultKeyMappings
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.domain.repository.GamepadMappingRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.libretro.GamepadButtonResolver
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GamepadMappingState(
    val consoleId: String = "",
    val port: Int = 0,
    val displayName: String = "",
    /** Effective position → RetroPad id (defaults + stored overrides). */
    val mapping: Map<GamepadPosition, Int> = emptyMap(),
    /** The console's RetroPad outputs (digital only) — the buttons the editor lists. */
    val outputs: List<ButtonInfo> = emptyList(),
    val isLoading: Boolean = true,
    /** The console button currently being (re)bound via hold-to-bind (#1377), or
     *  null when no binding session is active. Non-null shows the capture prompt. */
    val bindingOutput: ButtonInfo? = null,
    /** The canonical position currently held during the active binding session, or
     *  null while waiting for a press. Drives the 2s hold countdown in the prompt;
     *  shown by its positional name (e.g. "Bottom button"), never a brand glyph. */
    val bindingHeldPosition: GamepadPosition? = null,
    /** Increments on each binding phase change (start, press, release) so the UI's
     *  countdown animations restart in lock-step with the ViewModel's hold/abort
     *  timers (#1377). */
    val bindingTick: Int = 0,
)

sealed interface GamepadMappingIntent {
    data class Load(val consoleId: String, val port: Int = 0) : GamepadMappingIntent

    /** Begin a RetroArch-style hold-to-bind session for [output] (#1377): the next
     *  physical button held for 2s becomes its 1:1 binding; 5s idle aborts. */
    data class StartBinding(val output: ButtonInfo) : GamepadMappingIntent

    /**
     * Feed a captured gamepad press/release into the active binding session
     * (#1377). On Android the editor is a Dialog window, so its content captures
     * keys via Compose `onPreviewKeyEvent` and routes them here (the desktop poller
     * feeds the same signal directly). No-op when no session is active.
     */
    data class ReportBindInput(val position: GamepadPosition, val pressed: Boolean) : GamepadMappingIntent

    /** Cancel the active hold-to-bind session without changing the mapping. */
    data object CancelBinding : GamepadMappingIntent

    data object ResetAll : GamepadMappingIntent
}

/**
 * Edits the positional gamepad mapping layer (`GamepadPosition` → RetroPad) for
 * a console (#1334, desktop gamepad mode of component C). Brand-neutral: the UI
 * shows physical positions and the console's actions, never brand glyphs.
 *
 * Writes go through [GamepadMappingRepository]; after each change the live
 * [GamepadPortManager] mapping is reloaded so a running game picks it up
 * immediately.
 */
class GamepadMappingViewModel(
    private val gamepadMappingRepository: GamepadMappingRepository,
    private val gamepadPortManager: GamepadPortManager,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(GamepadMappingState())
    val state: StateFlow<GamepadMappingState> = _state.asStateFlow()

    /** Collects [GamepadPortManager.bindPressedPositions] for the active session. */
    private var bindCollectJob: Job? = null
    /** Fires after [HOLD_MS] of holding [GamepadMappingState.bindingHeldPosition]. */
    private var holdJob: Job? = null
    /** Fires after [ABORT_MS] to give up an unproductive session. */
    private var abortJob: Job? = null

    fun onIntent(intent: GamepadMappingIntent) {
        when (intent) {
            is GamepadMappingIntent.Load -> load(intent.consoleId, intent.port)
            is GamepadMappingIntent.StartBinding -> startBinding(intent.output)
            is GamepadMappingIntent.ReportBindInput ->
                gamepadPortManager.reportBindPosition(BIND_INPUT_DEVICE, intent.position, intent.pressed)
            GamepadMappingIntent.CancelBinding -> endBindingSession()
            GamepadMappingIntent.ResetAll -> {
                val s = _state.value
                if (s.consoleId.isEmpty()) return
                scope.launch(dispatchers.io) {
                    gamepadMappingRepository.resetToDefault(s.consoleId, s.port)
                    applyAndRefresh(s.consoleId, s.port)
                }
            }
        }
    }

    private fun load(consoleId: String, port: Int) {
        endBindingSession()
        _state.update { it.copy(consoleId = consoleId, port = port, isLoading = true) }
        scope.launch(dispatchers.io) {
            val layout = DefaultKeyMappings.getLayoutForConsole(consoleId)
            val mapping = gamepadMappingRepository.getEffectiveMapping(consoleId, port)
            val outputs = layout.buttons.filter { it.retroButtonId in 0 until GamepadButtonResolver.RETRO_BUTTON_COUNT }
            _state.update {
                it.copy(
                    displayName = layout.displayName,
                    mapping = mapping,
                    outputs = outputs,
                    isLoading = false,
                )
            }
        }
    }

    /**
     * RetroArch-style hold-to-bind (#1377): open capture for [output], then watch
     * [GamepadPortManager.bindPressedPositions]. Holding one position for [HOLD_MS]
     * commits it 1:1 to [output]; releasing resets the hold; [ABORT_MS] of *idle*
     * (no button held) ends the session unchanged.
     */
    private fun startBinding(output: ButtonInfo) {
        if (_state.value.consoleId.isEmpty()) return
        endBindingSession()
        _state.update { it.copy(bindingOutput = output, bindingHeldPosition = null, bindingTick = it.bindingTick + 1) }
        gamepadPortManager.setBindCaptureActive(true)
        bindCollectJob = scope.launch(dispatchers.default) {
            gamepadPortManager.bindPressedPositions.collect { positions ->
                onBindPositionsChanged(positions)
            }
        }
        restartAbortTimer()
    }

    /** (Re)arm the idle-abort timer — runs only while no button is being held. */
    private fun restartAbortTimer() {
        abortJob?.cancel()
        abortJob = scope.launch(dispatchers.default) {
            delay(ABORT_MS)
            endBindingSession()
        }
    }

    /** Reacts to the held-positions set changing during a binding session. */
    private fun onBindPositionsChanged(positions: Set<GamepadPosition>) {
        if (_state.value.bindingOutput == null) return
        // Deterministically pick one held position (lowest ordinal) so multi-press
        // is stable; the common case is exactly one held button.
        val held = positions.minByOrNull { it.ordinal }
        if (held == _state.value.bindingHeldPosition) return
        // The held position changed (new press, swap, or release) — reset the hold
        // and bump the tick so the prompt's countdown animation restarts in sync.
        holdJob?.cancel()
        _state.update { it.copy(bindingHeldPosition = held, bindingTick = it.bindingTick + 1) }
        if (held != null) {
            // A button is down: pause the idle-abort and run the 2s commit hold.
            abortJob?.cancel()
            holdJob = scope.launch(dispatchers.default) {
                delay(HOLD_MS)
                commitBinding(held)
            }
        } else {
            // Back to idle (released before committing): re-arm the idle-abort.
            restartAbortTimer()
        }
    }

    /** Persists [position] as [GamepadMappingState.bindingOutput]'s 1:1 binding. */
    private fun commitBinding(position: GamepadPosition) {
        val s = _state.value
        val output = s.bindingOutput ?: return
        scope.launch(dispatchers.io) {
            gamepadMappingRepository.bindPositionExclusive(s.consoleId, s.port, position, output.retroButtonId)
            applyAndRefresh(s.consoleId, s.port)
        }
        endBindingSession()
    }

    /** Tears down the active session: stop capture, cancel timers, clear prompt. */
    private fun endBindingSession() {
        abortJob?.cancel()
        bindCollectJob?.cancel()
        holdJob?.cancel()
        abortJob = null
        bindCollectJob = null
        holdJob = null
        gamepadPortManager.setBindCaptureActive(false)
        _state.update { it.copy(bindingOutput = null, bindingHeldPosition = null) }
    }

    /** Reload the live port mapping, refresh the displayed mapping, and sync the
     *  positional layer to the server (best-effort) so it follows the user. */
    private suspend fun applyAndRefresh(consoleId: String, port: Int) {
        gamepadPortManager.loadAllGamepadMappings(consoleId)
        val mapping = gamepadMappingRepository.getEffectiveMapping(consoleId, port)
        _state.update { it.copy(mapping = mapping) }
        preferencesRepository.pushKeyMappingsToServer()
    }

    companion object {
        /** Synthetic device id for presses captured via Compose key events (the
         *  Android dialog path); real Android device ids are non-negative. */
        private const val BIND_INPUT_DEVICE = -7777

        /** Continuous hold required to commit a binding (RetroArch-style). */
        const val HOLD_MS = 2000L
        /** Idle timeout after which an unproductive binding session gives up. */
        const val ABORT_MS = 5000L
    }
}
