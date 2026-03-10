package com.spela.player.domain.repository

import com.spela.player.domain.model.ActiveNowItem
import com.spela.player.domain.model.ArtworkItem
import com.spela.player.domain.model.CommunityTopGame
import com.spela.player.domain.model.ConsoleHighlight
import com.spela.player.domain.model.ConsoleShowcase
import com.spela.player.domain.model.CultClassicGame
import com.spela.player.domain.model.DeveloperDetail
import com.spela.player.domain.model.DeveloperSpotlight
import com.spela.player.domain.model.DeveloperSummary
import com.spela.player.domain.model.ExploreRow
import com.spela.player.domain.model.FeaturedGame
import com.spela.player.domain.model.FeaturedSeries
import com.spela.player.domain.model.ForYouRow
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameFranchiseLink
import com.spela.player.domain.model.GameSeriesLink
import com.spela.player.domain.model.Keyword
import com.spela.player.domain.model.MoodDefinition
import com.spela.player.domain.model.PlayersLikeYouResult
import com.spela.player.domain.model.RecentReviewItem
import com.spela.player.domain.model.ScreenshotItem
import com.spela.player.domain.model.SeriesDetail
import com.spela.player.domain.model.TasteProfile
import com.spela.player.domain.model.Theme
import com.spela.player.domain.model.TrendingGame

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
    suspend fun getForYou(): Result<List<ForYouRow>>
    suspend fun getTasteProfile(): Result<TasteProfile>
    suspend fun getPlayersLikeYou(): Result<PlayersLikeYouResult>
    suspend fun getDevelopers(): Result<List<DeveloperSummary>>
    suspend fun getDeveloperDetail(name: String): Result<DeveloperDetail>
    suspend fun getPublisherDetail(name: String): Result<DeveloperDetail>
    suspend fun getDeveloperSpotlight(): Result<DeveloperSpotlight>
    suspend fun getConsoleShowcase(consoleId: String): Result<ConsoleShowcase>
    suspend fun getConsoleHighlights(): Result<List<ConsoleHighlight>>
    suspend fun getArtworkGallery(page: Int = 1): Result<List<ArtworkItem>>
    suspend fun getScreenshotGallery(page: Int = 1): Result<List<ScreenshotItem>>
    suspend fun getTrending(): Result<List<TrendingGame>>
    suspend fun getCommunityTop(): Result<List<CommunityTopGame>>
    suspend fun getCultClassics(): Result<List<CultClassicGame>>
    suspend fun getRecentlyReviewed(): Result<List<RecentReviewItem>>
    suspend fun getActiveNow(): Result<List<ActiveNowItem>>
}
