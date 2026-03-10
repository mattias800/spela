package com.spela.player.domain.repository

import com.spela.player.domain.model.ExploreRow
import com.spela.player.domain.model.FeaturedGame
import com.spela.player.domain.model.FeaturedSeries
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameFranchiseLink
import com.spela.player.domain.model.GameSeriesLink
import com.spela.player.domain.model.Keyword
import com.spela.player.domain.model.MoodDefinition
import com.spela.player.domain.model.SeriesDetail
import com.spela.player.domain.model.Theme

interface ExploreRepository {
    suspend fun getFeaturedGames(): Result<List<FeaturedGame>>
    suspend fun getExploreRows(): Result<List<ExploreRow>>
    suspend fun getThemes(): Result<List<Theme>>
    suspend fun getThemeGames(themeId: String, page: Int = 1, pageSize: Int = 20): Result<List<Game>>
    suspend fun getKeywords(limit: Int = 50): Result<List<Keyword>>
    suspend fun getKeywordGames(keywordId: String, page: Int = 1, pageSize: Int = 20): Result<List<Game>>
    suspend fun getFeaturedSeries(): Result<List<FeaturedSeries>>
    suspend fun getSeriesDetail(id: String): Result<SeriesDetail>
    suspend fun getGameSeries(gameId: String): Result<List<GameSeriesLink>>
    suspend fun getGameFranchises(gameId: String): Result<List<GameFranchiseLink>>
    suspend fun getMoods(): Result<List<MoodDefinition>>
    suspend fun getMoodGames(mood: String): Result<List<Game>>
    suspend fun getSurpriseGame(): Result<Game>
}
