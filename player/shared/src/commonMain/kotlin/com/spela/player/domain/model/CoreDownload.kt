package com.spela.player.domain.model

/**
 * UI-facing snapshot of an in-progress libretro core download. Surfaced
 * via [com.spela.player.presentation.state.EmulationState.coreDownload]
 * while a play tap is waiting on a fresh-or-updated core binary. See
 * #1192.
 *
 * [totalBytes] is `null` until the HTTP response carries a Content-Length
 * header — buildbot always does, but we can't assume that for every
 * `CustomDownloadURL` so the sheet has to handle the indeterminate case
 * gracefully. When `null`, callers should render an indeterminate
 * progress bar; when known, [fraction] is derivable as
 * `bytesDownloaded / totalBytes`.
 */
data class CoreDownloadProgress(
    val coreName: String,
    val coreDisplayName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { bytesDownloaded.toFloat() / it }
}
