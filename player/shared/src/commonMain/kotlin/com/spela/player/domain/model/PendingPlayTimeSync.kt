package com.spela.player.domain.model

/**
 * One play-time delta that could not be sent when it was produced.
 *
 * Rows are scoped by server + user so a later sign-in on the same device cannot
 * replay another account's offline play activity into the wrong profile.
 */
data class PendingPlayTimeSync(
    val id: Long,
    val clientReportId: String,
    val serverUrl: String,
    val userId: String,
    val gameId: String,
    val gameTitle: String,
    val durationSeconds: Long,
    /** Epoch milliseconds for when this play happened, not when it syncs. */
    val playedAt: Long,
    /** Epoch milliseconds when the queue row was created locally. */
    val createdAt: Long,
    val retryCount: Int,
    val lastError: String?,
)

const val PENDING_PLAY_TIME_SYNC_STUCK_RETRY_THRESHOLD = 3

data class PendingPlayTimeSyncQueueSnapshot(
    val pendingCount: Int,
    val retryingCount: Int,
    val stuckCount: Int,
    val totalSeconds: Long,
    val isDraining: Boolean,
    val jobs: List<PendingPlayTimeSyncQueueJob>,
) {
    companion object {
        val Empty = PendingPlayTimeSyncQueueSnapshot(
            pendingCount = 0,
            retryingCount = 0,
            stuckCount = 0,
            totalSeconds = 0L,
            isDraining = false,
            jobs = emptyList(),
        )
    }
}

data class PendingPlayTimeSyncQueueJob(
    val id: Long,
    val clientReportId: String,
    val serverUrl: String,
    val userId: String,
    val gameId: String,
    val gameTitle: String,
    val durationSeconds: Long,
    val retryCount: Int,
    val lastError: String?,
    val playedAt: Long,
    val createdAt: Long,
)

fun pendingPlayTimeSyncQueueSnapshot(
    rows: List<PendingPlayTimeSync>,
    isDraining: Boolean,
): PendingPlayTimeSyncQueueSnapshot {
    val jobs = rows.map { row ->
        PendingPlayTimeSyncQueueJob(
            id = row.id,
            clientReportId = row.clientReportId,
            serverUrl = row.serverUrl,
            userId = row.userId,
            gameId = row.gameId,
            gameTitle = row.gameTitle,
            durationSeconds = row.durationSeconds,
            retryCount = row.retryCount,
            lastError = row.lastError,
            playedAt = row.playedAt,
            createdAt = row.createdAt,
        )
    }
    return PendingPlayTimeSyncQueueSnapshot(
        pendingCount = jobs.size,
        retryingCount = jobs.count { it.retryCount > 0 },
        stuckCount = jobs.count { it.retryCount >= PENDING_PLAY_TIME_SYNC_STUCK_RETRY_THRESHOLD },
        totalSeconds = jobs.sumOf { it.durationSeconds },
        isDraining = isDraining,
        jobs = jobs,
    )
}
