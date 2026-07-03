package com.spela.player.data.repository

import app.cash.sqldelight.coroutines.asFlow
import com.spela.player.PendingSaveUploadEntity
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.PendingSaveUpload
import com.spela.player.domain.model.PendingSaveUploadQueueSnapshot
import com.spela.player.domain.model.PendingUploadKind
import com.spela.player.domain.model.pendingSaveUploadQueueSnapshot
import com.spela.player.domain.repository.PendingSaveUploadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * SQLDelight-backed implementation of the pending-upload queue.
 * Plain CRUD — no policy, no upload logic. The drain worker (a
 * separate slice of #804 phase 6) is what actually pushes rows to
 * the server.
 */
class PendingSaveUploadRepositoryImpl(
    database: SpelaDatabase,
) : PendingSaveUploadRepository {

    private val queries = database.spelaDatabaseQueries
    private val isDraining = MutableStateFlow(false)

    override fun observeSnapshot(): Flow<PendingSaveUploadQueueSnapshot> =
        combine(
            queries.getAllPendingSaveUploads()
                .asFlow()
                .map { query -> query.executeAsList().map { it.toDomain() } },
            isDraining,
        ) { rows, draining ->
            pendingSaveUploadQueueSnapshot(rows, draining)
        }

    override suspend fun enqueue(
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
    ): Long {
        queries.enqueuePendingSaveUpload(
            session_id = sessionId,
            kind = kind.apiId,
            slot = slot?.toLong(),
            name = name,
            core_name = coreName,
            compression = compression,
            file_path = filePath,
            file_size = fileSize,
            screenshot_path = screenshotPath,
            created_at = createdAt,
        )
        return queries.lastInsertedPendingSaveUploadId().executeAsOne()
    }

    override suspend fun getAll(): List<PendingSaveUpload> =
        queries.getAllPendingSaveUploads().executeAsList().map { it.toDomain() }

    override suspend fun getForSession(sessionId: String): List<PendingSaveUpload> =
        queries.getPendingSaveUploadsForSession(sessionId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun getById(id: Long): PendingSaveUpload? =
        queries.getPendingSaveUploadById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun count(): Long =
        queries.countPendingSaveUploads().executeAsOne()

    override suspend fun delete(id: Long) {
        queries.deletePendingSaveUpload(id)
    }

    override suspend fun markRetry(id: Long, lastError: String?) {
        queries.incrementPendingSaveUploadRetry(last_error = lastError, id = id)
    }

    override fun setDraining(isDraining: Boolean) {
        this.isDraining.value = isDraining
    }
}

private fun PendingSaveUploadEntity.toDomain(): PendingSaveUpload = PendingSaveUpload(
    id = id,
    sessionId = session_id,
    kind = PendingUploadKind.fromApiId(kind),
    slot = slot?.toInt(),
    name = name,
    coreName = core_name,
    compression = compression,
    filePath = file_path,
    fileSize = file_size,
    screenshotPath = screenshot_path,
    createdAt = created_at,
    retryCount = retry_count.toInt(),
    lastError = last_error,
)
