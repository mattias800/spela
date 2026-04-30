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

    /**
     * Toggles the core-upgrade decision flags on a session. Each field
     * is tri-state: `null` leaves the flag untouched, `true` / `false`
     * sets it. Matches PUT /api/sessions/{id}'s partial-update
     * semantics. See #672.
     */
    suspend fun updateSessionCoreFlags(
        sessionId: String,
        userLockedCoreVersion: Boolean? = null,
        autoLoadSuppressed: Boolean? = null,
        rehearsalCrashPending: Boolean? = null,
    ): Result<GameSession>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun getSessionSaves(sessionId: String): Result<List<SaveState>>
    /** Rename a single session save (slot or named). Server returns the
     *  updated row. Used by the in-game slot manage UI (#831). */
    suspend fun updateSessionSave(sessionId: String, saveId: String, name: String): Result<SaveState>
    /** Delete a single session save by id. Server returns 200 with a
     *  message body on success. Used by the in-game slot manage UI
     *  (#831) to free a filled slot mid-session. */
    suspend fun deleteSessionSave(sessionId: String, saveId: String): Result<Unit>
    suspend fun uploadSessionSave(sessionId: String, name: String, data: ByteArray, screenshot: ByteArray?, coreName: String = ""): Result<SaveState>
    /** Streaming variant: reads save bytes from [savePath] without loading
     *  the full state into a JVM ByteArray. Required for cores like
     *  Dolphin whose save states (~90 MB) can't fit alongside the rest
     *  of the heap on Android. See #798.
     *
     *  [compression] tags the bytes at [savePath] with the codec the
     *  caller already applied (currently `""` for raw or `"gzip"`).
     *  The server stores it opaquely; players use it on download to
     *  decide whether to decompress. See #804 phase 2. */
    suspend fun uploadSessionSaveFromFile(sessionId: String, name: String, savePath: String, saveSize: Long, screenshot: ByteArray?, coreName: String = "", compression: String = ""): Result<SaveState>
    suspend fun downloadSessionSave(sessionId: String, saveId: String): Result<ByteArray>
    suspend fun uploadSessionAutoSave(sessionId: String, data: ByteArray, screenshot: ByteArray?, coreName: String = ""): Result<Unit>
    suspend fun uploadSessionAutoSaveFromFile(sessionId: String, savePath: String, saveSize: Long, screenshot: ByteArray?, coreName: String = "", compression: String = ""): Result<Unit>
    suspend fun downloadSessionAutoSave(sessionId: String): Result<ByteArray>
    /** Streaming variant of [downloadSessionAutoSave]: writes the body to
     *  [outputPath] without materialising the full payload as a ByteArray.
     *  See #798. */
    suspend fun downloadSessionAutoSaveToFile(sessionId: String, outputPath: String): Result<Unit>
    suspend fun uploadSlotSave(sessionId: String, slot: Int, data: ByteArray, screenshot: ByteArray?, coreName: String = ""): Result<SaveState>
    suspend fun uploadSlotSaveFromFile(sessionId: String, slot: Int, savePath: String, saveSize: Long, screenshot: ByteArray?, coreName: String = "", compression: String = ""): Result<SaveState>
    suspend fun downloadSlotSave(sessionId: String, slot: Int): Result<ByteArray>
    /** Streaming variant of [downloadSlotSave]. Writes the body to
     *  [outputPath] and inflates inline when the server tags the
     *  response with `X-Compression: gzip`, so callers can hand the
     *  file straight to libretro's `unserializeFromFile`. See #804. */
    suspend fun downloadSlotSaveToFile(sessionId: String, slot: Int, outputPath: String): Result<Unit>
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
