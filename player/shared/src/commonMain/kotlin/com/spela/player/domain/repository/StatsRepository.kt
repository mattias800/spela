package com.spela.player.domain.repository

import com.spela.player.domain.model.ActivePlayer
import com.spela.player.domain.model.MostPlayedGame

interface StatsRepository {
    suspend fun getMostPlayedGames(): Result<List<MostPlayedGame>>
    suspend fun getMostActivePlayers(): Result<List<ActivePlayer>>
}
