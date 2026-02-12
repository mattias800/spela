package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementsRepositoryImplTest {

    private lateinit var repo: AchievementsRepositoryImpl

    private fun createMockFactory(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): HttpClientEngineFactory<MockEngineConfig> {
        return object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine {
                return MockEngine(MockEngineConfig().apply {
                    addHandler(handler)
                    block()
                })
            }
        }
    }

    @Test
    fun getRAStatusParsesResponse() = runTest {
        val factory = createMockFactory { request ->
            respond(
                content = """{"linked":true,"username":"testuser","hardcoreEnabled":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val apiClient = SpelaApiClient(factory, TokenManager())
        apiClient.setBaseUrl("http://localhost")
        repo = AchievementsRepositoryImpl(apiClient)

        val result = repo.getRAStatus()
        assertTrue(result.isSuccess)
        val status = result.getOrThrow()
        assertTrue(status.linked)
        assertEquals("testuser", status.username)
        assertTrue(status.hardcoreEnabled)
    }

    @Test
    fun getRAStatusReturnsFailureOnError() = runTest {
        val factory = createMockFactory { request ->
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
            )
        }
        val apiClient = SpelaApiClient(factory, TokenManager())
        apiClient.setBaseUrl("http://localhost")
        repo = AchievementsRepositoryImpl(apiClient)

        val result = repo.getRAStatus()
        assertTrue(result.isFailure)
    }

    @Test
    fun linkRASendsCorrectRequest() = runTest {
        val factory = createMockFactory { request ->
            respond(
                content = """{"linked":true,"username":"newuser","hardcoreEnabled":false}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val apiClient = SpelaApiClient(factory, TokenManager())
        apiClient.setBaseUrl("http://localhost")
        repo = AchievementsRepositoryImpl(apiClient)

        val result = repo.linkRA("newuser", "pass123")
        assertTrue(result.isSuccess)
        val status = result.getOrThrow()
        assertTrue(status.linked)
        assertEquals("newuser", status.username)
        assertFalse(status.hardcoreEnabled)
    }

    @Test
    fun unlinkRAReturnsSuccess() = runTest {
        val factory = createMockFactory { request ->
            respond(
                content = "",
                status = HttpStatusCode.OK,
            )
        }
        val apiClient = SpelaApiClient(factory, TokenManager())
        apiClient.setBaseUrl("http://localhost")
        repo = AchievementsRepositoryImpl(apiClient)

        val result = repo.unlinkRA()
        assertTrue(result.isSuccess)
    }

    @Test
    fun getRATokenParsesCredentials() = runTest {
        val factory = createMockFactory { request ->
            respond(
                content = """{"username":"testuser","token":"abc123token"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val apiClient = SpelaApiClient(factory, TokenManager())
        apiClient.setBaseUrl("http://localhost")
        repo = AchievementsRepositoryImpl(apiClient)

        val result = repo.getRAToken()
        assertTrue(result.isSuccess)
        val credentials = result.getOrThrow()
        assertEquals("testuser", credentials.username)
        assertEquals("abc123token", credentials.token)
    }

    @Test
    fun updateRASettingsTogglesHardcore() = runTest {
        val factory = createMockFactory { request ->
            respond(
                content = """{"linked":true,"username":"testuser","hardcoreEnabled":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val apiClient = SpelaApiClient(factory, TokenManager())
        apiClient.setBaseUrl("http://localhost")
        repo = AchievementsRepositoryImpl(apiClient)

        val result = repo.updateRASettings(true)
        assertTrue(result.isSuccess)
        val status = result.getOrThrow()
        assertTrue(status.hardcoreEnabled)
    }
}
