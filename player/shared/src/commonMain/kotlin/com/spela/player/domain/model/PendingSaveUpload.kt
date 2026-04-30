package com.spela.player.domain.model

/**
 * One save staged on disk that has not yet been uploaded to the
 * server. Phase 6 of #804 — the user no longer waits for a network
 * round-trip on every Save tap; the queue runs opportunistically
 * (game exit, app pause, network reconnect).
 *
 * Survives an app kill: rows live in SQLDelight so a save+force-quit
 * sequence is replayed on next launch rather than dropped.
 */
data class PendingSaveUpload(
    val id: Long,
    val sessionId: String,
    val kind: PendingUploadKind,
    /** Slot number when [kind] is [PendingUploadKind.Slot]; null otherwise. */
    val slot: Int?,
    /** Display name for [PendingUploadKind.Manual] saves; ignored for auto/slot. */
    val name: String,
    val coreName: String,
    /** Codec applied to the bytes at [filePath] — `""` raw or `"gzip"`. */
    val compression: String,
    val filePath: String,
    val fileSize: Long,
    /** Absolute path to a PNG screenshot file, or null when none was captured. */
    val screenshotPath: String?,
    /** Epoch milliseconds when the save was staged. Used for FIFO ordering. */
    val createdAt: Long,
    val retryCount: Int,
    /** Most recent upload error message, when retry_count > 0. */
    val lastError: String?,
)

/** Categorises a pending upload so the worker calls the right server endpoint. */
enum class PendingUploadKind(val apiId: String) {
    Manual("manual"),
    Auto("auto"),
    Slot("slot");

    companion object {
        fun fromApiId(apiId: String?): PendingUploadKind =
            entries.find { it.apiId == apiId } ?: Manual
    }
}
