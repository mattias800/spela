package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import com.spela.player.data.remote.ScrapeService

/**
 * Provides the ScrapeService to the composable tree so any game card
 * can trigger scrape-if-needed without threading callbacks through every layer.
 */
val LocalScrapeService = compositionLocalOf<ScrapeService?> { null }

/**
 * Triggers a scrape-if-needed for a game when it enters composition.
 * Only fires if the game has no cover art and scrapeAttempts is 0.
 * Safe to call for any game — ScrapeService deduplicates and throttles.
 */
@Composable
fun AutoScrapeIfNeeded(gameId: String, coverUrl: String?, scrapeAttempts: Int) {
    val scrapeService = LocalScrapeService.current
    LaunchedEffect(gameId) {
        if (coverUrl.isNullOrEmpty() && scrapeAttempts == 0 && scrapeService != null) {
            scrapeService.enqueueScrape(gameId)
        }
    }
}
