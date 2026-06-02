package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.util.DispatcherProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the frame-driven contract of the play-time reporter (#1282): the
 * reporter sources play time by *polling the controller's active-frame
 * drain*, once per interval and once on stop — rather than crediting a
 * flat wall-clock interval. The drain lambda is invoked synchronously in
 * the reporter loop, so these assertions are deterministic.
 *
 * The value arithmetic the reporter applies to the drained millis (whole
 * seconds + sub-second carry) is covered exhaustively by
 * [com.spela.player.libretro.splitForFlush] unit tests; the network POST
 * itself is not asserted here because MockEngine completes on a real
 * dispatcher, which is non-deterministic under virtual time.
 *
 * Runs on a separate-Job scope sharing runTest's scheduler so the
 * never-ending loop isn't tracked by runTest cleanup (cancelled in
 * `finally`); Unconfined so launched work runs eagerly to its next
 * suspension.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PresenceServiceHeartbeatTest {

    private val intervalMs = 30_000L // PresenceService.HEARTBEAT_INTERVAL_MS

    private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
        override val main = d
        override val io = d
        override val default = d
    }

    /** Always-OK engine; we don't assert on requests here. */
    private object OkEngineFactory : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
            MockEngine(MockEngineConfig().apply {
                addHandler {
                    respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                block()
            })
    }

    private fun withService(
        block: suspend TestScope.(service: PresenceService) -> Unit,
    ) = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val serviceScope = CoroutineScope(dispatcher + Job())
        val tokenManager = TokenManager().also { it.setTokens("test-access", "test-refresh") }
        val apiClient = SpelaApiClient(OkEngineFactory, tokenManager).also { it.setBaseUrl("http://localhost:8080") }
        val service = PresenceService(apiClient, OkEngineFactory, TestDispatchers(dispatcher), serviceScope)
        try {
            block(service)
        } finally {
            service.stopHeartbeat()
            serviceScope.cancel()
        }
    }

    @Test
    fun pollsActivePlayDrainOncePerInterval() = withService { service ->
        var drainCalls = 0
        service.startHeartbeat("5") { drainCalls++; 0L }

        advanceTimeBy(intervalMs * 3 + 1)

        // One poll per heartbeat interval — play time tracks frames, not a
        // flat clock tick.
        assertEquals(3, drainCalls)
    }

    @Test
    fun keepsPollingWhilePaused_soAccumulatorDoesNotGrowUnbounded() = withService { service ->
        var drainCalls = 0
        service.startHeartbeat("5") { drainCalls++; 0L }
        service.paused = true

        advanceTimeBy(intervalMs * 2 + 1)

        // Paused gates *reporting*, not draining — the accumulator is still
        // emptied each interval (so it can't balloon while paused).
        assertEquals(2, drainCalls)
    }

    @Test
    fun stopDrainsAnyRemainingActivePlay() = withService { service ->
        var drainCalls = 0
        service.startHeartbeat("5") { drainCalls++; 0L }
        val afterStart = drainCalls

        service.stopHeartbeat()
        advanceTimeBy(1)

        // Stop performs a final drain so partial play since the last
        // interval isn't lost.
        assertTrue(drainCalls > afterStart, "stop should drain once more (was $afterStart, now $drainCalls)")
    }
}
