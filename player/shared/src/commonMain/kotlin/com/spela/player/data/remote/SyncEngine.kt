package com.spela.player.data.remote

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.domain.repository.SaveDataRepository
import com.spela.player.domain.repository.SaveRepository
import com.spela.player.util.DispatcherProvider
import com.spela.player.util.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.Instant as KdtInstant

data class SyncState(
    val isSyncing: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val pendingSaveStates: Int = 0,
    val pendingSaveData: Int = 0,
)

class SyncEngine(
    private val connectivityMonitor: ConnectivityMonitor,
    private val saveRepository: SaveRepository,
    private val saveDataRepository: SaveDataRepository,
    private val preferencesRepository: PreferencesRepository,
    private val gameRepository: GameRepository,
    private val apiClient: SpelaApiClient,
    private val database: SpelaDatabase,
    private val fileStorage: FileStorage,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun start() {
        // Observe reconnection events
        scope.launch(dispatchers.io) {
            connectivityMonitor.onReconnect.collect {
                syncAll()
            }
        }

        // Update pending counts periodically
        scope.launch(dispatchers.io) {
            updatePendingCounts()
        }
    }

    suspend fun syncAll() {
        _syncState.value = _syncState.value.copy(isSyncing = true)

        try {
            syncPendingSaveStates()
            syncPendingSaveData()
            refreshCaches()

            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncedAt = Clock.System.now(),
            )
        } catch (_: Exception) {
            _syncState.value = _syncState.value.copy(isSyncing = false)
        }

        updatePendingCounts()
    }

    private suspend fun syncPendingSaveStates() {
        val pendingSaves = database.spelaDatabaseQueries.getPendingSaveStateSyncs().executeAsList()

        for (save in pendingSaves) {
            try {
                val localData = fileStorage.readFile(save.local_path)
                val gameId = save.game_id

                if (save.is_auto != 0L) {
                    // Auto-save: handle conflict with server auto-save
                    val serverSaves = runCatching {
                        apiClient.getSaveStates(gameId).map { it.toDomain() }
                    }.getOrNull() ?: continue

                    val serverAutoSave = serverSaves.find { it.isAuto }

                    if (serverAutoSave != null) {
                        val localUpdated = KdtInstant.fromEpochMilliseconds(save.updated_at)
                        val serverUpdated = serverAutoSave.createdAt

                        if (serverUpdated != null && serverUpdated > localUpdated) {
                            // Server wins: upload local as backup, download server version
                            val timestamp = localUpdated.toString().take(19).replace('T', ' ')
                            apiClient.uploadSaveState(gameId, "Backup (offline) - $timestamp", localData)
                            val serverData = apiClient.downloadAutoSave(gameId)
                            fileStorage.writeFile(save.local_path, serverData)
                        } else {
                            // Local wins or equal: upload local as backup of server, then upload local as auto
                            if (serverUpdated != null) {
                                val serverData = runCatching { apiClient.downloadAutoSave(gameId) }.getOrNull()
                                if (serverData != null) {
                                    val timestamp = serverUpdated.toString().take(19).replace('T', ' ')
                                    apiClient.uploadSaveState(gameId, "Backup (server) - $timestamp", serverData)
                                }
                            }
                            apiClient.uploadAutoSave(gameId, localData)
                        }
                    } else {
                        // No server auto-save: simple upload
                        apiClient.uploadAutoSave(gameId, localData)
                    }
                } else {
                    // Named save: simple upload
                    apiClient.uploadSaveState(gameId, save.name, localData)
                }

                val serverSave = apiClient.getSaveStates(gameId).lastOrNull()
                database.spelaDatabaseQueries.markSaveStateSynced(
                    server_id = serverSave?.id?.toLong(),
                    last_synced_at = Clock.System.now().toEpochMilliseconds(),
                    id = save.id,
                )
            } catch (_: Exception) {
                // Skip this save and try next
            }
        }
    }

    private suspend fun syncPendingSaveData() {
        val pendingData = database.spelaDatabaseQueries.getPendingSaveDataSyncs().executeAsList()

        for (data in pendingData) {
            try {
                val localBytes = fileStorage.readFile(data.local_path)
                val gameId = data.game_id

                if (data.is_active != 0L) {
                    // Active SRAM: handle conflict
                    val serverList = runCatching {
                        apiClient.getSaveDataList(gameId).map { it.toDomain() }
                    }.getOrNull() ?: continue

                    val serverActive = serverList.find { it.isActive }

                    if (serverActive != null) {
                        val localUpdated = KdtInstant.fromEpochMilliseconds(data.updated_at)
                        val serverUpdated = serverActive.updatedAt

                        if (serverUpdated != null && serverUpdated > localUpdated) {
                            // Server wins
                            val timestamp = localUpdated.toString().take(19).replace('T', ' ')
                            apiClient.uploadSaveData(gameId, "Backup (offline) - $timestamp", localBytes)
                            val serverData = apiClient.downloadActiveSaveData(gameId)
                            fileStorage.writeFile(data.local_path, serverData)
                        } else {
                            // Local wins
                            if (serverUpdated != null) {
                                val serverData = runCatching { apiClient.downloadActiveSaveData(gameId) }.getOrNull()
                                if (serverData != null) {
                                    val timestamp = serverUpdated.toString().take(19).replace('T', ' ')
                                    apiClient.uploadSaveData(gameId, "Backup (server) - $timestamp", serverData)
                                }
                            }
                            apiClient.uploadActiveSaveData(gameId, localBytes)
                        }
                    } else {
                        apiClient.uploadActiveSaveData(gameId, localBytes)
                    }
                } else {
                    apiClient.uploadSaveData(gameId, data.name, localBytes)
                }

                val serverResult = apiClient.getSaveDataList(gameId).lastOrNull()
                database.spelaDatabaseQueries.markSaveDataSynced(
                    server_id = serverResult?.id,
                    id = data.id,
                )
            } catch (_: Exception) {
                // Skip this entry and try next
            }
        }
    }

    private suspend fun refreshCaches() {
        // Trigger cache refresh by fetching data (the repositories cache on success)
        runCatching { preferencesRepository.getPreferences() }
        runCatching { gameRepository.getConsoles() }
    }

    private fun updatePendingCounts() {
        val pendingSaves = database.spelaDatabaseQueries.getPendingSaveStateSyncs().executeAsList().size
        val pendingData = database.spelaDatabaseQueries.getPendingSaveDataSyncs().executeAsList().size
        _syncState.value = _syncState.value.copy(
            pendingSaveStates = pendingSaves,
            pendingSaveData = pendingData,
        )
    }
}
