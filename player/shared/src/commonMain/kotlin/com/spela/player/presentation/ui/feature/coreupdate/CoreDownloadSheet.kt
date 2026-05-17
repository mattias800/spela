package com.spela.player.presentation.ui.feature.coreupdate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.domain.model.CoreDownloadProgress
import com.spela.player.presentation.ui.components.SpDownloadProgressBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Foreground sheet rendered while [com.spela.player.presentation.state.EmulationState.coreDownload]
 * is non-null. Replaces the pre-#1192 opaque loading spinner with a
 * blocking modal that names which core is downloading, how big it is,
 * and how far along we are.
 *
 * Non-dismissable: the user can't bypass an in-progress core download
 * because the emulator can't start without the binary. The back press
 * already cancels the play action upstream — we intentionally don't add
 * a cancel button here in v1 (would require teardown of a partial file
 * and the existing play-cancel path doesn't propagate that yet).
 *
 * Reuses `SpDownloadProgressBar` so the indeterminate-and-determinate
 * transitions match the rest of the app (game downloads, save uploads).
 */
@Composable
fun CoreDownloadSheet(progress: CoreDownloadProgress) {
    Dialog(
        onDismissRequest = { /* non-dismissable while download is in flight */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge)
                .testTag("core_download_sheet"),
        ) {
            Text(
                text = "Updating ${progress.coreDisplayName}",
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
            )
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = "A newer build of the emulator core is available — fetching now so saves stay compatible.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )
            Spacer(Modifier.height(SpSpacing.XLarge))

            // SpDownloadProgressBar takes a Float fraction in [0, 1]
            // with a -1f sentinel for indeterminate. Match that contract
            // so the bar behaves identically to the rest of the app's
            // download surfaces.
            val fraction = progress.fraction ?: -1f
            val total = progress.totalBytes ?: 0L
            SpDownloadProgressBar(
                progress = fraction,
                bytesDownloaded = progress.bytesDownloaded,
                totalBytes = total,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(SpSpacing.Default))
            Text(
                text = "This might take a minute on slow connections.",
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
    }
}
