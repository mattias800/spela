package com.spela.player.libretro

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayTimeAccrualTest {

    private val ms = 1_000_000L // nanos per millisecond

    @Test
    fun noPreviousFrame_creditsZero() {
        // prevNanos == 0 is the baseline-reset sentinel (set on pause / first frame).
        assertEquals(0L, frameDeltaMillis(prevNanos = 0L, nowNanos = 5_000 * ms))
    }

    @Test
    fun normalFrameGap_creditsActualDelta() {
        // 16 ms between frames (~60 fps) is credited in full.
        assertEquals(16L, frameDeltaMillis(prevNanos = 1_000 * ms, nowNanos = 1_016 * ms))
    }

    @Test
    fun strugglingFrameRate_underCap_creditsActualDelta() {
        // 66 ms (~15 fps) is still below the cap and credited in full.
        assertEquals(66L, frameDeltaMillis(prevNanos = 0L + 100 * ms, nowNanos = 166 * ms))
    }

    @Test
    fun longGap_isClampedToCap() {
        // A 3-hour stretch with no frames (backgrounded / suspended) credits
        // at most one cap's worth, not 3 hours.
        val threeHoursNanos = 3L * 3600 * 1000 * ms
        assertEquals(
            MAX_FRAME_GAP_MILLIS,
            frameDeltaMillis(prevNanos = 1 * ms, nowNanos = 1 * ms + threeHoursNanos),
        )
    }

    @Test
    fun gapExactlyAtCap_isNotClamped() {
        assertEquals(
            MAX_FRAME_GAP_MILLIS,
            frameDeltaMillis(prevNanos = ms, nowNanos = ms + MAX_FRAME_GAP_MILLIS * ms),
        )
    }

    @Test
    fun nonPositiveDelta_creditsZero() {
        // Clock skew / equal timestamps must never credit (or go negative).
        assertEquals(0L, frameDeltaMillis(prevNanos = 5_000 * ms, nowNanos = 5_000 * ms))
        assertEquals(0L, frameDeltaMillis(prevNanos = 5_000 * ms, nowNanos = 4_000 * ms))
    }

    @Test
    fun subMillisDelta_creditsZero() {
        // Less than 1 ms rounds down to 0 (carried implicitly by the next frame's larger delta).
        assertEquals(0L, frameDeltaMillis(prevNanos = ms, nowNanos = ms + 500_000L))
    }

    @Test
    fun splitForFlush_wholeSeconds() {
        assertEquals(PlayTimeFlush(secondsToSend = 30L, remainderMillis = 0L), splitForFlush(30_000L))
    }

    @Test
    fun splitForFlush_carriesRemainder() {
        // 1500 ms -> send 1s, carry 500 ms.
        assertEquals(PlayTimeFlush(secondsToSend = 1L, remainderMillis = 500L), splitForFlush(1_500L))
    }

    @Test
    fun splitForFlush_belowOneSecond_sendsNothing_carriesAll() {
        assertEquals(PlayTimeFlush(secondsToSend = 0L, remainderMillis = 800L), splitForFlush(800L))
    }

    @Test
    fun splitForFlush_zeroOrNegative_sendsNothing() {
        assertEquals(PlayTimeFlush(0L, 0L), splitForFlush(0L))
        assertEquals(PlayTimeFlush(0L, 0L), splitForFlush(-50L))
    }

    @Test
    fun splitForFlush_remainderAccumulatesToWholeSecond() {
        // Two 1500 ms flushes: 1s+carry500, then (500+1500)=2000 -> 2s.
        val first = splitForFlush(1_500L)
        assertEquals(1L, first.secondsToSend)
        val second = splitForFlush(first.remainderMillis + 1_500L)
        assertEquals(2L, second.secondsToSend)
        assertEquals(0L, second.remainderMillis)
    }
}
