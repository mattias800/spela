package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.CreateRelayRequest
import com.spela.player.data.remote.dto.InviteToRelayRequest
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.Relay
import com.spela.player.domain.model.RelayDetail
import com.spela.player.domain.model.RelayInvitation
import com.spela.player.domain.model.RelaySave
import com.spela.player.domain.repository.RelayRepository

class RelayRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : RelayRepository {

    override suspend fun getMyRelays(page: Int, pageSize: Int): Result<List<Relay>> = runCatching {
        apiClient.getMyRelays(page, pageSize).data.map { it.toDomain() }
    }

    override suspend fun getRelay(relayId: String): Result<RelayDetail> = runCatching {
        apiClient.getRelay(relayId).toDomain()
    }

    override suspend fun getRelayInvitations(): Result<List<RelayInvitation>> = runCatching {
        apiClient.getRelayInvitations().data.map { it.toDomain() }
    }

    override suspend fun getPendingInvitationCount(): Result<Int> = runCatching {
        apiClient.getPendingInvitationCount().count
    }

    override suspend fun createRelay(name: String, gameId: String, description: String): Result<RelayDetail> =
        runCatching {
            apiClient.createRelay(CreateRelayRequest(name, gameId, description)).toDomain()
        }

    override suspend fun deleteRelay(relayId: String): Result<Unit> = runCatching {
        apiClient.deleteRelay(relayId)
    }

    override suspend fun inviteUser(relayId: String, username: String): Result<Unit> = runCatching {
        apiClient.inviteToRelay(relayId, InviteToRelayRequest(username))
    }

    override suspend fun acceptInvitation(invitationId: String): Result<Unit> = runCatching {
        apiClient.acceptRelayInvitation(invitationId)
    }

    override suspend fun rejectInvitation(invitationId: String): Result<Unit> = runCatching {
        apiClient.rejectRelayInvitation(invitationId)
    }

    override suspend fun leaveRelay(relayId: String): Result<Unit> = runCatching {
        apiClient.leaveRelay(relayId)
    }

    override suspend fun removeMember(relayId: String, userId: String): Result<Unit> = runCatching {
        apiClient.removeRelayMember(relayId, userId)
    }

    override suspend fun getGameRelays(gameId: String): Result<List<Relay>> = runCatching {
        apiClient.getGameRelays(gameId).map { it.toDomain() }
    }

    override suspend fun getRelaySaves(relayId: String): Result<List<RelaySave>> = runCatching {
        apiClient.getRelaySaves(relayId).map { it.toDomain() }
    }

    override suspend fun deleteRelaySave(relayId: String, saveId: Long): Result<Unit> = runCatching {
        apiClient.deleteRelaySave(relayId, saveId)
    }

    override suspend fun takeTurn(relayId: String): Result<String> = runCatching {
        apiClient.takeTurn(relayId).turnToken
    }

    override suspend fun releaseTurn(relayId: String): Result<Unit> = runCatching {
        apiClient.releaseTurn(relayId)
    }

    override suspend fun heartbeat(relayId: String): Result<Unit> = runCatching {
        apiClient.relayHeartbeat(relayId)
    }

    override suspend fun uploadRelaySave(
        relayId: String,
        name: String,
        turnToken: String,
        data: ByteArray,
    ): Result<RelaySave> = runCatching {
        apiClient.uploadRelaySave(relayId, name, turnToken, data).toDomain()
    }

    override suspend fun downloadRelaySave(relayId: String, saveId: Long): Result<ByteArray> = runCatching {
        apiClient.downloadRelaySave(relayId, saveId)
    }

    override suspend fun downloadRelayAutoSave(relayId: String): Result<ByteArray> = runCatching {
        apiClient.downloadRelayAutoSave(relayId)
    }

    override suspend fun copyRelaySaveToGame(relayId: String, saveId: Long): Result<Unit> = runCatching {
        apiClient.copyRelaySaveToGame(relayId, saveId)
    }

    override suspend fun uploadRelayAutoSave(
        relayId: String,
        turnToken: String,
        data: ByteArray,
    ): Result<RelaySave> = runCatching {
        apiClient.uploadRelayAutoSave(relayId, turnToken, data).toDomain()
    }
}
