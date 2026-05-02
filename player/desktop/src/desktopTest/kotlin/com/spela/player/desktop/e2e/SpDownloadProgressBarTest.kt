package com.spela.player.desktop.e2e

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.presentation.ui.components.SpDownloadProgressBar
import kotlin.test.Test

/**
 * Regression coverage for #894 / #797: a transient `progress = -1f`
 * emission mid-download (e.g. an IDLE re-emit, or a multi-disc
 * transition where totalBytes momentarily resets to -1) used to
 * unmount the determinate bar — the inner SpProgressBar lost its
 * remembered maxProgress and the bar visibly dropped to 0 every
 * frame. The fix latches the determinate path on first non-negative
 * progress, so subsequent -1 emissions don't collapse the bar height
 * back to the indeterminate stripe.
 */
@OptIn(ExperimentalTestApi::class)
class SpDownloadProgressBarTest {

    @Test
    fun determinateLatchSurvivesTransientNegativeProgress() = runComposeUiTest {
        var progress by mutableStateOf(-1f)
        setContent {
            SpDownloadProgressBar(
                progress = progress,
                bytesDownloaded = 0L,
                totalBytes = -1L,
            )
        }

        // Initial: indeterminate path is rendered.
        onNodeWithTag("sp_download_progress_bar_indeterminate").assertExists()

        // First real progress emission flips us to determinate.
        progress = 0.25f
        waitForIdle()
        onNodeWithTag("sp_download_progress_bar_determinate").assertExists()

        // Transient -1 (e.g. IDLE re-emit, multi-disc transition).
        // Determinate path must stay mounted; without the latch this
        // is exactly when the bar would visually drop to 0.
        progress = -1f
        waitForIdle()
        onNodeWithTag("sp_download_progress_bar_determinate").assertExists()

        // Subsequent real progress also stays determinate.
        progress = 0.7f
        waitForIdle()
        onNodeWithTag("sp_download_progress_bar_determinate").assertExists()
    }
}
