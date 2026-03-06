package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.GameSession
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SessionCheatConfig
import com.spela.player.domain.repository.SessionRepository

class SessionRepositoryImpl(
    private val apiClient: SpelaApiClient,
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

    override suspend fun updateSession(sessionId: String, name: String): Result<GameSession> = runCatching {
        apiClient.updateSession(sessionId, name).toDomain()
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
    ): Result<SaveState> = runCatching {
        apiClient.uploadSessionSave(sessionId, name, data, screenshot).toDomain()
    }

    override suspend fun downloadSessionSave(sessionId: String, saveId: String): Result<ByteArray> = runCatching {
        apiClient.downloadSessionSave(sessionId, saveId)
    }

    override suspend fun uploadSessionAutoSave(
        sessionId: String,
        data: ByteArray,
        screenshot: ByteArray?,
    ): Result<Unit> = runCatching {
        apiClient.uploadSessionAutoSave(sessionId, data, screenshot)
    }

    override suspend fun downloadSessionAutoSave(sessionId: String): Result<ByteArray> = runCatching {
        apiClient.downloadSessionAutoSave(sessionId)
    }

    override suspend fun uploadSessionSram(sessionId: String, data: ByteArray): Result<Unit> = runCatching {
        apiClient.uploadSessionSram(sessionId, data)
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

    override suspend fun duplicateSession(sessionId: String, name: String?): Result<GameSession> = runCatching {
        apiClient.duplicateSession(sessionId, name).toDomain()
    }
}
