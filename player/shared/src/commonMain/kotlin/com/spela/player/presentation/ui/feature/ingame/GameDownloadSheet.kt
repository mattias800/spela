package com.spela.player.presentation.ui.feature.ingame

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
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.presentation.ui.components.SpDownloadProgressBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Foreground sheet rendered while
 * [com.spela.player.presentation.state.EmulationState.gameDownload] is
 * non-null — i.e. a launch entry point (Start fresh / Continue from title /
 * session / shared-session / netplay / challenge) reached a game whose ROM
 * isn't on disk yet, so PrepareGameUseCase is fetching it on demand before
 * starting emulation (#1412).
 *
 * Non-dismissable: the emulator can't start without the ROM, and the back
 * press already cancels the play action upstream. Mirrors [CoreDownloadSheet]
 * (#1192) so launch-time core and ROM downloads look identical.
 */
@Composable
fun GameDownloadSheet(progress: DownloadProgress) {
    Dialog(
        onDismissRequest = { /* non-dismissable while the download is in flight */ },
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
                .testTag("game_download_sheet"),
        ) {
            val title = progress.gameTitle.ifBlank { "game" }
            Text(
                text = "Downloading $title",
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
            )
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = "Fetching the game so it's ready to play — this only happens the first time.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )
            Spacer(Modifier.height(SpSpacing.XLarge))

            SpDownloadProgressBar(
                progress = progress.progress,
                bytesDownloaded = progress.bytesDownloaded,
                totalBytes = progress.totalBytes,
                bytesPerSecond = progress.bytesPerSecond,
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
