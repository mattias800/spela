package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages a throttled queue of scrape-if-needed requests for games without cover art.
 *
 * Each game is only scraped once per session. Requests are processed sequentially
 * with a delay between them to avoid overwhelming the server.
 *
 * Also tracks per-game scraping status via WebSocket events from the server.
 */
class ScrapeService(
    private val apiClient: SpelaApiClient,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val requested = mutableSetOf<String>()
    private val queue = Channel<String>(Channel.UNLIMITED)

    /** Games currently being scraped (game IDs). */
    private val _scrapingGameIds = MutableStateFlow<Set<String>>(emptySet())
    val scrapingGameIds: StateFlow<Set<String>> = _scrapingGameIds.asStateFlow()

    /** Emitted when a game finishes scraping (status: idle). Observers should refetch game data. */
    private val _gameScrapeDone = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val gameScrapeDone: SharedFlow<String> = _gameScrapeDone.asSharedFlow()

    companion object {
        private const val THROTTLE_MS = 300L
    }

    init {
        scope.launch(dispatchers.io) {
            for (gameId in queue) {
                try {
                    apiClient.scrapeIfNeeded(gameId)
                } catch (_: Exception) {
                    // Server increments scrapeAttempts regardless -- silently continue
                }
                delay(THROTTLE_MS)
            }
        }
    }

    /**
     * Enqueue a game for scraping. No-op if already requested this session.
     */
    fun enqueueScrape(gameId: String) {
        synchronized(requested) {
            if (!requested.add(gameId)) return
        }
        queue.trySend(gameId)
    }

    /** Emitted when a game finishes scraping with updated cover URL. For cover art cards. */
    private val _scrapedCovers = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val scrapedCovers: SharedFlow<Pair<String, String>> = _scrapedCovers.asSharedFlow()

    /**
     * Called by PresenceService when a "game_scrape_status" WebSocket event arrives.
     */
    fun onScrapeStatusChanged(gameId: String, status: String) {
        when (status) {
            "scraping" -> _scrapingGameIds.update { it + gameId }
            "idle" -> {
                _scrapingGameIds.update { it - gameId }
                _gameScrapeDone.tryEmit(gameId)
                // Fetch updated game to get the new cover URL for card updates
                scope.launch(dispatchers.io) {
                    try {
                        val game = apiClient.getGameDetail(gameId)
                        val resolvedCover = apiClient.resolveUrl(game.coverUrl)
                        if (!resolvedCover.isNullOrEmpty()) {
                            _scrapedCovers.tryEmit(gameId to resolvedCover)
                        }
                    } catch (_: Exception) {
                        // Best effort — game detail page handles its own refresh
                    }
                }
            }
        }
    }

    /** Check if a specific game is currently being scraped. */
    fun isGameScraping(gameId: String): Boolean = gameId in _scrapingGameIds.value
}
