package com.spela.player.presentation.viewmodel

import com.spela.player.data.device.DeviceManager
import com.spela.player.data.repository.PreferencesRepositoryImpl
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShaderScope { DEFAULT, PER_CONSOLE }

data class SettingsState(
    val username: String = "",
    val serverUrl: String = "",
    val deviceName: String = "",
    val cacheSize: Long = 0,
    val showPerformanceOverlay: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val autoLoadSaveEnabled: Boolean = true,
    val selectedShader: ShaderPreset = ShaderPreset.NONE,
    val consoleShaders: Map<String, ShaderPreset> = emptyMap(),
    val deviceShaderOverrides: Map<String, ShaderPreset> = emptyMap(),
    val consoles: List<Console> = emptyList(),
    val shaderScope: ShaderScope = ShaderScope.DEFAULT,
    val expandedConsoleId: String? = null,
    val showLogoutConfirm: Boolean = false,
    val showClearCacheConfirm: Boolean = false,
)

sealed interface SettingsIntent {
    data object LoadSettings : SettingsIntent
    data object TogglePerformanceOverlay : SettingsIntent
    data object ToggleAutoSave : SettingsIntent
    data object ToggleAutoLoadSave : SettingsIntent
    data class SelectShader(val shader: ShaderPreset) : SettingsIntent
    data class SwitchShaderScope(val scope: ShaderScope) : SettingsIntent
    data class SelectConsoleShader(val consoleId: String, val shader: ShaderPreset) : SettingsIntent
    data class SetDeviceOverride(val consoleId: String, val shader: ShaderPreset?) : SettingsIntent
    data class ExpandConsole(val consoleId: String?) : SettingsIntent
    data class UpdateDeviceName(val name: String) : SettingsIntent
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
    private val preferencesRepository: PreferencesRepository,
    private val gameRepository: GameRepository,
    private val deviceManager: DeviceManager,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var deviceNameSyncJob: Job? = null

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.LoadSettings -> loadSettings()
            SettingsIntent.TogglePerformanceOverlay -> togglePreference(
                currentValue = { it.showPerformanceOverlay },
                optimisticUpdate = { s, v -> s.copy(showPerformanceOverlay = v) },
                apiCall = { preferencesRepository.updatePreferences(showPerformanceOverlay = it) },
            )
            SettingsIntent.ToggleAutoSave -> togglePreference(
                currentValue = { it.autoSaveEnabled },
                optimisticUpdate = { s, v -> s.copy(autoSaveEnabled = v) },
                apiCall = { preferencesRepository.updatePreferences(autoSaveEnabled = it) },
            )
            SettingsIntent.ToggleAutoLoadSave -> togglePreference(
                currentValue = { it.autoLoadSaveEnabled },
                optimisticUpdate = { s, v -> s.copy(autoLoadSaveEnabled = v) },
                apiCall = { preferencesRepository.updatePreferences(autoLoadSaveEnabled = it) },
            )
            is SettingsIntent.SelectShader -> selectShader(intent.shader)
            is SettingsIntent.SwitchShaderScope ->
                _state.update { it.copy(shaderScope = intent.scope) }
            is SettingsIntent.SelectConsoleShader ->
                selectConsoleShader(intent.consoleId, intent.shader)
            is SettingsIntent.SetDeviceOverride ->
                setDeviceOverride(intent.consoleId, intent.shader)
            is SettingsIntent.ExpandConsole ->
                _state.update {
                    it.copy(expandedConsoleId = if (it.expandedConsoleId == intent.consoleId) null else intent.consoleId)
                }
            is SettingsIntent.UpdateDeviceName -> updateDeviceName(intent.name)
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

    private fun togglePreference(
        currentValue: (SettingsState) -> Boolean,
        optimisticUpdate: (SettingsState, Boolean) -> SettingsState,
        apiCall: suspend (Boolean) -> Result<*>,
    ) {
        val newValue = !currentValue(_state.value)
        _state.update { optimisticUpdate(it, newValue) }
        scope.launch(dispatchers.io) {
            apiCall(newValue).onFailure {
                // Revert on failure
                _state.update { optimisticUpdate(it, !newValue) }
            }
        }
    }

    private fun loadSettings() {
        scope.launch(dispatchers.io) {
            val user = authRepository.getCurrentUser().getOrNull()
            val cacheSize = downloadRepository.getCacheSize()
            val deviceName = deviceManager.getDeviceName()
            _state.update {
                it.copy(
                    username = user?.username ?: "",
                    cacheSize = cacheSize,
                    deviceName = deviceName,
                )
            }

            preferencesRepository.getPreferences().onSuccess { prefs ->
                _state.update {
                    it.copy(
                        showPerformanceOverlay = prefs.showPerformanceOverlay,
                        autoSaveEnabled = prefs.autoSaveEnabled,
                        autoLoadSaveEnabled = prefs.autoLoadSaveEnabled,
                        selectedShader = prefs.selectedShader,
                        consoleShaders = prefs.consoleShaders,
                    )
                }
            }

            gameRepository.getConsoles().onSuccess { consoles ->
                _state.update { it.copy(consoles = consoles) }
            }

            // Sync device shader overrides from server, then load local
            preferencesRepository.syncDeviceShaderOverrides()
            val deviceOverrides = preferencesRepository.getAllDeviceShaderOverrides()
            _state.update { it.copy(deviceShaderOverrides = deviceOverrides) }
        }
    }

    private fun logout() {
        scope.launch(dispatchers.io) {
            authRepository.clearTokens()
            _state.update { it.copy(showLogoutConfirm = false) }
        }
    }

    private fun selectShader(shader: ShaderPreset) {
        val previous = _state.value.selectedShader
        _state.update { it.copy(selectedShader = shader) }
        scope.launch(dispatchers.io) {
            preferencesRepository.updatePreferences(selectedShader = shader.apiId).onFailure {
                _state.update { it.copy(selectedShader = previous) }
            }
        }
    }

    private fun selectConsoleShader(consoleId: String, shader: ShaderPreset) {
        val previous = _state.value.consoleShaders
        _state.update {
            it.copy(consoleShaders = it.consoleShaders + (consoleId to shader))
        }
        scope.launch(dispatchers.io) {
            preferencesRepository.updatePreferences(
                consoleShaders = mapOf(consoleId to shader.apiId),
            ).onFailure {
                _state.update { it.copy(consoleShaders = previous) }
            }
        }
    }

    private fun setDeviceOverride(consoleId: String, shader: ShaderPreset?) {
        preferencesRepository.setDeviceShaderOverride(consoleId, shader)
        _state.update {
            if (shader == null) {
                it.copy(deviceShaderOverrides = it.deviceShaderOverrides - consoleId)
            } else {
                it.copy(deviceShaderOverrides = it.deviceShaderOverrides + (consoleId to shader))
            }
        }
        // Sync device overrides to server
        scope.launch(dispatchers.io) {
            (preferencesRepository as? PreferencesRepositoryImpl)?.pushDeviceShaderOverridesToServer()
        }
    }

    private fun updateDeviceName(name: String) {
        _state.update { it.copy(deviceName = name) }
        deviceManager.setDeviceName(name)
        // Debounce server sync to avoid firing on every keystroke
        deviceNameSyncJob?.cancel()
        deviceNameSyncJob = scope.launch(dispatchers.io) {
            delay(500)
            deviceManager.updateDeviceNameOnServer(name)
        }
    }

    private fun clearCache() {
        scope.launch(dispatchers.io) {
            downloadRepository.clearCache()
            _state.update { it.copy(cacheSize = 0, showClearCacheConfirm = false) }
        }
    }
}
