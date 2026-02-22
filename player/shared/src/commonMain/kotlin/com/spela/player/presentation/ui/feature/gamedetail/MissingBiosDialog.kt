package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun MissingBiosDialog(
    consoleName: String,
    missingFiles: List<BiosMissingFile>,
    onGoBack: () -> Unit,
    onTryAnyway: () -> Unit,
) {
    Dialog(
        onDismissRequest = onGoBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge),
        ) {
            Text(
                text = "Missing BIOS Files",
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
            )

            Spacer(Modifier.height(SpSpacing.Default))

            Text(
                text = "$consoleName requires firmware files to run games. The following files are missing:",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )

            Spacer(Modifier.height(SpSpacing.Default))

            missingFiles.forEach { file ->
                Text(
                    text = "\u2022 ${file.fileName} -- ${file.description}",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundSecondary,
                )
                Spacer(Modifier.height(SpSpacing.XSmall))
            }

            Spacer(Modifier.height(SpSpacing.Default))

            Text(
                text = "BIOS files are system firmware that must be obtained separately. Ask your server admin to upload them.",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )

            Spacer(Modifier.height(SpSpacing.XLarge))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                SpButton(
                    text = "Go Back",
                    onClick = onGoBack,
                    style = SpButtonStyle.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpButton(
                    text = "Try Anyway",
                    onClick = onTryAnyway,
                    style = SpButtonStyle.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
