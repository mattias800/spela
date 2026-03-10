package com.spela.player.domain.repository

import com.spela.player.domain.model.ExploreRow
import com.spela.player.domain.model.FeaturedGame
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.Keyword
import com.spela.player.domain.model.Theme

interface ExploreRepository {
    suspend fun getFeaturedGames(): Result<List<FeaturedGame>>
    suspend fun getExploreRows(): Result<List<ExploreRow>>
    suspend fun getThemes(): Result<List<Theme>>
    suspend fun getThemeGames(themeId: String, page: Int = 1, pageSize: Int = 20): Result<List<Game>>
    suspend fun getKeywords(limit: Int = 50): Result<List<Keyword>>
    suspend fun getKeywordGames(keywordId: String, page: Int = 1, pageSize: Int = 20): Result<List<Game>>
}
