package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val username: String = "",
    val serverUrl: String = "",
    val cacheSize: Long = 0,
    val showPerformanceOverlay: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val autoLoadSaveEnabled: Boolean = true,
    val showLogoutConfirm: Boolean = false,
    val showClearCacheConfirm: Boolean = false,
)

sealed interface SettingsIntent {
    data object LoadSettings : SettingsIntent
    data object TogglePerformanceOverlay : SettingsIntent
    data object ToggleAutoSave : SettingsIntent
    data object ToggleAutoLoadSave : SettingsIntent
    data object ShowLogoutConfirm : SettingsIntent
    data object DismissLogoutConfirm : SettingsIntent
    data object Logout : SettingsIntent
    data object ShowClearCacheConfirm : SettingsIntent
    data object DismissClearCacheConfirm : SettingsIntent
    data object ClearCache : SettingsIntent
}

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val downloadRepository: DownloadRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.LoadSettings -> loadSettings()
            SettingsIntent.TogglePerformanceOverlay ->
                _state.update { it.copy(showPerformanceOverlay = !it.showPerformanceOverlay) }
            SettingsIntent.ToggleAutoSave ->
                _state.update { it.copy(autoSaveEnabled = !it.autoSaveEnabled) }
            SettingsIntent.ToggleAutoLoadSave ->
                _state.update { it.copy(autoLoadSaveEnabled = !it.autoLoadSaveEnabled) }
            SettingsIntent.ShowLogoutConfirm ->
                _state.update { it.copy(showLogoutConfirm = true) }
            SettingsIntent.DismissLogoutConfirm ->
                _state.update { it.copy(showLogoutConfirm = false) }
            SettingsIntent.Logout -> logout()
            SettingsIntent.ShowClearCacheConfirm ->
                _state.update { it.copy(showClearCacheConfirm = true) }
            SettingsIntent.DismissClearCacheConfirm ->
                _state.update { it.copy(showClearCacheConfirm = false) }
            SettingsIntent.ClearCache -> clearCache()
        }
    }

    private fun loadSettings() {
        scope.launch(dispatchers.io) {
            val user = authRepository.getCurrentUser().getOrNull()
            val cacheSize = downloadRepository.getCacheSize()
            _state.update {
                it.copy(
                    username = user?.username ?: "",
                    cacheSize = cacheSize,
                )
            }
        }
    }

    private fun logout() {
        scope.launch(dispatchers.io) {
            authRepository.clearTokens()
            _state.update { it.copy(showLogoutConfirm = false) }
        }
    }

    private fun clearCache() {
        scope.launch(dispatchers.io) {
            downloadRepository.clearCache()
            _state.update { it.copy(cacheSize = 0, showClearCacheConfirm = false) }
        }
    }
}
