package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun CoreMismatchSaveDialog(
    originalCoreName: String,
    currentCoreName: String,
    onSaveAnyway: () -> Unit,
    onSkipSaveState: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(SpSpacing.RadiusXLarge))
                .background(SpColor.SurfaceElevated)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(SpSpacing.XLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Save State Compatibility",
                style = SpTypography.HeadlineMedium,
                color = SpColor.Warning,
            )
            Spacer(Modifier.height(SpSpacing.Small))

            val bodyText = buildAnnotatedString {
                val bold = SpanStyle(fontWeight = FontWeight.Bold, color = SpColor.OnBackground)
                append("This session's save state was created with ")
                withStyle(bold) { append(originalCoreName) }
                append(". Saving now will replace it with a save state from ")
                withStyle(bold) { append(currentCoreName) }
                append(", which won't work on devices using ")
                withStyle(bold) { append(originalCoreName) }
                append(".")
            }
            Text(
                text = bodyText,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = "Your in-game save (game progress) has been saved and works on all cores.",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SpSpacing.XLarge))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                SpButton(
                    text = "Save State Anyway",
                    onClick = onSaveAnyway,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpSecondaryButton(
                    text = "Skip Save State",
                    onClick = onSkipSaveState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
