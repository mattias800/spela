package com.spela.player.data.remote

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.data.repository.PendingPlayTimeSyncRepositoryImpl
import com.spela.player.domain.model.CurrentUserContext
import com.spela.player.domain.model.ServerConnection
import com.spela.player.domain.model.User
import com.spela.player.domain.repository.CurrentUserContextRepository
import com.spela.player.domain.repository.ServerRepository
import com.spela.player.util.DispatcherProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayTimeSyncManagerTest {

    private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
        override val main = d
        override val io = d
        override val default = d
    }

    private class CapturingEngineFactory(
        private val status: () -> HttpStatusCode = { HttpStatusCode.OK },
    ) : HttpClientEngineFactory<MockEngineConfig> {
        val requestBodies = mutableListOf<String>()

        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
            MockEngine(MockEngineConfig().apply {
                addHandler { request ->
                    requestBodies.add(request.body.toByteArray().decodeToString())
                    respond(
                        """{"playTime":30,"lastPlayed":"2026-07-03T10:00:00Z"}""",
                        status(),
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                block()
            })
    }

    @Test
    fun reportPlayTimeQueuesImmediatelyWhenOffline() = runTest {
        val fixture = createFixture()
        fixture.connectivityMonitor.forceConnectionState(ConnectionState.Offline)

        fixture.manager.reportPlayTime(
            gameId = "42",
            gameTitle = "Metroid Prime",
            durationSeconds = 30L,
            playedAtMillis = 1_700_000_000_000L,
            trigger = "heartbeat",
        )

        val rows = fixture.pendingRepository.getAll()
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("http://server", row.serverUrl)
        assertEquals("user-1", row.userId)
        assertEquals("42", row.gameId)
        assertEquals("Metroid Prime", row.gameTitle)
        assertEquals(30L, row.durationSeconds)
        assertEquals(1_700_000_000_000L, row.playedAt)
        assertTrue(row.clientReportId.startsWith("play-"))
        assertEquals(0, fixture.engineFactory.requestBodies.size)
    }

    @Test
    fun reportPlayTimeKeepsQueuedRowWhenOnlineUploadFails() = runTest {
        val fixture = createFixture(status = { HttpStatusCode.ServiceUnavailable })

        fixture.manager.reportPlayTime(
            gameId = "42",
            gameTitle = "Metroid Prime",
            durationSeconds = 30L,
            playedAtMillis = 1_700_000_000_000L,
            trigger = "heartbeat",
        )

        val rows = fixture.pendingRepository.getAll()
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("42", row.gameId)
        assertEquals(30L, row.durationSeconds)
        assertEquals(1, row.retryCount)
        assertNotNull(row.lastError)
        assertEquals(1, fixture.engineFactory.requestBodies.size)
        val body = requestJson(fixture.engineFactory.requestBodies.single())
        assertEquals("true", body["updatePresence"]?.jsonPrimitive?.content)
    }

    @Test
    fun drainUploadsContextRowsAndDeletesOnSuccess() = runTest {
        val fixture = createFixture()
        val id = fixture.pendingRepository.enqueue(
            clientReportId = "report-1",
            serverUrl = "http://server",
            userId = "user-1",
            gameId = "42",
            gameTitle = "Metroid Prime",
            durationSeconds = 45L,
            playedAt = 1_700_000_000_000L,
            createdAt = 1L,
        )

        val drain = fixture.manager.drainPending(trigger = "test")
        advanceUntilIdle()
        drain.join()

        assertEquals(0L, fixture.pendingRepository.count())
        assertEquals(1, fixture.engineFactory.requestBodies.size)
        val body = requestJson(fixture.engineFactory.requestBodies.single())
        assertEquals("report-1", body["clientReportId"]?.jsonPrimitive?.content)
        assertEquals("45", body["seconds"]?.jsonPrimitive?.content)
        assertEquals("2023-11-14T22:13:20Z", body["playedAt"]?.jsonPrimitive?.content)
        assertEquals("false", body["updatePresence"]?.jsonPrimitive?.content)
        assertEquals(null, fixture.pendingRepository.getById(id))
    }

    @Test
    fun drainMarksRetryAndKeepsRowOnFailure() = runTest {
        val fixture = createFixture(status = { HttpStatusCode.ServiceUnavailable })
        val id = fixture.pendingRepository.enqueue(
            clientReportId = "report-1",
            serverUrl = "http://server",
            userId = "user-1",
            gameId = "42",
            gameTitle = "Metroid Prime",
            durationSeconds = 45L,
            playedAt = 1_700_000_000_000L,
            createdAt = 1L,
        )

        val drain = fixture.manager.drainPending(trigger = "test")
        advanceUntilIdle()
        drain.join()

        val row = fixture.pendingRepository.getById(id)
        assertNotNull(row)
        assertEquals(1, row.retryCount)
        assertNotNull(row.lastError)
    }

    @Test
    fun reconnectTriggersDrain() = runTest {
        val fixture = createFixture()
        fixture.pendingRepository.enqueue(
            clientReportId = "report-1",
            serverUrl = "http://server",
            userId = "user-1",
            gameId = "42",
            gameTitle = "Metroid Prime",
            durationSeconds = 30L,
            playedAt = 1_700_000_000_000L,
            createdAt = 1L,
        )
        val listener = fixture.manager.startReconnectListener()
        runCurrent()

        fixture.connectivityMonitor.forceConnectionState(ConnectionState.Offline)
        fixture.connectivityMonitor.forceConnectionState(ConnectionState.Online)
        awaitRealUntil {
            runCurrent()
            runBlocking { fixture.pendingRepository.count() == 0L }
        }
        advanceUntilIdle()

        assertEquals(0L, fixture.pendingRepository.count())
        assertEquals(1, fixture.engineFactory.requestBodies.size)
        listener.cancel()
    }

    private fun TestScope.createFixture(
        status: () -> HttpStatusCode = { HttpStatusCode.OK },
    ): Fixture {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = TestDispatchers(dispatcher)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        val database = SpelaDatabase(driver)
        val pendingRepository = PendingPlayTimeSyncRepositoryImpl(database)
        val engineFactory = CapturingEngineFactory(status)
        val tokenManager = TokenManager()
        val apiClient = SpelaApiClient(engineFactory, tokenManager).also {
            it.setBaseUrl("http://server")
        }
        val connectivityMonitor = ConnectivityMonitor(apiClient, dispatchers, this)
        val manager = PlayTimeSyncManager(
            apiClient = apiClient,
            connectivityMonitor = connectivityMonitor,
            pendingRepository = pendingRepository,
            serverRepository = FakeServerRepository(),
            currentUserContextRepository = FakeCurrentUserContextRepository(),
            dispatchers = dispatchers,
            scope = this,
        )
        return Fixture(
            manager = manager,
            pendingRepository = pendingRepository,
            connectivityMonitor = connectivityMonitor,
            engineFactory = engineFactory,
        )
    }

    private data class Fixture(
        val manager: PlayTimeSyncManager,
        val pendingRepository: PendingPlayTimeSyncRepositoryImpl,
        val connectivityMonitor: ConnectivityMonitor,
        val engineFactory: CapturingEngineFactory,
    )

    private fun awaitRealUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            Thread.sleep(10)
        }
        assertTrue(predicate())
    }

    private fun requestJson(body: String): JsonObject =
        Json.parseToJsonElement(body).jsonObject

    private class FakeServerRepository : ServerRepository {
        override fun observeServers(): Flow<List<ServerConnection>> =
            MutableStateFlow(listOf(active))

        override suspend fun getServers(): List<ServerConnection> = listOf(active)
        override suspend fun getActiveServer(): ServerConnection = active
        override suspend fun addServer(name: String, url: String): ServerConnection = active
        override suspend fun removeServer(id: String) {}
        override suspend fun setActiveServer(id: String) {}
        override suspend fun validateServer(url: String): Boolean = true

        private val active = ServerConnection(
            id = "server",
            name = "Server",
            url = "http://server",
            isActive = true,
        )
    }

    private class FakeCurrentUserContextRepository : CurrentUserContextRepository {
        override suspend fun cache(user: User) {}
        override suspend fun cache(context: CurrentUserContext) {}
        override suspend fun getCached(): CurrentUserContext =
            CurrentUserContext(userId = "user-1", username = "player")
        override suspend fun clear() {}
    }
}
