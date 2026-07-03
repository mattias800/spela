package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.libretro.splitForFlush
import com.spela.player.util.DispatcherProvider
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Manages WebSocket connection for online presence and periodic play-time heartbeats.
 *
 * When connected via WebSocket, the server considers the user "online".
 * During gameplay, periodic heartbeats report play time and set the current game.
 */
class PresenceService(
    private val apiClient: SpelaApiClient,
    private val engineFactory: io.ktor.client.engine.HttpClientEngineFactory<*>,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val scrapeService: ScrapeService? = null,
    private val playTimeSyncManager: PlayTimeSyncManager? = null,
) {
    // Cross-dispatcher: startHeartbeat/stopHeartbeat run on the main
    // dispatcher while the heartbeat loop body reads currentGameId from
    // dispatchers.io. Without @Volatile a write on one thread is not
    // guaranteed to be visible to the other.
    @Volatile
    private var wsJob: Job? = null
    @Volatile
    private var heartbeatJob: Job? = null
    @Volatile
    private var currentGameId: String? = null
    @Volatile
    private var currentGameTitle: String = ""

    // Drains the milliseconds of *active* play (frames advanced) since the
    // last call from the emulation controller. Play time is reported from
    // this — not wall-clock — so paused/backgrounded/stalled time is never
    // counted (#1282). Null when no game is running.
    @Volatile
    private var drainPlayMillis: (() -> Long)? = null

    @Volatile
    var paused: Boolean = false

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val RECONNECT_DELAY_MS = 5_000L
    }

    /**
     * Connect to the server WebSocket for online presence.
     * Automatically reconnects on disconnection.
     */
    fun connect() {
        if (wsJob?.isActive == true) return

        wsJob = scope.launch(dispatchers.io) {
            // A single Ktor client is reused across every reconnect attempt.
            // Creating a new HttpClient per attempt leaked the engine's
            // SelectorManager (and its kqueue/wakeup-pipe/socket FDs) on each
            // reconnect: webSocket() throws on a failed upgrade — the normal
            // reconnect case — which skipped the per-loop close(), eventually
            // exhausting the process file-descriptor limit (#1399). Ktor
            // clients are built to be reused, so we create one here and close
            // it once in finally when disconnect() cancels this coroutine.
            val wsClient = HttpClient(engineFactory) {
                install(WebSockets)
            }
            try {
                while (isActive) {
                    val wsUrl = apiClient.getWebSocketUrl() ?: run {
                        delay(RECONNECT_DELAY_MS)
                        return@launch
                    }
                    try {
                        wsClient.webSocket(wsUrl) {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    handleFrame(frame.readText())
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Connection failed or closed, retry
                    }
                    delay(RECONNECT_DELAY_MS)
                }
            } finally {
                wsClient.close()
            }
        }
    }

    /**
     * Disconnect from the server WebSocket.
     */
    fun disconnect() {
        wsJob?.cancel()
        wsJob = null
        stopHeartbeat()
    }

    /**
     * Start reporting play time for the given game. [drainMillis] is polled
     * each interval for the milliseconds of *active* play (frames advanced)
     * since the previous poll; the reporter sends the real accrued seconds
     * — not a flat interval — carrying any sub-second remainder forward so
     * nothing is lost or invented. Time while paused/backgrounded/stalled
     * drains as ~0, so it isn't counted (#1282).
     */
    fun startHeartbeat(gameId: String, gameTitle: String = "", drainMillis: () -> Long) {
        stopHeartbeat()
        paused = false
        currentGameId = gameId
        currentGameTitle = gameTitle
        drainPlayMillis = drainMillis
        println("[PlayTime] start game=$gameId")

        // Initial 0-second ping marks the user as playing this game
        // immediately (server sets current-game presence on any report).
        scope.launch(dispatchers.io) {
            sendPlayTime(
                gameId = gameId,
                gameTitle = gameTitle,
                seconds = 0L,
                trigger = "start",
            )
        }

        heartbeatJob = scope.launch(dispatchers.io) {
            var pendingMillis = 0L
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val gid = currentGameId ?: break
                // Always drain to keep the accumulator from growing while
                // paused; only the reporting is gated on `paused`.
                pendingMillis += drainPlayMillis?.invoke() ?: 0L
                if (paused) {
                    if (pendingMillis > 0L) {
                        println("[PlayTime] paused — holding ${pendingMillis}ms (game=$gid)")
                    }
                    continue
                }
                val (seconds, remainder) = splitForFlush(pendingMillis)
                pendingMillis = remainder
                if (seconds > 0L) {
                    println("[PlayTime] report game=$gid +${seconds}s (carry ${remainder}ms)")
                    sendPlayTime(
                        gameId = gid,
                        gameTitle = currentGameTitle,
                        seconds = seconds,
                        trigger = "heartbeat",
                    )
                }
            }
        }
    }

    /**
     * Stop reporting play time. Flushes any active play accrued since the
     * last interval, then clears the game status on the server (both
     * best-effort).
     */
    fun stopHeartbeat() {
        val gameId = currentGameId
        val gameTitle = currentGameTitle
        val drain = drainPlayMillis
        heartbeatJob?.cancel()
        heartbeatJob = null
        currentGameId = null
        currentGameTitle = ""
        drainPlayMillis = null

        if (gameId != null) {
            scope.launch(dispatchers.io) {
                try {
                    val (seconds, _) = splitForFlush(drain?.invoke() ?: 0L)
                    println("[PlayTime] stop game=$gameId final +${seconds}s")
                    if (seconds > 0L) {
                        sendPlayTime(
                            gameId = gameId,
                            gameTitle = gameTitle,
                            seconds = seconds,
                            trigger = "stop",
                        )
                    }
                } catch (_: Exception) {
                    // Best effort
                }
                // Tell the server we stopped playing
                try {
                    apiClient.clearCurrentGame(gameId)
                } catch (_: Exception) {
                    // Best effort — the server will time out stale entries anyway
                }
            }
        }
    }

    private suspend fun sendPlayTime(
        gameId: String,
        gameTitle: String,
        seconds: Long,
        trigger: String,
    ) {
        val playedAtMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val manager = playTimeSyncManager
        if (manager != null) {
            manager.reportPlayTime(
                gameId = gameId,
                gameTitle = gameTitle,
                durationSeconds = seconds,
                playedAtMillis = playedAtMillis,
                trigger = trigger,
            )
            return
        }

        try {
            apiClient.updatePlayTime(gameId, seconds)
        } catch (_: Exception) {
            // Best effort in tests that construct PresenceService without the
            // durable sync manager.
        }
    }

    private fun handleFrame(text: String) {
        try {
            val event = json.parseToJsonElement(text).jsonObject
            val type = event["type"]?.jsonPrimitive?.content ?: return
            val payload = event["payload"]?.jsonObject ?: return

            when (type) {
                "game_scrape_status" -> {
                    val gameId = payload["gameId"]?.jsonPrimitive?.content ?: return
                    val status = payload["status"]?.jsonPrimitive?.content ?: return
                    scrapeService?.onScrapeStatusChanged(gameId, status)
                }
            }
        } catch (_: Exception) {
            // Malformed frame, ignore
        }
    }
}
