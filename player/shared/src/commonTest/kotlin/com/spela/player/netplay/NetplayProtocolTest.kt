package com.spela.player.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetplayProtocolTest {

    // -- INPUT_FRAME encode/decode roundtrip --

    @Test
    fun inputFrameRoundtripBasic() {
        val input = InputState(buttonState = 0x00FFu, analogX = 100, analogY = -50)
        val encoded = NetplayProtocol.encodeInputFrame(frame = 42, port = 0, input = input)
        val decoded = NetplayProtocol.decodeInputFrame(encoded)

        assertNotNull(decoded)
        val (frame, port, decodedInput) = decoded
        assertEquals(42L, frame)
        assertEquals(0, port)
        assertEquals(input, decodedInput)
    }

    @Test
    fun inputFrameRoundtripZeroValues() {
        val input = InputState(buttonState = 0u, analogX = 0, analogY = 0)
        val encoded = NetplayProtocol.encodeInputFrame(frame = 0, port = 0, input = input)
        val decoded = NetplayProtocol.decodeInputFrame(encoded)

        assertNotNull(decoded)
        val (frame, port, decodedInput) = decoded
        assertEquals(0L, frame)
        assertEquals(0, port)
        assertEquals(input, decodedInput)
    }

    @Test
    fun inputFrameRoundtripMaxValues() {
        val input = InputState(buttonState = UShort.MAX_VALUE, analogX = Short.MAX_VALUE, analogY = Short.MAX_VALUE)
        val encoded = NetplayProtocol.encodeInputFrame(frame = 0xFFFFFFFFL, port = 255, input = input)
        val decoded = NetplayProtocol.decodeInputFrame(encoded)

        assertNotNull(decoded)
        val (frame, port, decodedInput) = decoded
        assertEquals(0xFFFFFFFFL, frame)
        assertEquals(255, port)
        assertEquals(UShort.MAX_VALUE, decodedInput.buttonState)
        assertEquals(Short.MAX_VALUE, decodedInput.analogX)
        assertEquals(Short.MAX_VALUE, decodedInput.analogY)
    }

    @Test
    fun inputFrameRoundtripNegativeAnalog() {
        val input = InputState(buttonState = 1u, analogX = Short.MIN_VALUE, analogY = -1)
        val encoded = NetplayProtocol.encodeInputFrame(frame = 1000, port = 1, input = input)
        val decoded = NetplayProtocol.decodeInputFrame(encoded)

        assertNotNull(decoded)
        val (_, _, decodedInput) = decoded
        assertEquals(Short.MIN_VALUE, decodedInput.analogX)
        assertEquals((-1).toShort(), decodedInput.analogY)
    }

    @Test
    fun inputFrameRoundtripAllButtonsPressed() {
        val input = InputState(buttonState = 0xFFFFu, analogX = -128, analogY = 127)
        val encoded = NetplayProtocol.encodeInputFrame(frame = 999999, port = 3, input = input)
        val decoded = NetplayProtocol.decodeInputFrame(encoded)

        assertNotNull(decoded)
        val (frame, port, decodedInput) = decoded
        assertEquals(999999L, frame)
        assertEquals(3, port)
        assertEquals(0xFFFFu.toUShort(), decodedInput.buttonState)
    }

    @Test
    fun inputFrameEncodedSizeIs12Bytes() {
        val input = InputState()
        val encoded = NetplayProtocol.encodeInputFrame(frame = 0, port = 0, input = input)
        assertEquals(12, encoded.size)
    }

    @Test
    fun inputFrameTypeByteIsCorrect() {
        val encoded = NetplayProtocol.encodeInputFrame(frame = 0, port = 0, input = InputState())
        assertEquals(NetplayProtocol.MSG_INPUT_FRAME, encoded[0])
    }

    @Test
    fun inputFrameDecodeReturnsNullForTooShortData() {
        val decoded = NetplayProtocol.decodeInputFrame(ByteArray(5))
        assertNull(decoded)
    }

    @Test
    fun inputFrameDecodeReturnsNullForWrongType() {
        val data = ByteArray(12)
        data[0] = NetplayProtocol.MSG_STATE_CHUNK
        val decoded = NetplayProtocol.decodeInputFrame(data)
        assertNull(decoded)
    }

    @Test
    fun inputFrameDecodeReturnsNullForEmptyData() {
        val decoded = NetplayProtocol.decodeInputFrame(ByteArray(0))
        assertNull(decoded)
    }

    // -- STATE_CHUNK encode/decode roundtrip --

    @Test
    fun stateChunkRoundtripBasic() {
        val chunkData = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = NetplayProtocol.encodeStateChunk(
            chunkIndex = 0,
            totalChunks = 3,
            totalSize = 15,
            data = chunkData,
        )
        val decoded = NetplayProtocol.decodeStateChunk(encoded)

        assertNotNull(decoded)
        assertEquals(0, decoded.chunkIndex)
        assertEquals(3, decoded.totalChunks)
        assertEquals(15, decoded.totalSize)
        assertTrue(chunkData.contentEquals(decoded.data))
    }

    @Test
    fun stateChunkRoundtripEmptyData() {
        val encoded = NetplayProtocol.encodeStateChunk(
            chunkIndex = 0,
            totalChunks = 1,
            totalSize = 0,
            data = ByteArray(0),
        )
        val decoded = NetplayProtocol.decodeStateChunk(encoded)

        assertNotNull(decoded)
        assertEquals(0, decoded.chunkIndex)
        assertEquals(1, decoded.totalChunks)
        assertEquals(0, decoded.totalSize)
        assertEquals(0, decoded.data.size)
    }

    @Test
    fun stateChunkRoundtripLargeData() {
        val chunkData = ByteArray(4096) { (it % 256).toByte() }
        val encoded = NetplayProtocol.encodeStateChunk(
            chunkIndex = 5,
            totalChunks = 10,
            totalSize = 40960,
            data = chunkData,
        )
        val decoded = NetplayProtocol.decodeStateChunk(encoded)

        assertNotNull(decoded)
        assertEquals(5, decoded.chunkIndex)
        assertEquals(10, decoded.totalChunks)
        assertEquals(40960, decoded.totalSize)
        assertTrue(chunkData.contentEquals(decoded.data))
    }

    @Test
    fun stateChunkEncodedSizeIsHeaderPlusData() {
        val chunkData = byteArrayOf(1, 2, 3)
        val encoded = NetplayProtocol.encodeStateChunk(0, 1, 3, chunkData)
        // Header: 1 (type) + 4 (chunkIndex) + 4 (totalChunks) + 4 (totalSize) = 13
        assertEquals(13 + 3, encoded.size)
    }

    @Test
    fun stateChunkTypeByteIsCorrect() {
        val encoded = NetplayProtocol.encodeStateChunk(0, 1, 0, ByteArray(0))
        assertEquals(NetplayProtocol.MSG_STATE_CHUNK, encoded[0])
    }

    @Test
    fun stateChunkDecodeReturnsNullForTooShortData() {
        val decoded = NetplayProtocol.decodeStateChunk(ByteArray(10))
        assertNull(decoded)
    }

    @Test
    fun stateChunkDecodeReturnsNullForWrongType() {
        val data = ByteArray(13)
        data[0] = NetplayProtocol.MSG_INPUT_FRAME
        val decoded = NetplayProtocol.decodeStateChunk(data)
        assertNull(decoded)
    }

    // -- DESYNC_CHECK encode/decode roundtrip --

    @Test
    fun desyncCheckRoundtripBasic() {
        val encoded = NetplayProtocol.encodeDesyncCheck(frame = 1000, crc32 = 0xDEADBEEF)
        val decoded = NetplayProtocol.decodeDesyncCheck(encoded)

        assertNotNull(decoded)
        assertEquals(1000L, decoded.first)
        assertEquals(0xDEADBEEFL, decoded.second)
    }

    @Test
    fun desyncCheckRoundtripZeroValues() {
        val encoded = NetplayProtocol.encodeDesyncCheck(frame = 0, crc32 = 0)
        val decoded = NetplayProtocol.decodeDesyncCheck(encoded)

        assertNotNull(decoded)
        assertEquals(0L, decoded.first)
        assertEquals(0L, decoded.second)
    }

    @Test
    fun desyncCheckRoundtripMaxValues() {
        val encoded = NetplayProtocol.encodeDesyncCheck(frame = 0xFFFFFFFFL, crc32 = 0xFFFFFFFFL)
        val decoded = NetplayProtocol.decodeDesyncCheck(encoded)

        assertNotNull(decoded)
        assertEquals(0xFFFFFFFFL, decoded.first)
        assertEquals(0xFFFFFFFFL, decoded.second)
    }

    @Test
    fun desyncCheckEncodedSizeIs9Bytes() {
        val encoded = NetplayProtocol.encodeDesyncCheck(frame = 0, crc32 = 0)
        assertEquals(9, encoded.size)
    }

    @Test
    fun desyncCheckTypeByteIsCorrect() {
        val encoded = NetplayProtocol.encodeDesyncCheck(frame = 0, crc32 = 0)
        assertEquals(NetplayProtocol.MSG_DESYNC_CHECK, encoded[0])
    }

    @Test
    fun desyncCheckDecodeReturnsNullForTooShortData() {
        val decoded = NetplayProtocol.decodeDesyncCheck(ByteArray(5))
        assertNull(decoded)
    }

    @Test
    fun desyncCheckDecodeReturnsNullForWrongType() {
        val data = ByteArray(9)
        data[0] = NetplayProtocol.MSG_INPUT_FRAME
        val decoded = NetplayProtocol.decodeDesyncCheck(data)
        assertNull(decoded)
    }

    @Test
    fun desyncCheckDecodeReturnsNullForEmptyData() {
        val decoded = NetplayProtocol.decodeDesyncCheck(ByteArray(0))
        assertNull(decoded)
    }

    // -- Cross-type decode rejection --

    @Test
    fun inputFrameDataNotDecodedAsStateChunk() {
        val encoded = NetplayProtocol.encodeInputFrame(42, 0, InputState())
        assertNull(NetplayProtocol.decodeStateChunk(encoded))
    }

    @Test
    fun stateChunkDataNotDecodedAsInputFrame() {
        val encoded = NetplayProtocol.encodeStateChunk(0, 1, 5, byteArrayOf(1, 2, 3, 4, 5))
        assertNull(NetplayProtocol.decodeInputFrame(encoded))
    }

    @Test
    fun desyncCheckDataNotDecodedAsInputFrame() {
        val encoded = NetplayProtocol.encodeDesyncCheck(100, 0xABCD)
        assertNull(NetplayProtocol.decodeInputFrame(encoded))
    }

    // -- Big-endian byte order verification --

    @Test
    fun inputFrameUseBigEndianByteOrder() {
        val input = InputState(buttonState = 0x0102u, analogX = 0x0304, analogY = 0x0506.toShort())
        val encoded = NetplayProtocol.encodeInputFrame(frame = 0x01020304, port = 5, input = input)

        // Verify frame bytes are in big-endian order
        assertEquals(0x01.toByte(), encoded[1])
        assertEquals(0x02.toByte(), encoded[2])
        assertEquals(0x03.toByte(), encoded[3])
        assertEquals(0x04.toByte(), encoded[4])

        // Verify port
        assertEquals(5.toByte(), encoded[5])

        // Verify buttons in big-endian
        assertEquals(0x01.toByte(), encoded[6])
        assertEquals(0x02.toByte(), encoded[7])

        // Verify analogX in big-endian
        assertEquals(0x03.toByte(), encoded[8])
        assertEquals(0x04.toByte(), encoded[9])

        // Verify analogY in big-endian
        assertEquals(0x05.toByte(), encoded[10])
        assertEquals(0x06.toByte(), encoded[11])
    }
}
