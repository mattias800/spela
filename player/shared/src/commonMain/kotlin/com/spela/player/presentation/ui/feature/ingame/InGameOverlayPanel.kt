package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.ui.screen.formatSessionDuration
import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.ui.components.EmulationActionButton
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.challenge.formatDuration
import com.spela.player.presentation.ui.components.fpsColor
import com.spela.player.presentation.ui.components.pingColor
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.EmulationViewModel

@Composable
internal fun InGameOverlayPanel(
    state: EmulationState,
    viewModel: EmulationViewModel,
    continueFocusRequester: FocusRequester,
    useGamepadConfig: Boolean = false,
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
                    .clip(RoundedCornerShape(SpSpacing.RadiusXLarge))
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

                // Performance stats / Netplay session info
                if (state.isNetplayMode) {
                    // Netplay: show ping and session duration
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Default),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics {
                            contentDescription = "Ping: ${state.netplayPeerLatencyMs} milliseconds, session: ${formatSessionDuration(state.sessionElapsedSeconds)}"
                        },
                    ) {
                        PerformanceBadge(
                            label = "Ping",
                            value = "${state.netplayPeerLatencyMs}ms",
                            color = pingColor(state.netplayPeerLatencyMs),
                        )
                        PerformanceBadge(
                            label = "Session",
                            value = formatSessionDuration(state.sessionElapsedSeconds),
                            color = SpColor.OnBackgroundSecondary,
                        )
                    }
                } else if (state.isRunning) {
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
                            color = fpsColor(state.fps),
                        )
                        PerformanceBadge(
                            label = "Frame",
                            value = "%.1fms".format(state.frameTime),
                            color = SpColor.OnBackgroundSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(if (isLandscape) SpSpacing.Medium else SpSpacing.XLarge))

                // Action buttons - different for netplay vs normal mode
                if (state.isNetplayMode) {
                    // Netplay mode: only Controls action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        OverlayAction(
                            label = "Controls",
                            icon = Icons.Filled.SportsEsports,
                            onClick = { viewModel.onIntent(EmulationIntent.ShowKeyMapping) },
                        )
                    }

                    Spacer(Modifier.height(if (isLandscape) SpSpacing.Medium else SpSpacing.XLarge))

                    // Leave Session / Resume buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        SpButton(
                            text = "Leave Session",
                            onClick = {
                                viewModel.onIntent(EmulationIntent.ShowNetplayLeaveConfirm)
                            },
                            style = SpButtonStyle.Outlined,
                            modifier = Modifier.weight(1f),
                        )
                        SpButton(
                            text = "Resume",
                            onClick = {
                                viewModel.onIntent(EmulationIntent.ToggleOverlay)
                            },
                            modifier = Modifier.weight(1f).focusRequester(continueFocusRequester),
                        )
                    }
                } else if (state.isChallengeMode) {
                    // Challenge mode: restricted actions
                    // Timer display
                    Text(
                        text = formatDuration(state.challengeElapsedMs),
                        style = SpTypography.DisplaySmall,
                        color = SpColor.Primary,
                        modifier = Modifier.semantics {
                            contentDescription = "Challenge timer: ${formatDuration(state.challengeElapsedMs)}"
                        },
                    )
                    if (state.challengeObjective.isNotBlank()) {
                        Spacer(Modifier.height(SpSpacing.XSmall))
                        Text(
                            text = state.challengeObjective,
                            style = SpTypography.LabelMedium,
                            color = SpColor.OnBackgroundSecondary,
                        )
                    }

                    Spacer(Modifier.height(if (isLandscape) SpSpacing.Medium else SpSpacing.XLarge))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        OverlayAction(
                            label = "Mark Complete",
                            icon = Icons.Filled.CheckCircle,
                            onClick = { viewModel.onIntent(EmulationIntent.CompleteChallenge) },
                        )
                        OverlayAction(
                            label = "Restart",
                            icon = Icons.Filled.Replay,
                            onClick = { viewModel.onIntent(EmulationIntent.RestartChallenge) },
                        )
                        OverlayAction(
                            label = "Give Up",
                            icon = Icons.Filled.Stop,
                            onClick = { viewModel.onIntent(EmulationIntent.ShowGiveUpConfirm) },
                        )
                        OverlayAction(
                            label = "Controls",
                            icon = Icons.Filled.SportsEsports,
                            onClick = { viewModel.onIntent(EmulationIntent.ShowKeyMapping) },
                        )
                    }

                    Spacer(Modifier.height(if (isLandscape) SpSpacing.Medium else SpSpacing.XLarge))

                    SpButton(
                        text = "Resume",
                        onClick = {
                            viewModel.onIntent(EmulationIntent.ToggleOverlay)
                            viewModel.onIntent(EmulationIntent.ResumeGame)
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(continueFocusRequester),
                    )
                } else {
                    // Normal mode: all action buttons
                    // Slot indicator
                    if (state.supportsSaveStates) {
                        Text(
                            text = "Slot ${state.activeSlot}",
                            style = SpTypography.LabelMedium,
                            color = SpColor.OnBackgroundSecondary,
                            modifier = Modifier.semantics {
                                contentDescription = "Active quick-save slot: ${state.activeSlot}"
                            },
                        )
                        Spacer(Modifier.height(SpSpacing.Small))
                    }

                    // Action buttons (shared between landscape/portrait)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = if (isLandscape) Alignment.CenterVertically else Alignment.Top,
                    ) {
                        OverlayActionButtons(
                            isFastForward = state.isFastForward,
                            supportsSaveStates = state.supportsSaveStates,
                            rewindEnabled = state.rewindEnabled,
                            onSave = { viewModel.onIntent(EmulationIntent.SaveState) },
                            onLoad = { viewModel.onIntent(EmulationIntent.LoadState) },
                            onScreenshot = { viewModel.onIntent(EmulationIntent.TakeScreenshot) },
                            onToggleFastForward = { viewModel.onIntent(EmulationIntent.ToggleFastForward) },
                            onRewind = { viewModel.onIntent(EmulationIntent.RewindStep) },
                            onChallenge = { viewModel.onIntent(EmulationIntent.CreateChallenge) },
                            onControls = {
                            viewModel.onIntent(
                                if (useGamepadConfig) EmulationIntent.ShowGamepadConfig else EmulationIntent.ShowKeyMapping
                            )
                        },
                        )
                    }

                    Spacer(Modifier.height(if (isLandscape) SpSpacing.Medium else SpSpacing.XLarge))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        SpButton(
                            text = "Exit Game",
                            onClick = {
                                viewModel.onIntent(EmulationIntent.ShowExitConfirm)
                            },
                            style = SpButtonStyle.Outlined,
                            modifier = Modifier.weight(1f),
                        )
                        SpButton(
                            text = "Continue",
                            onClick = {
                                viewModel.onIntent(EmulationIntent.ToggleOverlay)
                            },
                            modifier = Modifier.weight(1f).focusRequester(continueFocusRequester),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PerformanceBadge(
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
internal fun RowScope.OverlayActionButtons(
    isFastForward: Boolean,
    supportsSaveStates: Boolean,
    rewindEnabled: Boolean = false,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleFastForward: () -> Unit,
    onRewind: () -> Unit = {},
    onChallenge: () -> Unit,
    onControls: () -> Unit,
) {
    OverlayAction(label = "Save", icon = Icons.Filled.Save, onClick = onSave)
    OverlayAction(label = "Load", icon = Icons.Filled.FolderOpen, onClick = onLoad)
    if (rewindEnabled) {
        OverlayAction(label = "Rewind", icon = Icons.Filled.FastRewind, onClick = onRewind)
    }
    OverlayAction(label = "Screenshot", icon = Icons.Filled.CameraAlt, onClick = onScreenshot)
    OverlayAction(
        label = if (isFastForward) "Normal" else "Fast",
        icon = if (isFastForward) Icons.Filled.PlayArrow else Icons.Filled.FastForward,
        onClick = onToggleFastForward,
        isActive = isFastForward,
    )
    if (supportsSaveStates) {
        OverlayAction(label = "Challenge", icon = Icons.Filled.Flag, onClick = onChallenge)
    }
    OverlayAction(label = "Controls", icon = Icons.Filled.SportsEsports, onClick = onControls)
}

@Composable
internal fun OverlayAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isActive: Boolean = false,
) {
    EmulationActionButton(
        icon = icon,
        label = label,
        onClick = onClick,
        isActive = isActive,
    )
}
