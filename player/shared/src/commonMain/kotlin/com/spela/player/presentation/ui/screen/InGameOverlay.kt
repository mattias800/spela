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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.spela.player.domain.model.DefaultKeyMappings
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCountdownOverlay
import com.spela.player.presentation.ui.components.SpNetplayHud
import com.spela.player.presentation.ui.components.keymapping.KeyMappingDialog
import com.spela.player.presentation.ui.components.keymapping.platformKeyName
import com.spela.player.presentation.ui.components.pingColor
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import org.koin.compose.koinInject

@Composable
fun InGameOverlay(
    viewModel: EmulationViewModel,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val continueFocusRequester = remember { FocusRequester() }

    AnimatedVisibility(
        visible = state.showOverlay,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
    ) {
        LaunchedEffect(Unit) {
            try { continueFocusRequester.requestFocus() } catch (_: Exception) {}
        }
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
                                    viewModel.onIntent(EmulationIntent.ResumeGame)
                                },
                                modifier = Modifier.weight(1f).focusRequester(continueFocusRequester),
                            )
                        }
                    } else {
                        // Normal mode: all action buttons
                        if (isLandscape) {
                            // Landscape: horizontal row with resume/exit alongside actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OverlayAction(
                                    label = "Save",
                                    icon = Icons.Filled.Save,
                                    onClick = { viewModel.onIntent(EmulationIntent.SaveState) },
                                )
                                OverlayAction(
                                    label = "Load",
                                    icon = Icons.Filled.FolderOpen,
                                    onClick = { viewModel.onIntent(EmulationIntent.LoadState) },
                                )
                                OverlayAction(
                                    label = "Screenshot",
                                    icon = Icons.Filled.CameraAlt,
                                    onClick = { viewModel.onIntent(EmulationIntent.TakeScreenshot) },
                                )
                                OverlayAction(
                                    label = if (state.isFastForward) "Normal" else "Fast",
                                    icon = if (state.isFastForward) Icons.Filled.PlayArrow else Icons.Filled.FastForward,
                                    onClick = { viewModel.onIntent(EmulationIntent.ToggleFastForward) },
                                    isActive = state.isFastForward,
                                )
                                OverlayAction(
                                    label = "Controls",
                                    icon = Icons.Filled.SportsEsports,
                                    onClick = { viewModel.onIntent(EmulationIntent.ShowKeyMapping) },
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
                                        viewModel.onIntent(EmulationIntent.ShowExitConfirm)
                                    },
                                    style = SpButtonStyle.Outlined,
                                    modifier = Modifier.weight(1f),
                                )
                                SpButton(
                                    text = "Continue",
                                    onClick = {
                                        viewModel.onIntent(EmulationIntent.ToggleOverlay)
                                        viewModel.onIntent(EmulationIntent.ResumeGame)
                                    },
                                    modifier = Modifier.weight(1f).focusRequester(continueFocusRequester),
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
                                    icon = Icons.Filled.Save,
                                    onClick = { viewModel.onIntent(EmulationIntent.SaveState) },
                                )
                                OverlayAction(
                                    label = "Load",
                                    icon = Icons.Filled.FolderOpen,
                                    onClick = { viewModel.onIntent(EmulationIntent.LoadState) },
                                )
                                OverlayAction(
                                    label = "Screenshot",
                                    icon = Icons.Filled.CameraAlt,
                                    onClick = { viewModel.onIntent(EmulationIntent.TakeScreenshot) },
                                )
                                OverlayAction(
                                    label = if (state.isFastForward) "Normal" else "Fast",
                                    icon = if (state.isFastForward) Icons.Filled.PlayArrow else Icons.Filled.FastForward,
                                    onClick = { viewModel.onIntent(EmulationIntent.ToggleFastForward) },
                                    isActive = state.isFastForward,
                                )
                                OverlayAction(
                                    label = "Controls",
                                    icon = Icons.Filled.SportsEsports,
                                    onClick = { viewModel.onIntent(EmulationIntent.ShowKeyMapping) },
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
                                        viewModel.onIntent(EmulationIntent.ShowExitConfirm)
                                    },
                                    style = SpButtonStyle.Outlined,
                                    modifier = Modifier.weight(1f),
                                )
                                SpButton(
                                    text = "Continue",
                                    onClick = {
                                        viewModel.onIntent(EmulationIntent.ToggleOverlay)
                                        viewModel.onIntent(EmulationIntent.ResumeGame)
                                    },
                                    modifier = Modifier.weight(1f).focusRequester(continueFocusRequester),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Performance HUD (always visible when game is running, but small)
    // Hidden when secondary display is active (HUD moves there)
    if (state.isRunning && !state.showOverlay && !state.secondaryDisplayActive) {
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpColor.Scrim)
                    .clickable { viewModel.onIntent(EmulationIntent.ToggleOverlay) }
                    .focusable()
                    .semantics {
                        contentDescription = "%.0f FPS, tap to open game menu".format(state.fps)
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
                    text = "%.0f FPS".format(state.fps),
                    style = SpTypography.LabelSmall,
                    color = when {
                        state.fps >= 55f -> SpColor.Success
                        state.fps >= 30f -> SpColor.Warning
                        else -> SpColor.Error
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "FPS counter %.0f".format(state.fps)
                    },
                )
            }

            // Netplay HUD - top left
            if (state.isNetplayMode && state.netplayPeerUsername != null) {
                SpNetplayHud(
                    peerUsername = state.netplayPeerUsername!!,
                    latencyMs = state.netplayPeerLatencyMs,
                    visible = true,
                    onClick = { viewModel.onIntent(EmulationIntent.ToggleOverlay) },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
    }

    // Netplay pause attribution overlay
    if (state.isNetplayMode && state.netplayPausedByUsername != null && !state.showOverlay) {
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
                    text = "Paused by ${state.netplayPausedByUsername!!}",
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground,
                    textAlign = TextAlign.Center,
                )
                // Show elapsed pause time after 60 seconds
                if (state.netplayPauseElapsedSeconds >= 60) {
                    Spacer(Modifier.height(SpSpacing.Small))
                    Text(
                        text = "Paused for ${state.netplayPauseElapsedSeconds / 60}m ${state.netplayPauseElapsedSeconds % 60}s",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
                Spacer(Modifier.height(SpSpacing.XLarge))
                SpButton(
                    text = "Resume",
                    onClick = { viewModel.onIntent(EmulationIntent.ResumeGame) },
                )
                // After 5 minutes of pause, show "Leave Session" option (AC-8)
                if (state.netplayPauseElapsedSeconds >= 300) {
                    Spacer(Modifier.height(SpSpacing.Small))
                    SpButton(
                        text = "Leave Session",
                        onClick = { viewModel.onIntent(EmulationIntent.ShowNetplayLeaveConfirm) },
                        style = SpButtonStyle.Ghost,
                    )
                }
            }
        }
    }

    // Netplay disconnect countdown overlay
    if (state.isNetplayMode && state.netplayPeerDisconnected) {
        SpCountdownOverlay(
            title = "${state.netplayPeerUsername ?: "Player"} disconnected",
            subtitle = "Waiting for reconnection...",
            totalSeconds = 60,
            visible = true,
            onTimeout = { onExit() },
            actions = {
                SpButton(
                    text = "Leave Session",
                    onClick = {
                        viewModel.onIntent(EmulationIntent.ConfirmNetplayLeave)
                        onExit()
                    },
                    style = SpButtonStyle.Outlined,
                )
            },
        )
    }

    // Netplay session expiration warning (AC-12: 15-minute max session)
    if (state.isNetplayMode && state.netplaySessionExpired) {
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
                    onClick = {
                        viewModel.onIntent(EmulationIntent.ConfirmNetplayLeave)
                        onExit()
                    },
                )
            }
        }
    }

    // Netplay leave confirmation dialog
    if (state.netplayShowLeaveConfirm) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.onIntent(EmulationIntent.DismissNetplayLeaveConfirm) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .clip(RoundedCornerShape(24.dp))
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
                    text = "Leave Netplay Session?",
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground,
                )
                Spacer(Modifier.height(SpSpacing.Small))
                Text(
                    text = "The other player will be disconnected.",
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
                        text = "Keep Playing",
                        onClick = { viewModel.onIntent(EmulationIntent.DismissNetplayLeaveConfirm) },
                        style = SpButtonStyle.Outlined,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SpButton(
                        text = "Leave Session",
                        onClick = {
                            viewModel.onIntent(EmulationIntent.ConfirmNetplayLeave)
                            onExit()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    // Exit confirmation dialog (non-netplay)
    if (state.showExitConfirm && !state.isNetplayMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.onIntent(EmulationIntent.DismissExitConfirm) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .clip(RoundedCornerShape(24.dp))
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
                    text = "Exit without saving?",
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground,
                )
                Spacer(Modifier.height(SpSpacing.Small))
                Text(
                    text = "This game doesn't support save states. Any unsaved progress will be lost.",
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
                        text = "Keep Playing",
                        onClick = { viewModel.onIntent(EmulationIntent.DismissExitConfirm) },
                        style = SpButtonStyle.Outlined,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SpButton(
                        text = "Exit Anyway",
                        onClick = {
                            viewModel.onIntent(EmulationIntent.ConfirmExit)
                            onExit()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    // Status message toast (auto-dismisses after 2 seconds)
    state.statusMessage?.let { message ->
        LaunchedEffect(message) {
            delay(2000)
            viewModel.onIntent(EmulationIntent.DismissStatus)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = SpSpacing.XXXLarge),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
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

    // Key mapping dialog
    if (state.showKeyMapping) {
        val keyMappingViewModel: KeyMappingViewModel = koinInject()
        val keyMappingState by keyMappingViewModel.state.collectAsState()
        val consoleId = state.consoleId
        val layout = remember(consoleId) { DefaultKeyMappings.getLayoutForConsole(consoleId) }

        LaunchedEffect(consoleId) {
            keyMappingViewModel.onIntent(KeyMappingIntent.LoadMapping(consoleId))
        }

        KeyMappingDialog(
            layout = layout,
            state = keyMappingState,
            onButtonClick = { retroButtonId ->
                keyMappingViewModel.onIntent(KeyMappingIntent.StartSingleButtonMap(retroButtonId))
            },
            onStartWizard = {
                keyMappingViewModel.onIntent(KeyMappingIntent.StartWizard(consoleId))
            },
            onResetToDefaults = {
                keyMappingViewModel.onIntent(KeyMappingIntent.ResetAll)
            },
            onDismiss = {
                keyMappingViewModel.onIntent(KeyMappingIntent.FinishMapping)
                viewModel.onIntent(EmulationIntent.HideKeyMapping)
            },
            keyNameResolver = ::platformKeyName,
        )
    }
}

private fun formatSessionDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
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
    icon: ImageVector,
    onClick: () -> Unit,
    isActive: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .spFocusRing(shape = RoundedCornerShape(16.dp))
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) SpColor.Primary else SpColor.OnBackground,
                modifier = Modifier.size(28.dp),
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
