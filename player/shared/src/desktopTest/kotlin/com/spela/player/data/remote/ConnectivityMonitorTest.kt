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
        assertTrue(monitor.connectionState.value.isConnected)
        assertTrue(monitor.isOnline.value)
    }

    @Test
    fun reportAuthFailureSetsAuthFailed() = runTest(testDispatcher) {
        val monitor = createMonitor(this)
        monitor.reportAuthFailure(AuthFailureReason.SESSION_EXPIRED)
        assertTrue(monitor.connectionState.value is ConnectionState.AuthFailed)
        assertFalse(monitor.connectionState.value.isConnected)
    }

    @Test
    fun reportDatabaseErrorSetsDatabaseError() = runTest(testDispatcher) {
        val monitor = createMonitor(this)
        monitor.reportDatabaseError("Schema mismatch")
        assertTrue(monitor.connectionState.value is ConnectionState.DatabaseError)
        assertEquals(
            "Schema mismatch",
            (monitor.connectionState.value as ConnectionState.DatabaseError).message,
        )
    }

    @Test
    fun clearAuthFailureTransitionsToOnline() = runTest(testDispatcher) {
        val monitor = createMonitor(this)
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.onReconnect.collect { events.add(it) }
        }

        monitor.reportAuthFailure(AuthFailureReason.SESSION_EXPIRED)
        assertTrue(monitor.connectionState.value is ConnectionState.AuthFailed)

        monitor.clearAuthFailure()
        assertTrue(monitor.connectionState.value is ConnectionState.Online)
        assertEquals(1, events.size)

        job.cancel()
    }

    @Test
    fun clearAuthFailureNoOpWhenNotAuthFailed() = runTest(testDispatcher) {
        val monitor = createMonitor(this)
        val events = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.onReconnect.collect { events.add(it) }
        }

        // Already online, clearAuthFailure should not emit
        monitor.clearAuthFailure()
        assertTrue(monitor.connectionState.value is ConnectionState.Online)
        assertEquals(0, events.size)

        job.cancel()
    }

    @Test
    fun clearAuthFailureSuppressesReReporting() = runTest(testDispatcher) {
        val monitor = createMonitor(this)

        monitor.reportAuthFailure(AuthFailureReason.SESSION_EXPIRED)
        assertTrue(monitor.connectionState.value is ConnectionState.AuthFailed)

        // User dismisses dialog
        monitor.clearAuthFailure()
        assertTrue(monitor.connectionState.value is ConnectionState.Online)

        // Subsequent auth failure should be suppressed (e.g. health check with expired token)
        monitor.reportAuthFailure(AuthFailureReason.REFRESH_FAILED)
        assertTrue(monitor.connectionState.value is ConnectionState.Online)
    }

    @Test
    fun clearDatabaseErrorTransitionsToOnline() = runTest(testDispatcher) {
        val monitor = createMonitor(this)
        monitor.reportDatabaseError("Schema mismatch")
        assertTrue(monitor.connectionState.value is ConnectionState.DatabaseError)

        monitor.clearDatabaseError()
        assertTrue(monitor.connectionState.value is ConnectionState.Online)
    }

    @Test
    fun connectionStateConvenienceProperties() = runTest(testDispatcher) {
        val monitor = createMonitor(this)

        // Online
        assertTrue(monitor.connectionState.value.isConnected)
        assertTrue(monitor.connectionState.value.hasNetwork)
        assertTrue(monitor.connectionState.value.canReachServer)

        // AuthFailed
        monitor.reportAuthFailure(AuthFailureReason.SESSION_EXPIRED)
        assertFalse(monitor.connectionState.value.isConnected)
        assertTrue(monitor.connectionState.value.hasNetwork)
        assertFalse(monitor.connectionState.value.canReachServer)
    }

    @Test
    fun classifyExceptionMapsCorrectly() = runTest(testDispatcher) {
        val monitor = createMonitor(this)

        assertTrue(
            monitor.classifyException(java.net.UnknownHostException("DNS failure")) is ConnectionState.Offline
        )
        assertTrue(
            monitor.classifyException(java.net.ConnectException("Connection refused")) is ConnectionState.ServerUnreachable
        )
        assertTrue(
            monitor.classifyException(RuntimeException("Unknown error")) is ConnectionState.ServerUnreachable
        )
    }

    @Test
    fun isOnlineDerivedFlowReflectsConnectionState() = runTest(testDispatcher) {
        val monitor = createMonitor(this)

        assertTrue(monitor.isOnline.value)

        monitor.reportAuthFailure(AuthFailureReason.SESSION_EXPIRED)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(monitor.isOnline.value)

        monitor.clearAuthFailure()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(monitor.isOnline.value)
    }
}
