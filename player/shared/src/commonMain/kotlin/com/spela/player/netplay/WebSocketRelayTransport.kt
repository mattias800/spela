package com.spela.player.netplay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * NetplayTransport implementation that relays all data through the server WebSocket.
 *
 * This is the Phase 1 transport: all input frames and binary data are sent through
 * the server's WebSocket relay. No P2P connections are used.
 *
 * The signaling layer handles the WebSocket connection and message routing.
 * This transport encodes/decodes binary frames using [NetplayProtocol] and
 * exposes typed flows for the emulation loop to consume.
 */
class WebSocketRelayTransport(
    private val signaling: NetplaySignaling,
    private val scope: CoroutineScope,
) : NetplayTransport {

    private val _remoteInputs = MutableSharedFlow<RemoteInput>(extraBufferCapacity = 256)
    override val remoteInputs: Flow<RemoteInput> = _remoteInputs.asSharedFlow()

    private val _remoteBinary = MutableSharedFlow<BinaryMessage>(extraBufferCapacity = 64)
    override val remoteBinary: Flow<BinaryMessage> = _remoteBinary.asSharedFlow()

    override val controlMessages: Flow<ControlMessage> = signaling.controlMessages

    private var signalingBinaryCollectorJob: Job? = null

    override fun sendInput(frame: Long, port: Int, input: InputState) {
        val encoded = NetplayProtocol.encodeInputFrame(frame, port, input)
        signaling.sendBinary(encoded)
    }

    override fun sendBinary(data: ByteArray, targetPort: Int?) {
        signaling.sendBinary(data)
    }

    override suspend fun sendBinaryReliable(data: ByteArray, targetPort: Int?) {
        signaling.sendBinaryReliable(data)
    }

    override fun sendControl(message: ControlMessage) {
        throw UnsupportedOperationException("Server relay does not support sending control messages")
    }

    override suspend fun connect() {
        if (signalingBinaryCollectorJob?.isActive != true) {
            // Subscribe before the WebSocket can receive one-shot readiness
            // messages or state chunks.
            signalingBinaryCollectorJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                signaling.binaryMessages.collect { data ->
                    handleBinaryMessage(data)
                }
            }
        }

        signaling.connect()
    }

    override fun disconnect() {
        signalingBinaryCollectorJob?.cancel()
        signalingBinaryCollectorJob = null
        signaling.disconnect()
    }

    internal suspend fun handleBinaryMessage(data: ByteArray) {
        if (data.isEmpty()) return

        when (data[0]) {
            NetplayProtocol.MSG_INPUT_FRAME -> {
                val decoded = NetplayProtocol.decodeInputFrame(data) ?: return
                val (frame, port, input) = decoded
                _remoteInputs.tryEmit(RemoteInput(frame, port, input))
            }
            NetplayProtocol.MSG_STATE_CHUNK,
            NetplayProtocol.MSG_DESYNC_CHECK,
            NetplayProtocol.MSG_CLIENT_READY,
            NetplayProtocol.MSG_STATE_APPLIED,
            NetplayProtocol.MSG_SYNC_COMPLETE -> {
                // Forward as raw binary for higher-level handling
                // fromPort is unknown in relay mode; use -1 as sentinel
                _remoteBinary.emit(BinaryMessage(data, -1))
            }
        }
    }
}
