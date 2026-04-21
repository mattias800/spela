package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression guard for #663.
 *
 * Before the fix, `SpelaApiClient.cloneSession(sessionId, name = null,
 * saveId = null)` passed a Kotlin `null` to the generated `cloneSession`
 * API call. Ktor then serialised that null as the JSON literal `"null"`
 * (4 bytes), which the server's huma validator rejected with a 422. The
 * bug was invisible to the desktop suite (fake repositories) and only
 * surfaced on the Android smoke run.
 *
 * These tests pin the body shape that actually leaves the client.
 */
class SpelaApiClientCloneSessionTest {

    private class CapturingMockEngineFactory(
        private val fakeResponseJson: String,
    ) : HttpClientEngineFactory<MockEngineConfig> {
        var lastRequestBody: String = ""
            private set

        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
            MockEngine(MockEngineConfig().apply {
                addHandler { request ->
                    lastRequestBody = request.body.toByteArray().decodeToString()
                    respond(
                        fakeResponseJson,
                        HttpStatusCode.Created,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                block()
            })
    }

    // The smallest GameSessionResponse the generated model will deserialise.
    // kotlinx-serialization strict mode rejects missing required fields, so
    // every @Required field must be present here. Keep in sync with the
    // model under test.
    private val fakeSessionJson = """
        {
          "id": "new-session",
          "ownerId": "u1",
          "ownerUsername": "alice",
          "gameId": "g1",
          "name": "My Run (Copy)",
          "totalPlayTime": 0,
          "cheatsEnabled": false,
          "isSharedSession": false,
          "coreName": "",
          "memberCount": 1,
          "memberAvatars": [],
          "memberUsernames": [],
          "saveCount": 0,
          "screenshotUrl": "",
          "pinnedCoreSha256": "",
          "createdAt": "2026-04-21T00:00:00Z",
          "updatedAt": "2026-04-21T00:00:00Z",
          "lastPlayedAt": null,
          "lastPlayedBy": null,
          "lastPlayedByUsername": null,
          "sharedSessionId": null
        }
    """.trimIndent()

    @Test
    fun cloneSessionWithNullNameSendsObjectNotNullLiteral() = runTest {
        val engineFactory = CapturingMockEngineFactory(fakeSessionJson)
        val tokenManager = TokenManager().also {
            it.setTokens("test-access", "test-refresh")
        }
        val apiClient = SpelaApiClient(engineFactory, tokenManager)
        apiClient.setBaseUrl("http://localhost:8080")

        apiClient.cloneSession(sessionId = "src-1", name = null, saveId = null)

        val body = engineFactory.lastRequestBody
        assertNotEquals(
            "null",
            body,
            "#663: cloneSession(name=null) must not send the JSON literal 'null' — " +
                "huma rejects it with 422. Expected an object literal like " +
                "{\"name\":null}.",
        )
        assertTrue(
            body.startsWith("{") && body.endsWith("}"),
            "expected JSON object body, got: $body",
        )
        assertTrue(
            body.contains("\"name\""),
            "expected the body to include the name field, got: $body",
        )
    }

    @Test
    fun cloneSessionWithNonNullNameSendsThatName() = runTest {
        val engineFactory = CapturingMockEngineFactory(fakeSessionJson)
        val tokenManager = TokenManager().also {
            it.setTokens("test-access", "test-refresh")
        }
        val apiClient = SpelaApiClient(engineFactory, tokenManager)
        apiClient.setBaseUrl("http://localhost:8080")

        apiClient.cloneSession(sessionId = "src-1", name = "My Copy", saveId = null)

        val body = engineFactory.lastRequestBody
        assertTrue(
            body.contains("\"name\":\"My Copy\"") || body.contains("\"name\": \"My Copy\""),
            "expected body to contain name=\"My Copy\", got: $body",
        )
    }

    @Test
    fun cloneSessionWithSaveIdStillSendsValidObjectBody() = runTest {
        val engineFactory = CapturingMockEngineFactory(fakeSessionJson)
        val tokenManager = TokenManager().also {
            it.setTokens("test-access", "test-refresh")
        }
        val apiClient = SpelaApiClient(engineFactory, tokenManager)
        apiClient.setBaseUrl("http://localhost:8080")

        apiClient.cloneSession(sessionId = "src-1", name = null, saveId = 42L)

        // saveId travels as a query parameter, not in the body. The body
        // should still be an object with the name field (null or absent),
        // never the JSON literal null.
        val body = engineFactory.lastRequestBody
        assertNotEquals("null", body)
        assertTrue(
            body.startsWith("{") && body.endsWith("}"),
            "expected JSON object body, got: $body",
        )
    }

    @Test
    fun cloneSessionTargetsCloneNotDuplicatePath() = runTest {
        var lastPath = ""
        val engineFactory = object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                MockEngine(MockEngineConfig().apply {
                    addHandler { request ->
                        lastPath = request.url.encodedPath
                        respond(
                            fakeSessionJson,
                            HttpStatusCode.Created,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    block()
                })
        }
        val tokenManager = TokenManager().also {
            it.setTokens("test-access", "test-refresh")
        }
        val apiClient = SpelaApiClient(engineFactory, tokenManager)
        apiClient.setBaseUrl("http://localhost:8080")

        apiClient.cloneSession(sessionId = "src-1", name = "Any", saveId = null)

        assertEquals("/api/sessions/src-1/clone", lastPath)
    }
}
