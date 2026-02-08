package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.EmulationViewModel

@Composable
fun InGameOverlay(
    viewModel: EmulationViewModel,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    AnimatedVisibility(
        visible = state.showOverlay,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.onIntent(EmulationIntent.ToggleOverlay) },
                )
                .semantics { contentDescription = "Game overlay, tap to dismiss" },
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints {
                val isLandscape = maxWidth > maxHeight
                val panelWidth = if (isLandscape) 0.65f else 0.85f

                Column(
                    modifier = Modifier
                        .fillMaxWidth(panelWidth)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SpColor.SurfaceElevated)
                        .padding(SpSpacing.XLarge)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}, // Prevent click-through
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Game title
                    Text(
                        text = state.gameTitle,
                        style = SpTypography.HeadlineMedium,
                        color = SpColor.OnBackground,
                        modifier = Modifier.semantics { heading() },
                    )

                    Spacer(Modifier.height(SpSpacing.Small))

                    // Performance stats
                    if (state.isRunning) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Default),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.semantics {
                                contentDescription = "Performance: %.0f FPS, %.1f ms frame time".format(
                                    state.fps, state.frameTime
                                )
                            },
                        ) {
                            PerformanceBadge(
                                label = "FPS",
                                value = "%.0f".format(state.fps),
                                color = when {
                                    state.fps >= 55f -> SpColor.Success
                                    state.fps >= 30f -> SpColor.Warning
                                    else -> SpColor.Error
                                },
                            )
                            PerformanceBadge(
                                label = "Frame",
                                value = "%.1fms".format(state.frameTime),
                                color = SpColor.OnBackgroundSecondary,
                            )
                        }
                    }

                    Spacer(Modifier.height(if (isLandscape) SpSpacing.Medium else SpSpacing.XLarge))

                    // Action buttons - responsive layout
                    if (isLandscape) {
                        // Landscape: horizontal row with resume/exit alongside actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OverlayAction(
                                label = "Save",
                                icon = "\uD83D\uDCBE",
                                onClick = { viewModel.onIntent(EmulationIntent.SaveState) },
                            )
                            OverlayAction(
                                label = "Load",
                                icon = "\uD83D\uDCC2",
                                onClick = { viewModel.onIntent(EmulationIntent.LoadState) },
                            )
                            OverlayAction(
                                label = "Screenshot",
                                icon = "\uD83D\uDCF7",
                                onClick = { viewModel.onIntent(EmulationIntent.TakeScreenshot) },
                            )
                            OverlayAction(
                                label = if (state.isFastForward) "Normal" else "Fast",
                                icon = if (state.isFastForward) "\u25B6" else "\u23E9",
                                onClick = { viewModel.onIntent(EmulationIntent.ToggleFastForward) },
                                isActive = state.isFastForward,
                            )
                        }

                        Spacer(Modifier.height(SpSpacing.Medium))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                        ) {
                            SpButton(
                                text = "Exit Game",
                                onClick = {
                                    viewModel.onIntent(EmulationIntent.StopGame)
                                    onExit()
                                },
                                style = SpButtonStyle.Outlined,
                                modifier = Modifier.weight(1f),
                            )
                            SpButton(
                                text = "Resume",
                                onClick = {
                                    viewModel.onIntent(EmulationIntent.ToggleOverlay)
                                    viewModel.onIntent(EmulationIntent.ResumeGame)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        // Portrait: stacked layout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            OverlayAction(
                                label = "Save",
                                icon = "\uD83D\uDCBE",
                                onClick = { viewModel.onIntent(EmulationIntent.SaveState) },
                            )
                            OverlayAction(
                                label = "Load",
                                icon = "\uD83D\uDCC2",
                                onClick = { viewModel.onIntent(EmulationIntent.LoadState) },
                            )
                            OverlayAction(
                                label = "Screenshot",
                                icon = "\uD83D\uDCF7",
                                onClick = { viewModel.onIntent(EmulationIntent.TakeScreenshot) },
                            )
                            OverlayAction(
                                label = if (state.isFastForward) "Normal" else "Fast",
                                icon = if (state.isFastForward) "\u25B6" else "\u23E9",
                                onClick = { viewModel.onIntent(EmulationIntent.ToggleFastForward) },
                                isActive = state.isFastForward,
                            )
                        }

                        Spacer(Modifier.height(SpSpacing.XLarge))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                        ) {
                            SpButton(
                                text = "Exit Game",
                                onClick = {
                                    viewModel.onIntent(EmulationIntent.StopGame)
                                    onExit()
                                },
                                style = SpButtonStyle.Outlined,
                                modifier = Modifier.weight(1f),
                            )
                            SpButton(
                                text = "Resume",
                                onClick = {
                                    viewModel.onIntent(EmulationIntent.ToggleOverlay)
                                    viewModel.onIntent(EmulationIntent.ResumeGame)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    // Performance HUD (always visible when game is running, but small)
    if (state.isRunning && !state.showOverlay) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(SpSpacing.Default),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpColor.Scrim)
                    .clickable { viewModel.onIntent(EmulationIntent.ToggleOverlay) }
                    .focusable()
                    .semantics {
                        contentDescription = "%.0f FPS, tap to open game menu".format(state.fps)
                        role = Role.Button
                    }
                    .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XSmall),
            ) {
                Text(
                    text = "%.0f FPS".format(state.fps),
                    style = SpTypography.LabelSmall,
                    color = when {
                        state.fps >= 55f -> SpColor.Success
                        state.fps >= 30f -> SpColor.Warning
                        else -> SpColor.Error
                    },
                )
            }
        }
    }
}

@Composable
private fun PerformanceBadge(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = SpTypography.TitleLarge,
            color = color,
        )
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}

@Composable
private fun OverlayAction(
    label: String,
    icon: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .focusable()
            .semantics {
                contentDescription = label
                role = Role.Button
            },
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) SpColor.Primary.copy(alpha = 0.2f) else SpColor.SurfaceBright
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon,
                style = SpTypography.HeadlineMedium,
            )
        }
        Spacer(Modifier.height(SpSpacing.XSmall))
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            color = if (isActive) SpColor.Primary else SpColor.OnBackgroundSecondary,
        )
    }
}
