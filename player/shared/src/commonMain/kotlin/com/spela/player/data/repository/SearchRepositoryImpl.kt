package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.GlobalSearchResult
import com.spela.player.domain.repository.SearchRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SearchRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val database: SpelaDatabase,
) : SearchRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun globalSearch(query: String, limit: Int): Result<GlobalSearchResult> = runCatching {
        val dto = apiClient.globalSearch(query, limit)
        val result = dto.toDomain()
        // Resolve image URLs for games and consoles
        result.copy(
            games = result.games.copy(
                results = result.games.results.map { game ->
                    game.copy(coverUrl = apiClient.resolveUrl(game.coverUrl))
                },
            ),
            consoles = result.consoles.copy(
                results = result.consoles.results.map { console ->
                    console.copy(iconUrl = apiClient.resolveUrl(console.iconUrl) ?: "")
                },
            ),
            collections = result.collections.copy(
                results = result.collections.results.map { collection ->
                    collection.copy(coverUrl = apiClient.resolveUrl(collection.coverUrl))
                },
            ),
        )
    }

    override fun getRecentSearches(): List<String> {
        val raw = database.spelaDatabaseQueries
            .getDeviceSetting(RECENT_SEARCHES_KEY)
            .executeAsOneOrNull() ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<String>>(raw)
        }.getOrDefault(emptyList())
    }

    override fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = getRecentSearches().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val capped = current.take(MAX_RECENT_SEARCHES)
        database.spelaDatabaseQueries.insertDeviceSetting(
            RECENT_SEARCHES_KEY,
            json.encodeToString(capped),
        )
    }

    override fun removeRecentSearch(query: String) {
        val current = getRecentSearches().toMutableList()
        current.remove(query)
        if (current.isEmpty()) {
            database.spelaDatabaseQueries.deleteDeviceSetting(RECENT_SEARCHES_KEY)
        } else {
            database.spelaDatabaseQueries.insertDeviceSetting(
                RECENT_SEARCHES_KEY,
                json.encodeToString(current),
            )
        }
    }

    override fun clearRecentSearches() {
        database.spelaDatabaseQueries.deleteDeviceSetting(RECENT_SEARCHES_KEY)
    }

    companion object {
        private const val RECENT_SEARCHES_KEY = "recent_searches"
        private const val MAX_RECENT_SEARCHES = 5
    }
}
