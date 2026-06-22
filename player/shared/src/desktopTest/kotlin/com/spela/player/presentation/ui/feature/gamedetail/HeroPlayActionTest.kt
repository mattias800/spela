package com.spela.player.presentation.ui.feature.gamedetail

import com.spela.player.domain.model.INSTANT_DOWNLOAD_THRESHOLD_BYTES
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the game-detail hero button rule (#1412): the directly-playable
 * Play/Resume button is shown only for cached games or small (on-demand)
 * uncached ones; a large uncached game always shows Download — even when it
 * has play sessions/saves. This prevents a session-driven play action from
 * launching an un-downloaded game (which used to crash with "Game not
 * downloaded" and could trigger an unwanted multi-hundred-MB download).
 */
class HeroPlayActionTest {

    private val small = 1_000_000L // 1 MB, well under the on-demand limit
    private val large = INSTANT_DOWNLOAD_THRESHOLD_BYTES + 1 // just over the limit

    @Test
    fun cachedGameShowsPlay_regardlessOfSize() {
        assertTrue(
            showHeroPlayButton(isGameCached = true, fileSizeBytes = large, isDownloading = false, isInstantDownload = false),
            "a downloaded game is always directly playable",
        )
    }

    @Test
    fun smallUncachedGameShowsPlay() {
        assertTrue(
            showHeroPlayButton(isGameCached = false, fileSizeBytes = small, isDownloading = false, isInstantDownload = false),
            "a small uncached game plays via silent on-demand download",
        )
    }

    @Test
    fun largeUncachedGameShowsDownload_evenThoughItCouldHaveSessions() {
        // The rule is intentionally session-agnostic: this function only sees
        // size + cache/download state, never sessions/saves. So a large
        // uncached game can never present a play action — it shows Download.
        assertFalse(
            showHeroPlayButton(isGameCached = false, fileSizeBytes = large, isDownloading = false, isInstantDownload = false),
            "a large uncached game must show Download, regardless of play sessions",
        )
    }

    @Test
    fun unknownSizeUncachedGameShowsDownload() {
        assertFalse(
            showHeroPlayButton(isGameCached = false, fileSizeBytes = 0, isDownloading = false, isInstantDownload = false),
            "unknown size falls back to the explicit Download button rather than guessing 'small'",
        )
    }

    @Test
    fun exactlyAtThresholdShowsDownload() {
        // The on-demand window is `1 until THRESHOLD`, so the threshold itself
        // is "large".
        assertFalse(
            showHeroPlayButton(
                isGameCached = false,
                fileSizeBytes = INSTANT_DOWNLOAD_THRESHOLD_BYTES,
                isDownloading = false,
                isInstantDownload = false,
            ),
            "a game exactly at the threshold is treated as large",
        )
    }

    @Test
    fun smallUncachedDuringDownload_staysPlayOnlyWhileInSilentWindow() {
        // While the silent instant-download window is active, keep the
        // cached-style button up (#932) …
        assertTrue(
            showHeroPlayButton(isGameCached = false, fileSizeBytes = small, isDownloading = true, isInstantDownload = true),
            "small game keeps the Play button during the silent window",
        )
        // … once the window closes mid-download, fall back to Download +
        // progress so the user knows something is happening.
        assertFalse(
            showHeroPlayButton(isGameCached = false, fileSizeBytes = small, isDownloading = true, isInstantDownload = false),
            "after the silent window the small game drops back to the Download/progress button",
        )
    }
}
