package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.DefaultKeyMappings
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.state.KeyMappingState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KeyMappingViewModel(
    private val keyMappingRepository: KeyMappingRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(KeyMappingState())
    val state: StateFlow<KeyMappingState> = _state.asStateFlow()

    fun onIntent(intent: KeyMappingIntent) {
        when (intent) {
            is KeyMappingIntent.LoadMapping -> loadMapping(intent.consoleId, intent.port)
            is KeyMappingIntent.StartWizard -> startWizard(intent.consoleId)
            is KeyMappingIntent.StartSingleButtonMap -> startSingleButtonMap(intent.retroButtonId)
            is KeyMappingIntent.CaptureButton -> captureButton(intent.platformKeyCode)
            KeyMappingIntent.SkipButton -> skipButton()
            KeyMappingIntent.ResetAll -> resetAll()
            KeyMappingIntent.FinishMapping -> finishMapping()
            KeyMappingIntent.CancelMapping -> cancelMapping()
            KeyMappingIntent.DismissError -> _state.update { it.copy(error = null) }
            KeyMappingIntent.ShowPresetPicker -> showPresetPicker()
            KeyMappingIntent.DismissPresetPicker -> _state.update { it.copy(showPresetPicker = false) }
            is KeyMappingIntent.ApplyPreset -> applyPreset(intent.presetId)
            is KeyMappingIntent.LoadGameMapping -> loadGameMapping(intent.gameId, intent.consoleId)
            is KeyMappingIntent.SaveAsGameOverride -> saveAsGameOverride(intent.gameId)
            is KeyMappingIntent.ClearGameOverride -> clearGameOverride(intent.gameId)
        }
    }

    private fun loadMapping(consoleId: String, port: Int) {
        _state.update { it.copy(isLoading = true, consoleId = consoleId, port = port, gameId = null) }
        scope.launch(dispatchers.io) {
            try {
                val layout = DefaultKeyMappings.getLayoutForConsole(consoleId)
                val effectiveMapping = keyMappingRepository.getEffectiveMapping(consoleId, port)
                val presets = keyMappingRepository.getAvailablePresets()
                val activePreset = presets.find { it.bindings == effectiveMapping }
                _state.update {
                    it.copy(
                        currentBindings = effectiveMapping,
                        buttonsForConsole = layout.buttons.map { btn -> btn.retroButtonId },
                        isLoading = false,
                        availablePresets = presets,
                        activePresetId = activePreset?.id,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Failed to load mappings: ${e.message}", isLoading = false)
                }
            }
        }
    }

    private fun startWizard(consoleId: String) {
        val layout = DefaultKeyMappings.getLayoutForConsole(consoleId)
        val buttons = layout.buttons.map { it.retroButtonId }
        if (buttons.isEmpty()) return

        _state.update {
            it.copy(
                consoleId = consoleId,
                buttonsForConsole = buttons,
                isWizardMode = true,
                mappingStep = 1,
                totalSteps = buttons.size,
                currentMappingButton = buttons.first(),
            )
        }
    }

    private fun startSingleButtonMap(retroButtonId: Int) {
        _state.update {
            it.copy(
                isWizardMode = false,
                currentMappingButton = retroButtonId,
                mappingStep = 0,
                totalSteps = 0,
            )
        }
    }

    private fun captureButton(platformKeyCode: Int) {
        val currentButton = _state.value.currentMappingButton ?: return
        val consoleId = _state.value.consoleId
        val port = _state.value.port

        // Update bindings optimistically
        val updatedBindings = _state.value.currentBindings.toMutableMap()
        updatedBindings[currentButton] = platformKeyCode

        // Persist locally and sync to server
        scope.launch(dispatchers.io) {
            try {
                keyMappingRepository.setBinding(consoleId, port, currentButton, platformKeyCode)
                preferencesRepository.pushKeyMappingsToServer()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to save binding: ${e.message}") }
            }
        }

        if (_state.value.isWizardMode) {
            advanceWizard(updatedBindings)
        } else {
            _state.update {
                it.copy(
                    currentBindings = updatedBindings,
                    currentMappingButton = null,
                    activePresetId = null,
                )
            }
        }
    }

    private fun skipButton() {
        if (!_state.value.isWizardMode) return
        advanceWizard(_state.value.currentBindings)
    }

    private fun advanceWizard(updatedBindings: Map<Int, Int>) {
        val buttons = _state.value.buttonsForConsole
        val currentStep = _state.value.mappingStep
        if (currentStep >= buttons.size) {
            // Wizard complete
            _state.update {
                it.copy(
                    currentBindings = updatedBindings,
                    currentMappingButton = null,
                    isWizardMode = false,
                    mappingStep = 0,
                    activePresetId = null,
                )
            }
        } else {
            _state.update {
                it.copy(
                    currentBindings = updatedBindings,
                    mappingStep = currentStep + 1,
                    currentMappingButton = buttons[currentStep],
                )
            }
        }
    }

    private fun resetAll() {
        val consoleId = _state.value.consoleId
        val port = _state.value.port
        scope.launch(dispatchers.io) {
            try {
                keyMappingRepository.resetToDefault(consoleId, port)
                val defaults = keyMappingRepository.getEffectiveMapping(consoleId, port)
                val activePreset = _state.value.availablePresets.find { it.bindings == defaults }
                _state.update { it.copy(currentBindings = defaults, activePresetId = activePreset?.id) }
                preferencesRepository.pushKeyMappingsToServer()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to reset: ${e.message}") }
            }
        }
    }

    private fun finishMapping() {
        _state.update {
            it.copy(
                currentMappingButton = null,
                isWizardMode = false,
                mappingStep = 0,
                totalSteps = 0,
            )
        }
    }

    private fun cancelMapping() {
        if (_state.value.isWizardMode) {
            val consoleId = _state.value.consoleId
            val port = _state.value.port
            scope.launch(dispatchers.io) {
                val effective = keyMappingRepository.getEffectiveMapping(consoleId, port)
                _state.update {
                    it.copy(
                        currentBindings = effective,
                        currentMappingButton = null,
                        isWizardMode = false,
                        mappingStep = 0,
                        totalSteps = 0,
                    )
                }
            }
        } else {
            _state.update {
                it.copy(currentMappingButton = null)
            }
        }
    }

    private fun showPresetPicker() {
        val presets = keyMappingRepository.getAvailablePresets()
        _state.update { it.copy(availablePresets = presets, showPresetPicker = true) }
    }

    private fun applyPreset(presetId: String) {
        _state.update { it.copy(showPresetPicker = false) }
        scope.launch(dispatchers.io) {
            try {
                keyMappingRepository.applyPreset(presetId)
                val consoleId = _state.value.consoleId
                val port = _state.value.port
                val effectiveMapping = keyMappingRepository.getEffectiveMapping(consoleId, port)
                _state.update {
                    it.copy(
                        currentBindings = effectiveMapping,
                        activePresetId = presetId,
                    )
                }
                preferencesRepository.pushKeyMappingsToServer()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to apply preset: ${e.message}") }
            }
        }
    }

    private fun loadGameMapping(gameId: String, consoleId: String) {
        _state.update { it.copy(isLoading = true, gameId = gameId, consoleId = consoleId) }
        scope.launch(dispatchers.io) {
            try {
                val layout = DefaultKeyMappings.getLayoutForConsole(consoleId)
                val effectiveMapping = keyMappingRepository.getEffectiveMappingForGame(gameId, consoleId)
                val hasOverride = keyMappingRepository.hasGameMapping(gameId)
                val presets = keyMappingRepository.getAvailablePresets()
                _state.update {
                    it.copy(
                        currentBindings = effectiveMapping,
                        buttonsForConsole = layout.buttons.map { btn -> btn.retroButtonId },
                        isLoading = false,
                        gameId = gameId,
                        hasGameOverride = hasOverride,
                        availablePresets = presets,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Failed to load game mappings: ${e.message}", isLoading = false)
                }
            }
        }
    }

    private fun saveAsGameOverride(gameId: String) {
        val bindings = _state.value.currentBindings
        scope.launch(dispatchers.io) {
            try {
                keyMappingRepository.setGameMapping(gameId, bindings)
                _state.update { it.copy(hasGameOverride = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to save game override: ${e.message}") }
            }
        }
    }

    private fun clearGameOverride(gameId: String) {
        val consoleId = _state.value.consoleId
        scope.launch(dispatchers.io) {
            try {
                keyMappingRepository.clearGameMapping(gameId)
                val effectiveMapping = keyMappingRepository.getEffectiveMappingForGame(gameId, consoleId)
                _state.update {
                    it.copy(
                        currentBindings = effectiveMapping,
                        hasGameOverride = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to clear game override: ${e.message}") }
            }
        }
    }
}
