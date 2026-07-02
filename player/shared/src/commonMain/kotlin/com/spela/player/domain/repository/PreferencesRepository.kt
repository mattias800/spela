package com.spela.player.domain.repository

import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.RenderScale
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.model.WidescreenMode
import com.spela.player.domain.model.defaultWidescreenMode

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
         * Per-console internal render-scale upserts. Keys are console
         * abbreviations; values are RenderScale apiIds ("2x" | "3x"
         * | "4x") or "native"/"1x"/empty string to clear server state.
         * See #1546.
         */
        consoleRenderScales: Map<String, String>? = null,
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
    fun resolveWidescreenMode(gameId: String, consoleId: String): WidescreenMode =
        defaultWidescreenMode(consoleId)
    fun setWidescreenMode(gameId: String, consoleId: String, mode: WidescreenMode) {}
    fun resolveRenderScale(consoleId: String, preferences: UserPreferences? = null): RenderScale = RenderScale.NATIVE
    fun setRenderScale(consoleId: String, scale: RenderScale) {}
    suspend fun pushDeviceShaderOverridesToServer()
    suspend fun syncKeyMappingsFromServer()
    suspend fun pushKeyMappingsToServer()

    /**
     * Per-game key-mapping sync (#1336). Console/global mappings ride inside the
     * bulk preferences endpoint; per-game overrides have dedicated endpoints, so
     * they sync per game on save/clear/load. All best-effort — a network failure
     * leaves the local override intact (offline-first).
     */
    suspend fun pushGameKeyMappingToServer(gameId: String, bindings: Map<Int, Int>)
    suspend fun deleteGameKeyMappingOnServer(gameId: String)
    /** Pulls the server's per-game override into the local store, if any. */
    suspend fun syncGameKeyMappingFromServer(gameId: String)
    fun getOrientationLock(): String
    fun setOrientationLock(mode: String)
    fun getControlTab(consoleId: String): String
    fun setControlTab(consoleId: String, tab: String)

    /**
     * Device-local confirm/back button convention (#1448): which positional face
     * button confirms in menus. [ConfirmButtonConvention.XBOX] = bottom (south)
     * confirms / right (east) backs (Xbox, PlayStation, Steam Deck);
     * [ConfirmButtonConvention.NINTENDO] = right (east) confirms / bottom backs.
     * Defaults to XBOX. Read synchronously by the input layer per button press
     * (Android) and per poll (desktop), so it's backed by an in-memory cache.
     */
    fun getConfirmButtonConvention(): String
    fun setConfirmButtonConvention(convention: String)

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

/** Values for [PreferencesRepository.getConfirmButtonConvention] (#1448). */
object ConfirmButtonConvention {
    /** Bottom (south) confirms, right (east) backs. Xbox/PlayStation/Steam Deck. The default. */
    const val XBOX = "xbox"

    /** Right (east) confirms, bottom (south) backs. Nintendo. */
    const val NINTENDO = "nintendo"
}
