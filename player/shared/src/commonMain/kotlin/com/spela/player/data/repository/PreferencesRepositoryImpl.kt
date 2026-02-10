package com.spela.player.data.repository

import com.spela.player.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.UpdatePreferencesRequest
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.PreferencesRepository

class PreferencesRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val database: SpelaDatabase,
) : PreferencesRepository {

    override suspend fun getPreferences(): Result<UserPreferences> = runCatching {
        apiClient.getPreferences().toDomain()
    }

    override suspend fun updatePreferences(
        showPerformanceOverlay: Boolean?,
        autoSaveEnabled: Boolean?,
        autoLoadSaveEnabled: Boolean?,
        selectedShader: String?,
        consoleShaders: Map<String, String>?,
    ): Result<UserPreferences> = runCatching {
        apiClient.updatePreferences(
            UpdatePreferencesRequest(
                showPerformanceOverlay = showPerformanceOverlay,
                autoSaveEnabled = autoSaveEnabled,
                autoLoadSaveEnabled = autoLoadSaveEnabled,
                selectedShader = selectedShader,
                consoleShaders = consoleShaders,
            )
        ).toDomain()
    }

    override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? {
        return database.spelaDatabaseQueries.getShaderOverride(consoleId)
            .executeAsOneOrNull()
            ?.let { ShaderPreset.fromApiId(it) }
    }

    override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {
        if (shader == null || shader == ShaderPreset.NONE) {
            database.spelaDatabaseQueries.deleteShaderOverride(consoleId)
        } else {
            database.spelaDatabaseQueries.insertShaderOverride(consoleId, shader.apiId)
        }
    }

    override fun getAllDeviceShaderOverrides(): Map<String, ShaderPreset> {
        return database.spelaDatabaseQueries.getAllShaderOverrides()
            .executeAsList()
            .associate { it.console_id to ShaderPreset.fromApiId(it.shader) }
    }

    override suspend fun resolveShader(consoleId: String): ShaderPreset {
        // 1. Check device-local override first
        val deviceOverride = getDeviceShaderOverride(consoleId)
        if (deviceOverride != null) return deviceOverride

        // 2. Check server per-console preference
        val preferences = getPreferences().getOrNull()
        if (preferences != null) {
            val consoleShader = preferences.consoleShaders[consoleId]
            if (consoleShader != null) return consoleShader

            // 3. Fall back to global selected shader
            return preferences.selectedShader
        }

        // 4. Final fallback
        return ShaderPreset.NONE
    }
}
