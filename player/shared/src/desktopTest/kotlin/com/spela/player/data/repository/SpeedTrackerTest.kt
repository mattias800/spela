package com.spela.player.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeedTrackerTest {

    private class FakeClock(initial: Long = 0L) {
        var t: Long = initial
        val now: () -> Long = { t }
        fun advance(ms: Long) { t += ms }
    }

    @Test fun firstSampleReturnsZero() {
        val clock = FakeClock()
        val s = SpeedTracker(now = clock.now)
        assertEquals(0L, s.record(0L), "single sample has no rate yet")
    }

    @Test fun computesAverageOverTwoSamples() {
        val clock = FakeClock()
        val s = SpeedTracker(now = clock.now)
        s.record(0L)
        clock.advance(1_000) // 1 s
        // 5 MB in 1 s => 5 MB/s
        val rate = s.record(5L * 1024 * 1024)
        assertEquals(5L * 1024 * 1024, rate)
    }

    @Test fun rollingWindowDropsStaleSamples() {
        val clock = FakeClock()
        val s = SpeedTracker(windowMs = 2_000, now = clock.now)
        s.record(0L)                                    // @ 0 ms, 0 B  (will be dropped)
        clock.advance(500); s.record(1_000_000L)        // @ 500 ms, 1 MB
        clock.advance(500); s.record(2_000_000L)        // @ 1000 ms, 2 MB
        clock.advance(500); s.record(3_000_000L)        // @ 1500 ms, 3 MB
        // Advance to 2_500 ms — the 0-byte sample from t=0 falls
        // outside the 2 s window (2500 - 0 = 2500 > 2000) and is
        // dropped. Remaining oldest in-window sample: t=500 ms, 1 MB.
        clock.advance(1_000); val rate = s.record(4_000_000L) // @ 2500 ms, 4 MB
        // Window now spans (500 ms, 1 MB) → (2500 ms, 4 MB) = 2 s, 3 MB.
        // Expected rate = 3 MB / 2 s = 1.5 MB/s.
        assertTrue(rate in 1_400_000L..1_600_000L, "stale samples should be dropped, in-window kept; got $rate")
    }

    @Test fun stalledDownloadReportsZero() {
        val clock = FakeClock()
        val s = SpeedTracker(windowMs = 2_000, now = clock.now)
        s.record(0L)
        clock.advance(100); s.record(10_000_000L) // 10 MB transferred
        // No more bytes — tick through the window and beyond.
        clock.advance(500); val r1 = s.record(10_000_000L)
        clock.advance(2_000); val r2 = s.record(10_000_000L)
        // Within window: still showing some rate from the burst.
        // Past window: only stalled samples remain → rate 0.
        assertTrue(r1 > 0L, "rate should be non-zero while burst is in window; got $r1")
        assertEquals(0L, r2, "rate should fall to 0 when the only in-window samples are stalled")
    }

    @Test fun nonMonotonicBytesReturnsZero() {
        // If the caller resets bytesDownloaded (e.g. starts a new game with
        // the same id), the delta would go negative — guard against that.
        val clock = FakeClock()
        val s = SpeedTracker(now = clock.now)
        s.record(5_000_000L)
        clock.advance(500); val r = s.record(1_000_000L) // counter went backward
        assertEquals(0L, r)
    }
}
