package com.spela.player.domain.repository

import com.spela.player.domain.model.PendingSaveUpload
import com.spela.player.domain.model.PendingUploadKind

/**
 * Persistent FIFO queue of save uploads that have been staged to
 * disk but not yet sent to the server. The implementation is
 * SQLDelight-backed so the queue survives an app kill — a save
 * followed by a force-quit replays on the next launch rather than
 * being silently dropped. See #804 phase 6.
 *
 * The repository is the data layer only — it does not know how to
 * upload. The drain worker (a separate slice) reads from here, calls
 * the appropriate session endpoint, and either deletes the row on
 * success or increments [PendingSaveUpload.retryCount] on failure.
 */
interface PendingSaveUploadRepository {
    /**
     * Enqueue a save for later upload. Returns the row's auto-
     * incremented id so callers can correlate UI feedback (e.g.
     * "save 17 syncing…") with the persistent row.
     *
     * The caller is responsible for the file lifecycle at
     * [filePath] — rows in the queue point to files the SaveManager
     * has already staged via the existing #798 staging fast-path.
     */
    suspend fun enqueue(
        sessionId: String,
        kind: PendingUploadKind,
        slot: Int?,
        name: String,
        coreName: String,
        compression: String,
        filePath: String,
        fileSize: Long,
        screenshotPath: String?,
        createdAt: Long,
    ): Long

    /** All pending uploads in FIFO order (created_at ASC, id ASC). */
    suspend fun getAll(): List<PendingSaveUpload>

    /** Pending uploads scoped to a single session. */
    suspend fun getForSession(sessionId: String): List<PendingSaveUpload>

    /** Single row by id; null when the row has already drained. */
    suspend fun getById(id: Long): PendingSaveUpload?

    /** Total number of rows currently queued. */
    suspend fun count(): Long

    /**
     * Remove a row after a successful upload. The caller is responsible
     * for deleting the on-disk file at [PendingSaveUpload.filePath]
     * (this method does not touch the filesystem).
     */
    suspend fun delete(id: Long)

    /**
     * Bump [PendingSaveUpload.retryCount] and record [lastError] on a
     * failed upload attempt. The row stays in the queue so the drain
     * worker can retry on the next opportunity.
     */
    suspend fun markRetry(id: Long, lastError: String?)
}
