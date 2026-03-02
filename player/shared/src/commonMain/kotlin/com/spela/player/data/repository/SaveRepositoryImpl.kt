package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.QuickSaveSlot
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.StorageUsage
import com.spela.player.domain.repository.SaveRepository
import com.spela.player.util.FileStorage
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SaveRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val database: SpelaDatabase,
    private val fileStorage: FileStorage,
    private val connectivityMonitor: ConnectivityMonitor,
) : SaveRepository {

    override suspend fun getSaveStates(gameId: String): Result<List<SaveState>> {
        return runCatching {
            val serverSaves = if (connectivityMonitor.isOnline.value) {
                runCatching {
                    apiClient.getSaveStates(gameId).map { it.toDomain() }
                        .map { it.copy(screenshotUrl = apiClient.resolveAuthenticatedUrl(it.screenshotUrl)) }
                }.getOrNull()
            } else null

            val localSaves = database.spelaDatabaseQueries
                .getLocalSaveStatesForGame(gameId)
                .executeAsList()
                .map { it.toSaveState() }

            if (serverSaves != null) {
                // Merge: server saves + local-only saves (those without a server_id)
                val serverIds = serverSaves.map { it.id }.toSet()
                val localOnly = localSaves.filter { it.id !in serverIds }
                serverSaves + localOnly
            } else {
                localSaves
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray, coreName: String?): Result<SaveState> {
        return uploadSaveStateWithScreenshot(gameId, name, data, null, coreName)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun uploadSaveStateWithScreenshot(gameId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String?): Result<SaveState> {
        // Save locally first
        val localResult = saveLocally(gameId, name, data, isAuto = false)
        val localSave = localResult.getOrNull() ?: return localResult

        // Try server upload if online
        if (connectivityMonitor.isOnline.value) {
            runCatching {
                val serverSave = apiClient.uploadSaveStateWithScreenshot(gameId, name, data, screenshot, coreName).toDomain()
                    .let { it.copy(screenshotUrl = apiClient.resolveAuthenticatedUrl(it.screenshotUrl)) }
                markSynced(localSave.id.toString(), serverSave.id, Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()))
                return Result.success(serverSave)
            }
        }

        return Result.success(localSave)
    }

    override suspend fun downloadSaveState(gameId: String, saveId: String): Result<ByteArray> {
        // Try local first
        val localEntity = database.spelaDatabaseQueries
            .getLocalSaveStatesForGame(gameId)
            .executeAsList()
            .find { it.id == saveId || it.server_id?.toString() == saveId }

        if (localEntity != null) {
            runCatching {
                return Result.success(fileStorage.readFile(localEntity.local_path))
            }
        }

        // Fall back to server
        return runCatching { apiClient.downloadSaveState(gameId, saveId) }
    }

    override suspend fun deleteSaveState(gameId: String, saveId: String): Result<Unit> {
        // Delete local
        val localEntity = database.spelaDatabaseQueries
            .getLocalSaveStatesForGame(gameId)
            .executeAsList()
            .find { it.id == saveId || it.server_id?.toString() == saveId }

        if (localEntity != null) {
            runCatching { fileStorage.deleteFile(localEntity.local_path) }
            database.spelaDatabaseQueries.deleteLocalSaveState(localEntity.id)
        }

        // Delete from server if online
        if (connectivityMonitor.isOnline.value) {
            val serverId = localEntity?.server_id?.toString() ?: saveId
            return runCatching { apiClient.deleteSaveState(gameId, serverId) }
        }

        return Result.success(Unit)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun uploadAutoSave(gameId: String, data: ByteArray, coreName: String?): Result<SaveState> {
        return uploadAutoSaveWithScreenshot(gameId, data, null, coreName)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun uploadAutoSaveWithScreenshot(gameId: String, data: ByteArray, screenshot: ByteArray?, coreName: String?): Result<SaveState> {
        // Save locally first
        val localResult = saveLocally(gameId, "Auto Save", data, isAuto = true)
        val localSave = localResult.getOrNull() ?: return localResult

        // Try server upload if online
        if (connectivityMonitor.isOnline.value) {
            runCatching {
                val serverSave = apiClient.uploadAutoSaveWithScreenshot(gameId, data, screenshot, coreName).toDomain()
                    .let { it.copy(screenshotUrl = apiClient.resolveAuthenticatedUrl(it.screenshotUrl)) }
                markSynced(localSave.id.toString(), serverSave.id, Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()))
                return Result.success(serverSave)
            }
        }

        return Result.success(localSave)
    }

    override suspend fun downloadAutoSave(gameId: String): Result<ByteArray> {
        // Try local auto-save first
        val localResult = loadLocalAutoSave(gameId)
        if (localResult.isSuccess) return localResult

        // Fall back to server
        if (connectivityMonitor.isOnline.value) {
            return runCatching { apiClient.downloadAutoSave(gameId) }
        }

        return Result.failure(IllegalStateException("No auto-save available offline"))
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun saveLocally(gameId: String, name: String, data: ByteArray, isAuto: Boolean): Result<SaveState> =
        runCatching {
            val localId = Uuid.random().toString()
            val dir = "${fileStorage.getSavesDir()}/savestates/$gameId"
            val path = "$dir/$localId.sav"
            val nowMillis = Clock.System.now().toEpochMilliseconds()

            fileStorage.createDirectory(dir)
            fileStorage.writeFile(path, data)

            // For auto-saves, delete previous local auto-save for this game
            if (isAuto) {
                val oldAuto = database.spelaDatabaseQueries.getLocalAutoSave(gameId).executeAsOneOrNull()
                if (oldAuto != null) {
                    runCatching { fileStorage.deleteFile(oldAuto.local_path) }
                    database.spelaDatabaseQueries.deleteLocalSaveState(oldAuto.id)
                }
            }

            database.spelaDatabaseQueries.insertLocalSaveState(
                id = localId,
                game_id = gameId,
                name = name,
                local_path = path,
                file_size = data.size.toLong(),
                is_auto = if (isAuto) 1L else 0L,
                created_at = nowMillis,
                updated_at = nowMillis,
                server_id = null,
                sync_status = "pending",
                last_synced_at = null,
            )

            SaveState(
                id = localId.hashCode().toLong(),
                gameId = gameId.toLongOrNull() ?: 0L,
                name = name,
                createdAt = Instant.fromEpochMilliseconds(nowMillis),
                fileSize = data.size.toLong(),
                isAuto = isAuto,
                isSynced = false,
            )
        }

    override suspend fun loadLocalAutoSave(gameId: String): Result<ByteArray> = runCatching {
        val entity = database.spelaDatabaseQueries.getLocalAutoSave(gameId).executeAsOneOrNull()
            ?: throw IllegalStateException("No local auto-save found")
        fileStorage.readFile(entity.local_path)
    }

    override suspend fun getPendingSyncCount(): Int {
        return database.spelaDatabaseQueries.getPendingSaveStateSyncs().executeAsList().size
    }

    // Feature 2: Rename
    override suspend fun renameSaveState(gameId: String, saveId: String, name: String): Result<Unit> = runCatching {
        apiClient.renameSaveState(gameId, saveId, name)
    }

    // Feature 4: Notes
    override suspend fun updateSaveNotes(gameId: String, saveId: String, notes: String): Result<Unit> = runCatching {
        apiClient.updateSaveNotes(gameId, saveId, notes)
    }

    // Feature 5: Quick-save slots
    override suspend fun saveToSlot(gameId: String, slot: Int, data: ByteArray, screenshot: ByteArray?, coreName: String?): Result<SaveState> = runCatching {
        apiClient.saveToSlot(gameId, slot, data, screenshot, coreName).toDomain()
            .let { it.copy(screenshotUrl = apiClient.resolveAuthenticatedUrl(it.screenshotUrl)) }
    }

    override suspend fun loadFromSlot(gameId: String, slot: Int): Result<ByteArray> = runCatching {
        apiClient.loadFromSlot(gameId, slot)
    }

    override suspend fun getSlots(gameId: String): Result<List<QuickSaveSlot>> = runCatching {
        val saves = apiClient.getSlots(gameId).map { it.toDomain() }
            .map { it.copy(screenshotUrl = apiClient.resolveAuthenticatedUrl(it.screenshotUrl)) }
        (1..10).map { slotNum ->
            QuickSaveSlot(
                slot = slotNum,
                saveState = saves.find { it.slot == slotNum },
            )
        }
    }

    // Feature 6: Auto-save history
    override suspend fun getAutoSaveHistory(gameId: String): Result<List<SaveState>> = runCatching {
        apiClient.getAutoSaveHistory(gameId).map { it.toDomain() }
            .map { it.copy(screenshotUrl = apiClient.resolveAuthenticatedUrl(it.screenshotUrl)) }
    }

    // Feature 7: Bulk delete
    override suspend fun bulkDeleteSaves(gameId: String, saveIds: List<Long>): Result<Int> = runCatching {
        // Also clean up local entries
        saveIds.forEach { saveId ->
            val saveIdStr = saveId.toString()
            val localEntity = database.spelaDatabaseQueries
                .getLocalSaveStatesForGame(gameId)
                .executeAsList()
                .find { it.id == saveIdStr || it.server_id == saveId }
            if (localEntity != null) {
                runCatching { fileStorage.deleteFile(localEntity.local_path) }
                database.spelaDatabaseQueries.deleteLocalSaveState(localEntity.id)
            }
        }
        apiClient.bulkDeleteSaves(gameId, saveIds)
    }

    // Feature 8: Storage usage
    override suspend fun getStorageUsage(): Result<StorageUsage> = runCatching {
        apiClient.getStorageUsage().toDomain()
    }

    // Feature 9: Import
    override suspend fun importSaveState(gameId: String, name: String, fileData: ByteArray): Result<SaveState> = runCatching {
        apiClient.importSaveState(gameId, name, fileData).toDomain()
            .let { it.copy(screenshotUrl = apiClient.resolveAuthenticatedUrl(it.screenshotUrl)) }
    }

    private fun markSynced(localId: String, serverId: Long, syncedAt: Instant) {
        database.spelaDatabaseQueries.markSaveStateSynced(
            server_id = serverId,
            last_synced_at = syncedAt.toEpochMilliseconds(),
            id = localId,
        )
    }

    private fun com.spela.player.LocalSaveStateEntity.toSaveState(): SaveState = SaveState(
        id = server_id ?: id.hashCode().toLong(),
        gameId = game_id.toLongOrNull() ?: 0L,
        name = name,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        fileSize = file_size,
        isAuto = is_auto != 0L,
        isSynced = sync_status == "synced",
    )
}
