package com.spela.player.data.remote

import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

data class SyncState(
    val isSyncing: Boolean = false,
    val lastSyncedAt: Instant? = null,
)

class SyncEngine(
    private val connectivityMonitor: ConnectivityMonitor,
    private val preferencesRepository: PreferencesRepository,
    private val gameRepository: GameRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private var collectJob: Job? = null

    fun start() {
        // Observe reconnection events. Cancel any previous collector
        // first so re-calling start (server switch, re-login) doesn't
        // stack collectors and fire syncAll N times per reconnect.
        collectJob?.cancel()
        collectJob = scope.launch(dispatchers.io) {
            connectivityMonitor.onReconnect.collect {
                syncAll()
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
    }

    suspend fun syncAll() {
        _syncState.value = _syncState.value.copy(isSyncing = true)

        try {
            refreshCaches()

            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncedAt = Clock.System.now(),
            )
        } catch (_: Exception) {
            _syncState.value = _syncState.value.copy(isSyncing = false)
        }
    }

    private suspend fun refreshCaches() {
        // Trigger cache refresh by fetching data (the repositories cache on success)
        runCatching { preferencesRepository.getPreferences() }
        runCatching { gameRepository.getConsoles() }
    }
}
