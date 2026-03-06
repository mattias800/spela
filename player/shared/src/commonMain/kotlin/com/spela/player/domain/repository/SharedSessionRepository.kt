package com.spela.player.domain.repository

import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionInvitation
import com.spela.player.domain.model.SharedSessionSave

interface SharedSessionRepository {
    suspend fun getMySharedSessions(page: Int = 1, pageSize: Int = 20): Result<List<SharedSession>>
    suspend fun getSharedSession(sharedSessionId: String): Result<SharedSessionDetail>
    suspend fun getSharedSessionInvitations(): Result<List<SharedSessionInvitation>>
    suspend fun getPendingInvitationCount(): Result<Int>
    suspend fun createSharedSession(name: String, gameId: String, description: String = ""): Result<SharedSessionDetail>
    suspend fun deleteSharedSession(sharedSessionId: String): Result<Unit>
    suspend fun inviteUser(sharedSessionId: String, username: String): Result<Unit>
    suspend fun acceptInvitation(invitationId: String): Result<Unit>
    suspend fun rejectInvitation(invitationId: String): Result<Unit>
    suspend fun leaveSharedSession(sharedSessionId: String): Result<Unit>
    suspend fun removeMember(sharedSessionId: String, userId: String): Result<Unit>
    suspend fun getGameSharedSessions(gameId: String): Result<List<SharedSession>>
    suspend fun getSharedSessionSaves(sharedSessionId: String): Result<List<SharedSessionSave>>
    suspend fun deleteSharedSessionSave(sharedSessionId: String, saveId: Long): Result<Unit>
    suspend fun takeTurn(sharedSessionId: String): Result<String>
    suspend fun releaseTurn(sharedSessionId: String): Result<Unit>
    suspend fun heartbeat(sharedSessionId: String): Result<Unit>
    suspend fun uploadSharedSessionSave(sharedSessionId: String, name: String, turnToken: String, data: ByteArray): Result<SharedSessionSave>
    suspend fun downloadSharedSessionSave(sharedSessionId: String, saveId: Long): Result<ByteArray>
    suspend fun downloadSharedSessionAutoSave(sharedSessionId: String): Result<ByteArray>
    suspend fun uploadSharedSessionAutoSave(sharedSessionId: String, turnToken: String, data: ByteArray): Result<SharedSessionSave>
    suspend fun copySharedSessionSaveToGame(sharedSessionId: String, saveId: Long): Result<Unit>
}
