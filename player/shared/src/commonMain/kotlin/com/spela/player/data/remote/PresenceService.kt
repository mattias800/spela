package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.util.DispatcherProvider
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
) {
    private var wsJob: Job? = null
    private var heartbeatJob: Job? = null
    private var currentGameId: String? = null

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
            while (isActive) {
                val wsUrl = apiClient.getWebSocketUrl() ?: run {
                    delay(RECONNECT_DELAY_MS)
                    return@launch
                }
                try {
                    val wsClient = HttpClient(engineFactory) {
                        install(WebSockets)
                    }
                    wsClient.webSocket(wsUrl) {
                        // Keep connection alive by reading frames
                        for (frame in incoming) {
                            // Server sends events; we just need to stay connected
                        }
                    }
                    wsClient.close()
                } catch (_: Exception) {
                    // Connection failed or closed, retry
                }
                delay(RECONNECT_DELAY_MS)
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
     * Start sending play-time heartbeats for the given game.
     * Each heartbeat reports the interval seconds and marks the user as playing.
     */
    fun startHeartbeat(gameId: String) {
        stopHeartbeat()
        currentGameId = gameId

        // Send initial heartbeat immediately
        scope.launch(dispatchers.io) {
            try {
                apiClient.updatePlayTime(gameId, 0)
            } catch (_: Exception) {
                // Best effort
            }
        }

        heartbeatJob = scope.launch(dispatchers.io) {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val gid = currentGameId ?: break
                try {
                    val seconds = HEARTBEAT_INTERVAL_MS / 1000
                    apiClient.updatePlayTime(gid, seconds)
                } catch (_: Exception) {
                    // Best effort, will retry on next interval
                }
            }
        }
    }

    /**
     * Stop sending play-time heartbeats.
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        currentGameId = null
    }
}
