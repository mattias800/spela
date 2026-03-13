package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.GlobalSearchResult
import com.spela.player.domain.repository.SearchRepository

class SearchRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : SearchRepository {

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
}
