package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.ActivePlayer
import com.spela.player.domain.model.MostPlayedGame
import com.spela.player.domain.repository.StatsRepository

class StatsRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : StatsRepository {

    override suspend fun getMostPlayedGames(): Result<List<MostPlayedGame>> = runCatching {
        apiClient.getMostPlayedGames().games.orEmpty().map { dto ->
            val mostPlayed = dto.toDomain()
            mostPlayed.copy(
                game = mostPlayed.game.copy(
                    coverUrl = apiClient.resolveUrl(mostPlayed.game.coverUrl),
                ),
            )
        }
    }

    override suspend fun getMostActivePlayers(): Result<List<ActivePlayer>> = runCatching {
        apiClient.getMostActivePlayers().players.orEmpty().map { dto ->
            val player = dto.toDomain()
            player.copy(
                avatarUrl = apiClient.resolveUrl(player.avatarUrl),
            )
        }
    }
}
