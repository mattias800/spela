package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Cold-load resilience for the Collections and related entity-detail screens
 * (#1231).
 *
 * A truncated / garbled 2xx body — connection contention while cores prefetch,
 * a proxy hiccup — used to surface as a hard JSON error even though a retry
 * would succeed. `SpelaApiClient.getMyCollections/getPublicCollections/
 * getCollection` now retry the transient failure modes (and log the raw body).
 * The same symptom was seen on developer/publisher detail screens, so those
 * read-only calls share the same retry path. These tests pin that behaviour.
 */
class SpelaApiClientCollectionResilienceTest {

    // Minimal PaginatedResponseCollectionResponse with one CollectionResponse —
    // every @Required field present, else strict deserialization rejects it.
    private val validJson = """
        {
          "data": [
            {
              "id": "1", "userId": "u1", "username": "alice", "avatarUrl": "",
              "name": "My Collection", "description": "", "isPublic": true,
              "coverUrl": "", "gameCount": 0,
              "createdAt": "2026-04-21T00:00:00Z", "updatedAt": "2026-04-21T00:00:00Z"
            }
          ],
          "total": 1, "page": 1, "pageSize": 20
        }
    """.trimIndent()

    // A 2xx body that is cut off mid-object → SerializationException on decode.
    private val truncatedJson = """{"data":[{"id":"1","userId":"u1","""

    private fun entityDetailJson(name: String, publisher: Boolean = false): String {
        val relationshipKey = if (publisher) "developers" else "publishers"
        val relatedKey = if (publisher) "relatedPublishers" else "relatedDevelopers"
        return """
            {
              "activeYears": {"first": 1983, "last": 2026},
              "avgRating": 4.5,
              "companyInfo": {
                "country": "", "description": "", "foundedYear": 1983,
                "logoUrl": "", "websiteUrl": "", "wikipediaUrl": ""
              },
              "consoles": [],
              "$relationshipKey": [],
              "gameCount": 0,
              "games": [],
              "heroUrl": "",
              "name": "$name",
              "platformBreakdown": [],
              "primaryGenre": "",
              "ratingDistribution": {
                "average": 0, "excellent": 0, "good": 0, "poor": 0, "unrated": 0
              },
              "$relatedKey": [],
              "timeline": [],
              "topGames": [],
              "userStats": {"favoriteCount": 0, "gamesPlayed": 0, "totalPlayTime": 0}
            }
        """.trimIndent()
    }

    private fun sequencedEngine(
        callCounter: IntArray,
        bodyFor: (Int) -> Pair<String, HttpStatusCode>,
    ) = object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
            MockEngine(MockEngineConfig().apply {
                addHandler { _ ->
                    callCounter[0]++
                    val (body, status) = bodyFor(callCounter[0])
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                block()
            })
    }

    private suspend fun client(engineFactory: HttpClientEngineFactory<MockEngineConfig>): SpelaApiClient {
        val tokenManager = TokenManager().also { it.setTokens("a", "r") }
        return SpelaApiClient(engineFactory, tokenManager).also { it.setBaseUrl("http://localhost:8080") }
    }

    @Test
    fun getMyCollectionsRetriesAndRecoversAfterTruncatedBody() = runTest {
        val calls = intArrayOf(0)
        val apiClient = client(
            sequencedEngine(calls) { n ->
                (if (n == 1) truncatedJson else validJson) to HttpStatusCode.OK
            },
        )

        val result = apiClient.getMyCollections()

        assertEquals(2, calls[0], "expected exactly one retry after the first truncated body")
        assertEquals(1, result.data.size)
        assertEquals("My Collection", result.data[0].name)
    }

    @Test
    fun getMyCollectionsGivesUpAfterRepeatedTruncation() = runTest {
        val calls = intArrayOf(0)
        val apiClient = client(sequencedEngine(calls) { truncatedJson to HttpStatusCode.OK })

        assertFailsWith<SerializationException> { apiClient.getMyCollections() }
        assertEquals(3, calls[0], "should retry up to the attempt cap, then surface the error")
    }

    @Test
    fun getMyCollectionsDoesNotRetryNonTransientError() = runTest {
        val calls = intArrayOf(0)
        // 404 is a hard client error (not transient) — must not be retried.
        val apiClient = client(sequencedEngine(calls) { """{"title":"not found"}""" to HttpStatusCode.NotFound })

        assertFailsWith<Throwable> { apiClient.getMyCollections() }
        assertEquals(1, calls[0], "non-transient 4xx must not be retried")
    }

    @Test
    fun getDeveloperDetailRetriesAndRecoversAfterTruncatedBody() = runTest {
        val calls = intArrayOf(0)
        val apiClient = client(
            sequencedEngine(calls) { n ->
                (if (n == 1) truncatedJson else entityDetailJson("Nintendo")) to HttpStatusCode.OK
            },
        )

        val result = apiClient.getDeveloperDetail("Nintendo")

        assertEquals(2, calls[0], "expected exactly one retry after the first truncated body")
        assertEquals("Nintendo", result.name)
    }

    @Test
    fun getPublisherDetailRetriesAndRecoversAfterTruncatedBody() = runTest {
        val calls = intArrayOf(0)
        val apiClient = client(
            sequencedEngine(calls) { n ->
                (if (n == 1) truncatedJson else entityDetailJson("Capcom", publisher = true)) to HttpStatusCode.OK
            },
        )

        val result = apiClient.getPublisherDetail("Capcom")

        assertEquals(2, calls[0], "expected exactly one retry after the first truncated body")
        assertEquals("Capcom", result.name)
    }
}
