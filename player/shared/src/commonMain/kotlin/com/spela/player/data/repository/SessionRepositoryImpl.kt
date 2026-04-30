package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.GameSession
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SessionCheatConfig
import com.spela.player.domain.repository.SessionRepository
import com.spela.player.util.FileStorage

class SessionRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val fileStorage: FileStorage,
) : SessionRepository {

    override suspend fun getSessionsForGame(gameId: String): Result<List<GameSession>> = runCatching {
        apiClient.getSessionsForGame(gameId).map { it.toDomain() }
    }

    override suspend fun getSession(sessionId: String): Result<GameSession> = runCatching {
        apiClient.getSession(sessionId).toDomain()
    }

    override suspend fun createSession(gameId: String, name: String): Result<GameSession> = runCatching {
        apiClient.createSession(gameId, name).toDomain()
    }

    override suspend fun createSessionFromSharedSave(gameId: String, saveId: String): Result<GameSession> = runCatching {
        apiClient.createSessionFromSharedSave(gameId, saveId).toDomain()
    }

    override suspend fun updateSession(sessionId: String, name: String?, coreName: String?): Result<GameSession> = runCatching {
        apiClient.updateSession(sessionId, name, coreName).toDomain()
    }

    override suspend fun updateSessionCoreFlags(
        sessionId: String,
        userLockedCoreVersion: Boolean?,
        autoLoadSuppressed: Boolean?,
        rehearsalCrashPending: Boolean?,
    ): Result<GameSession> = runCatching {
        apiClient.updateSessionCoreFlags(
            sessionId,
            userLockedCoreVersion = userLockedCoreVersion,
            autoLoadSuppressed = autoLoadSuppressed,
            rehearsalCrashPending = rehearsalCrashPending,
        ).toDomain()
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        apiClient.deleteSession(sessionId)
    }

    override suspend fun getSessionSaves(sessionId: String): Result<List<SaveState>> = runCatching {
        apiClient.getSessionSaves(sessionId).map { it.toDomain() }
    }

    override suspend fun uploadSessionSave(
        sessionId: String,
        name: String,
        data: ByteArray,
        screenshot: ByteArray?,
        coreName: String,
    ): Result<SaveState> = runCatching {
        apiClient.uploadSessionSave(sessionId, name, data, screenshot, coreName).toDomain()
    }

    override suspend fun uploadSessionSaveFromFile(
        sessionId: String,
        name: String,
        savePath: String,
        saveSize: Long,
        screenshot: ByteArray?,
        coreName: String,
        compression: String,
    ): Result<SaveState> = runCatching {
        apiClient.uploadSessionSaveFromFile(sessionId, name, savePath, saveSize, screenshot, coreName, compression).toDomain()
    }

    override suspend fun downloadSessionSave(sessionId: String, saveId: String): Result<ByteArray> = runCatching {
        apiClient.downloadSessionSave(sessionId, saveId)
    }

    override suspend fun uploadSessionAutoSave(
        sessionId: String,
        data: ByteArray,
        screenshot: ByteArray?,
        coreName: String,
    ): Result<Unit> = runCatching {
        apiClient.uploadSessionAutoSave(sessionId, data, screenshot, coreName)
    }

    override suspend fun uploadSessionAutoSaveFromFile(
        sessionId: String,
        savePath: String,
        saveSize: Long,
        screenshot: ByteArray?,
        coreName: String,
        compression: String,
    ): Result<Unit> = runCatching {
        apiClient.uploadSessionAutoSaveFromFile(sessionId, savePath, saveSize, screenshot, coreName, compression)
    }

    override suspend fun downloadSessionAutoSave(sessionId: String): Result<ByteArray> = runCatching {
        apiClient.downloadSessionAutoSave(sessionId)
    }

    override suspend fun downloadSessionAutoSaveToFile(sessionId: String, outputPath: String): Result<Unit> = runCatching {
        apiClient.downloadSessionAutoSaveToFile(sessionId, fileStorage, outputPath)
    }

    override suspend fun uploadSlotSave(
        sessionId: String,
        slot: Int,
        data: ByteArray,
        screenshot: ByteArray?,
        coreName: String,
    ): Result<SaveState> = runCatching {
        apiClient.uploadSlotSave(sessionId, slot, data, screenshot, coreName).toDomain()
    }

    override suspend fun uploadSlotSaveFromFile(
        sessionId: String,
        slot: Int,
        savePath: String,
        saveSize: Long,
        screenshot: ByteArray?,
        coreName: String,
        compression: String,
    ): Result<SaveState> = runCatching {
        apiClient.uploadSlotSaveFromFile(sessionId, slot, savePath, saveSize, screenshot, coreName, compression).toDomain()
    }

    override suspend fun downloadSlotSave(sessionId: String, slot: Int): Result<ByteArray> = runCatching {
        apiClient.downloadSlotSave(sessionId, slot)
    }

    override suspend fun uploadSessionSram(sessionId: String, data: ByteArray, coreName: String): Result<Unit> = runCatching {
        apiClient.uploadSessionSram(sessionId, data, coreName)
    }

    override suspend fun downloadSessionSram(sessionId: String): Result<ByteArray> = runCatching {
        apiClient.downloadSessionSram(sessionId)
    }

    override suspend fun getSessionCheats(sessionId: String): Result<SessionCheatConfig> = runCatching {
        apiClient.getSessionCheats(sessionId).toDomain()
    }

    override suspend fun updateSessionCheats(
        sessionId: String,
        cheatsEnabled: Boolean,
        enabledIndices: List<Int>,
    ): Result<SessionCheatConfig> = runCatching {
        apiClient.updateSessionCheats(sessionId, cheatsEnabled, enabledIndices).toDomain()
    }

    override suspend fun cloneSession(sessionId: String, name: String?, saveId: Long?): Result<GameSession> = runCatching {
        apiClient.cloneSession(sessionId, name, saveId).toDomain()
    }
}
