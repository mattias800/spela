package com.spela.player.domain.usecase

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.AuthTokens
import com.spela.player.domain.model.ServerConnection
import com.spela.player.domain.model.User
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.ServerRepository
import com.spela.player.test.NoOpMockEngineFactory
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.*

class RestoreSessionUseCaseTest {

    private val defaultServer = ServerConnection(
        id = "server1",
        name = "Test Server",
        url = "http://localhost:8080",
        isActive = true,
    )

    private val defaultTokens = AuthTokens(
        accessToken = "access-token",
        refreshToken = "refresh-token",
    )

    private val defaultUser = User(
        id = "user1",
        username = "testuser",
        role = "user",
    )

    @Test
    fun noActiveServerReturnsNoSession() = runTest {
        val useCase = createUseCase(
            serverRepo = StubServerRepository(activeServer = null),
        )

        val result = useCase()

        assertIs<RestoreSessionResult.NoSession>(result)
    }

    @Test
    fun serverButNoTokensReturnsNeedsLogin() = runTest {
        val useCase = createUseCase(
            serverRepo = StubServerRepository(activeServer = defaultServer),
            authRepo = StubAuthRepository(storedTokens = null),
        )

        val result = useCase()

        assertIs<RestoreSessionResult.NeedsLogin>(result)
        assertEquals(defaultServer.url, result.serverUrl)
    }

    @Test
    fun serverAndTokensAndApiSuccessReturnsSuccess() = runTest {
        val useCase = createUseCase(
            serverRepo = StubServerRepository(activeServer = defaultServer),
            authRepo = StubAuthRepository(
                storedTokens = defaultTokens,
                currentUserResult = Result.success(defaultUser),
            ),
        )

        val result = useCase()

        assertIs<RestoreSessionResult.Success>(result)
    }

    @Test
    fun networkErrorReturnsOfflineSuccess() = runTest {
        val useCase = createUseCase(
            serverRepo = StubServerRepository(activeServer = defaultServer),
            authRepo = StubAuthRepository(
                storedTokens = defaultTokens,
                currentUserResult = Result.failure(ConnectException("Connection refused")),
            ),
        )

        val result = useCase()

        assertIs<RestoreSessionResult.OfflineSuccess>(result)
    }

    @Test
    fun timeoutReturnsOfflineSuccess() = runTest {
        val useCase = createUseCase(
            serverRepo = StubServerRepository(activeServer = defaultServer),
            authRepo = StubAuthRepository(
                storedTokens = defaultTokens,
                currentUserResult = Result.failure(SocketTimeoutException("Read timed out")),
            ),
        )

        val result = useCase()

        assertIs<RestoreSessionResult.OfflineSuccess>(result)
    }

    @Test
    fun authErrorReturnsNeedsLoginAndClearsTokens() = runTest {
        val authException = createResponseException(HttpStatusCode.Unauthorized)
        val authRepo = StubAuthRepository(
            storedTokens = defaultTokens,
            currentUserResult = Result.failure(authException),
        )
        val useCase = createUseCase(
            serverRepo = StubServerRepository(activeServer = defaultServer),
            authRepo = authRepo,
        )

        val result = useCase()

        assertIs<RestoreSessionResult.NeedsLogin>(result)
        assertEquals(defaultServer.url, result.serverUrl)
        assertTrue(authRepo.clearTokensCalled, "clearTokens should have been called on auth error")
    }

    // -- Helper to build a ResponseException with a specific status code --

    private suspend fun createResponseException(status: HttpStatusCode): ResponseException {
        val client = HttpClient(MockEngine { respond("Error", status) })
        return try {
            client.get("http://test") { expectSuccess = true }
            error("Should have thrown")
        } catch (e: ResponseException) {
            e
        }
    }

    // -- Factory --

    private fun createUseCase(
        authRepo: AuthRepository = StubAuthRepository(storedTokens = null),
        serverRepo: ServerRepository = StubServerRepository(activeServer = null),
    ): RestoreSessionUseCase {
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        return RestoreSessionUseCase(
            authRepository = authRepo,
            serverRepository = serverRepo,
            apiClient = apiClient,
        )
    }

    // -- Stubs --

    private class StubServerRepository(
        private val activeServer: ServerConnection?,
    ) : ServerRepository {
        override fun observeServers(): Flow<List<ServerConnection>> = flowOf(
            if (activeServer != null) listOf(activeServer) else emptyList()
        )
        override suspend fun getServers(): List<ServerConnection> =
            if (activeServer != null) listOf(activeServer) else emptyList()
        override suspend fun getActiveServer(): ServerConnection? = activeServer
        override suspend fun addServer(name: String, url: String): ServerConnection =
            throw UnsupportedOperationException()
        override suspend fun removeServer(id: String) = throw UnsupportedOperationException()
        override suspend fun setActiveServer(id: String) = throw UnsupportedOperationException()
        override suspend fun validateServer(url: String): Boolean = throw UnsupportedOperationException()
    }

    private class StubAuthRepository(
        private val storedTokens: AuthTokens?,
        private val currentUserResult: Result<User> = Result.failure(Exception("not configured")),
    ) : AuthRepository {
        var clearTokensCalled = false
            private set

        override suspend fun login(serverUrl: String, username: String, password: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun register(serverUrl: String, username: String, password: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun refreshToken(serverUrl: String, refreshToken: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun getCurrentUser(): Result<User> = currentUserResult
        override suspend fun getStoredTokens(): AuthTokens? = storedTokens
        override suspend fun storeTokens(tokens: AuthTokens) = throw UnsupportedOperationException()
        override suspend fun clearTokens() {
            clearTokensCalled = true
        }
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
        override fun isLoggedIn(): Boolean = storedTokens != null
    }
}
