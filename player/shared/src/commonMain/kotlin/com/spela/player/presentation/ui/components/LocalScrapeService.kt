package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import com.spela.player.data.remote.ScrapeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provides the ScrapeService to the composable tree so any game card
 * can trigger scrape-if-needed without threading callbacks through every layer.
 */
val LocalScrapeService = compositionLocalOf<ScrapeService?> { null }

/**
 * Holds a map of game ID → updated cover URL from scrape events.
 * Composables can observe this to reactively update cover art.
 */
object ScrapeUpdates {
    private val _updatedCovers = MutableStateFlow<Map<String, String>>(emptyMap())
    val updatedCovers: StateFlow<Map<String, String>> = _updatedCovers.asStateFlow()

    fun onCoverUpdated(gameId: String, coverUrl: String) {
        _updatedCovers.value = _updatedCovers.value + (gameId to coverUrl)
    }
}

/**
 * Triggers a scrape-if-needed for a game when it enters composition.
 * Only fires if the game has no cover art and scrapeAttempts is 0.
 * Safe to call for any game — ScrapeService deduplicates and throttles.
 *
 * Returns the resolved cover URL — either the original or one from a scrape update.
 */
@Composable
fun rememberResolvedCoverUrl(gameId: String, coverUrl: String?, scrapeAttempts: Int): String? {
    val scrapeService = LocalScrapeService.current
    LaunchedEffect(gameId) {
        if (coverUrl.isNullOrEmpty() && scrapeAttempts == 0 && scrapeService != null) {
            scrapeService.enqueueScrape(gameId)
        }
    }

    val updatedCovers by ScrapeUpdates.updatedCovers.collectAsState()
    return updatedCovers[gameId] ?: coverUrl
}
