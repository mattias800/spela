package com.spela.player.presentation.viewmodel

import com.spela.player.data.device.DeviceManager
import com.spela.player.data.remote.ConnectionState
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.SyncEngine
import com.spela.player.data.remote.SyncState
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.DeviceDto
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.RAStatus
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.repository.AchievementsRepository
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.domain.repository.ServerRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val userId: String = "",
    val username: String = "",
    val serverUrl: String = "",
    val deviceName: String = "",
    val cacheSize: Long = 0,
    val showPerformanceOverlay: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val autoLoadSaveEnabled: Boolean = true,
    val autoUpdateCoresEnabled: Boolean = true,
    val selectedShader: ShaderPreset = ShaderPreset.NONE,
    val selectedTheme: String = "default-dark",
    val consoleShaders: Map<String, ShaderPreset> = emptyMap(),
    /**
     * Per-console save-state opt-out overrides, keyed by lowercased
     * console abbreviation. Only contains consoles where the user has
     * made a deliberate choice — absence means "use the tier default"
     * (small/medium = Enabled, large = AskOnce). The Settings screen
     * lets the user clear or change these. See #804 phase 4b.
     */
    val consoleSaveStatePolicies: Map<String, com.spela.player.domain.model.SaveStateChoice> = emptyMap(),
    val deviceShaderOverrides: Map<String, ShaderPreset> = emptyMap(),
    val consoles: List<Console> = emptyList(),
    val showLogoutConfirm: Boolean = false,
    /**
     * Set to true after [SettingsIntent.Logout] has finished clearing
     * the auth tokens. Consumers (the navigator) should observe this
     * to navigate away from authenticated screens — navigating before
     * tokens clear races against the in-memory cache and lets the next
     * screen auto-login back into Home.
     */
    val loggedOut: Boolean = false,
    val showClearCacheConfirm: Boolean = false,
    val fullscreenPreviewConsoleId: String? = null,
    val raStatus: RAStatus? = null,
    val showRALinkDialog: Boolean = false,
    val raLinkLoading: Boolean = false,
    val raLinkError: String? = null,
    val devices: List<DeviceDto> = emptyList(),
    val isLoadingDevices: Boolean = false,
    val showDeleteDeviceConfirm: Long? = null,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val orientationLock: String = "auto",
    val defaultSecondScreenPage: String = "art",
)

sealed interface SettingsIntent {
    data object LoadSettings : SettingsIntent
    data object TogglePerformanceOverlay : SettingsIntent
    data object ToggleAutoSave : SettingsIntent
    data object ToggleAutoLoadSave : SettingsIntent
    data object ToggleAutoUpdateCores : SettingsIntent
    data class SelectShader(val shader: ShaderPreset) : SettingsIntent
    data class SelectTheme(val theme: String) : SettingsIntent
    data class SelectConsoleShader(val consoleId: String, val shader: ShaderPreset) : SettingsIntent
    /**
     * Set a per-console save-state opt-out from the Settings screen.
     * `choice == null` clears the override so the console reverts to
     * its tier-driven default. See #804 phase 4b.
     */
    data class SetConsoleSaveStatePolicy(
        val consoleId: String,
        val choice: com.spela.player.domain.model.SaveStateChoice?,
    ) : SettingsIntent
    data class SetDeviceOverride(val consoleId: String, val shader: ShaderPreset?) : SettingsIntent
    data class UpdateDeviceName(val name: String) : SettingsIntent
    data object ShowLogoutConfirm : SettingsIntent
    data object DismissLogoutConfirm : SettingsIntent
    data object Logout : SettingsIntent
    data object ShowClearCacheConfirm : SettingsIntent
    data object DismissClearCacheConfirm : SettingsIntent
    data object ClearCache : SettingsIntent
    data class ShowShaderPreviewFullscreen(val consoleId: String) : SettingsIntent
    data object DismissShaderPreviewFullscreen : SettingsIntent

    data object ShowRALinkDialog : SettingsIntent
    data object DismissRALinkDialog : SettingsIntent
    data class LinkRA(val username: String, val password: String) : SettingsIntent
    data object UnlinkRA : SettingsIntent
    data object ToggleRAHardcore : SettingsIntent
    data object LoadDevices : SettingsIntent
    data class RenameDevice(val deviceId: Long, val newName: String) : SettingsIntent
    data class DeleteDevice(val deviceId: Long) : SettingsIntent
    data class ShowDeleteDeviceConfirm(val deviceId: Long) : SettingsIntent
    data object DismissDeleteDeviceConfirm : SettingsIntent
    data class SaveScrollPosition(val index: Int, val offset: Int) : SettingsIntent
    data object SyncNow : SettingsIntent
    data class SetOrientationLock(val mode: String) : SettingsIntent
    data class SelectDefaultSecondScreenPage(val page: String) : SettingsIntent
}

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesRepository: PreferencesRepository,
    private val gameRepository: GameRepository,
    private val serverRepository: ServerRepository,
    private val achievementsRepository: AchievementsRepository,
    private val keyMappingRepository: KeyMappingRepository,
    private val deviceManager: DeviceManager,
    private val syncEngine: SyncEngine,
    private val connectivityMonitor: ConnectivityMonitor,
    private val apiClient: SpelaApiClient,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val selectedTheme: StateFlow<String> = _state
        .map { it.selectedTheme }
        .distinctUntilChanged()
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "default-dark")

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
            SettingsIntent.ToggleAutoUpdateCores -> togglePreference(
                currentValue = { it.autoUpdateCoresEnabled },
                optimisticUpdate = { s, v -> s.copy(autoUpdateCoresEnabled = v) },
                apiCall = { preferencesRepository.updatePreferences(autoUpdateCoresEnabled = it) },
            )
            is SettingsIntent.SelectShader -> selectShader(intent.shader)
            is SettingsIntent.SelectTheme -> selectTheme(intent.theme)
            is SettingsIntent.SelectConsoleShader ->
                selectConsoleShader(intent.consoleId, intent.shader)
            is SettingsIntent.SetConsoleSaveStatePolicy ->
                setConsoleSaveStatePolicy(intent.consoleId, intent.choice)
            is SettingsIntent.SetDeviceOverride ->
                setDeviceOverride(intent.consoleId, intent.shader)
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
            is SettingsIntent.ShowShaderPreviewFullscreen ->
                _state.update { it.copy(fullscreenPreviewConsoleId = intent.consoleId) }
            SettingsIntent.DismissShaderPreviewFullscreen ->
                _state.update { it.copy(fullscreenPreviewConsoleId = null) }

            SettingsIntent.ShowRALinkDialog ->
                _state.update { it.copy(showRALinkDialog = true, raLinkError = null) }
            SettingsIntent.DismissRALinkDialog ->
                _state.update { it.copy(showRALinkDialog = false, raLinkError = null) }
            is SettingsIntent.LinkRA -> linkRA(intent.username, intent.password)
            SettingsIntent.UnlinkRA -> unlinkRA()
            SettingsIntent.ToggleRAHardcore -> toggleRAHardcore()
            SettingsIntent.LoadDevices -> loadDevices()
            is SettingsIntent.RenameDevice -> renameDevice(intent.deviceId, intent.newName)
            is SettingsIntent.DeleteDevice -> deleteDevice(intent.deviceId)
            is SettingsIntent.ShowDeleteDeviceConfirm ->
                _state.update { it.copy(showDeleteDeviceConfirm = intent.deviceId) }
            SettingsIntent.DismissDeleteDeviceConfirm ->
                _state.update { it.copy(showDeleteDeviceConfirm = null) }
            is SettingsIntent.SaveScrollPosition ->
                _state.update { it.copy(scrollIndex = intent.index, scrollOffset = intent.offset) }
            SettingsIntent.SyncNow -> syncNow()
            is SettingsIntent.SetOrientationLock -> setOrientationLock(intent.mode)
            is SettingsIntent.SelectDefaultSecondScreenPage -> selectDefaultSecondScreenPage(intent.page)
        }
    }

    val syncState: StateFlow<SyncState> = syncEngine.syncState
    val isOnline: StateFlow<Boolean> = connectivityMonitor.isOnline
    val connectionState: StateFlow<ConnectionState> = connectivityMonitor.connectionState

    private fun setOrientationLock(mode: String) {
        preferencesRepository.setOrientationLock(mode)
        _state.update { it.copy(orientationLock = mode) }
    }

    private fun syncNow() {
        scope.launch(dispatchers.io) {
            syncEngine.syncAll()
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
            val activeServer = serverRepository.getActiveServer()
            val orientationLock = preferencesRepository.getOrientationLock()
            _state.update {
                it.copy(
                    userId = user?.id ?: "",
                    username = user?.username ?: "",
                    serverUrl = activeServer?.url?.trimEnd('/') ?: "",
                    cacheSize = cacheSize,
                    deviceName = deviceName,
                    orientationLock = orientationLock,
                )
            }

            preferencesRepository.getPreferences().onSuccess { prefs ->
                _state.update {
                    it.copy(
                        showPerformanceOverlay = prefs.showPerformanceOverlay,
                        autoSaveEnabled = prefs.autoSaveEnabled,
                        autoLoadSaveEnabled = prefs.autoLoadSaveEnabled,
                        autoUpdateCoresEnabled = prefs.autoUpdateCoresEnabled,
                        selectedShader = prefs.selectedShader,
                        selectedTheme = prefs.selectedTheme,
                        consoleShaders = prefs.consoleShaders,
                        consoleSaveStatePolicies = prefs.consoleSaveStatePolicies,
                        defaultSecondScreenPage = prefs.defaultSecondScreenPage,
                    )
                }
            }

            gameRepository.getConsoles().onSuccess { consoles ->
                _state.update { it.copy(consoles = consoles) }
            }

            // Sync key mappings from server (populates local DB on first device)
            preferencesRepository.syncKeyMappingsFromServer()
            // Auto-detect and apply defaults if no mappings exist yet
            keyMappingRepository.ensureDefaultsApplied()

            // Sync device shader overrides from server, then load local
            preferencesRepository.syncDeviceShaderOverrides()
            val deviceOverrides = preferencesRepository.getAllDeviceShaderOverrides()
            _state.update { it.copy(deviceShaderOverrides = deviceOverrides) }

            achievementsRepository.getRAStatus().onSuccess { status ->
                _state.update { it.copy(raStatus = status) }
            }

            loadDevices()
        }
    }

    private fun logout() {
        // Hide the confirm dialog immediately so the UI doesn't look
        // wedged while clearTokens runs.
        _state.update { it.copy(showLogoutConfirm = false) }
        scope.launch(dispatchers.io) {
            authRepository.clearTokens()
            // Tokens are gone — only now is it safe to navigate away
            // from authenticated screens. Consumers observe loggedOut.
            _state.update { it.copy(loggedOut = true) }
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

    private fun selectTheme(theme: String) {
        val previous = _state.value.selectedTheme
        _state.update { it.copy(selectedTheme = theme) }
        scope.launch(dispatchers.io) {
            preferencesRepository.updatePreferences(selectedTheme = theme).onFailure {
                _state.update { it.copy(selectedTheme = previous) }
            }
        }
    }

    private fun selectDefaultSecondScreenPage(page: String) {
        val previous = _state.value.defaultSecondScreenPage
        _state.update { it.copy(defaultSecondScreenPage = page) }
        scope.launch(dispatchers.io) {
            preferencesRepository.updatePreferences(defaultSecondScreenPage = page).onFailure {
                _state.update { it.copy(defaultSecondScreenPage = previous) }
            }
        }
    }

    /**
     * Upserts (or clears) the per-console save-state opt-out from the
     * Settings screen. Optimistic update with rollback on API failure
     * — same pattern as [selectConsoleShader]. The wire format sends
     * an empty string to clear the row server-side. See #804 phase 4b.
     */
    private fun setConsoleSaveStatePolicy(
        consoleId: String,
        choice: com.spela.player.domain.model.SaveStateChoice?,
    ) {
        val key = consoleId.lowercase()
        val previous = _state.value.consoleSaveStatePolicies
        _state.update {
            it.copy(
                consoleSaveStatePolicies = if (choice == null) {
                    it.consoleSaveStatePolicies - key
                } else {
                    it.consoleSaveStatePolicies + (key to choice)
                },
            )
        }
        scope.launch(dispatchers.io) {
            preferencesRepository.updatePreferences(
                consoleSaveStatePolicies = mapOf(key to (choice?.apiId ?: "")),
            ).onFailure {
                _state.update { it.copy(consoleSaveStatePolicies = previous) }
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
            preferencesRepository.pushDeviceShaderOverridesToServer()
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

    private fun linkRA(username: String, password: String) {
        _state.update { it.copy(raLinkLoading = true, raLinkError = null) }
        scope.launch(dispatchers.io) {
            achievementsRepository.linkRA(username, password).fold(
                onSuccess = { status ->
                    _state.update {
                        it.copy(
                            raStatus = status,
                            showRALinkDialog = false,
                            raLinkLoading = false,
                            raLinkError = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            raLinkLoading = false,
                            raLinkError = error.message ?: "Failed to link account",
                        )
                    }
                },
            )
        }
    }

    private fun unlinkRA() {
        scope.launch(dispatchers.io) {
            achievementsRepository.unlinkRA().onSuccess {
                _state.update { it.copy(raStatus = RAStatus()) }
            }
        }
    }

    private fun toggleRAHardcore() {
        val current = _state.value.raStatus ?: return
        val newValue = !current.hardcoreEnabled
        _state.update { it.copy(raStatus = current.copy(hardcoreEnabled = newValue)) }
        scope.launch(dispatchers.io) {
            achievementsRepository.updateRASettings(newValue).onFailure {
                _state.update { it.copy(raStatus = current) }
            }
        }
    }

    private fun loadDevices() {
        _state.update { it.copy(isLoadingDevices = true) }
        scope.launch(dispatchers.io) {
            runCatching { apiClient.getDevices() }.fold(
                onSuccess = { devices ->
                    _state.update { it.copy(devices = devices, isLoadingDevices = false) }
                },
                onFailure = {
                    _state.update { it.copy(isLoadingDevices = false) }
                },
            )
        }
    }

    private fun renameDevice(deviceId: Long, newName: String) {
        scope.launch(dispatchers.io) {
            runCatching { apiClient.updateDevice(deviceId, newName) }.onSuccess {
                loadDevices()
            }
        }
    }

    private fun deleteDevice(deviceId: Long) {
        _state.update { it.copy(showDeleteDeviceConfirm = null) }
        scope.launch(dispatchers.io) {
            runCatching { apiClient.deleteDevice(deviceId) }.onSuccess {
                loadDevices()
            }
        }
    }
}
