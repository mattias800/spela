package com.spela.player.data.remote

import com.spela.client.models.AuthLoginRequest
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.data.repository.SessionRepositoryImpl
import com.spela.player.platform.DesktopFileStorage
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Live API/repository contract smokes migrated out of Android instrumentation
 * for #1483. These tests are opt-in because they require docker-compose.e2e.yml
 * to be running. CI enables them in a dedicated job with:
 *
 *   -Dspela.liveApiContract=true
 *
 * The primary mutations go through the production [SessionRepositoryImpl] and
 * generated [SpelaApiClient]. Raw HTTP reads are used as independent
 * persistence cross-checks against the same live backend.
 */
class LiveApiContractSmokeTest {

    @Test
    fun cloneSessionRoundTripsThroughRepositoryAndServer() = runTest {
        val env = liveEnvironmentOrSkip() ?: return@runTest
        val gameId = env.firstAvailableGameId()
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val sourceName = "JVM-Clone-Source-$suffix"
        val source = env.repository.createSession(gameId, sourceName).getOrThrow()
        var cloneId: String? = null

        try {
            val expectedCopyName = "$sourceName (Copy)"
            val clone = env.repository.cloneSession(
                sessionId = source.id,
                name = expectedCopyName,
                saveId = null,
            ).getOrThrow()
            cloneId = clone.id

            assertNotEquals(source.id, clone.id, "clone endpoint must create a new row")
            assertEquals(expectedCopyName, clone.name)
            assertEquals(gameId, clone.gameId)

            val sessions = env.getJson("/api/games/$gameId/sessions").jsonArray
            val persisted = sessions.firstOrNull {
                it.jsonObject["id"]?.jsonPrimitive?.content == clone.id
            }?.jsonObject
            assertNotNull(persisted, "cloned session must be persisted in the server list")
            assertEquals(expectedCopyName, persisted["name"]?.jsonPrimitive?.content)

            val ownerId = env.sessionJson(clone.id).jsonObject["ownerId"]?.jsonPrimitive?.content
            val callerUserId = env.apiClient.getCurrentUser().id
            assertEquals(callerUserId, ownerId, "clone endpoint must transfer ownership to caller")

            val fetched = env.repository.getSession(clone.id).getOrThrow()
            assertEquals(expectedCopyName, fetched.name)
        } finally {
            cloneId?.let { runCatching { env.apiClient.deleteSession(it) } }
            runCatching { env.apiClient.deleteSession(source.id) }
        }
    }

    @Test
    fun coreDecisionFlagsRoundTripThroughRepositoryAndServer() = runTest {
        val env = liveEnvironmentOrSkip() ?: return@runTest
        val gameId = env.firstAvailableGameId()
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val session = env.repository.createSession(gameId, "JVM-CoreFlags-$suffix").getOrThrow()

        try {
            val afterLock = env.repository.updateSessionCoreFlags(
                sessionId = session.id,
                userLockedCoreVersion = true,
                autoLoadSuppressed = null,
                rehearsalCrashPending = null,
            ).getOrThrow()
            assertTrue(afterLock.userLockedCoreVersion)
            assertFalse(afterLock.autoLoadSuppressed)
            assertFalse(afterLock.rehearsalCrashPending)
            assertEquals(true, env.sessionBoolean(session.id, "userLockedCoreVersion"))

            val afterSuppress = env.repository.updateSessionCoreFlags(
                sessionId = session.id,
                userLockedCoreVersion = null,
                autoLoadSuppressed = true,
                rehearsalCrashPending = null,
            ).getOrThrow()
            assertTrue(afterSuppress.userLockedCoreVersion, "null lock field must leave lock untouched")
            assertTrue(afterSuppress.autoLoadSuppressed)
            assertEquals(true, env.sessionBoolean(session.id, "userLockedCoreVersion"))
            assertEquals(true, env.sessionBoolean(session.id, "autoLoadSuppressed"))

            val afterArm = env.repository.updateSessionCoreFlags(
                sessionId = session.id,
                userLockedCoreVersion = null,
                autoLoadSuppressed = null,
                rehearsalCrashPending = true,
            ).getOrThrow()
            assertTrue(afterArm.userLockedCoreVersion)
            assertTrue(afterArm.autoLoadSuppressed)
            assertTrue(afterArm.rehearsalCrashPending)
            assertEquals(true, env.sessionBoolean(session.id, "userLockedCoreVersion"))
            assertEquals(true, env.sessionBoolean(session.id, "autoLoadSuppressed"))
            assertEquals(true, env.sessionBoolean(session.id, "rehearsalCrashPending"))

            val afterUnlock = env.repository.updateSessionCoreFlags(
                sessionId = session.id,
                userLockedCoreVersion = false,
                autoLoadSuppressed = null,
                rehearsalCrashPending = null,
            ).getOrThrow()
            assertFalse(afterUnlock.userLockedCoreVersion)
            assertTrue(afterUnlock.autoLoadSuppressed)
            assertTrue(afterUnlock.rehearsalCrashPending)
            assertEquals(false, env.sessionBoolean(session.id, "userLockedCoreVersion"))
            assertEquals(true, env.sessionBoolean(session.id, "autoLoadSuppressed"))
            assertEquals(true, env.sessionBoolean(session.id, "rehearsalCrashPending"))
        } finally {
            runCatching { env.apiClient.deleteSession(session.id) }
        }
    }

    private suspend fun liveEnvironmentOrSkip(): LiveEnvironment? {
        if (System.getProperty("spela.liveApiContract") != "true") {
            return null
        }

        val baseUrl = System.getProperty("spela.liveApiBaseUrl") ?: DEFAULT_BASE_URL
        val tokenManager = TokenManager()
        val apiClient = SpelaApiClient(CIO, tokenManager).also { it.setBaseUrl(baseUrl) }
        val login = apiClient.login(AuthLoginRequest(username = PLAYER_USERNAME, password = PLAYER_PASSWORD))
        tokenManager.setTokens(login.accessToken, login.refreshToken)

        return LiveEnvironment(
            baseUrl = baseUrl,
            accessToken = login.accessToken,
            apiClient = apiClient,
            repository = SessionRepositoryImpl(apiClient, DesktopFileStorage()),
        )
    }

    private class LiveEnvironment(
        private val baseUrl: String,
        private val accessToken: String,
        val apiClient: SpelaApiClient,
        val repository: SessionRepositoryImpl,
    ) {
        suspend fun firstAvailableGameId(): String {
            val games = apiClient.getAllGames(pageSize = 1).data
            return games.firstOrNull()?.id
                ?: error("No games available on live E2E backend")
        }

        fun getJson(path: String) = Json.parseToJsonElement(get(path))

        fun sessionJson(sessionId: String) = getJson("/api/sessions/$sessionId")

        fun sessionBoolean(sessionId: String, field: String): Boolean =
            sessionJson(sessionId).jsonObject[field]?.jsonPrimitive?.boolean
                ?: error("Session $sessionId did not contain boolean field $field")

        private fun get(path: String): String {
            val conn = (URI("$baseUrl$path").toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            return try {
                check(conn.responseCode == 200) {
                    "GET $path failed with HTTP ${conn.responseCode}"
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:8080"
        const val PLAYER_USERNAME = "player"
        const val PLAYER_PASSWORD = "player123"
    }
}
