package com.spela.player.presentation.ui.feature.gamedetail

import com.spela.player.domain.model.INSTANT_DOWNLOAD_THRESHOLD_BYTES

/**
 * Decides whether the game-detail hero shows the directly-playable
 * Play / Resume / Start-fresh split-button (`true`) or the Download
 * button (`false`).
 *
 * The rule (#1412): a game is playable straight from the hero only when it is
 * already on disk, OR it is small enough to download silently on demand
 * (`fileSize` under [INSTANT_DOWNLOAD_THRESHOLD_BYTES], the on-demand limit).
 * A large, un-downloaded game ALWAYS shows Download — **regardless of whether
 * it has play sessions / saves** — so a session-driven play action can never
 * kick off a multi-hundred-MB download (or, before the central download gate,
 * crash) at launch.
 *
 * [fileSizeBytes] of 0 (unknown size) is treated as "not an instant
 * candidate" so we fall back to the explicit Download button rather than
 * guessing the game is small.
 *
 * During an in-flight instant download the cached-style button stays up only
 * while the silent window is active ([isInstantDownload]); once that window
 * closes we drop back to the Download button + progress bar so the user knows
 * something is happening (#932).
 */
fun showHeroPlayButton(
    isGameCached: Boolean,
    fileSizeBytes: Long,
    isDownloading: Boolean,
    isInstantDownload: Boolean,
): Boolean {
    val isInstantDownloadCandidate =
        !isGameCached && fileSizeBytes in 1L..<INSTANT_DOWNLOAD_THRESHOLD_BYTES
    return isGameCached ||
        (isInstantDownloadCandidate && (!isDownloading || isInstantDownload))
}
