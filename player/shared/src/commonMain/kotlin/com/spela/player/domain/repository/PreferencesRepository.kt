package com.spela.player.domain.repository

import com.spela.player.domain.model.UserPreferences

interface PreferencesRepository {
    suspend fun getPreferences(): Result<UserPreferences>
    suspend fun updatePreferences(
        showPerformanceOverlay: Boolean? = null,
        autoSaveEnabled: Boolean? = null,
        autoLoadSaveEnabled: Boolean? = null,
    ): Result<UserPreferences>
}
