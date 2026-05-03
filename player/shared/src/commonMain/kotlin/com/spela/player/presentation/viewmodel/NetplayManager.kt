package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.repository.SharedSessionRepository
import com.spela.player.netplay.ControlMessage
import com.spela.player.netplay.NetplayInputBuffer
import com.spela.player.netplay.NetplaySignaling
import com.spela.player.netplay.NetplayTransport
import com.spela.player.netplay.WebSocketRelayTransport
import com.spela.player.presentation.state.EmulationState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manages netplay transport setup, state chunk transfer, shared session heartbeat,
 * and shared session save/load operations.
 */
class NetplayManager(
    private val sharedSessionRepository: SharedSessionRepository,
    private val libretroController: LibretroController,
    private val apiClient: SpelaApiClient,
    private val engineFactory: io.ktor.client.engine.HttpClientEngineFactory<*>,
    private val _state: MutableStateFlow<EmulationState>,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private var netplayTransport: NetplayTransport? = null
    private var netplayInputCollectorJob: Job? = null
    private var netplayControlCollectorJob: Job? = null
    private var sharedSessionHeartbeatJob: Job? = null

    /**
     * Load shared session auto-save and unserialize it.
     * Called from within an IO coroutine in startGame().
     */
    suspend fun loadSharedSessionSave(sharedSessionId: String) {
        sharedSessionRepository.downloadSharedSessionAutoSave(sharedSessionId).onSuccess { saveData ->
            libretroController.unserialize(saveData)
        }
    }

    /**
     * Upload shared session auto-save and release turn on stop.
     * Called from within an IO coroutine in stopGame().
     */
    suspend fun saveSharedSessionOnStop(sharedSessionId: String, turnToken: String) {
        try {
            val saveData = libretroController.serialize()
            if (saveData != null) {
                sharedSessionRepository.uploadSharedSessionAutoSave(sharedSessionId, turnToken, saveData)
            }
        } catch (_: Exception) {
            // Best effort shared session auto-save
        }
        try {
            sharedSessionRepository.releaseTurn(sharedSessionId)
        } catch (_: Exception) {
            // Best effort release turn
        }
    }

    fun startSharedSessionHeartbeat(sharedSessionId: String) {
        sharedSessionHeartbeatJob?.cancel()
        sharedSessionHeartbeatJob = scope.launch(dispatchers.io) {
            while (isActive) {
                delay(60_000) // 60 seconds
                try {
                    sharedSessionRepository.heartbeat(sharedSessionId)
                } catch (_: Exception) {
                    // Best effort heartbeat
                }
            }
        }
    }

    /**
     * Set up the netplay transport, connect to the WebSocket, configure lockstep mode
     * on the controller, and start collecting remote inputs.
     *
     * For the host: sends initial state to the client via STATE_CHUNK messages.
     * For the client: receives and applies the initial state from the host.
     */
    suspend fun setupNetplayTransport(
        sessionId: String,
        localPort: Int,
        inputDelay: Int,
        isHost: Boolean,
    ) {
        val signaling = NetplaySignaling(
            apiClient = apiClient,
            engineFactory = engineFactory,
            dispatchers = dispatchers,
            scope = scope,
            sessionId = sessionId,
        )
        val transport = WebSocketRelayTransport(signaling, scope)
        this.netplayTransport = transport

        // Connect to the WebSocket
        transport.connect()

        // Small delay to let the WebSocket connection establish
        delay(500)

        val inputBuffer = NetplayInputBuffer()

        // Initial state sync: host serializes and sends, client waits to receive
        if (isHost) {
            val stateData = libretroController.serialize()
            if (stateData != null) {
                sendStateChunks(transport, stateData)
            }
        } else {
            // Client waits for state chunks from host
            val stateData = receiveStateChunks(transport)
            if (stateData != null) {
                libretroController.unserialize(stateData)
            }
        }

        // Configure lockstep mode on the controller
        libretroController.setNetplayMode(transport, inputBuffer, localPort, inputDelay)

        // Collect remote inputs from transport and feed into input buffer
        netplayInputCollectorJob = scope.launch(dispatchers.io) {
            transport.remoteInputs.collect { remote ->
                inputBuffer.setRemoteInput(remote.frame, remote.port, remote.input)
            }
        }

        // Collect control messages for disconnect/reconnect handling
        netplayControlCollectorJob = scope.launch(dispatchers.io) {
            transport.controlMessages.collect { msg ->
                when (msg) {
                    is ControlMessage.PlayerLeft -> {
                        withContext(dispatchers.main) {
                            _state.update { it.copy(netplayPeerDisconnected = true) }
                        }
                    }
                    is ControlMessage.PlayerJoined -> {
                        withContext(dispatchers.main) {
                            _state.update { it.copy(netplayPeerDisconnected = false) }
                        }
                    }
                    else -> { /* other messages handled elsewhere */ }
                }
            }
        }
    }

    private fun sendStateChunks(transport: NetplayTransport, stateData: ByteArray) {
        val chunkSize = 16_384 // 16 KB chunks
        val totalChunks = (stateData.size + chunkSize - 1) / chunkSize
        for (i in 0 until totalChunks) {
            val offset = i * chunkSize
            val end = minOf(offset + chunkSize, stateData.size)
            val chunk = stateData.copyOfRange(offset, end)
            val encoded = com.spela.player.netplay.NetplayProtocol.encodeStateChunk(
                chunkIndex = i,
                totalChunks = totalChunks,
                totalSize = stateData.size,
                data = chunk,
            )
            transport.sendBinary(encoded)
        }
    }

    private suspend fun receiveStateChunks(transport: NetplayTransport): ByteArray? {
        val receivedChunks = mutableMapOf<Int, ByteArray>()
        var totalChunks = -1
        var totalSize = -1

        // Wait for all state chunks with a timeout.
        // Use takeWhile to break out of collect when all chunks arrive.
        withTimeoutOrNull(10_000) {
            transport.remoteBinary.takeWhile { msg ->
                val chunk = com.spela.player.netplay.NetplayProtocol.decodeStateChunk(msg.data)
                if (chunk != null) {
                    totalChunks = chunk.totalChunks
                    totalSize = chunk.totalSize
                    receivedChunks[chunk.chunkIndex] = chunk.data
                }
                // Continue collecting while we don't have all chunks yet
                receivedChunks.size < totalChunks || totalChunks < 0
            }.collect {}
        } ?: return null

        if (totalSize < 0 || receivedChunks.size < totalChunks) return null

        // Reassemble in order
        val result = ByteArray(totalSize)
        var offset = 0
        for (i in 0 until totalChunks) {
            val chunkData = receivedChunks[i] ?: return null
            chunkData.copyInto(result, offset)
            offset += chunkData.size
        }
        return result
    }

    /**
     * Cancel all netplay/shared-session-related jobs and disconnect transport.
     * Called from stopGame().
     *
     * Order matters: clearNetplayMode() runs first to unblock the libretro
     * core's lockstep loop (which may be waiting on the next input frame).
     * Disconnecting the transport before clearing the mode could leave the
     * emulator thread stuck waiting for an input that will never arrive.
     */
    fun cleanup() {
        libretroController.clearNetplayMode()
        sharedSessionHeartbeatJob?.cancel()
        sharedSessionHeartbeatJob = null
        netplayInputCollectorJob?.cancel()
        netplayInputCollectorJob = null
        netplayControlCollectorJob?.cancel()
        netplayControlCollectorJob = null
        netplayTransport?.disconnect()
        netplayTransport = null
    }
}
