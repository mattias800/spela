package com.spela.player.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ServerRepositoryImplTest {

    @Test
    fun addServerCreatesNew() = runTest {
        val repo = ServerRepositoryImpl()
        val server = repo.addServer("Home Server", "http://192.168.1.100:8080")

        assertEquals("Home Server", server.name)
        assertEquals("http://192.168.1.100:8080", server.url)
        assertTrue(server.isActive) // First server is auto-active
    }

    @Test
    fun firstServerIsAutoActive() = runTest {
        val repo = ServerRepositoryImpl()
        val server1 = repo.addServer("Server 1", "http://server1.local")
        val server2 = repo.addServer("Server 2", "http://server2.local")

        assertTrue(server1.isActive)
        assertFalse(server2.isActive)
    }

    @Test
    fun setActiveServerChangesActive() = runTest {
        val repo = ServerRepositoryImpl()
        val server1 = repo.addServer("Server 1", "http://server1.local")
        val server2 = repo.addServer("Server 2", "http://server2.local")

        repo.setActiveServer(server2.id)

        val active = repo.getActiveServer()
        assertNotNull(active)
        assertEquals(server2.id, active.id)
    }

    @Test
    fun removeServerAndAutoActivateAnother() = runTest {
        val repo = ServerRepositoryImpl()
        val server1 = repo.addServer("Server 1", "http://server1.local")
        val server2 = repo.addServer("Server 2", "http://server2.local")

        repo.removeServer(server1.id)

        val servers = repo.getServers()
        assertEquals(1, servers.size)
        assertTrue(servers[0].isActive)
    }

    @Test
    fun observeServersReflectsChanges() = runTest {
        val repo = ServerRepositoryImpl()

        val initial = repo.observeServers().first()
        assertTrue(initial.isEmpty())

        repo.addServer("Test", "http://test.local")
        val updated = repo.observeServers().first()
        assertEquals(1, updated.size)
    }

    @Test
    fun urlIsNormalized() = runTest {
        val repo = ServerRepositoryImpl()
        val server = repo.addServer("Test", "http://test.local/")

        assertEquals("http://test.local", server.url) // trailing slash removed
    }

    @Test
    fun getActiveServerReturnsNullWhenEmpty() = runTest {
        val repo = ServerRepositoryImpl()
        assertNull(repo.getActiveServer())
    }
}
