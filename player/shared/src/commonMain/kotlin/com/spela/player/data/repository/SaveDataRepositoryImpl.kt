package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.SaveData
import com.spela.player.domain.repository.SaveDataRepository
import com.spela.player.util.FileStorage
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SaveDataRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val database: SpelaDatabase,
    private val fileStorage: FileStorage,
    private val connectivityMonitor: ConnectivityMonitor,
) : SaveDataRepository {

    override suspend fun getSaveDataList(gameId: String): Result<List<SaveData>> {
        return runCatching {
            if (connectivityMonitor.isOnline.value) {
                apiClient.getSaveDataList(gameId).map { it.toDomain() }
            } else {
                database.spelaDatabaseQueries
                    .getLocalSaveDataForGame(gameId)
                    .executeAsList()
                    .map { it.toSaveData() }
            }
        }
    }

    override suspend fun uploadActiveSaveData(gameId: String, data: ByteArray): Result<SaveData> {
        return runCatching {
            if (connectivityMonitor.isOnline.value) {
                apiClient.uploadActiveSaveData(gameId, data).toDomain()
            } else {
                saveLocalSRAM(gameId, data)
                SaveData(
                    id = 0,
                    gameId = gameId.toLongOrNull() ?: 0L,
                    name = "Active Save Data",
                    fileSize = data.size.toLong(),
                    isActive = true,
                    updatedAt = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
                )
            }
        }
    }

    override suspend fun downloadActiveSaveData(gameId: String): Result<ByteArray> {
        return runCatching {
            if (connectivityMonitor.isOnline.value) {
                val data = apiClient.downloadActiveSaveData(gameId)
                // Cache locally
                saveLocalSRAM(gameId, data)
                data
            } else {
                loadLocalSRAM(gameId) ?: throw IllegalStateException("No local SRAM available")
            }
        }
    }

    override suspend fun downloadSaveData(gameId: String, saveDataId: String): Result<ByteArray> =
        runCatching {
            apiClient.downloadSaveData(gameId, saveDataId)
        }

    override suspend fun activateSaveData(gameId: String, saveDataId: String): Result<Unit> =
        runCatching {
            apiClient.activateSaveData(gameId, saveDataId)
        }

    override suspend fun renameSaveData(gameId: String, saveDataId: String, name: String): Result<Unit> =
        runCatching {
            apiClient.renameSaveData(gameId, saveDataId, name)
        }

    override suspend fun deleteSaveData(gameId: String, saveDataId: String): Result<Unit> =
        runCatching {
            apiClient.deleteSaveData(gameId, saveDataId)
        }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun saveLocalSRAM(gameId: String, data: ByteArray) {
        val dir = "${fileStorage.getSavesDir()}/sram/$gameId"
        val path = "$dir/active.srm"
        fileStorage.createDirectory(dir)
        fileStorage.writeFile(path, data)

        val now = Clock.System.now()
        // Upsert active local save data entry
        val existing = database.spelaDatabaseQueries
            .getActiveLocalSaveData(gameId)
            .executeAsOneOrNull()

        val id = existing?.id ?: Uuid.random().toString()
        database.spelaDatabaseQueries.insertLocalSaveData(
            id = id,
            game_id = gameId,
            name = "Active Save Data",
            local_path = path,
            file_size = data.size.toLong(),
            is_active = 1L,
            created_at = existing?.created_at ?: now.toEpochMilliseconds(),
            updated_at = now.toEpochMilliseconds(),
            server_id = existing?.server_id,
            sync_status = "pending",
        )
    }

    override suspend fun loadLocalSRAM(gameId: String): ByteArray? {
        val path = "${fileStorage.getSavesDir()}/sram/$gameId/active.srm"
        return if (fileStorage.fileExists(path)) {
            runCatching { fileStorage.readFile(path) }.getOrNull()
        } else null
    }

    override suspend fun getPendingSyncCount(): Int {
        return database.spelaDatabaseQueries.getPendingSaveDataSyncs().executeAsList().size
    }

    private fun com.spela.player.LocalSaveDataEntity.toSaveData(): SaveData = SaveData(
        id = server_id ?: id.hashCode().toLong(),
        gameId = game_id.toLongOrNull() ?: 0L,
        name = name,
        fileSize = file_size,
        isActive = is_active != 0L,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
    )
}
