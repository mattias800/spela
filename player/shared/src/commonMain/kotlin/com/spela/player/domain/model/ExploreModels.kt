package com.spela.player.domain.model

data class FeaturedGame(
    val gameId: String,
    val title: String,
    val heroUrl: String?,
    val logoUrl: String?,
    val consoleAbbreviation: String,
    val consoleColor: String,
    val rating: Double,
    val genre: String,
    val isFavorite: Boolean,
    val isPlayLater: Boolean,
)

data class ExploreRow(
    val id: String,
    val title: String,
    val games: List<Game>,
)

data class Theme(
    val id: String,
    val name: String,
    val gameCount: Int,
)

data class Keyword(
    val id: String,
    val name: String,
    val gameCount: Int,
)

data class FeaturedSeries(
    val id: String,
    val name: String,
    val libraryGames: Int,
    val totalGames: Int,
    val consoleCount: Int,
    val heroUrl: String?,
)

data class SeriesDetail(
    val id: String,
    val name: String,
    val heroUrl: String?,
    val consoles: List<SeriesConsole>,
    val libraryGames: Int,
    val totalGames: Int,
    val games: List<SeriesGame>,
)

data class SeriesConsole(
    val abbreviation: String,
    val name: String,
    val color: String,
    val gameCount: Int,
)

data class SeriesGame(
    val igdbGameId: Int,
    val name: String,
    val inLibrary: Boolean,
    val localGameId: String?,
    val coverUrl: String?,
    val releaseDate: String?,
    val rating: Double,
    val consoleAbbreviation: String?,
    val consoleName: String?,
    val consoleColor: String?,
)

data class GameSeriesLink(
    val id: String,
    val name: String,
    val totalGames: Int,
    val libraryGames: Int,
)

data class GameFranchiseLink(
    val id: String,
    val name: String,
    val gameCount: Int,
)

data class MoodDefinition(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val gradient: List<String>,
)

data class ForYouRow(
    val type: String,  // "because_you_played", "more_genre", "unfinished", "expand_horizons"
    val title: String,
    val sourceGame: Game?,  // only for because_you_played
    val genre: String?,     // only for expand_horizons
    val games: List<Game>,
)

data class TasteBreakdown(
    val name: String,
    val percentage: Double,
    val playTime: Long,
    val gameCount: Int,
)

data class ConsoleBreakdown(
    val name: String,
    val abbreviation: String,
    val playTime: Long,
    val gameCount: Int,
)

data class TasteProfile(
    val totalPlayTime: Long,
    val genres: List<TasteBreakdown>,
    val themes: List<TasteBreakdown>,
    val topConsoles: List<ConsoleBreakdown>,
)

data class PlayersLikeYouResult(
    val games: List<Game>,
    val similarUsersCount: Int,
)

data class DeveloperSummary(
    val name: String,
    val gameCount: Int,
    val avgRating: Double,
    val consoles: List<String>,
)

data class DeveloperDetail(
    val name: String,
    val gameCount: Int,
    val avgRating: Double,
    val consoles: List<String>,
    val games: List<Game>,
)

data class DeveloperSpotlight(
    val name: String,
    val gameCount: Int,
    val avgRating: Double,
    val consoles: List<String>,
    val topGames: List<Game>,
    val heroUrl: String?,
)

data class GenreCount(
    val name: String,
    val gameCount: Int,
)

data class ConsoleShowcase(
    val console: Console,
    val essentials: List<Game>,
    val hiddenGems: List<Game>,
    val genreBreakdown: List<GenreCount>,
    val topDevelopers: List<DeveloperSummary>,
    val recentlyPlayed: List<Game>,
)

data class ConsoleHighlight(
    val id: String,
    val name: String,
    val colorTheme: String,
    val iconUrl: String,
    val logoUrl: String,
    val gameCount: Int,
    val topGame: Game?,
)

data class ArtworkItem(
    val url: String,
    val width: Int,
    val height: Int,
    val gameId: String,
    val gameTitle: String,
    val consoleName: String,
    val consoleAbbreviation: String,
    val consoleColor: String,
)

data class ScreenshotItem(
    val url: String,
    val gameId: String,
    val gameTitle: String,
    val consoleName: String,
    val consoleAbbreviation: String,
    val consoleColor: String,
)

// --- Phase 10: Social & Community Discovery ---

data class TrendingGame(
    val game: Game,
    val playersThisWeek: Int,
)

data class CommunityTopGame(
    val game: Game,
    val avgRating: Double,
    val ratingCount: Int,
)

data class CultClassicGame(
    val game: Game,
    val communityRating: Double,
    val igdbRating: Double,
    val ratingCount: Int,
)

data class RecentReviewItem(
    val game: Game,
    val rating: Int,
    val review: String,
    val reviewerName: String,
    val reviewedAt: String,
)

data class ActiveNowItem(
    val game: Game,
    val activeSessions: Int,
    val activeChallenges: Int,
)

// --- Phase 11: Temporal Discovery ---

data class AnniversaryItem(
    val game: Game,
    val yearsAgo: Int,
    val playedAt: String,
)

// --- Phase 12: Achievement & Challenge-Driven Discovery ---

data class AchievementGameItem(
    val game: Game,
    val totalAchievements: Int,
    val avgCompletion: Float,
    val playersAttempted: Int,
    val playersCompleted: Int,
)

data class AlmostDoneGame(
    val game: Game,
    val unlockedCount: Int,
    val totalCount: Int,
    val completionPercent: Float,
)

data class FreshChallengeGame(
    val game: Game,
    val totalAchievements: Int,
    val totalPoints: Int,
)

data class ExploreChallenge(
    val id: String,
    val creatorUsername: String,
    val gameId: String,
    val gameTitle: String,
    val gameCoverUrl: String?,
    val consoleName: String?,
    val name: String,
    val description: String?,
    val type: String,
    val difficulty: String,
    val attemptCount: Int,
    val completionCount: Int,
    val expiresAt: String?,
    val createdAt: String,
)

// --- Phase 13: Advanced Search & Multi-Faceted Filtering ---

data class SavedSearch(
    val id: String,
    val name: String,
    val filters: Map<String, String>,
    val createdAt: String,
)

data class GameFilters(
    val search: String = "",
    val consoles: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val perspectives: List<String> = emptyList(),
    val developer: String = "",
    val publisher: String = "",
    val yearMin: Int? = null,
    val yearMax: Int? = null,
    val ratingMin: Double? = null,
    val ratingMax: Double? = null,
    val playStatus: String = "",
    val sortBy: String = "",
    val sortOrder: String = "",
) {
    val isEmpty: Boolean
        get() = search.isBlank() && consoles.isEmpty() && genres.isEmpty() &&
            themes.isEmpty() && keywords.isEmpty() && perspectives.isEmpty() &&
            developer.isBlank() && publisher.isBlank() &&
            yearMin == null && yearMax == null &&
            ratingMin == null && ratingMax == null &&
            playStatus.isBlank() && sortBy.isBlank()

    fun toQueryMap(): Map<String, String> = buildMap {
        if (search.isNotBlank()) put("search", search)
        if (consoles.isNotEmpty()) put("consoles", consoles.joinToString(","))
        if (genres.isNotEmpty()) put("genres", genres.joinToString(","))
        if (themes.isNotEmpty()) put("themes", themes.joinToString(","))
        if (keywords.isNotEmpty()) put("keywords", keywords.joinToString(","))
        if (perspectives.isNotEmpty()) put("perspectives", perspectives.joinToString(","))
        if (developer.isNotBlank()) put("developer", developer)
        if (publisher.isNotBlank()) put("publisher", publisher)
        yearMin?.let { put("yearMin", it.toString()) }
        yearMax?.let { put("yearMax", it.toString()) }
        ratingMin?.let { put("ratingMin", it.toString()) }
        ratingMax?.let { put("ratingMax", it.toString()) }
        if (playStatus.isNotBlank()) put("playStatus", playStatus)
        if (sortBy.isNotBlank()) put("sortBy", sortBy)
        if (sortOrder.isNotBlank()) put("sortOrder", sortOrder)
    }

    companion object {
        fun fromQueryMap(map: Map<String, String>): GameFilters = GameFilters(
            search = map["search"] ?: "",
            consoles = map["consoles"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            genres = map["genres"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            themes = map["themes"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            keywords = map["keywords"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            perspectives = map["perspectives"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            developer = map["developer"] ?: "",
            publisher = map["publisher"] ?: "",
            yearMin = map["yearMin"]?.toIntOrNull(),
            yearMax = map["yearMax"]?.toIntOrNull(),
            ratingMin = map["ratingMin"]?.toDoubleOrNull(),
            ratingMax = map["ratingMax"]?.toDoubleOrNull(),
            playStatus = map["playStatus"] ?: "",
            sortBy = map["sortBy"] ?: "",
            sortOrder = map["sortOrder"] ?: "",
        )
    }
}
