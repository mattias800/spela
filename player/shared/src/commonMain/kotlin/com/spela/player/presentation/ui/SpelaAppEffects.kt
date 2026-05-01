package com.spela.player.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.spela.player.data.remote.PresenceService
import com.spela.player.data.remote.ScrapeService
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.presentation.ui.components.ScrapeUpdates
import com.spela.player.presentation.navigation.NavigationEvent
import com.spela.player.presentation.navigation.NavigationEventBus
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel

/**
 * Root-level side effects that used to be three interleaved
 * `LaunchedEffect` blocks scattered through the `SpelaApp` body.
 * Grouped here in #693 PR D so the main composable reads as a layout
 * description rather than a mix of layout and wire-up.
 *
 * Each effect keeps its own key, so reactivity is identical to the
 * pre-refactor code:
 *
 * - `scrapeService` — re-subscribes if the service reference changes
 *   (practically once, since it comes from DI).
 * - `isAuthenticated` — flips between connect/disconnect whenever the
 *   navigation state crosses the login / server-connection screens.
 * - `navigationEventBus` — registers once if the bus is present (null
 *   on Android; desktop wires it up for gamepad nav).
 */
@Composable
fun SpelaAppEffects(
    scrapeService: ScrapeService?,
    presenceService: PresenceService,
    isAuthenticated: Boolean,
    navigationEventBus: NavigationEventBus?,
    navigationViewModel: NavigationViewModel,
    downloadRepository: DownloadRepository,
) {
    LaunchedEffect(scrapeService) {
        scrapeService?.scrapedCovers?.collect { (gameId, coverUrl) ->
            ScrapeUpdates.onCoverUpdated(gameId, coverUrl)
        }
    }

    // Sweep partial download artifacts left over from a previous app
    // launch (process death, force-stop, OOM kill mid-download). Walks
    // the games directory once at composition start and removes any
    // per-game directory not in the local downloads table. Idempotent.
    // See #845.
    LaunchedEffect(downloadRepository) {
        try {
            downloadRepository.scanForOrphanedDownloads()
        } catch (e: Exception) {
            println("[Download] orphan scan failed at startup: ${e.message}")
        }
    }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            presenceService.connect()
        } else {
            presenceService.disconnect()
        }
    }

    if (navigationEventBus != null) {
        LaunchedEffect(navigationEventBus) {
            navigationEventBus.events.collect { event ->
                when (event) {
                    NavigationEvent.NextSection ->
                        navigationViewModel.onIntent(NavigationIntent.NextSection)
                    NavigationEvent.PreviousSection ->
                        navigationViewModel.onIntent(NavigationIntent.PreviousSection)
                }
            }
        }
    }
}
