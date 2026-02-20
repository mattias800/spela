package com.spela.player.presentation.state

import com.spela.player.domain.model.KeyMappingPreset

data class KeyMappingState(
    val consoleId: String = "",
    val port: Int = 0,
    /** Current bindings: retroButtonId -> platformKeyCode */
    val currentBindings: Map<Int, Int> = emptyMap(),
    /** Which retroButtonIds this console uses (for display) */
    val buttonsForConsole: List<Int> = emptyList(),
    /** retroButtonId currently listening for a key press (null = not listening) */
    val currentMappingButton: Int? = null,
    /** Progress through the wizard (1-based step index) */
    val mappingStep: Int = 0,
    /** Total number of buttons in the wizard */
    val totalSteps: Int = 0,
    /** Whether wizard mode is active (maps all buttons sequentially) */
    val isWizardMode: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Available presets for the current platform */
    val availablePresets: List<KeyMappingPreset> = emptyList(),
    /** Whether to show the preset picker dialog */
    val showPresetPicker: Boolean = false,
    /** ID of the currently active preset (if any) */
    val activePresetId: String? = null,
    /** Game ID if editing per-game mapping (null = editing console/global mapping) */
    val gameId: String? = null,
    /** Whether the current game has a custom key mapping override */
    val hasGameOverride: Boolean = false,
)
