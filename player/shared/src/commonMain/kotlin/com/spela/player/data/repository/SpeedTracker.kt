package com.spela.player.data.repository

import kotlin.time.Clock

/**
 * Rolling-window byte-rate calculator for active downloads.
 *
 * Each call to [record] supplies a `(now, totalBytesDownloadedSoFar)` sample
 * and returns the average bytes-per-second over the most recent
 * [windowMs]. Samples older than the window are dropped on every call.
 *
 * The instantaneous delta between two emissions is jittery because the
 * underlying HTTP callback fires unevenly (network bursts, OkHttp
 * buffer flushes). The rolling average smooths that out so the UI label
 * doesn't flicker between unrealistic numbers.
 *
 * Speed reported as 0 until the window has at least two samples *and*
 * non-zero elapsed time. When a download stalls (no bytes for the full
 * window), the oldest non-stale sample's bytes equal the latest, so
 * the reported speed naturally falls back to 0.
 *
 * Thread-safety: not synchronised — callers serialise their own
 * progress emissions per game id.
 */
internal class SpeedTracker(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private data class Sample(val timestamp: Long, val bytes: Long)

    private val samples = ArrayDeque<Sample>()

    fun record(bytesDownloaded: Long): Long {
        val t = now()
        // Drop samples older than the window.
        while (samples.isNotEmpty() && t - samples.first().timestamp > windowMs) {
            samples.removeFirst()
        }
        samples.addLast(Sample(t, bytesDownloaded))
        if (samples.size < 2) return 0L
        val first = samples.first()
        val deltaBytes = bytesDownloaded - first.bytes
        val deltaMs = t - first.timestamp
        if (deltaMs <= 0L || deltaBytes < 0L) return 0L
        return deltaBytes * 1_000L / deltaMs
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 2_000L
    }
}
