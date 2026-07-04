package com.spela.player.netplay

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.presentation.viewmodel.emulation.StubMockEngineFactory
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class WebSocketRelayTransportTest {

    @Test
    fun forwardsClientReadyMessagesToRemoteBinary() = runTest {
        val transport = createTransport()
        val received = async(start = CoroutineStart.UNDISPATCHED) { transport.remoteBinary.first() }

        transport.handleBinaryMessage(NetplayProtocol.encodeClientReady())

        assertTrue(NetplayProtocol.isClientReady(received.await().data))
    }

    @Test
    fun forwardsStateAppliedMessagesToRemoteBinary() = runTest {
        val transport = createTransport()
        val received = async(start = CoroutineStart.UNDISPATCHED) { transport.remoteBinary.first() }

        transport.handleBinaryMessage(NetplayProtocol.encodeStateApplied())

        assertTrue(NetplayProtocol.isStateApplied(received.await().data))
    }

    @Test
    fun forwardsSyncCompleteMessagesToRemoteBinary() = runTest {
        val transport = createTransport()
        val received = async(start = CoroutineStart.UNDISPATCHED) { transport.remoteBinary.first() }

        transport.handleBinaryMessage(NetplayProtocol.encodeSyncComplete())

        assertTrue(NetplayProtocol.isSyncComplete(received.await().data))
    }

    private fun kotlinx.coroutines.test.TestScope.createTransport(): WebSocketRelayTransport {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        }
        val signaling = NetplaySignaling(
            apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager()),
            engineFactory = StubMockEngineFactory,
            dispatchers = dispatchers,
            scope = this,
            sessionId = "np-1",
        )
        return WebSocketRelayTransport(signaling, this)
    }
}
