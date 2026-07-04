package com.spela.player.presentation.viewmodel

import com.spela.player.netplay.BinaryMessage
import com.spela.player.netplay.ControlMessage
import com.spela.player.netplay.InputState
import com.spela.player.netplay.NetplayProtocol
import com.spela.player.netplay.NetplayTransport
import com.spela.player.netplay.RemoteInput
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelNetplayStartupTest {

    private lateinit var builder: EmulationViewModelTestBuilder

    @BeforeTest
    fun setup() {
        builder = EmulationViewModelTestBuilder()
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        builder.tearDown()
        Dispatchers.resetMain()
    }

    @Test
    fun netplayHostSerializesInitialStateAfterCoreReadiness() = runTest {
        val transport = FakeNetplayTransport(
            binaryMessagesOnConnect = listOf(NetplayProtocol.encodeClientReady()),
            emitStateAppliedAfterLastChunk = true,
        )
        builder.netplayTransportFactory = { _, _ -> transport }
        builder.libretroController.serializeResult = byteArrayOf(10, 20, 30)
        val vm = builder.build()

        vm.onIntent(
            EmulationIntent.StartGame(
                gameId = "game1",
                netplaySessionId = "np-1",
                netplayLocalPort = 0,
                netplayInputDelay = 3,
                netplayIsHost = true,
            ),
        )
        builder.advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertFalse(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.setNetplayModeCallCount)
        assertEquals(1, builder.libretroController.serializeCallCount)
        assertEquals(1, builder.libretroController.startNetplayInputSyncCallCount)
        assertTrue(
            transport.sentBinary.any { NetplayProtocol.decodeStateChunk(it) != null },
            "host must send serialized state chunks after readiness",
        )
        assertTrue(
            transport.sentBinary.any { NetplayProtocol.isSyncComplete(it) },
            "host must confirm sync completion after the client applies state",
        )
        assertEquals(0, transport.unreliableBinarySendCount)

        val calls = builder.libretroController.calls
        assertCallOrder(calls, "setNetplayMode", "start")
        assertCallOrder(calls, "start", "pause")
        assertCallOrder(calls, "pause", "serialize")
        assertCallOrder(calls, "serialize", "startNetplayInputSync")
        assertCallOrder(calls, "startNetplayInputSync", "resume")
    }

    @Test
    fun netplayClientAppliesHostStateAfterCoreReadiness() = runTest {
        val hostState = byteArrayOf(55, 66, 77)
        val transport = FakeNetplayTransport(
            binaryMessagesAfterClientReady = listOf(
                NetplayProtocol.encodeStateChunk(
                    chunkIndex = 0,
                    totalChunks = 1,
                    totalSize = hostState.size,
                    data = hostState,
                ),
            ),
            binaryMessagesAfterStateApplied = listOf(NetplayProtocol.encodeSyncComplete()),
        )
        builder.netplayTransportFactory = { _, _ -> transport }
        val vm = builder.build()

        vm.onIntent(
            EmulationIntent.StartGame(
                gameId = "game1",
                netplaySessionId = "np-1",
                netplayLocalPort = 1,
                netplayInputDelay = 3,
                netplayIsHost = false,
            ),
        )
        builder.advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertFalse(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.setNetplayModeCallCount)
        assertEquals(0, builder.libretroController.serializeCallCount)
        assertEquals(1, builder.libretroController.unserializeCallCount)
        assertTrue(builder.libretroController.lastUnserializeData.contentEquals(hostState))
        assertEquals(1, builder.libretroController.startNetplayInputSyncCallCount)
        assertTrue(
            transport.sentBinary.any { NetplayProtocol.isClientReady(it) },
            "client must signal readiness before waiting for host state",
        )
        assertTrue(
            transport.sentBinary.any { NetplayProtocol.isStateApplied(it) },
            "client must acknowledge that the host state was applied",
        )
        assertEquals(0, transport.unreliableBinarySendCount)

        val calls = builder.libretroController.calls
        assertCallOrder(calls, "setNetplayMode", "start")
        assertCallOrder(calls, "start", "pause")
        assertCallOrder(calls, "pause", "unserialize")
        assertCallOrder(calls, "unserialize", "startNetplayInputSync")
        assertCallOrder(calls, "startNetplayInputSync", "resume")
    }

    @Test
    fun netplayHostUsesReliableSendForLargeInitialState() = runTest {
        val transport = FakeNetplayTransport(
            binaryMessagesOnConnect = listOf(NetplayProtocol.encodeClientReady()),
            emitStateAppliedAfterLastChunk = true,
        )
        builder.netplayTransportFactory = { _, _ -> transport }
        val stateData = ByteArray(16_384 * 70 + 123) { (it % 251).toByte() }
        builder.libretroController.serializeResult = stateData
        val vm = builder.build()

        vm.onIntent(
            EmulationIntent.StartGame(
                gameId = "game1",
                netplaySessionId = "np-1",
                netplayLocalPort = 0,
                netplayInputDelay = 3,
                netplayIsHost = true,
            ),
        )
        builder.advanceTimeBy(100)

        val stateChunks = transport.sentBinary.mapNotNull { NetplayProtocol.decodeStateChunk(it) }
        assertEquals(71, stateChunks.size)
        assertEquals(0, transport.unreliableBinarySendCount)
        assertTrue(vm.state.value.isRunning)
    }

    @Test
    fun netplayHostWaitsForPeerStateAppliedAckBeforeInputSync() = runTest {
        val transport = FakeNetplayTransport(
            binaryMessagesOnConnect = listOf(NetplayProtocol.encodeClientReady()),
        )
        builder.netplayTransportFactory = { _, _ -> transport }
        builder.libretroController.serializeResult = byteArrayOf(1, 2, 3)
        val vm = builder.build()

        vm.onIntent(
            EmulationIntent.StartGame(
                gameId = "game1",
                netplaySessionId = "np-1",
                netplayLocalPort = 0,
                netplayInputDelay = 3,
                netplayIsHost = true,
            ),
        )
        builder.advanceTimeBy(10_100)

        assertEquals(0, builder.libretroController.startNetplayInputSyncCallCount)
        assertFalse(vm.state.value.isRunning)
        assertTrue(vm.state.value.error.orEmpty().contains("apply initial state"))
        assertEquals(vm.state.value.error, vm.state.value.fatalError)
    }

    @Test
    fun netplayClientWaitsForHostSyncCompleteBeforeInputSync() = runTest {
        val hostState = byteArrayOf(55, 66, 77)
        val transport = FakeNetplayTransport(
            binaryMessagesAfterClientReady = listOf(
                NetplayProtocol.encodeStateChunk(
                    chunkIndex = 0,
                    totalChunks = 1,
                    totalSize = hostState.size,
                    data = hostState,
                ),
            ),
        )
        builder.netplayTransportFactory = { _, _ -> transport }
        val vm = builder.build()

        vm.onIntent(
            EmulationIntent.StartGame(
                gameId = "game1",
                netplaySessionId = "np-1",
                netplayLocalPort = 1,
                netplayInputDelay = 3,
                netplayIsHost = false,
            ),
        )
        builder.advanceTimeBy(10_100)

        assertEquals(0, builder.libretroController.startNetplayInputSyncCallCount)
        assertFalse(vm.state.value.isRunning)
        assertTrue(vm.state.value.error.orEmpty().contains("host sync confirmation"))
        assertEquals(vm.state.value.error, vm.state.value.fatalError)
    }

    @Test
    fun netplaySyncFailureLeavesStickyFatalErrorAndClearsNetplayState() = runTest {
        val transport = FakeNetplayTransport()
        builder.netplayTransportFactory = { _, _ -> transport }
        val vm = builder.build()

        vm.onIntent(
            EmulationIntent.StartGame(
                gameId = "game1",
                netplaySessionId = "np-1",
                netplayLocalPort = 0,
                netplayInputDelay = 3,
                netplayIsHost = true,
            ),
        )
        builder.advanceTimeBy(10_100)

        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.isNetplayMode)
        assertTrue(vm.state.value.error.orEmpty().contains("Failed to synchronize netplay state"))
        assertEquals(vm.state.value.error, vm.state.value.fatalError)

        vm.onIntent(EmulationIntent.DismissError)

        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.fatalError.orEmpty().contains("Failed to synchronize netplay state"))
    }

    private fun assertCallOrder(calls: List<String>, first: String, second: String) {
        val firstIndex = calls.indexOf(first)
        val secondIndex = calls.indexOf(second)
        assertTrue(firstIndex >= 0, "missing $first in calls=$calls")
        assertTrue(secondIndex >= 0, "missing $second in calls=$calls")
        assertTrue(firstIndex < secondIndex, "expected $first before $second; calls=$calls")
    }
}

private class FakeNetplayTransport(
    private val binaryMessagesOnConnect: List<ByteArray> = emptyList(),
    private val binaryMessagesAfterClientReady: List<ByteArray> = emptyList(),
    private val binaryMessagesAfterStateApplied: List<ByteArray> = emptyList(),
    private val emitStateAppliedAfterLastChunk: Boolean = false,
) : NetplayTransport {
    private val remoteInputFlow = MutableSharedFlow<RemoteInput>(extraBufferCapacity = 16)
    private val remoteBinaryFlow = MutableSharedFlow<BinaryMessage>(extraBufferCapacity = 16)
    private val controlMessageFlow = MutableSharedFlow<ControlMessage>(extraBufferCapacity = 16)

    val sentBinary = mutableListOf<ByteArray>()
    val sentInputs = mutableListOf<RemoteInput>()
    var unreliableBinarySendCount = 0
        private set
    var connectCallCount = 0
        private set
    var disconnectCallCount = 0
        private set

    override fun sendInput(frame: Long, port: Int, input: InputState) {
        sentInputs += RemoteInput(frame, port, input)
    }

    override fun sendBinary(data: ByteArray, targetPort: Int?) {
        unreliableBinarySendCount++
    }

    override suspend fun sendBinaryReliable(data: ByteArray, targetPort: Int?) {
        sentBinary += data
        if (NetplayProtocol.isClientReady(data)) {
            binaryMessagesAfterClientReady.forEach { remoteData ->
                remoteBinaryFlow.emit(BinaryMessage(remoteData, -1))
            }
        }
        if (NetplayProtocol.isStateApplied(data)) {
            binaryMessagesAfterStateApplied.forEach { remoteData ->
                remoteBinaryFlow.emit(BinaryMessage(remoteData, -1))
            }
        }
        val stateChunk = NetplayProtocol.decodeStateChunk(data)
        if (
            emitStateAppliedAfterLastChunk &&
            stateChunk != null &&
            stateChunk.chunkIndex == stateChunk.totalChunks - 1
        ) {
            remoteBinaryFlow.emit(BinaryMessage(NetplayProtocol.encodeStateApplied(), -1))
        }
    }

    override val remoteInputs = remoteInputFlow.asSharedFlow()
    override val remoteBinary = remoteBinaryFlow.asSharedFlow()
    override val controlMessages = controlMessageFlow.asSharedFlow()

    override fun sendControl(message: ControlMessage) {}

    override suspend fun connect() {
        connectCallCount++
        binaryMessagesOnConnect.forEach { data ->
            remoteBinaryFlow.emit(BinaryMessage(data, -1))
        }
    }

    override fun disconnect() {
        disconnectCallCount++
    }
}
