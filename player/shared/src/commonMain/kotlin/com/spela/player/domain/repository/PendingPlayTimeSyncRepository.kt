package com.spela.player.domain.repository

import com.spela.player.domain.model.PendingPlayTimeSync
import com.spela.player.domain.model.PendingPlayTimeSyncQueueSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Durable FIFO queue for play-time deltas that failed while offline or while
 * the server was unreachable.
 */
interface PendingPlayTimeSyncRepository {
    fun observeSnapshot(): Flow<PendingPlayTimeSyncQueueSnapshot>

    suspend fun enqueue(
        clientReportId: String,
        serverUrl: String,
        userId: String,
        gameId: String,
        gameTitle: String,
        durationSeconds: Long,
        playedAt: Long,
        createdAt: Long,
    ): Long

    suspend fun getAll(): List<PendingPlayTimeSync>

    suspend fun getForContext(serverUrl: String, userId: String): List<PendingPlayTimeSync>

    suspend fun getById(id: Long): PendingPlayTimeSync?

    suspend fun count(): Long

    suspend fun delete(id: Long)

    suspend fun markRetry(id: Long, lastError: String?)

    fun setDraining(isDraining: Boolean)
}
