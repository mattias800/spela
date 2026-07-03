package com.spela.player.data.repository

import app.cash.sqldelight.coroutines.asFlow
import com.spela.player.PendingPlayTimeSyncEntity
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.PendingPlayTimeSync
import com.spela.player.domain.model.PendingPlayTimeSyncQueueSnapshot
import com.spela.player.domain.model.pendingPlayTimeSyncQueueSnapshot
import com.spela.player.domain.repository.PendingPlayTimeSyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PendingPlayTimeSyncRepositoryImpl(
    database: SpelaDatabase,
) : PendingPlayTimeSyncRepository {

    private val queries = database.spelaDatabaseQueries
    private val isDraining = MutableStateFlow(false)

    override fun observeSnapshot(): Flow<PendingPlayTimeSyncQueueSnapshot> =
        combine(
            queries.getAllPendingPlayTimeSyncs()
                .asFlow()
                .map { query -> query.executeAsList().map { it.toDomain() } },
            isDraining,
        ) { rows, draining ->
            pendingPlayTimeSyncQueueSnapshot(rows, draining)
        }

    override suspend fun enqueue(
        clientReportId: String,
        serverUrl: String,
        userId: String,
        gameId: String,
        gameTitle: String,
        durationSeconds: Long,
        playedAt: Long,
        createdAt: Long,
    ): Long {
        queries.enqueuePendingPlayTimeSync(
            client_report_id = clientReportId,
            server_url = serverUrl,
            user_id = userId,
            game_id = gameId,
            game_title = gameTitle,
            duration_seconds = durationSeconds,
            played_at = playedAt,
            created_at = createdAt,
        )
        return queries.lastInsertedPendingPlayTimeSyncId().executeAsOne()
    }

    override suspend fun getAll(): List<PendingPlayTimeSync> =
        queries.getAllPendingPlayTimeSyncs().executeAsList().map { it.toDomain() }

    override suspend fun getForContext(serverUrl: String, userId: String): List<PendingPlayTimeSync> =
        queries.getPendingPlayTimeSyncsForContext(
            server_url = serverUrl,
            user_id = userId,
        ).executeAsList().map { it.toDomain() }

    override suspend fun getById(id: Long): PendingPlayTimeSync? =
        queries.getPendingPlayTimeSyncById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun count(): Long =
        queries.countPendingPlayTimeSyncs().executeAsOne()

    override suspend fun delete(id: Long) {
        queries.deletePendingPlayTimeSync(id)
    }

    override suspend fun markRetry(id: Long, lastError: String?) {
        queries.incrementPendingPlayTimeSyncRetry(last_error = lastError, id = id)
    }

    override fun setDraining(isDraining: Boolean) {
        this.isDraining.value = isDraining
    }
}

private fun PendingPlayTimeSyncEntity.toDomain(): PendingPlayTimeSync = PendingPlayTimeSync(
    id = id,
    clientReportId = client_report_id,
    serverUrl = server_url,
    userId = user_id,
    gameId = game_id,
    gameTitle = game_title,
    durationSeconds = duration_seconds,
    playedAt = played_at,
    createdAt = created_at,
    retryCount = retry_count.toInt(),
    lastError = last_error,
)
