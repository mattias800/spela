package com.spela.player.libretro

/**
 * Largest wall-clock gap between two consecutive presented frames that
 * still counts as active play time, in milliseconds.
 *
 * When the emulator is actually running, frames arrive every ~16 ms
 * (60 fps) up to ~66 ms on a struggling 15 fps core — always well under
 * this cap. A gap larger than the cap means the emulator was *not*
 * advancing frames (in-app pause, app backgrounded, OS suspend, lid
 * closed, a long stall), so the gap is clamped to one cap's worth: the
 * idle stretch is not credited as play time. This is the mechanism that
 * keeps "game loaded but not running" time out of the total (#1282).
 */
const val MAX_FRAME_GAP_MILLIS: Long = 250L

/**
 * Clamped wall-clock delta (ms) to credit for a frame presented at
 * [nowNanos], given the previous presented frame at [prevNanos].
 *
 * Returns 0 when there is no previous frame ([prevNanos] == 0, the
 * baseline-reset sentinel used after a pause) or the delta is
 * non-positive (clock skew). Otherwise the delta is clamped to
 * [maxGapMillis] so a no-frame stretch credits at most one frame.
 */
fun frameDeltaMillis(
    prevNanos: Long,
    nowNanos: Long,
    maxGapMillis: Long = MAX_FRAME_GAP_MILLIS,
): Long {
    if (prevNanos == 0L) return 0L
    val deltaMs = (nowNanos - prevNanos) / 1_000_000L
    if (deltaMs <= 0L) return 0L
    return if (deltaMs > maxGapMillis) maxGapMillis else deltaMs
}

/** Result of folding newly-drained active-play millis into a reporter's
 *  pending bucket: the whole seconds to report now, and the sub-second
 *  remainder to carry forward so nothing is lost across flushes. */
data class PlayTimeFlush(val secondsToSend: Long, val remainderMillis: Long)

/**
 * Splits [pendingMillis] into whole seconds to report and the leftover
 * milliseconds to carry to the next flush. Keeping the remainder means a
 * series of sub-second/odd flushes still sums to the right total instead
 * of truncating a little every time.
 */
fun splitForFlush(pendingMillis: Long): PlayTimeFlush {
    if (pendingMillis <= 0L) return PlayTimeFlush(0L, pendingMillis.coerceAtLeast(0L))
    val seconds = pendingMillis / 1000L
    return PlayTimeFlush(seconds, pendingMillis - seconds * 1000L)
}
