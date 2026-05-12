package com.spela.player.domain.repository

import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.UserPreferences

interface PreferencesRepository {
    suspend fun getPreferences(): Result<UserPreferences>
    suspend fun updatePreferences(
        showPerformanceOverlay: Boolean? = null,
        autoSaveEnabled: Boolean? = null,
        autoLoadSaveEnabled: Boolean? = null,
        autoUpdateCoresEnabled: Boolean? = null,
        selectedShader: String? = null,
        selectedTheme: String? = null,
        consoleShaders: Map<String, String>? = null,
        /**
         * Per-console save-state opt-out upserts. Keys are console
         * abbreviations; values are SaveStateChoice apiIds ("enabled"
         * | "disabled" | "ask-once") or empty string to clear the
         * row. See #804 phase 4.
         */
        consoleSaveStatePolicies: Map<String, String>? = null,
        /**
         * Per-game save-state opt-out upserts keyed by game ID
         * string. Same sanitiser semantics as
         * [consoleSaveStatePolicies] — empty string clears the row,
         * unknown values are silently dropped server-side.
         * See #804 phase 4b spec point (c).
         */
        gameSaveStatePolicies: Map<String, String>? = null,
        defaultSecondScreenPage: String? = null,
    ): Result<UserPreferences>
    fun getDeviceShaderOverride(consoleId: String): ShaderPreset?
    fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?)
    fun getAllDeviceShaderOverrides(): Map<String, ShaderPreset>
    suspend fun syncDeviceShaderOverrides()
    suspend fun resolveShader(consoleId: String): ShaderPreset
    suspend fun pushDeviceShaderOverridesToServer()
    suspend fun syncKeyMappingsFromServer()
    suspend fun pushKeyMappingsToServer()
    fun getOrientationLock(): String
    fun setOrientationLock(mode: String)
    fun getControlTab(consoleId: String): String
    fun setControlTab(consoleId: String, tab: String)

    /**
     * Device-local console list grouping preference ("generation" |
     * "manufacturer"). Defaults to "generation" when unset — matches
     * the web behaviour in `consoles-page.tsx` and so a user landing
     * on either client for the first time sees the same default
     * grouping. See #1176.
     */
    fun getConsoleListGrouping(): String
    fun setConsoleListGrouping(grouping: String)
}
