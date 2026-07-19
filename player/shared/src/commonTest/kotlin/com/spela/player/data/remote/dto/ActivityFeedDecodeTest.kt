package com.spela.player.data.remote.dto

import com.spela.client.infrastructure.ApiClient
import com.spela.client.models.PaginatedResponseActivityEventResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression test for #720 / #1675: the server sends
 * ActivityEventResponse.metadata as a JSON object (e.g. {"seconds":0}) and
 * omits it for events without metadata. The generated DTO once declared the
 * field as kotlin.String, which made every activity feed fetch throw
 * JsonDecodingException at runtime — and a client regen silently
 * reintroduced the bug after it was hand-patched.
 *
 * Decodes a captured real payload with the exact serializer and Json
 * configuration the app uses (SpelaApiClient.getActivityFeed →
 * ApiClient.JSON_DEFAULT), so a mistyped regen fails this test at build time
 * instead of breaking the feed at runtime.
 */
class ActivityFeedDecodeTest {

    // Trimmed capture of GET /api/social/activity from a real server
    // (2026-07-19): one event per metadata shape — object metadata,
    // omitted metadata, and rated_game with an extra unknown key to
    // verify forward compatibility.
    private val capturedFeedJson = """
        {"${'$'}schema":"https://spela.example/api/schemas/PaginatedResponseActivityEventResponse.json",
         "data":[
          {"id":"14070","eventType":"started_playing","createdAt":"2026-07-19T14:50:44.053601236Z",
           "userId":"5","username":"lindskogen","avatarUrl":"","gameId":"32765",
           "gameTitle":"Avatar: The Last Airbender","gameCoverUrl":"/api/images/GBA/32765/boxart-libretro.png",
           "consoleName":"Game Boy Advance","metadata":{"seconds":0}},
          {"id":"14069","eventType":"favorited_game","createdAt":"2026-07-19T14:49:01Z",
           "userId":"5","username":"lindskogen","avatarUrl":"","gameId":"32765",
           "gameTitle":"Avatar: The Last Airbender","gameCoverUrl":"/api/images/GBA/32765/boxart-libretro.png",
           "consoleName":"Game Boy Advance"},
          {"id":"14068","eventType":"rated_game","createdAt":"2026-07-19T14:48:00Z",
           "userId":"5","username":"lindskogen","avatarUrl":"","gameId":"32765",
           "gameTitle":"Avatar: The Last Airbender","gameCoverUrl":"/api/images/GBA/32765/boxart-libretro.png",
           "consoleName":"Game Boy Advance","metadata":{"rating":5,"someFutureField":"x"}}
         ],
         "total":3,"page":1,"pageSize":20}
    """.trimIndent()

    @Test
    fun decodesCapturedActivityFeedPayload() {
        val feed = ApiClient.JSON_DEFAULT.decodeFromString(
            PaginatedResponseActivityEventResponse.serializer(),
            capturedFeedJson,
        )

        assertEquals(3, feed.data.size)
        assertEquals(0L, feed.data[0].metadata?.seconds)
        assertNull(feed.data[1].metadata)
        assertEquals(5L, feed.data[2].metadata?.rating)
    }
}
