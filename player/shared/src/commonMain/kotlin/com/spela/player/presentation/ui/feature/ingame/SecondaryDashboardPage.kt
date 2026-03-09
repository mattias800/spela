package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Cheat
import com.spela.player.presentation.ui.components.EmulationActionButton
import com.spela.player.presentation.ui.components.SpSwitch
import com.spela.player.presentation.ui.components.fpsColor
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private val ACTION_BUTTON_SIZE = 48.dp
private val ACTION_ICON_SIZE = 24.dp

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
    cheats: List<Cheat>,
    isFastForward: Boolean,
    rewindEnabled: Boolean,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleFastForward: () -> Unit,
    onRewind: () -> Unit,
    onToggleCheat: (cheatId: String, enabled: Boolean) -> Unit,
) {
    var showCheatsPanel by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = showCheatsPanel,
        transitionSpec = {
            (fadeIn() + slideInVertically { it / 4 })
                .togetherWith(fadeOut() + slideOutVertically { -it / 4 })
        },
        label = "dashboardCheatsTransition",
    ) { showingCheats ->
        if (showingCheats) {
            SecondaryCheatsPanel(
                cheats = cheats,
                onToggleCheat = onToggleCheat,
                onDismiss = { showCheatsPanel = false },
            )
        } else {
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
                        label = if (hasCheats) "Cheats \u2022 Tap" else "Cheats",
                        contentDesc = if (hasCheats) "$enabledCheatCount cheats active, tap to manage" else "No cheats available",
                        modifier = Modifier.weight(1f),
                        onClick = if (hasCheats) {
                            { showCheatsPanel = true }
                        } else {
                            null
                        },
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
                        buttonSize = ACTION_BUTTON_SIZE,
                        iconSize = ACTION_ICON_SIZE,
                        showLabel = false,
                        useFocusRing = false,
                    )
                    EmulationActionButton(
                        icon = Icons.Filled.FolderOpen,
                        label = "Load",
                        onClick = onLoad,
                        buttonSize = ACTION_BUTTON_SIZE,
                        iconSize = ACTION_ICON_SIZE,
                        showLabel = false,
                        useFocusRing = false,
                    )
                    EmulationActionButton(
                        icon = Icons.Filled.CameraAlt,
                        label = "Screenshot",
                        onClick = onScreenshot,
                        buttonSize = ACTION_BUTTON_SIZE,
                        iconSize = ACTION_ICON_SIZE,
                        showLabel = false,
                        useFocusRing = false,
                    )
                    EmulationActionButton(
                        icon = if (isFastForward) Icons.Filled.PlayArrow else Icons.Filled.FastForward,
                        label = if (isFastForward) "Normal" else "Fast",
                        isActive = isFastForward,
                        onClick = onToggleFastForward,
                        buttonSize = ACTION_BUTTON_SIZE,
                        iconSize = ACTION_ICON_SIZE,
                        showLabel = false,
                        useFocusRing = false,
                    )
                    if (rewindEnabled) {
                        EmulationActionButton(
                            icon = Icons.Filled.FastRewind,
                            label = "Rewind",
                            onClick = onRewind,
                            buttonSize = ACTION_BUTTON_SIZE,
                            iconSize = ACTION_ICON_SIZE,
                            showLabel = false,
                            useFocusRing = false,
                        )
                    }
                }
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
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
            .background(SpColor.Card)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
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

/**
 * Compact cheats toggle panel for the secondary dashboard.
 * Shows a scrollable list of cheats with toggle switches and a close button.
 */
@Composable
private fun SecondaryCheatsPanel(
    cheats: List<Cheat>,
    onToggleCheat: (cheatId: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small),
    ) {
        // Header row with title and close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SpSpacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Cheats",
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics {
                    contentDescription = "Close cheats panel"
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = SpColor.OnBackground,
                )
            }
        }

        if (cheats.isEmpty()) {
            Text(
                text = "No cheats available for this game.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                modifier = Modifier.padding(vertical = SpSpacing.Medium),
            )
        } else {
            // Scrollable cheat list
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                cheats.forEach { cheat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
                            .background(SpColor.Card)
                            .clickable { onToggleCheat(cheat.id, !cheat.enabled) }
                            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Medium)
                            .semantics {
                                contentDescription = "Cheat: ${cheat.description}, ${if (cheat.enabled) "enabled" else "disabled"}"
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = cheat.description,
                            style = SpTypography.BodyMedium,
                            color = SpColor.OnBackground,
                            modifier = Modifier.weight(1f).padding(end = SpSpacing.Small),
                            maxLines = 2,
                        )
                        SpSwitch(
                            checked = cheat.enabled,
                            onCheckedChange = { enabled ->
                                onToggleCheat(cheat.id, enabled)
                            },
                        )
                    }
                    Spacer(Modifier.height(SpSpacing.Small))
                }
            }
        }
    }
}
