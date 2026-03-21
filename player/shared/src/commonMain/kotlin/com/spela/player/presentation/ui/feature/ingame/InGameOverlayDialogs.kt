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
import androidx.compose.ui.text.style.TextAlign
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.challenge.formatDuration
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun OverlayConfirmDialog(
    title: String,
    message: String,
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
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
                text = title,
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
            )
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = message,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SpSpacing.XLarge))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                SpSecondaryButton(
                    text = cancelText,
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun OverlayToast(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = SpSpacing.XXXLarge),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
                .background(SpColor.SuccessContainer)
                .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small),
        ) {
            Text(
                text = message,
                style = SpTypography.BodyMedium,
                color = SpColor.Success,
            )
        }
    }
}

@Composable
internal fun NetplayPauseOverlay(
    pausedByUsername: String,
    pauseElapsedSeconds: Long,
    onResume: () -> Unit,
    onLeaveSession: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Paused by $pausedByUsername",
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
                textAlign = TextAlign.Center,
            )
            // Show elapsed pause time after 60 seconds
            if (pauseElapsedSeconds >= 60) {
                Spacer(Modifier.height(SpSpacing.Small))
                Text(
                    text = "Paused for ${pauseElapsedSeconds / 60}m ${pauseElapsedSeconds % 60}s",
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
            Spacer(Modifier.height(SpSpacing.XLarge))
            SpButton(
                text = "Resume",
                onClick = onResume,
            )
            // After 5 minutes of pause, show "Leave Session" option (AC-8)
            if (pauseElapsedSeconds >= 300) {
                Spacer(Modifier.height(SpSpacing.Small))
                SpButton(
                    text = "Leave Session",
                    onClick = onLeaveSession,
                    style = SpButtonStyle.Ghost,
                )
            }
        }
    }
}

@Composable
internal fun NetplaySessionExpiredOverlay(
    onLeaveSession: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(SpSpacing.XLarge),
        ) {
            Text(
                text = "Session Expired",
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = "The maximum session time of 15 minutes has been reached.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SpSpacing.XLarge))
            SpButton(
                text = "Leave Session",
                onClick = onLeaveSession,
            )
        }
    }
}

@Composable
internal fun ChallengeCompletedDialog(
    durationMs: Long,
    isBest: Boolean,
    onDone: () -> Unit,
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
                .fillMaxWidth(0.80f)
                .clip(RoundedCornerShape(SpSpacing.RadiusXLarge))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Challenge Complete!",
                style = SpTypography.HeadlineMedium,
                color = SpColor.Success,
            )
            Spacer(Modifier.height(SpSpacing.Large))
            Text(
                text = formatDuration(durationMs),
                style = SpTypography.DisplaySmall,
                color = SpColor.Primary,
            )
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = "Your time",
                style = SpTypography.LabelMedium,
                color = SpColor.OnBackgroundSecondary,
            )
            if (isBest) {
                Spacer(Modifier.height(SpSpacing.Small))
                Text(
                    text = "New personal best!",
                    style = SpTypography.TitleMedium,
                    color = SpColor.Warning,
                )
            }
            Spacer(Modifier.height(SpSpacing.XLarge))
            SpButton(
                text = "Done",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
