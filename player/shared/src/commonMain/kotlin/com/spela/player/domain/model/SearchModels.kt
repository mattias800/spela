package com.spela.player.domain.model

data class GlobalSearchResult(
    val games: SearchCategory<SearchGameResult> = SearchCategory(),
    val consoles: SearchCategory<SearchConsoleResult> = SearchCategory(),
    val developers: SearchCategory<SearchDeveloperResult> = SearchCategory(),
    val publishers: SearchCategory<SearchPublisherResult> = SearchCategory(),
    val collections: SearchCategory<SearchCollectionResult> = SearchCategory(),
    val series: SearchCategory<SearchSeriesResult> = SearchCategory(),
    val franchises: SearchCategory<SearchFranchiseResult> = SearchCategory(),
) {
    val isEmpty: Boolean
        get() = games.results.isEmpty() &&
                consoles.results.isEmpty() &&
                developers.results.isEmpty() &&
                publishers.results.isEmpty() &&
                collections.results.isEmpty() &&
                series.results.isEmpty() &&
                franchises.results.isEmpty()
}

data class SearchCategory<T>(
    val results: List<T> = emptyList(),
    val total: Int = 0,
)

data class SearchGameResult(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val consoleName: String = "",
    val consoleId: String = "",
    val developer: String? = null,
    val genre: String? = null,
    val coverAspectRatio: Float = 0.75f,
    val platforms: List<GamePlatform> = emptyList(),
)

data class SearchConsoleResult(
    val id: String,
    val name: String,
    val iconUrl: String = "",
    val gameCount: Int = 0,
    val colorTheme: String = "#6366f1",
)

data class SearchDeveloperResult(
    val name: String,
    val gameCount: Int = 0,
    val avgRating: Double = 0.0,
)

data class SearchPublisherResult(
    val name: String,
    val gameCount: Int = 0,
    val avgRating: Double = 0.0,
)

data class SearchCollectionResult(
    val id: String,
    val name: String,
    val gameCount: Int = 0,
    val coverUrl: String? = null,
    val username: String = "",
)

data class SearchSeriesResult(
    val id: String,
    val name: String,
    val totalGames: Int = 0,
    val libraryGames: Int = 0,
)

data class SearchFranchiseResult(
    val id: String,
    val name: String,
    val totalGames: Int = 0,
    val libraryGames: Int = 0,
)

data class SearchSuggestion(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val imageUrl: String? = null,
    val navigationType: SuggestionNavigationType,
) {
    val type: String get() = when (navigationType) {
        is SuggestionNavigationType.Game -> "Game"
        is SuggestionNavigationType.Console -> "Console"
        is SuggestionNavigationType.Developer -> "Developer"
        is SuggestionNavigationType.Publisher -> "Publisher"
        is SuggestionNavigationType.Collection -> "Collection"
        is SuggestionNavigationType.Series -> "Series"
        is SuggestionNavigationType.Franchise -> "Franchise"
    }
}

sealed class SuggestionNavigationType {
    data class Game(val id: String) : SuggestionNavigationType()
    data class Console(val id: String) : SuggestionNavigationType()
    data class Developer(val name: String) : SuggestionNavigationType()
    data class Publisher(val name: String) : SuggestionNavigationType()
    data class Collection(val id: String) : SuggestionNavigationType()
    data class Series(val id: String, val name: String) : SuggestionNavigationType()
    data class Franchise(val id: String, val name: String) : SuggestionNavigationType()
}
