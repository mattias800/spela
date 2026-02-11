package com.spela.player.presentation.state

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
)
