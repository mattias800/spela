package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.util.DispatcherProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Locks the reconnect-loop file-descriptor leak fix (#1399).
 *
 * [PresenceService.connect] reuses a single Ktor [io.ktor.client.HttpClient]
 * across every reconnect attempt. The previous code created a fresh client
 * each iteration and only closed it *after* a successful session — but
 * `webSocket()` throws on a failed upgrade (the normal offline-reconnect
 * case), skipping the close. Every leaked client kept a live SelectorManager
 * holding kqueue/wakeup-pipe/socket FDs, so a server that stayed unreachable
 * drained the process FD limit one reconnect at a time.
 *
 * Run against a real dispatcher (not virtual time): the WebSocket attempt is
 * routed through MockEngine, whose request execution completes on its own
 * dispatcher, so virtual-time advancement can't drive the reconnect loop.
 * The reconnect delay is a real 5s, so the wait below spans more than one
 * reconnect cycle — long enough that the old per-attempt-client code would
 * have built a second engine. With the fix, exactly one engine is ever built
 * no matter how long the loop churns, and it is closed when disconnect()
 * cancels the loop.
 */
class PresenceServiceConnectionTest {

    private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
        override val main = d
        override val io = d
        override val default = d
    }

    /** Plain engine for the API client; its own client must not skew the count. */
    private object ApiEngineFactory : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
            MockEngine(
                MockEngineConfig().apply {
                    addHandler { throw RuntimeException("no API calls expected") }
                    block()
                },
            )
    }

    /**
     * Counts engine constructions and remembers the engine it built so the
     * test can assert close-on-disconnect. Every request fails, so the
     * WebSocket upgrade always throws and `connect()` keeps reconnecting.
     */
    private class CountingEngineFactory : HttpClientEngineFactory<MockEngineConfig> {
        val createCount = AtomicInteger(0)

        @Volatile
        var lastEngine: MockEngine? = null

        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine {
            createCount.incrementAndGet()
            return MockEngine(
                MockEngineConfig().apply {
                    addHandler { throw RuntimeException("simulated connection failure") }
                    block()
                },
            ).also { lastEngine = it }
        }
    }

    @Test
    fun reusesOneClientAcrossReconnects_andClosesItOnDisconnect() = runBlocking {
        val serviceScope = CoroutineScope(Dispatchers.IO + Job())
        val factory = CountingEngineFactory()
        val tokenManager = TokenManager().also { it.setTokens("test-access", "test-refresh") }
        val apiClient = SpelaApiClient(ApiEngineFactory, tokenManager).also { it.setBaseUrl("http://localhost:8080") }
        val service = PresenceService(apiClient, factory, TestDispatchers(Dispatchers.IO), serviceScope)

        try {
            service.connect()
            // Span more than one 5s reconnect cycle. The buggy code would have
            // built a second (leaked) client by now.
            delay(7_000)

            assertEquals(
                1,
                factory.createCount.get(),
                "connect() must reuse a single HttpClient across reconnects, not build one per attempt",
            )

            service.disconnect()

            // Poll for the cancellation + finally { close() } to propagate
            // rather than sleeping a fixed amount, so a loaded CI box can't
            // flake the assertion.
            val engineJob = factory.lastEngine?.coroutineContext?.job
            var waited = 0
            while (engineJob?.isActive == true && waited < 3_000) {
                delay(50)
                waited += 50
            }
            assertFalse(
                engineJob?.isActive ?: true,
                "disconnect() must close the reused client's engine",
            )
        } finally {
            service.disconnect()
            serviceScope.cancel()
        }
    }
}
