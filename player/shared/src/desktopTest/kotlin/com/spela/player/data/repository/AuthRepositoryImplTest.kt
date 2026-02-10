package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.AuthTokens
import com.spela.player.test.NoOpMockEngineFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthRepositoryImplTest {

    private lateinit var database: SpelaDatabase
    private lateinit var tokenManager: TokenManager

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
        tokenManager = TokenManager()
    }

    private fun createRepo(): AuthRepositoryImpl {
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        return AuthRepositoryImpl(
            apiClient = apiClient,
            tokenManager = tokenManager,
            database = database,
        )
    }

    @Test
    fun storeTokensPersistsToDatabase() = runTest {
        val repo = createRepo()
        val tokens = AuthTokens(
            accessToken = "access-123",
            refreshToken = "refresh-456",
        )

        repo.storeTokens(tokens)

        val entity = database.spelaDatabaseQueries.getTokens().executeAsOneOrNull()
        assertNotNull(entity)
        assertEquals("access-123", entity.access_token)
        assertEquals("refresh-456", entity.refresh_token)
    }

    @Test
    fun getStoredTokensReadsFromDatabaseWhenCacheEmpty() = runTest {
        database.spelaDatabaseQueries.insertTokens(
            access_token = "persisted-access",
            refresh_token = "persisted-refresh",
            expires_at = "",
        )

        val repo = createRepo()
        val tokens = repo.getStoredTokens()

        assertNotNull(tokens)
        assertEquals("persisted-access", tokens.accessToken)
        assertEquals("persisted-refresh", tokens.refreshToken)
    }

    @Test
    fun clearTokensDeletesFromDatabase() = runTest {
        val repo = createRepo()
        repo.storeTokens(AuthTokens("access", "refresh"))

        repo.clearTokens()

        val entity = database.spelaDatabaseQueries.getTokens().executeAsOneOrNull()
        assertNull(entity)
        assertNull(repo.getStoredTokens())
    }

    @Test
    fun storeTokensUpdatesTokenManager() = runTest {
        val repo = createRepo()
        repo.storeTokens(AuthTokens("new-access", "new-refresh"))

        assertEquals("new-access", tokenManager.accessToken)
        assertEquals("new-refresh", tokenManager.refreshToken)
    }

    @Test
    fun clearTokensClearsTokenManager() = runTest {
        val repo = createRepo()
        repo.storeTokens(AuthTokens("access", "refresh"))
        repo.clearTokens()

        assertNull(tokenManager.accessToken)
        assertNull(tokenManager.refreshToken)
    }

    @Test
    fun getStoredTokensCachesAfterFirstDbRead() = runTest {
        database.spelaDatabaseQueries.insertTokens("access", "refresh", "")

        val repo = createRepo()
        val first = repo.getStoredTokens()
        assertNotNull(first)

        // Clear DB directly — cached value should still return
        database.spelaDatabaseQueries.deleteTokens()
        val second = repo.getStoredTokens()
        assertNotNull(second)
        assertEquals(first, second)
    }

    @Test
    fun getStoredTokensReturnsNullWhenNoPersistence() = runTest {
        val repo = createRepo()
        assertNull(repo.getStoredTokens())
    }
}
