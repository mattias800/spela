package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.repository.CurrentUserContextRepository
import com.spela.player.domain.repository.PendingPlayTimeSyncRepository
import com.spela.player.domain.repository.ServerRepository
import com.spela.player.util.DispatcherProvider
import com.spela.player.util.spelaLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Durable sync worker for play-time deltas.
 *
 * PresenceService owns the active-play accounting; this class owns network
 * delivery, offline queueing, retry, and user-visible queue state.
 */
class PlayTimeSyncManager(
    private val apiClient: SpelaApiClient,
    private val connectivityMonitor: ConnectivityMonitor,
    private val pendingRepository: PendingPlayTimeSyncRepository,
    private val serverRepository: ServerRepository,
    private val currentUserContextRepository: CurrentUserContextRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val drainMutex = Mutex()

    suspend fun reportPlayTime(
        gameId: String,
        gameTitle: String,
        durationSeconds: Long,
        playedAtMillis: Long,
        trigger: String,
    ) {
        if (durationSeconds <= 0L) {
            runCatching {
                apiClient.updatePlayTime(gameId = gameId, seconds = 0L, updatePresence = true)
            }.onFailure { e ->
                playTimeSyncLog(
                    "presence_ping_failure game=$gameId trigger=$trigger error=${errorSummary(e)}",
                )
            }
            return
        }

        val clientReportId = newClientReportId(playedAtMillis)
        val queuedId = enqueue(
            clientReportId = clientReportId,
            gameId = gameId,
            gameTitle = gameTitle,
            durationSeconds = durationSeconds,
            playedAtMillis = playedAtMillis,
            trigger = if (connectivityMonitor.isOnline.value) trigger else "$trigger.offline",
            error = null,
        )
        if (queuedId == null || !connectivityMonitor.isOnline.value) {
            return
        }

        playTimeSyncLog("drain_request id=$queuedId clientReportId=$clientReportId trigger=$trigger")
        drainPendingNow(trigger = trigger, livePresenceRowId = queuedId)
    }

    fun drainPending(trigger: String): Job =
        scope.launch(dispatchers.io) {
            drainPendingNow(trigger)
        }

    fun startReconnectListener(): Job =
        scope.launch(dispatchers.io) {
            connectivityMonitor.onReconnect.collect {
                playTimeSyncLog("reconnect_drain")
                drainPendingNow(trigger = "reconnect")
            }
        }

    private suspend fun drainPendingNow(trigger: String, livePresenceRowId: Long? = null) {
        if (!drainMutex.tryLock()) {
            playTimeSyncLog("drain_skip reason=already_running trigger=$trigger")
            return
        }
        playTimeSyncLog("drain_start trigger=$trigger")
        pendingRepository.setDraining(true)
        try {
            val context = currentContext()
            if (context == null) {
                playTimeSyncLog("drain_skip reason=missing_context trigger=$trigger")
                return
            }
            while (true) {
                val pending = pendingRepository.getForContext(
                    serverUrl = context.serverUrl,
                    userId = context.userId,
                )
                if (pending.isEmpty()) {
                    playTimeSyncLog("empty_queue trigger=$trigger")
                    return
                }

                val row = pending.first()
                playTimeSyncLog(
                    "row_upload_start id=${row.id} clientReportId=${row.clientReportId} " +
                        "game=${row.gameId} seconds=${row.durationSeconds} " +
                        "retry=${row.retryCount} trigger=$trigger",
                )
                val result = upload(
                    gameId = row.gameId,
                    durationSeconds = row.durationSeconds,
                    playedAtMillis = row.playedAt,
                    clientReportId = row.clientReportId,
                    updatePresence = row.id == livePresenceRowId,
                )
                if (result.isSuccess) {
                    playTimeSyncLog(
                        "row_upload_success id=${row.id} clientReportId=${row.clientReportId} " +
                            "game=${row.gameId} seconds=${row.durationSeconds}",
                    )
                    pendingRepository.delete(row.id)
                    playTimeSyncLog("delete id=${row.id} clientReportId=${row.clientReportId}")
                } else {
                    val msg = result.exceptionOrNull()?.let { errorSummary(it) } ?: "upload failed"
                    playTimeSyncLog(
                        "row_upload_failure id=${row.id} clientReportId=${row.clientReportId} " +
                            "game=${row.gameId} error=$msg",
                    )
                    pendingRepository.markRetry(row.id, msg)
                    playTimeSyncLog(
                        "retry_marked id=${row.id} clientReportId=${row.clientReportId} " +
                            "retry=${row.retryCount + 1} error=$msg",
                    )
                    return
                }
            }
        } finally {
            pendingRepository.setDraining(false)
            drainMutex.unlock()
        }
    }

    private suspend fun enqueue(
        clientReportId: String,
        gameId: String,
        gameTitle: String,
        durationSeconds: Long,
        playedAtMillis: Long,
        trigger: String,
        error: String?,
    ): Long? {
        val context = runCatching { currentContext() }.getOrNull()
        if (context == null) {
            playTimeSyncLog(
                "enqueue_skip reason=missing_context clientReportId=$clientReportId " +
                    "game=$gameId seconds=$durationSeconds trigger=$trigger",
            )
            return null
        }
        val id = runCatching {
            pendingRepository.enqueue(
                clientReportId = clientReportId,
                serverUrl = context.serverUrl,
                userId = context.userId,
                gameId = gameId,
                gameTitle = gameTitle,
                durationSeconds = durationSeconds,
                playedAt = playedAtMillis,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
        }.onFailure { e ->
            playTimeSyncLog(
                "enqueue_failure clientReportId=$clientReportId game=$gameId " +
                    "seconds=$durationSeconds trigger=$trigger error=${errorSummary(e)}",
            )
        }.getOrNull() ?: return null
        playTimeSyncLog(
            "enqueue id=$id clientReportId=$clientReportId game=$gameId " +
                "seconds=$durationSeconds trigger=$trigger retry=0" +
                (error?.let { " error=$it" } ?: ""),
        )
        return id
    }

    private suspend fun upload(
        gameId: String,
        durationSeconds: Long,
        playedAtMillis: Long,
        clientReportId: String,
        updatePresence: Boolean,
    ): Result<Unit> = runCatching {
        apiClient.updatePlayTime(
            gameId = gameId,
            seconds = durationSeconds,
            playedAtMillis = playedAtMillis,
            clientReportId = clientReportId,
            updatePresence = updatePresence,
        )
    }

    private suspend fun currentContext(): PlayTimeSyncContext? {
        val serverUrl = serverRepository.getActiveServer()?.url?.trimEnd('/') ?: return null
        val user = currentUserContextRepository.getCached() ?: return null
        return PlayTimeSyncContext(serverUrl = serverUrl, userId = user.userId)
    }

    private fun newClientReportId(playedAtMillis: Long): String =
        "play-${playedAtMillis}-${Random.nextLong(0, Long.MAX_VALUE)}"

    private fun playTimeSyncLog(message: String) {
        spelaLog(PLAY_TIME_SYNC_LOG_TAG, message)
    }

    private fun errorSummary(error: Throwable): String =
        (error.message ?: error::class.simpleName ?: "error")
            .replace('\n', ' ')
            .take(200)

    private data class PlayTimeSyncContext(
        val serverUrl: String,
        val userId: String,
    )

    companion object {
        const val PLAY_TIME_SYNC_LOG_TAG = "PlayTimeSync"
    }
}
