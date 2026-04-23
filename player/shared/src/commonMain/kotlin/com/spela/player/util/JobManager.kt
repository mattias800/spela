package com.spela.player.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Helper for cancellable, keyed coroutine launches. Replaces the
 * common `private var xxxJob: Job? = null; xxxJob?.cancel(); xxxJob = scope.launch { … }`
 * pattern with a single `jobs.launch("xxx") { … }` call. The previous
 * job under the same key is automatically cancelled before the new
 * one starts, so re-entrant loaders never leak overlapping coroutines.
 *
 * Created in #690 to retire 25 separate Job fields in
 * `ExploreViewModel`. Lives in `util/` so other ViewModels with
 * the same pattern (NetplayManager, achievement loaders, etc.) can
 * adopt it.
 */
class JobManager(private val scope: CoroutineScope) {
    private val jobs = mutableMapOf<String, Job>()

    /**
     * Launch [block] under [key], cancelling any previous launch
     * under the same key. [context] is forwarded to the underlying
     * `scope.launch` (e.g. `dispatchers.io`).
     */
    fun launch(
        key: String,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        jobs[key]?.cancel()
        return scope.launch(context, block = block).also { jobs[key] = it }
    }

    /** Cancel the job under [key] if any; no-op otherwise. */
    fun cancel(key: String) {
        jobs.remove(key)?.cancel()
    }

    /**
     * True when there is a tracked job under [key] that is still
     * running. Used by callers that want to skip re-entrant work
     * (e.g. "if featured load is already in flight, don't restart").
     */
    fun isActive(key: String): Boolean = jobs[key]?.isActive == true

    /** Cancel every job currently tracked. Call from VM cleanup. */
    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}
