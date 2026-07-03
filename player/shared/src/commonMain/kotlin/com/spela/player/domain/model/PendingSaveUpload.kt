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

/**
 * Retry count at which a pending upload is no longer just "waiting" and should
 * be surfaced as stuck in user-facing queue state.
 */
const val PENDING_SAVE_UPLOAD_STUCK_RETRY_THRESHOLD = 3

/**
 * Aggregate, UI-safe view of the durable save-sync queue.
 *
 * The raw queue row includes local file paths. Settings only needs operational
 * visibility, so expose a small snapshot with counts plus the per-job fields a
 * user or tester can reason about.
 */
data class PendingSaveUploadQueueSnapshot(
    val pendingCount: Int,
    val retryingCount: Int,
    val stuckCount: Int,
    val isDraining: Boolean,
    val jobs: List<PendingSaveUploadQueueJob>,
) {
    companion object {
        val Empty = PendingSaveUploadQueueSnapshot(
            pendingCount = 0,
            retryingCount = 0,
            stuckCount = 0,
            isDraining = false,
            jobs = emptyList(),
        )
    }
}

data class PendingSaveUploadQueueJob(
    val id: Long,
    val kind: PendingUploadKind,
    val sessionId: String,
    val slot: Int?,
    val name: String,
    val size: Long,
    val retryCount: Int,
    val lastError: String?,
    val createdAt: Long,
)

fun pendingSaveUploadQueueSnapshot(
    rows: List<PendingSaveUpload>,
    isDraining: Boolean,
): PendingSaveUploadQueueSnapshot {
    val jobs = rows.map { row ->
        PendingSaveUploadQueueJob(
            id = row.id,
            kind = row.kind,
            sessionId = row.sessionId,
            slot = row.slot,
            name = row.name,
            size = row.fileSize,
            retryCount = row.retryCount,
            lastError = row.lastError,
            createdAt = row.createdAt,
        )
    }
    return PendingSaveUploadQueueSnapshot(
        pendingCount = jobs.size,
        retryingCount = jobs.count { it.retryCount > 0 },
        stuckCount = jobs.count { it.retryCount >= PENDING_SAVE_UPLOAD_STUCK_RETRY_THRESHOLD },
        isDraining = isDraining,
        jobs = jobs,
    )
}

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
