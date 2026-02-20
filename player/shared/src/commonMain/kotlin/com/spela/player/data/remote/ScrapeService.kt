package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.model.Game
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Manages a throttled queue of scrape-if-needed requests for games without cover art.
 *
 * Each game is only scraped once per session. Requests are processed sequentially
 * with a delay between them to avoid overwhelming the server.
 */
class ScrapeService(
    private val apiClient: SpelaApiClient,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val requested = mutableSetOf<String>()
    private val queue = Channel<String>(Channel.UNLIMITED)

    private val _scrapedGames = MutableSharedFlow<Game>(extraBufferCapacity = 16)
    val scrapedGames: SharedFlow<Game> = _scrapedGames.asSharedFlow()

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

    /**
     * Called by PresenceService when a "game_scraped" WebSocket event arrives.
     * Emits the updated game to observers.
     */
    fun onGameScraped(game: Game) {
        _scrapedGames.tryEmit(game)
    }
}
