package com.spela.player.presentation.intent

sealed interface KeyMappingIntent {
    /** Load existing mappings for a console and port. */
    data class LoadMapping(val consoleId: String, val port: Int = 0) : KeyMappingIntent

    /** Start the wizard: map all buttons for a console sequentially. */
    data class StartWizard(val consoleId: String) : KeyMappingIntent

    /** Start listening for a single button remap. */
    data class StartSingleButtonMap(val retroButtonId: Int) : KeyMappingIntent

    /** A platform key was pressed while in listening mode. */
    data class CaptureButton(val platformKeyCode: Int) : KeyMappingIntent

    /** Skip the current button in wizard mode. */
    data object SkipButton : KeyMappingIntent

    /** Reset all custom mappings for the current console to defaults. */
    data object ResetAll : KeyMappingIntent

    /** Finish the wizard or single-button mapping. */
    data object FinishMapping : KeyMappingIntent

    /** Cancel the current mapping operation. */
    data object CancelMapping : KeyMappingIntent

    /** Clear the binding for the currently-highlighted button and exit listening mode. */
    data object ClearCurrentBinding : KeyMappingIntent

    /** Dismiss error message. */
    data object DismissError : KeyMappingIntent

    /** Show the preset picker dialog. */
    data object ShowPresetPicker : KeyMappingIntent

    /** Dismiss the preset picker dialog. */
    data object DismissPresetPicker : KeyMappingIntent

    /** Apply a preset by its ID. */
    data class ApplyPreset(val presetId: String) : KeyMappingIntent

    /** Load per-game mapping override. */
    data class LoadGameMapping(val gameId: String, val consoleId: String) : KeyMappingIntent

    /** Save current bindings as a per-game override. */
    data class SaveAsGameOverride(val gameId: String) : KeyMappingIntent

    /** Clear per-game mapping override, reverting to console/global defaults. */
    data class ClearGameOverride(val gameId: String) : KeyMappingIntent
}
