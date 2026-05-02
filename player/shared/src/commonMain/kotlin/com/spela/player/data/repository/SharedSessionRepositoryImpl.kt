package com.spela.player.data.repository

import com.spela.client.models.CreateSharedSessionRequest
import com.spela.client.models.InviteToSharedSessionRequest
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionInvitation
import com.spela.player.domain.model.SharedSessionSave
import com.spela.player.domain.repository.SharedSessionRepository

class SharedSessionRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : SharedSessionRepository {

    override suspend fun getMySharedSessions(): Result<List<SharedSession>> = runCatching {
        apiClient.getMySharedSessions().map { it.toDomain() }
    }

    override suspend fun getSharedSession(sharedSessionId: String): Result<SharedSessionDetail> = runCatching {
        apiClient.getSharedSession(sharedSessionId).toDomain()
    }

    override suspend fun getSharedSessionInvitations(): Result<List<SharedSessionInvitation>> = runCatching {
        apiClient.getSharedSessionInvitations().map { it.toDomain() }
    }

    override suspend fun getPendingInvitationCount(): Result<Int> = runCatching {
        apiClient.getPendingInvitationCount().count.toInt()
    }

    override suspend fun createSharedSession(
        name: String,
        gameId: String,
        description: String,
        sourceSessionId: Long?,
    ): Result<SharedSessionDetail> =
        runCatching {
            // description is retained on the repository API for backwards
            // compatibility but is not sent — the server's
            // CreateSharedSessionRequest only accepts name + gameId +
            // sourceSessionId. See #885 for the source-session seeding
            // semantics.
            apiClient.createSharedSession(
                CreateSharedSessionRequest(
                    name = name,
                    gameId = gameId,
                    sourceSessionId = sourceSessionId,
                ),
            ).toDomain()
        }

    override suspend fun deleteSharedSession(sharedSessionId: String): Result<Unit> = runCatching {
        apiClient.deleteSharedSession(sharedSessionId)
    }

    override suspend fun inviteUser(sharedSessionId: String, username: String): Result<Unit> = runCatching {
        apiClient.inviteToSharedSession(sharedSessionId, InviteToSharedSessionRequest(username = username))
    }

    override suspend fun acceptInvitation(invitationId: String): Result<Unit> = runCatching {
        apiClient.acceptSharedSessionInvitation(invitationId)
    }

    override suspend fun rejectInvitation(invitationId: String): Result<Unit> = runCatching {
        apiClient.rejectSharedSessionInvitation(invitationId)
    }

    override suspend fun leaveSharedSession(sharedSessionId: String): Result<Unit> = runCatching {
        apiClient.leaveSharedSession(sharedSessionId)
    }

    override suspend fun removeMember(sharedSessionId: String, userId: String): Result<Unit> = runCatching {
        apiClient.removeSharedSessionMember(sharedSessionId, userId)
    }

    override suspend fun getGameSharedSessions(gameId: String): Result<List<SharedSession>> = runCatching {
        apiClient.getGameSharedSessions(gameId).map { it.toDomain() }
    }

    override suspend fun getSharedSessionSaves(sharedSessionId: String): Result<List<SharedSessionSave>> = runCatching {
        apiClient.getSharedSessionSaves(sharedSessionId).map { it.toDomain() }
    }

    override suspend fun deleteSharedSessionSave(sharedSessionId: String, saveId: Long): Result<Unit> = runCatching {
        apiClient.deleteSharedSessionSave(sharedSessionId, saveId)
    }

    override suspend fun takeTurn(sharedSessionId: String): Result<String> = runCatching {
        apiClient.takeTurn(sharedSessionId).turnToken
    }

    override suspend fun releaseTurn(sharedSessionId: String): Result<Unit> = runCatching {
        apiClient.releaseTurn(sharedSessionId)
    }

    override suspend fun heartbeat(sharedSessionId: String): Result<Unit> = runCatching {
        apiClient.sharedSessionHeartbeat(sharedSessionId)
    }

    override suspend fun uploadSharedSessionSave(
        sharedSessionId: String,
        name: String,
        turnToken: String,
        data: ByteArray,
    ): Result<SharedSessionSave> = runCatching {
        apiClient.uploadSharedSessionSave(sharedSessionId, name, turnToken, data).toDomain()
    }

    override suspend fun downloadSharedSessionSave(sharedSessionId: String, saveId: Long): Result<ByteArray> = runCatching {
        apiClient.downloadSharedSessionSave(sharedSessionId, saveId)
    }

    override suspend fun downloadSharedSessionAutoSave(sharedSessionId: String): Result<ByteArray> = runCatching {
        apiClient.downloadSharedSessionAutoSave(sharedSessionId)
    }

    override suspend fun uploadSharedSessionAutoSave(
        sharedSessionId: String,
        turnToken: String,
        data: ByteArray,
    ): Result<SharedSessionSave> = runCatching {
        apiClient.uploadSharedSessionAutoSave(sharedSessionId, turnToken, data).toDomain()
    }
}
