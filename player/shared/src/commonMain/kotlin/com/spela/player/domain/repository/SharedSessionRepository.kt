package com.spela.player.domain.repository

import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionInvitation
import com.spela.player.domain.model.SharedSessionSave

interface SharedSessionRepository {
    suspend fun getMySharedSessions(): Result<List<SharedSession>>
    suspend fun getSharedSession(sharedSessionId: String): Result<SharedSessionDetail>
    suspend fun getSharedSessionInvitations(): Result<List<SharedSessionInvitation>>
    suspend fun getPendingInvitationCount(): Result<Int>
    /**
     * Creates a shared session for [gameId]. When [sourceSessionId] is
     * non-null, the server seeds the new shared session's save state
     * with a copy of that local session's most-recent save — i.e.
     * "share THIS playthrough's progress" rather than starting from
     * scratch. The caller must own the source session and the source
     * must belong to the same game. See #885.
     *
     * [description] is currently ignored on the wire (the server
     * request only takes name + gameId + sourceSessionId), but the
     * parameter is kept on the API for callers that want to surface
     * descriptions in the future without another signature churn.
     */
    suspend fun createSharedSession(
        name: String,
        gameId: String,
        description: String = "",
        sourceSessionId: Long? = null,
    ): Result<SharedSessionDetail>
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
}
