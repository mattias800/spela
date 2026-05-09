package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
// TokenManager is exposed from the AuthInterceptor file alongside the
// interceptor itself.
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerRepositoryImplDbTest {

    private lateinit var database: SpelaDatabase

    private val stubEngineFactory = object : HttpClientEngineFactory<HttpClientEngineConfig> {
        override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
            return MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        }
    }

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
    }

    private fun createRepo(): ServerRepositoryImpl {
        // SpelaApiClient is invoked from setActiveServer/addServer to keep
        // the shared baseUrl in sync with the active server (#1146). Use
        // a real instance with the stub engine so the setBaseUrl side
        // effect is exercised without making real HTTP calls.
        val apiClient = SpelaApiClient(
            engineFactory = stubEngineFactory,
            tokenManager = TokenManager(),
        )
        return ServerRepositoryImpl(database, stubEngineFactory, apiClient)
    }

    @Test
    fun addServerPersistsToDatabase() = runTest {
        val repo = createRepo()
        val server = repo.addServer("Home Server", "http://192.168.1.100:8080")

        assertEquals("Home Server", server.name)
        assertEquals("http://192.168.1.100:8080", server.url)

        // Verify in DB
        val entities = database.spelaDatabaseQueries.getAllServers().executeAsList()
        assertEquals(1, entities.size)
        assertEquals("Home Server", entities[0].name)
    }

    @Test
    fun serverIdIsUuid() = runTest {
        val repo = createRepo()
        val server = repo.addServer("Test", "http://test.local")

        // UUID format: 8-4-4-4-12 hex chars
        assertTrue(server.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun firstServerIsAutoActive() = runTest {
        val repo = createRepo()
        val server1 = repo.addServer("Server 1", "http://server1.local")
        val server2 = repo.addServer("Server 2", "http://server2.local")

        assertTrue(server1.isActive)
        assertFalse(server2.isActive)
    }

    @Test
    fun setActiveServerChangesActive() = runTest {
        val repo = createRepo()
        val server1 = repo.addServer("Server 1", "http://server1.local")
        val server2 = repo.addServer("Server 2", "http://server2.local")

        repo.setActiveServer(server2.id)

        val active = repo.getActiveServer()
        assertNotNull(active)
        assertEquals(server2.id, active.id)
    }

    @Test
    fun removeServerAndAutoActivateAnother() = runTest {
        val repo = createRepo()
        val server1 = repo.addServer("Server 1", "http://server1.local")
        val server2 = repo.addServer("Server 2", "http://server2.local")

        repo.removeServer(server1.id)

        val servers = repo.getServers()
        assertEquals(1, servers.size)
        assertTrue(servers[0].isActive)
    }

    @Test
    fun removeServerDeletesFromDatabase() = runTest {
        val repo = createRepo()
        val server = repo.addServer("Test", "http://test.local")

        repo.removeServer(server.id)

        val entities = database.spelaDatabaseQueries.getAllServers().executeAsList()
        assertTrue(entities.isEmpty())
    }

    @Test
    fun serversPersistAcrossRepositoryInstances() = runTest {
        val repo1 = createRepo()
        repo1.addServer("Persistent Server", "http://persistent.local")

        // Create a new repo instance against the same DB (simulates app restart)
        val repo2 = createRepo()
        val servers = repo2.getServers()

        assertEquals(1, servers.size)
        assertEquals("Persistent Server", servers[0].name)
        assertTrue(servers[0].isActive)
    }

    @Test
    fun observeServersReflectsChanges() = runTest {
        val repo = createRepo()

        val initial = repo.observeServers().first()
        assertTrue(initial.isEmpty())

        repo.addServer("Test", "http://test.local")
        val updated = repo.observeServers().first()
        assertEquals(1, updated.size)
    }

    @Test
    fun urlIsNormalized() = runTest {
        val repo = createRepo()
        val server = repo.addServer("Test", "http://test.local/")

        assertEquals("http://test.local", server.url)
    }

    @Test
    fun getActiveServerReturnsNullWhenEmpty() = runTest {
        val repo = createRepo()
        assertNull(repo.getActiveServer())
    }
}
