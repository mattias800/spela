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
