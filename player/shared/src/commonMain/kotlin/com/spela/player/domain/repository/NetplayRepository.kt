package com.spela.player.domain.repository

import com.spela.player.domain.model.NetplaySession

interface NetplayRepository {
    suspend fun createSession(gameId: String, inputDelay: Int = 3): Result<NetplaySession>
    suspend fun getSessions(): Result<List<NetplaySession>>
    suspend fun getSession(sessionId: String): Result<NetplaySession>
    suspend fun joinByInviteCode(code: String): Result<NetplaySession>
    suspend fun leaveSession(sessionId: String): Result<Unit>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun updateInputDelay(sessionId: String, inputDelay: Int): Result<NetplaySession>
}
