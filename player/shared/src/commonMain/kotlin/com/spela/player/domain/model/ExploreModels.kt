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
