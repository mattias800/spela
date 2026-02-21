package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.test.NoOpMockEngineFactory
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityMonitorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMonitor(scope: CoroutineScope): ConnectivityMonitor {
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        return ConnectivityMonitor(apiClient, testDispatchers, scope)
    }

    @Test
    fun defaultIsOnline() = runTest(testDispatcher) {
        val monitor = createMonitor(this)
        assertTrue(monitor.isOnline.value)
    }

    @Test
    fun reportOfflineSetsOffline() = runTest(testDispatcher) {
        val monitor = createMonitor(this)
        monitor.reportOffline()
        assertFalse(monitor.isOnline.value)
    }

    @Test
    fun reportOnlineSetsOnlineAfterOffline() = runTest(testDispatcher) {
        val monitor = createMonitor(this)
        monitor.reportOffline()
        assertFalse(monitor.isOnline.value)

        monitor.reportOnline()
        assertTrue(monitor.isOnline.value)
    }

    @Test
    fun offlineToOnlineEmitsOnReconnect() = runTest(testDispatcher) {
        val monitor = createMonitor(this)
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.onReconnect.collect { events.add(it) }
        }

        monitor.reportOffline()
        monitor.reportOnline()

        assertEquals(1, events.size)
        job.cancel()
    }

    @Test
    fun onlineToOnlineDoesNotEmitOnReconnect() = runTest(testDispatcher) {
        val monitor = createMonitor(this)
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.onReconnect.collect { events.add(it) }
        }

        // Already online by default, calling reportOnline should not emit
        monitor.reportOnline()

        assertEquals(0, events.size)
        job.cancel()
    }

    @Test
    fun multipleOfflineOnlineCyclesEmitCorrectCount() = runTest(testDispatcher) {
        val monitor = createMonitor(this)
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.onReconnect.collect { events.add(it) }
        }

        repeat(3) {
            monitor.reportOffline()
            monitor.reportOnline()
        }

        assertEquals(3, events.size)
        job.cancel()
    }
}
