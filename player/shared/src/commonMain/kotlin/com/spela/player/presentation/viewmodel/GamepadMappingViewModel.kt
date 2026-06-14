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
    /** The console's RetroPad outputs (digital only), for the action picker. */
    val outputs: List<ButtonInfo> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface GamepadMappingIntent {
    data class Load(val consoleId: String, val port: Int = 0) : GamepadMappingIntent
    data class SetBinding(val position: GamepadPosition, val retroButtonId: Int) : GamepadMappingIntent
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

    fun onIntent(intent: GamepadMappingIntent) {
        when (intent) {
            is GamepadMappingIntent.Load -> load(intent.consoleId, intent.port)
            is GamepadMappingIntent.SetBinding -> {
                val s = _state.value
                if (s.consoleId.isEmpty()) return
                scope.launch(dispatchers.io) {
                    gamepadMappingRepository.setBinding(s.consoleId, s.port, intent.position, intent.retroButtonId)
                    applyAndRefresh(s.consoleId, s.port)
                }
            }
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

    /** Reload the live port mapping, refresh the displayed mapping, and sync the
     *  positional layer to the server (best-effort) so it follows the user. */
    private suspend fun applyAndRefresh(consoleId: String, port: Int) {
        gamepadPortManager.loadAllGamepadMappings(consoleId)
        val mapping = gamepadMappingRepository.getEffectiveMapping(consoleId, port)
        _state.update { it.copy(mapping = mapping) }
        preferencesRepository.pushKeyMappingsToServer()
    }
}
