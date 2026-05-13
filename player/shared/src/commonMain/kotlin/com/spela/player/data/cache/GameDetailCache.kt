package com.spela.player.data.cache

import com.spela.player.domain.model.GameDetail

/**
 * Process-wide in-memory cache of the most recently observed
 * [GameDetail] for each gameId. Populated whenever the
 * [com.spela.player.presentation.viewmodel.GameDetailViewModel] writes
 * a fresh detail into its on-screen state — load success, scrape
 * refresh, favourite toggle, etc.
 *
 * Read by `loadGame(...)` at screen entry: if the entry exists, the
 * cached detail is shown *immediately* while a fresh network fetch is
 * fired in the background. By the time the fetch returns, the user
 * has already been looking at correct (or near-correct) data for
 * hundreds of ms — instead of a skeleton.
 *
 * Lifetime: in-memory only, dies with the JVM. No TTL — we trust the
 * fresh-fetch-on-every-visit to correct any drift, and the write-
 * through pattern means cache entries reflect on-screen truth as long
 * as the ViewModel routes its `gameDetail` mutations through
 * [GameDetailViewModel.mutateGameDetail] (see that helper for the
 * discipline).
 *
 * Thread-safety: reads and writes both happen on the Compose
 * composition / main thread or on `Dispatchers.IO` collected back
 * into the StateFlow. We use a synchronized map to avoid a published-
 * but-not-yet-readable window without paying for a full concurrent
 * collection's allocator overhead — game-detail mutations are rare
 * enough that the lock contention is negligible.
 */
object GameDetailCache {
    private val entries = mutableMapOf<String, GameDetail>()
    private val lock = Any()

    fun get(gameId: String): GameDetail? = synchronized(lock) { entries[gameId] }

    fun put(gameId: String, detail: GameDetail) {
        synchronized(lock) { entries[gameId] = detail }
    }

    /** Test-only — clears every entry. Production code never needs this. */
    fun clear() {
        synchronized(lock) { entries.clear() }
    }
}
