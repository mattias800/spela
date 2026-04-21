package com.spela.player.domain.repository

import com.spela.player.domain.model.GameSession
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SessionCheatConfig

interface SessionRepository {
    suspend fun getSessionsForGame(gameId: String): Result<List<GameSession>>
    suspend fun getSession(sessionId: String): Result<GameSession>
    suspend fun createSession(gameId: String, name: String): Result<GameSession>
    suspend fun createSessionFromSharedSave(gameId: String, saveId: String): Result<GameSession>
    suspend fun updateSession(sessionId: String, name: String? = null, coreName: String? = null): Result<GameSession>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun getSessionSaves(sessionId: String): Result<List<SaveState>>
    suspend fun uploadSessionSave(sessionId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String = ""): Result<SaveState>
    suspend fun downloadSessionSave(sessionId: String, saveId: String): Result<ByteArray>
    suspend fun uploadSessionAutoSave(sessionId: String, data: ByteArray, screenshot: ByteArray?, coreName: String = ""): Result<Unit>
    suspend fun downloadSessionAutoSave(sessionId: String): Result<ByteArray>
    suspend fun uploadSlotSave(sessionId: String, slot: Int, data: ByteArray, screenshot: ByteArray?, coreName: String = ""): Result<SaveState>
    suspend fun downloadSlotSave(sessionId: String, slot: Int): Result<ByteArray>
    suspend fun uploadSessionSram(sessionId: String, data: ByteArray, coreName: String = ""): Result<Unit>
    suspend fun downloadSessionSram(sessionId: String): Result<ByteArray>
    suspend fun getSessionCheats(sessionId: String): Result<SessionCheatConfig>
    suspend fun updateSessionCheats(sessionId: String, cheatsEnabled: Boolean, enabledIndices: List<Int>): Result<SessionCheatConfig>
    /**
     * Clones an existing session into a new session owned by the caller.
     * The new session inherits `totalPlayTime` and `pinnedCoreSha256`
     * from the source, copies SRAM/cheats/screenshot, and is seeded
     * with one save state — the most recent save when [saveId] is null
     * or zero, or the save with the given id when provided.
     *
     * Replaces the deprecated `duplicateSession` operation; the server
     * still exposes `POST /api/sessions/{id}/duplicate` as an alias.
     */
    suspend fun cloneSession(sessionId: String, name: String? = null, saveId: Long? = null): Result<GameSession>
}
