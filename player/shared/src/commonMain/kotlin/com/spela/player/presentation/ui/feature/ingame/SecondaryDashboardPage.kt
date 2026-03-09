package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.EmulationActionButton
import com.spela.player.presentation.ui.components.fpsColor
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Dashboard page for the secondary screen companion.
 *
 * Shows stat cards (FPS, save slot, cheats) and a quick action row
 * (save, load, screenshot, fast forward, rewind).
 */
@Composable
fun SecondaryDashboardPage(
    fps: Float,
    frameTime: Float,
    activeSlot: Int,
    hasCheats: Boolean,
    enabledCheatCount: Int,
    isFastForward: Boolean,
    rewindEnabled: Boolean,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleFastForward: () -> Unit,
    onRewind: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Stat cards row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            StatCard(
                value = "%.0f".format(fps),
                valueColor = fpsColor(fps),
                label = "%.1fms".format(frameTime),
                contentDesc = "%.0f FPS, %.1f ms frame time".format(fps, frameTime),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = "Slot $activeSlot",
                valueColor = SpColor.OnBackground,
                label = "Save slot",
                contentDesc = "Active save slot $activeSlot",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = if (hasCheats) "$enabledCheatCount active" else "No cheats",
                valueColor = if (hasCheats && enabledCheatCount > 0) SpColor.Warning else SpColor.OnBackgroundTertiary,
                label = "Cheats",
                contentDesc = if (hasCheats) "$enabledCheatCount cheats active" else "No cheats available",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(SpSpacing.Medium))

        // Quick action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmulationActionButton(
                icon = Icons.Filled.Save,
                label = "Save",
                onClick = onSave,
                buttonSize = 48.dp,
                iconSize = 24.dp,
                showLabel = false,
                useFocusRing = false,
            )
            EmulationActionButton(
                icon = Icons.Filled.FolderOpen,
                label = "Load",
                onClick = onLoad,
                buttonSize = 48.dp,
                iconSize = 24.dp,
                showLabel = false,
                useFocusRing = false,
            )
            EmulationActionButton(
                icon = Icons.Filled.CameraAlt,
                label = "Screenshot",
                onClick = onScreenshot,
                buttonSize = 48.dp,
                iconSize = 24.dp,
                showLabel = false,
                useFocusRing = false,
            )
            EmulationActionButton(
                icon = if (isFastForward) Icons.Filled.PlayArrow else Icons.Filled.FastForward,
                label = if (isFastForward) "Normal" else "Fast",
                isActive = isFastForward,
                onClick = onToggleFastForward,
                buttonSize = 48.dp,
                iconSize = 24.dp,
                showLabel = false,
                useFocusRing = false,
            )
            if (rewindEnabled) {
                EmulationActionButton(
                    icon = Icons.Filled.FastRewind,
                    label = "Rewind",
                    onClick = onRewind,
                    buttonSize = 48.dp,
                    iconSize = 24.dp,
                    showLabel = false,
                    useFocusRing = false,
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    valueColor: Color,
    label: String,
    contentDesc: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
            .background(SpColor.Card)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics { contentDescription = contentDesc },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = SpTypography.HeadlineSmall,
            color = valueColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
