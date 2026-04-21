package com.spela.player.data.repository

import com.spela.client.models.CreateNetplaySessionRequest
import com.spela.client.models.JoinByInviteCodeRequest
import com.spela.client.models.NetplayInviteUserRequest
import com.spela.client.models.UpdateNetplaySettingsRequest
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.NetplaySession
import com.spela.player.domain.repository.NetplayRepository

class NetplayRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : NetplayRepository {

    override suspend fun createSession(
        gameId: String,
        inputDelay: Int,
    ): Result<NetplaySession> = runCatching {
        apiClient.createNetplaySession(
            CreateNetplaySessionRequest(gameId = gameId, inputDelay = inputDelay.toLong())
        ).toDomain()
    }

    override suspend fun getSessions(): Result<List<NetplaySession>> = runCatching {
        apiClient.getNetplaySessions().data.map { it.toDomain() }
    }

    override suspend fun getSession(sessionId: String): Result<NetplaySession> = runCatching {
        apiClient.getNetplaySession(sessionId).toDomain()
    }

    override suspend fun joinByInviteCode(code: String): Result<NetplaySession> = runCatching {
        apiClient.joinNetplayByInviteCode(JoinByInviteCodeRequest(inviteCode = code)).toDomain()
    }

    override suspend fun leaveSession(sessionId: String): Result<Unit> = runCatching {
        apiClient.leaveNetplaySession(sessionId)
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        apiClient.deleteNetplaySession(sessionId)
    }

    override suspend fun updateInputDelay(sessionId: String, inputDelay: Int): Result<NetplaySession> = runCatching {
        apiClient.updateNetplaySettings(
            sessionId,
            UpdateNetplaySettingsRequest(inputDelay = inputDelay.toLong()),
        ).toDomain()
    }

    override suspend fun sendNetplayInvite(sessionId: String, username: String): Result<Unit> = runCatching {
        apiClient.sendNetplayInvite(sessionId, NetplayInviteUserRequest(username = username))
    }
}
