package com.spela.player.data.repository

import com.spela.player.domain.repository.CoreRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mutable byte-counter snapshot exposed by [CoreDownloadHandle]. Mirrors
 * the underlying `CoreRepository.downloadCore` callback contract — total
 * may be `null` when the server omits Content-Length.
 */
data class CoreDownloadSnapshot(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
)

/**
 * Handle to an in-flight (or just-completed) core download.
 *
 * Two callers may share one handle: the post-login prefetch in
 * [CoreUpdateService.prefetchStaleCachedCores] kicks off downloads
 * without observing, then a game-launch path that lands on the same
 * core during the prefetch reuses the in-flight handle so both see the
 * same byte counter and the same finished binary. There is never more
 * than one HTTP fetch for a single core at a time.
 */
class CoreDownloadHandle internal constructor(
    val coreName: String,
    progressFlow: StateFlow<CoreDownloadSnapshot>,
    private val deferred: Deferred<Result<String>>,
) {
    val progress: StateFlow<CoreDownloadSnapshot> = progressFlow

    /** Suspends until the download completes, then returns its result. */
    suspend fun await(): Result<String> = deferred.await()
}

/**
 * Coordinates core download lifecycles so the post-login prefetch pass
 * and the per-game-launch download flow don't race each other or
 * double-fetch the same core. See #1192.
 *
 * Single instance per app session (wired as a Koin `single`). Holds a
 * map of `coreName -> CoreDownloadHandle` for in-flight fetches; the
 * entry is removed once the download completes (success or failure) so
 * a subsequent stale-vs-server check on the next game launch can
 * trigger a fresh download.
 *
 * Lifetime:
 *
 *   - [prefetchStaleCachedCores] runs at most once per app session
 *     (gated by [sessionStarted]). Called after login completes —
 *     re-calls within the same session no-op so the post-login one-
 *     shot wiring in NavigationViewModel doesn't need to track its
 *     own "already fired" flag.
 *
 *   - [downloadCore] is safe to call repeatedly: it dedupes against the
 *     in-flight map, so two concurrent callers for the same core share
 *     one download.
 *
 * The service intentionally has no shutdown hook. It owns no resources
 * outside the injected [scope]; cancelling that scope cancels every
 * in-flight download.
 */
class CoreUpdateService(
    private val coreRepository: CoreRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CoreDownloadHandle>()

    // Guarded by [mutex]. Not atomic because every prefetchStaleCachedCores
    // path acquires the mutex anyway (via downloadCore) — keeping the flag
    // under the same lock means we don't need a second synchronization
    // primitive.
    private var sessionStarted: Boolean = false

    /**
     * Returns a deduplicated handle for downloading [coreName]. If a
     * download is already in flight for the same name, the existing
     * handle is returned so both callers see the same progress and the
     * same finished file.
     *
     * The download runs on [scope] regardless of who awaits. If no one
     * awaits, the download still completes (used by the prefetch pass,
     * which fires-and-forgets).
     */
    suspend fun downloadCore(coreName: String, customDownloadUrl: String?): CoreDownloadHandle =
        mutex.withLock {
            inFlight[coreName]?.let { return@withLock it }

            val progressFlow = MutableStateFlow(CoreDownloadSnapshot(0L, null))
            val deferred = scope.async(dispatchers.io) {
                val result = coreRepository.downloadCore(
                    coreName = coreName,
                    customDownloadUrl = customDownloadUrl,
                    onProgress = { sent, total ->
                        progressFlow.value = CoreDownloadSnapshot(sent, total)
                    },
                )
                // Belt-and-suspenders: when the HTTP layer never emitted
                // progress (Content-Length absent OR the body was fully
                // buffered before any chunk reached us), flag completion
                // so observers can tear down the sheet. If we DID see a
                // total, mirror it as the final value so the UI shows
                // 100% rather than the last mid-flight sample.
                progressFlow.value = progressFlow.value.let { last ->
                    val total = last.totalBytes
                    if (total != null && total > 0) {
                        CoreDownloadSnapshot(total, total)
                    } else {
                        last
                    }
                }
                mutex.withLock { inFlight.remove(coreName) }
                result
            }
            val handle = CoreDownloadHandle(coreName, progressFlow.asStateFlow(), deferred)
            inFlight[coreName] = handle
            handle
        }

    /**
     * Returns the in-flight handle for [coreName] if a download is
     * currently running, or `null` otherwise. Game-launch paths use
     * this to decide whether to reuse a prefetch's download
     * (subscribing to its progress) or start their own.
     */
    suspend fun inFlightDownload(coreName: String): CoreDownloadHandle? =
        mutex.withLock { inFlight[coreName] }

    /**
     * Background prefetch: for every locally-cached core, ask the
     * server for its current per-platform sha256 and silently re-
     * download anything that's stale. Gated on the user's
     * `autoUpdateCoresEnabled` preference (default true).
     *
     * Idempotent across the app session: only the first call kicks off
     * the pass; subsequent calls return immediately. The prefetch
     * launches on [scope] and never blocks the caller.
     */
    fun prefetchStaleCachedCores() {
        scope.launch(dispatchers.io) {
            val shouldRun = mutex.withLock {
                if (sessionStarted) false else {
                    sessionStarted = true
                    true
                }
            }
            if (!shouldRun) return@launch
            runPrefetchPass()
        }
    }

    private suspend fun runPrefetchPass() {
        val enabled = runCatching {
            preferencesRepository.getPreferences().getOrNull()?.autoUpdateCoresEnabled
        }.getOrNull() ?: true
        if (!enabled) {
            println("[CoreUpdateService] prefetch skipped — autoUpdateCoresEnabled is false")
            return
        }

        val cores = coreRepository.getAvailableCores().getOrNull() ?: run {
            println("[CoreUpdateService] prefetch skipped — getAvailableCores failed")
            return
        }

        var stale = 0
        for (core in cores) {
            // Only cores the user has actually used. New users / never-
            // played consoles cost zero extra bandwidth.
            if (coreRepository.getLocalCorePath(core.name) == null) continue
            // Tri-state: only act on an explicit "false" (server sha
            // known and differs). `null` means "can't decide" — defer
            // to the per-launch staleness check rather than guess.
            if (coreRepository.isCachedCoreCurrent(core.name) != false) continue

            stale++
            println("[CoreUpdateService] prefetching stale core ${core.name}")
            // Kick off the download but don't await — the prefetch
            // pass should complete promptly even if individual cores
            // take a while.
            downloadCore(core.name, core.customDownloadUrl)
        }
        println("[CoreUpdateService] prefetch pass complete (stale=$stale of ${cores.size} cached/known)")
    }
}
