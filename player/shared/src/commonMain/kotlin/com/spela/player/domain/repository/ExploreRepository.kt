package com.spela.player.domain.repository

import com.spela.player.domain.model.ExploreRow
import com.spela.player.domain.model.FeaturedGame

interface ExploreRepository {
    suspend fun getFeaturedGames(): Result<List<FeaturedGame>>
    suspend fun getExploreRows(): Result<List<ExploreRow>>
}
