package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.UpdatePreferencesRequest
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.PreferencesRepository

class PreferencesRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : PreferencesRepository {

    override suspend fun getPreferences(): Result<UserPreferences> = runCatching {
        apiClient.getPreferences().toDomain()
    }

    override suspend fun updatePreferences(
        showPerformanceOverlay: Boolean?,
        autoSaveEnabled: Boolean?,
        autoLoadSaveEnabled: Boolean?,
        selectedShader: String?,
    ): Result<UserPreferences> = runCatching {
        apiClient.updatePreferences(
            UpdatePreferencesRequest(
                showPerformanceOverlay = showPerformanceOverlay,
                autoSaveEnabled = autoSaveEnabled,
                autoLoadSaveEnabled = autoLoadSaveEnabled,
                selectedShader = selectedShader,
            )
        ).toDomain()
    }
}
