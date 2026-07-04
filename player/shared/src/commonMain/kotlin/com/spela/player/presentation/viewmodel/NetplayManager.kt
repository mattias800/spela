package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.repository.SharedSessionRepository
import com.spela.player.netplay.ControlMessage
import com.spela.player.netplay.BinaryMessage
import com.spela.player.netplay.NetplayInputBuffer
import com.spela.player.netplay.NetplaySignaling
import com.spela.player.netplay.NetplayTransport
import com.spela.player.netplay.WebSocketRelayTransport
import com.spela.player.presentation.state.EmulationState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val INITIAL_NETPLAY_SYNC_TIMEOUT_MS = 10_000L

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
    private val transportFactory: (NetplaySignaling, CoroutineScope) -> NetplayTransport = { signaling, coroutineScope ->
        WebSocketRelayTransport(signaling, coroutineScope)
    },
) {
    private var netplayTransport: NetplayTransport? = null
    private var netplayInputCollectorJob: Job? = null
    private var netplayControlCollectorJob: Job? = null
    private var netplayBinaryCollectorJob: Job? = null
    private var netplayBinaryMessages: Channel<BinaryMessage>? = null
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
     * Set up the netplay transport, connect to the WebSocket, configure the
     * controller for netplay, and start collecting remote inputs.
     *
     * Initial host/client state exchange intentionally happens later in
     * [syncInitialNetplayState], after the core has started and reached the
     * same readiness gate used by normal save-state restore.
     */
    suspend fun setupNetplayTransport(
        sessionId: String,
        localPort: Int,
        inputDelay: Int,
    ) {
        val signaling = NetplaySignaling(
            apiClient = apiClient,
            engineFactory = engineFactory,
            dispatchers = dispatchers,
            scope = scope,
            sessionId = sessionId,
        )
        val transport = transportFactory(signaling, scope)
        this.netplayTransport = transport

        val binaryMessages = Channel<BinaryMessage>(Channel.UNLIMITED)
        netplayBinaryMessages = binaryMessages
        netplayBinaryCollectorJob = scope.launch(dispatchers.io, start = CoroutineStart.UNDISPATCHED) {
            transport.remoteBinary.collect { msg ->
                binaryMessages.send(msg)
            }
        }

        // Connect to the WebSocket
        transport.connect()

        val inputBuffer = NetplayInputBuffer()

        // Configure netplay mode before start(); platform controllers choose
        // their emulation loop when start() is called. Lockstep input sync is
        // activated separately after initial state sync.
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

    /**
     * Exchange initial netplay state after the core has reached post-start
     * readiness. Host serializes and sends its authoritative state; client
     * receives and applies that state. Returns an error instead of silently
     * continuing desynced.
     */
    suspend fun syncInitialNetplayState(isHost: Boolean): NetplayInitialStateSyncResult {
        val transport = netplayTransport
            ?: return NetplayInitialStateSyncResult.Error("Netplay transport is not connected")
        val binaryMessages = netplayBinaryMessages
            ?: return NetplayInitialStateSyncResult.Error("Netplay binary channel is not ready")

        return try {
            if (isHost) {
                if (!waitForClientReady(binaryMessages)) {
                    return NetplayInitialStateSyncResult.Error("Timed out waiting for peer readiness")
                }

                println("[Netplay] Host serializing initial state after core readiness")
                val stateData = libretroController.serialize()
                    ?: return NetplayInitialStateSyncResult.Error("Core did not provide initial state")
                if (!sendStateChunks(transport, stateData)) {
                    return NetplayInitialStateSyncResult.Error("Timed out sending host state")
                }
                if (!waitForStateApplied(binaryMessages)) {
                    return NetplayInitialStateSyncResult.Error("Timed out waiting for peer to apply initial state")
                }
                if (!sendBinaryReliable(transport, com.spela.player.netplay.NetplayProtocol.encodeSyncComplete())) {
                    return NetplayInitialStateSyncResult.Error("Timed out confirming netplay sync completion")
                }
                NetplayInitialStateSyncResult.Success
            } else {
                println("[Netplay] Client waiting for host initial state after core readiness")
                if (!sendBinaryReliable(transport, com.spela.player.netplay.NetplayProtocol.encodeClientReady())) {
                    return NetplayInitialStateSyncResult.Error("Timed out signaling peer readiness")
                }

                val stateData = receiveStateChunks(binaryMessages)
                    ?: return NetplayInitialStateSyncResult.Error("Timed out waiting for host state")

                val loaded = libretroController.unserialize(stateData)
                if (loaded) {
                    if (!sendBinaryReliable(transport, com.spela.player.netplay.NetplayProtocol.encodeStateApplied())) {
                        return NetplayInitialStateSyncResult.Error("Timed out confirming host state")
                    }
                    if (!waitForSyncComplete(binaryMessages)) {
                        return NetplayInitialStateSyncResult.Error("Timed out waiting for host sync confirmation")
                    }
                    println("[Netplay] Client applied host initial state")
                    NetplayInitialStateSyncResult.Success
                } else {
                    NetplayInitialStateSyncResult.Error("Host state could not be loaded by the core")
                }
            }
        } finally {
            stopInitialBinaryCapture()
        }
    }

    private suspend fun waitForClientReady(messages: ReceiveChannel<BinaryMessage>): Boolean {
        return withTimeoutOrNull(INITIAL_NETPLAY_SYNC_TIMEOUT_MS) {
            while (true) {
                val msg = messages.receiveCatching().getOrNull() ?: return@withTimeoutOrNull false
                if (com.spela.player.netplay.NetplayProtocol.isClientReady(msg.data)) {
                    return@withTimeoutOrNull true
                }
            }
        } == true
    }

    private suspend fun waitForStateApplied(messages: ReceiveChannel<BinaryMessage>): Boolean {
        return withTimeoutOrNull(INITIAL_NETPLAY_SYNC_TIMEOUT_MS) {
            while (true) {
                val msg = messages.receiveCatching().getOrNull() ?: return@withTimeoutOrNull false
                if (com.spela.player.netplay.NetplayProtocol.isStateApplied(msg.data)) {
                    return@withTimeoutOrNull true
                }
            }
        } == true
    }

    private suspend fun waitForSyncComplete(messages: ReceiveChannel<BinaryMessage>): Boolean {
        return withTimeoutOrNull(INITIAL_NETPLAY_SYNC_TIMEOUT_MS) {
            while (true) {
                val msg = messages.receiveCatching().getOrNull() ?: return@withTimeoutOrNull false
                if (com.spela.player.netplay.NetplayProtocol.isSyncComplete(msg.data)) {
                    return@withTimeoutOrNull true
                }
            }
        } == true
    }

    private suspend fun sendBinaryReliable(transport: NetplayTransport, data: ByteArray): Boolean {
        return withTimeoutOrNull(INITIAL_NETPLAY_SYNC_TIMEOUT_MS) {
            try {
                transport.sendBinaryReliable(data)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        } == true
    }

    private suspend fun sendStateChunks(transport: NetplayTransport, stateData: ByteArray): Boolean {
        val chunkSize = 16_384 // 16 KB chunks
        val totalChunks = maxOf(1, (stateData.size + chunkSize - 1) / chunkSize)
        return withTimeoutOrNull(INITIAL_NETPLAY_SYNC_TIMEOUT_MS) {
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
                try {
                    transport.sendBinaryReliable(encoded)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return@withTimeoutOrNull false
                }
            }
            true
        } == true
    }

    private suspend fun receiveStateChunks(messages: ReceiveChannel<BinaryMessage>): ByteArray? {
        val receivedChunks = mutableMapOf<Int, ByteArray>()
        var totalChunks = -1
        var totalSize = -1

        val receivedAll = withTimeoutOrNull(INITIAL_NETPLAY_SYNC_TIMEOUT_MS) {
            while (receivedChunks.size < totalChunks || totalChunks < 0) {
                val msg = messages.receiveCatching().getOrNull() ?: return@withTimeoutOrNull false
                val chunk = com.spela.player.netplay.NetplayProtocol.decodeStateChunk(msg.data)
                if (chunk != null) {
                    totalChunks = chunk.totalChunks
                    totalSize = chunk.totalSize
                    receivedChunks[chunk.chunkIndex] = chunk.data
                }
            }
            true
        } == true

        if (!receivedAll) return null

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

    private fun stopInitialBinaryCapture() {
        netplayBinaryCollectorJob?.cancel()
        netplayBinaryCollectorJob = null
        netplayBinaryMessages?.close()
        netplayBinaryMessages = null
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
        stopInitialBinaryCapture()
        netplayTransport?.disconnect()
        netplayTransport = null
    }
}

sealed class NetplayInitialStateSyncResult {
    data object Success : NetplayInitialStateSyncResult()
    data class Error(val message: String) : NetplayInitialStateSyncResult()
}
