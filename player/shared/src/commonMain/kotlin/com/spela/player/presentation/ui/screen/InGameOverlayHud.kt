package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpNetplayHud
import com.spela.player.presentation.ui.components.challenge.formatDuration
import com.spela.player.presentation.ui.components.fpsColor
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun FpsHud(
    fps: Float,
    isNetplayMode: Boolean,
    netplayPeerUsername: String?,
    netplayPeerLatencyMs: Int,
    onToggleOverlay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(SpSpacing.Default),
    ) {
        // FPS pill with menu icon - top right
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
                .background(SpColor.Scrim)
                .clickable { onToggleOverlay() }
                .focusable()
                .semantics {
                    contentDescription = "%.0f FPS, tap to open game menu".format(fps)
                    role = Role.Button
                }
                .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XSmall),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = null,
                tint = SpColor.OnBackground,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "%.0f FPS".format(fps),
                style = SpTypography.LabelSmall,
                color = fpsColor(fps),
                modifier = Modifier.semantics {
                    contentDescription = "FPS counter %.0f".format(fps)
                },
            )
        }

        // Netplay HUD - top left
        if (isNetplayMode && netplayPeerUsername != null) {
            SpNetplayHud(
                peerUsername = netplayPeerUsername,
                latencyMs = netplayPeerLatencyMs,
                visible = true,
                onClick = { onToggleOverlay() },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

@Composable
internal fun ChallengeTimerHud(
    challengeElapsedMs: Long,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(SpSpacing.Default),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
                .background(SpColor.Scrim)
                .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XSmall),
        ) {
            Text(
                text = formatDuration(challengeElapsedMs),
                style = SpTypography.LabelSmall,
                color = SpColor.Primary,
                modifier = Modifier.semantics {
                    contentDescription = "Challenge timer ${formatDuration(challengeElapsedMs)}"
                },
            )
        }
    }
}
