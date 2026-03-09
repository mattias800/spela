package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.ExploreRow
import com.spela.player.domain.model.FeaturedGame
import com.spela.player.domain.repository.ExploreRepository

class ExploreRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : ExploreRepository {

    override suspend fun getFeaturedGames(): Result<List<FeaturedGame>> = runCatching {
        apiClient.getExploreFeatured().map { dto ->
            dto.toDomain().copy(
                heroUrl = apiClient.resolveUrl(dto.heroUrl),
                logoUrl = apiClient.resolveUrl(dto.logoUrl),
            )
        }
    }

    override suspend fun getExploreRows(): Result<List<ExploreRow>> = runCatching {
        apiClient.getExploreRows().map { dto ->
            dto.toDomain().copy(
                games = dto.games.map { gameDto ->
                    gameDto.toDomain().copy(
                        coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                        heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
                        logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
                    )
                },
            )
        }
    }
}
