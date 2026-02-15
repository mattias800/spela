package com.spela.player.domain.repository

import com.spela.player.domain.model.Relay
import com.spela.player.domain.model.RelayDetail
import com.spela.player.domain.model.RelayInvitation
import com.spela.player.domain.model.RelaySave

interface RelayRepository {
    suspend fun getMyRelays(page: Int = 1, pageSize: Int = 20): Result<List<Relay>>
    suspend fun getRelay(relayId: String): Result<RelayDetail>
    suspend fun getRelayInvitations(): Result<List<RelayInvitation>>
    suspend fun getPendingInvitationCount(): Result<Int>
    suspend fun createRelay(name: String, gameId: String, description: String = ""): Result<RelayDetail>
    suspend fun deleteRelay(relayId: String): Result<Unit>
    suspend fun inviteUser(relayId: String, username: String): Result<Unit>
    suspend fun acceptInvitation(invitationId: String): Result<Unit>
    suspend fun rejectInvitation(invitationId: String): Result<Unit>
    suspend fun leaveRelay(relayId: String): Result<Unit>
    suspend fun removeMember(relayId: String, userId: String): Result<Unit>
    suspend fun getGameRelays(gameId: String): Result<List<Relay>>
    suspend fun getRelaySaves(relayId: String): Result<List<RelaySave>>
    suspend fun deleteRelaySave(relayId: String, saveId: Long): Result<Unit>
    suspend fun takeTurn(relayId: String): Result<String>
    suspend fun releaseTurn(relayId: String): Result<Unit>
    suspend fun heartbeat(relayId: String): Result<Unit>
    suspend fun uploadRelaySave(relayId: String, name: String, turnToken: String, data: ByteArray): Result<RelaySave>
    suspend fun downloadRelaySave(relayId: String, saveId: Long): Result<ByteArray>
    suspend fun downloadRelayAutoSave(relayId: String): Result<ByteArray>
    suspend fun uploadRelayAutoSave(relayId: String, turnToken: String, data: ByteArray): Result<RelaySave>
}
