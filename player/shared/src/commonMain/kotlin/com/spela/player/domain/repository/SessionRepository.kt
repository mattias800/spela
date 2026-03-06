package com.spela.player.domain.repository

import com.spela.player.domain.model.GameSession
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SessionCheatConfig

interface SessionRepository {
    suspend fun getSessionsForGame(gameId: String): Result<List<GameSession>>
    suspend fun getSession(sessionId: String): Result<GameSession>
    suspend fun createSession(gameId: String, name: String): Result<GameSession>
    suspend fun createSessionFromSharedSave(gameId: String, saveId: String): Result<GameSession>
    suspend fun updateSession(sessionId: String, name: String): Result<GameSession>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun getSessionSaves(sessionId: String): Result<List<SaveState>>
    suspend fun uploadSessionSave(sessionId: String, name: String, data: ByteArray, screenshot: ByteArray?): Result<SaveState>
    suspend fun downloadSessionSave(sessionId: String, saveId: String): Result<ByteArray>
    suspend fun uploadSessionAutoSave(sessionId: String, data: ByteArray, screenshot: ByteArray?): Result<Unit>
    suspend fun downloadSessionAutoSave(sessionId: String): Result<ByteArray>
    suspend fun uploadSessionSram(sessionId: String, data: ByteArray): Result<Unit>
    suspend fun downloadSessionSram(sessionId: String): Result<ByteArray>
    suspend fun getSessionCheats(sessionId: String): Result<SessionCheatConfig>
    suspend fun updateSessionCheats(sessionId: String, cheatsEnabled: Boolean, enabledIndices: List<Int>): Result<SessionCheatConfig>
}
