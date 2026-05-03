package com.spela.player.domain.model

/**
 * Size threshold under which a download is treated as instant — no
 * separate "Download" button, no progress indicator. Below this size
 * the download finishes faster than a progress bar can render
 * usefully on any reasonable home connection (~25 MB/s ≈ 0.6 s for a
 * 16 MB game), so flashing a "Downloading…" UI just adds visual noise
 * for what feels like one click.
 *
 * The button-as-Play behaviour kicks in here: tapping the (Play /
 * Resume / Continue) button on a sub-threshold game starts the
 * download silently and launches the emulator on completion. If the
 * download takes longer than [INSTANT_DOWNLOAD_FALLBACK_DELAY_MS]
 * — slow network, server cold start, threshold tuned too aggressively
 * — the regular progress indicator is surfaced so the user isn't left
 * with a silent spinner.
 *
 * See #932.
 */
const val INSTANT_DOWNLOAD_THRESHOLD_BYTES: Long = 16L * 1024 * 1024

/**
 * After this delay, a still-in-flight instant-download falls back to
 * the regular progress UI (download bar + status text). Sized so a
 * fast connection completes silently and a slow one transitions
 * before the user thinks the app has hung.
 */
const val INSTANT_DOWNLOAD_FALLBACK_DELAY_MS: Long = 750L
