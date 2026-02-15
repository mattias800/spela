package com.spela.player.netplay

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.util.DispatcherProvider
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * WebSocket signaling client for netplay sessions.
 *
 * Connects to the server's netplay WebSocket endpoint and handles
 * JSON signaling messages for session lifecycle events. Also supports
 * sending and receiving binary frames for game data relay.
 *
 * Follows the same reconnection pattern as [com.spela.player.data.remote.PresenceService].
 */
class NetplaySignaling(
    private val apiClient: SpelaApiClient,
    private val engineFactory: io.ktor.client.engine.HttpClientEngineFactory<*>,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val sessionId: String,
) {
    private var wsJob: Job? = null
    private var session: DefaultClientWebSocketSession? = null
    private val outgoingBinary = Channel<ByteArray>(Channel.BUFFERED)
    private val outgoingText = Channel<String>(Channel.BUFFERED)

    private val _controlMessages = MutableSharedFlow<ControlMessage>(extraBufferCapacity = 64)
    val controlMessages: Flow<ControlMessage> = _controlMessages.asSharedFlow()

    private val _binaryMessages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val binaryMessages: Flow<ByteArray> = _binaryMessages.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val RECONNECT_DELAY_MS = 2_000L
    }

    /**
     * Connect to the netplay session WebSocket.
     * Automatically reconnects on disconnection.
     */
    fun connect() {
        if (wsJob?.isActive == true) return

        wsJob = scope.launch(dispatchers.io) {
            while (isActive) {
                val wsUrl = buildWsUrl() ?: run {
                    delay(RECONNECT_DELAY_MS)
                    continue
                }
                try {
                    val wsClient = HttpClient(engineFactory) {
                        install(WebSockets)
                    }
                    wsClient.webSocket(wsUrl) {
                        session = this

                        // Launch sender coroutines
                        val binarySender = launch {
                            for (data in outgoingBinary) {
                                send(Frame.Binary(true, data))
                            }
                        }
                        val textSender = launch {
                            for (text in outgoingText) {
                                send(Frame.Text(text))
                            }
                        }

                        // Receive loop
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> handleTextFrame(frame.readText())
                                is Frame.Binary -> _binaryMessages.tryEmit(frame.readBytes())
                                else -> { /* ignore ping/pong/close */ }
                            }
                        }

                        binarySender.cancel()
                        textSender.cancel()
                    }
                    session = null
                    wsClient.close()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    session = null
                }
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    /**
     * Disconnect from the netplay session WebSocket.
     */
    fun disconnect() {
        wsJob?.cancel()
        wsJob = null
        session = null
    }

    /**
     * Send binary data through the WebSocket.
     */
    fun sendBinary(data: ByteArray) {
        outgoingBinary.trySend(data)
    }

    /**
     * Send a JSON text message through the WebSocket.
     */
    fun sendText(message: String) {
        outgoingText.trySend(message)
    }

    private fun buildWsUrl(): String? {
        val httpUrl = apiClient.getWebSocketUrl() ?: return null
        // Replace the /api/ws path with the netplay session path
        val baseWsUrl = httpUrl.substringBefore("/api/ws")
        val token = httpUrl.substringAfter("token=", "")
        if (token.isBlank()) return null
        return "$baseWsUrl/api/netplay/sessions/$sessionId/ws?token=$token"
    }

    private fun handleTextFrame(text: String) {
        try {
            val msg = json.decodeFromString<SignalingMessage>(text)
            val controlMessage = when (msg.type) {
                "player_joined" -> {
                    val player = NetplayPlayer(
                        userId = msg.userId ?: return,
                        username = msg.username ?: "",
                        port = msg.port ?: 0,
                    )
                    ControlMessage.PlayerJoined(player)
                }
                "player_left" -> ControlMessage.PlayerLeft(msg.userId ?: return)
                "session_status" -> {
                    val status = parseSessionStatus(msg.status ?: return) ?: return
                    ControlMessage.SessionStatus(status)
                }
                "connection_mode" -> ControlMessage.ConnectionMode(
                    userId = msg.userId ?: return,
                    mode = msg.mode ?: return,
                )
                "all_ready" -> ControlMessage.AllReady
                else -> return
            }
            _controlMessages.tryEmit(controlMessage)
        } catch (_: Exception) {
            // Malformed message, skip
        }
    }

    private fun parseSessionStatus(status: String): TransportSessionStatus? {
        return when (status) {
            "waiting" -> TransportSessionStatus.WAITING
            "syncing" -> TransportSessionStatus.SYNCING
            "playing" -> TransportSessionStatus.PLAYING
            "paused" -> TransportSessionStatus.PAUSED
            "finished" -> TransportSessionStatus.FINISHED
            else -> null
        }
    }
}

@Serializable
private data class SignalingMessage(
    val type: String,
    val userId: String? = null,
    val username: String? = null,
    val port: Int? = null,
    val status: String? = null,
    val mode: String? = null,
)
