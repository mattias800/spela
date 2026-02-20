package com.spela.player.domain.repository

import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.UserPreferences

interface PreferencesRepository {
    suspend fun getPreferences(): Result<UserPreferences>
    suspend fun updatePreferences(
        showPerformanceOverlay: Boolean? = null,
        autoSaveEnabled: Boolean? = null,
        autoLoadSaveEnabled: Boolean? = null,
        selectedShader: String? = null,
        selectedTheme: String? = null,
        consoleShaders: Map<String, String>? = null,
    ): Result<UserPreferences>
    fun getDeviceShaderOverride(consoleId: String): ShaderPreset?
    fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?)
    fun getAllDeviceShaderOverrides(): Map<String, ShaderPreset>
    suspend fun syncDeviceShaderOverrides()
    suspend fun resolveShader(consoleId: String): ShaderPreset
    suspend fun pushDeviceShaderOverridesToServer()
    suspend fun syncKeyMappingsFromServer()
    suspend fun pushKeyMappingsToServer()
}
