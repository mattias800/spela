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
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Dialog shown when auto-load detects that the last save point was created
 * with a different emulator core than the one currently in use.
 * Presents three options to the user.
 */
@Composable
internal fun CoreMismatchDialog(
    saveCoreName: String,
    currentCoreName: String,
    onTryAnyway: () -> Unit,
    onGameSaveOnly: () -> Unit,
    onStartFresh: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}, // Block click-through
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
                    onClick = {}, // Prevent click-through
                )
                .padding(SpSpacing.XLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Save State Compatibility Warning",
                style = SpTypography.HeadlineMedium,
                color = SpColor.Warning,
            )
            Spacer(Modifier.height(SpSpacing.Small))

            val bodyText = buildAnnotatedString {
                val boldStyle = SpanStyle(fontWeight = FontWeight.Bold, color = SpColor.OnBackground)
                append("Your last save state was created with a different emulator configuration (")
                withStyle(boldStyle) { append(saveCoreName) }
                append(" vs current ")
                withStyle(boldStyle) { append(currentCoreName) }
                append("). It may not load correctly on this platform.")
            }
            Text(
                text = bodyText,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SpSpacing.XLarge))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                SpButton(
                    text = "Try Loading Anyway",
                    onClick = onTryAnyway,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpSecondaryButton(
                    text = "Start with Game Save Only",
                    onClick = onGameSaveOnly,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpButton(
                    text = "Start Fresh",
                    onClick = onStartFresh,
                    style = SpButtonStyle.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
