package com.spela.player.netplay

import kotlinx.coroutines.flow.Flow

/**
 * A remote input received from another player.
 */
data class RemoteInput(val frame: Long, val port: Int, val input: InputState)

/**
 * A binary message received from another player.
 */
data class BinaryMessage(val data: ByteArray, val fromPort: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is BinaryMessage) return false
        return data.contentEquals(other.data) && fromPort == other.fromPort
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + fromPort
        return result
    }
}

/**
 * Netplay player info used in control messages.
 */
data class NetplayPlayer(
    val userId: String,
    val username: String,
    val port: Int,
)

/**
 * Real-time transport session status, distinct from the REST API session status
 * in [com.spela.player.domain.model.NetplaySessionStatus].
 * These values are exchanged over the WebSocket during gameplay.
 */
enum class TransportSessionStatus {
    WAITING,
    SYNCING,
    PLAYING,
    PAUSED,
    FINISHED,
}

/**
 * Control messages for netplay session lifecycle.
 */
sealed interface ControlMessage {
    data class PlayerJoined(val player: NetplayPlayer) : ControlMessage
    data class PlayerLeft(val userId: String) : ControlMessage
    data class SessionStatus(val status: TransportSessionStatus) : ControlMessage
    data class ConnectionMode(val userId: String, val mode: String) : ControlMessage
    data object AllReady : ControlMessage
}

/**
 * Transport abstraction for netplay data exchange.
 *
 * Implementations handle the actual network communication (WebSocket relay, P2P, etc.)
 * while exposing a uniform interface for the emulation loop to send/receive inputs.
 */
interface NetplayTransport {
    /**
     * Send local input for a frame on a given port.
     */
    fun sendInput(frame: Long, port: Int, input: InputState)

    /**
     * Send raw binary data, optionally targeting a specific port's player.
     */
    fun sendBinary(data: ByteArray, targetPort: Int? = null)

    /**
     * Flow of remote player inputs as they arrive.
     */
    val remoteInputs: Flow<RemoteInput>

    /**
     * Flow of raw binary messages from remote players.
     */
    val remoteBinary: Flow<BinaryMessage>

    /**
     * Flow of session lifecycle control messages.
     */
    val controlMessages: Flow<ControlMessage>

    /**
     * Send a control message to the session.
     */
    fun sendControl(message: ControlMessage)

    /**
     * Connect to the netplay session.
     */
    suspend fun connect()

    /**
     * Disconnect from the netplay session.
     */
    fun disconnect()
}
