package com.spela.player.netplay

/**
 * Binary encode/decode utilities for netplay wire protocol.
 * All multi-byte integers use big-endian byte order.
 */
object NetplayProtocol {
    const val MSG_STATE_CHUNK: Byte = 0x01
    const val MSG_INPUT_FRAME: Byte = 0x02
    const val MSG_DESYNC_CHECK: Byte = 0x03
    /** Client → host signal: client has subscribed to remoteBinary and is ready
     * to receive state chunks. The host waits for this before sending state to
     * close the race where the host's chunks would otherwise arrive before the
     * client started collecting (silently dropped). See #1006. */
    const val MSG_CLIENT_READY: Byte = 0x04
    /** Client → host signal: client received and applied the host state. The
     * host waits for this before entering lockstep input sync so queued-but-not-
     * delivered state chunks cannot be mistaken for a synchronized session. */
    const val MSG_STATE_APPLIED: Byte = 0x05
    /** Host → client signal: host observed the state-applied ACK and both sides
     * may enter lockstep input sync. */
    const val MSG_SYNC_COMPLETE: Byte = 0x06
    private val CLIENT_READY_PAYLOAD = byteArrayOf(MSG_CLIENT_READY)
    private val STATE_APPLIED_PAYLOAD = byteArrayOf(MSG_STATE_APPLIED)
    private val SYNC_COMPLETE_PAYLOAD = byteArrayOf(MSG_SYNC_COMPLETE)

    /** Returns the single-byte client-ready signal. */
    fun encodeClientReady(): ByteArray = CLIENT_READY_PAYLOAD.copyOf()

    /** True if [data] is the client-ready signal. */
    fun isClientReady(data: ByteArray): Boolean = data.size == 1 && data[0] == MSG_CLIENT_READY

    /** Returns the single-byte client state-applied acknowledgement. */
    fun encodeStateApplied(): ByteArray = STATE_APPLIED_PAYLOAD.copyOf()

    /** True if [data] is the client state-applied acknowledgement. */
    fun isStateApplied(data: ByteArray): Boolean = data.size == 1 && data[0] == MSG_STATE_APPLIED

    /** Returns the single-byte host sync-complete signal. */
    fun encodeSyncComplete(): ByteArray = SYNC_COMPLETE_PAYLOAD.copyOf()

    /** True if [data] is the host sync-complete signal. */
    fun isSyncComplete(data: ByteArray): Boolean = data.size == 1 && data[0] == MSG_SYNC_COMPLETE

    private const val INPUT_FRAME_SIZE = 12
    private const val DESYNC_CHECK_SIZE = 9
    private const val STATE_CHUNK_HEADER_SIZE = 13

    /**
     * Encode an input frame message (12 bytes total).
     * [1: type] [4: frame uint32 BE] [1: port] [2: buttons uint16 BE] [2: analogX int16 BE] [2: analogY int16 BE]
     */
    fun encodeInputFrame(frame: Long, port: Int, input: InputState): ByteArray {
        val data = ByteArray(INPUT_FRAME_SIZE)
        data[0] = MSG_INPUT_FRAME
        putUInt32BE(data, 1, frame)
        data[5] = port.toByte()
        putUInt16BE(data, 6, input.buttonState.toInt())
        putInt16BE(data, 8, input.analogX)
        putInt16BE(data, 10, input.analogY)
        return data
    }

    /**
     * Decode an input frame message.
     * Returns Triple(frame, port, InputState) or null if data is invalid.
     */
    fun decodeInputFrame(data: ByteArray): Triple<Long, Int, InputState>? {
        if (data.size < INPUT_FRAME_SIZE) return null
        if (data[0] != MSG_INPUT_FRAME) return null
        val frame = getUInt32BE(data, 1)
        val port = data[5].toInt() and 0xFF
        val buttons = getUInt16BE(data, 6).toUShort()
        val analogX = getInt16BE(data, 8)
        val analogY = getInt16BE(data, 10)
        return Triple(frame, port, InputState(buttons, analogX, analogY))
    }

    /**
     * Encode a state chunk message (variable length).
     * [1: type] [4: chunkIndex uint32] [4: totalChunks uint32] [4: totalSize uint32] [N: data]
     */
    fun encodeStateChunk(chunkIndex: Int, totalChunks: Int, totalSize: Int, data: ByteArray): ByteArray {
        val result = ByteArray(STATE_CHUNK_HEADER_SIZE + data.size)
        result[0] = MSG_STATE_CHUNK
        putUInt32BE(result, 1, chunkIndex.toLong())
        putUInt32BE(result, 5, totalChunks.toLong())
        putUInt32BE(result, 9, totalSize.toLong())
        data.copyInto(result, STATE_CHUNK_HEADER_SIZE)
        return result
    }

    /**
     * Decode a state chunk message.
     * Returns StateChunk or null if data is invalid.
     */
    fun decodeStateChunk(data: ByteArray): StateChunk? {
        if (data.size < STATE_CHUNK_HEADER_SIZE) return null
        if (data[0] != MSG_STATE_CHUNK) return null
        val chunkIndex = getUInt32BE(data, 1).toInt()
        val totalChunks = getUInt32BE(data, 5).toInt()
        val totalSize = getUInt32BE(data, 9).toInt()
        val chunkData = data.copyOfRange(STATE_CHUNK_HEADER_SIZE, data.size)
        return StateChunk(chunkIndex, totalChunks, totalSize, chunkData)
    }

    /**
     * Encode a desync check message (9 bytes).
     * [1: type] [4: frame uint32 BE] [4: crc32 uint32 BE]
     */
    fun encodeDesyncCheck(frame: Long, crc32: Long): ByteArray {
        val data = ByteArray(DESYNC_CHECK_SIZE)
        data[0] = MSG_DESYNC_CHECK
        putUInt32BE(data, 1, frame)
        putUInt32BE(data, 5, crc32)
        return data
    }

    /**
     * Decode a desync check message.
     * Returns Pair(frame, crc32) or null if data is invalid.
     */
    fun decodeDesyncCheck(data: ByteArray): Pair<Long, Long>? {
        if (data.size < DESYNC_CHECK_SIZE) return null
        if (data[0] != MSG_DESYNC_CHECK) return null
        val frame = getUInt32BE(data, 1)
        val crc32 = getUInt32BE(data, 5)
        return Pair(frame, crc32)
    }

    // Big-endian encoding helpers

    private fun putUInt32BE(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = ((value shr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    private fun getUInt32BE(buf: ByteArray, offset: Int): Long {
        return ((buf[offset].toLong() and 0xFF) shl 24) or
            ((buf[offset + 1].toLong() and 0xFF) shl 16) or
            ((buf[offset + 2].toLong() and 0xFF) shl 8) or
            (buf[offset + 3].toLong() and 0xFF)
    }

    private fun putUInt16BE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }

    private fun getUInt16BE(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 8) or
            (buf[offset + 1].toInt() and 0xFF)
    }

    private fun putInt16BE(buf: ByteArray, offset: Int, value: Short) {
        buf[offset] = ((value.toInt() shr 8) and 0xFF).toByte()
        buf[offset + 1] = (value.toInt() and 0xFF).toByte()
    }

    private fun getInt16BE(buf: ByteArray, offset: Int): Short {
        return (((buf[offset].toInt() and 0xFF) shl 8) or
            (buf[offset + 1].toInt() and 0xFF)).toShort()
    }
}

data class InputState(
    val buttonState: UShort = 0u,
    val analogX: Short = 0,
    val analogY: Short = 0,
)

data class StateChunk(
    val chunkIndex: Int,
    val totalChunks: Int,
    val totalSize: Int,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is StateChunk) return false
        return chunkIndex == other.chunkIndex &&
            totalChunks == other.totalChunks &&
            totalSize == other.totalSize &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + totalSize
        result = 31 * result + data.contentHashCode()
        return result
    }
}
