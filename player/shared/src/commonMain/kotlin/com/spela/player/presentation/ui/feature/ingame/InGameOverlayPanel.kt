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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.spela.player.presentation.ui.components.SpSlider
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
import com.spela.player.presentation.ui.components.SpSecondaryButton
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
    showButtonRemap: Boolean = false,
    onConfigureButtons: () -> Unit = {},
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
                        SpSecondaryButton(
                            text = "Leave Session",
                            onClick = {
                                viewModel.onIntent(EmulationIntent.ShowNetplayLeaveConfirm)
                            },
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
                            saveStatesOptedOut = state.saveStatesOptedOut,
                            rewindEnabled = state.rewindEnabled,
                            hasCheats = state.hasCheats,
                            isSaveInProgress = state.isSaveInProgress,
                            saveStateError = state.saveStateError,
                            saveStateJustSucceeded = state.saveStateJustSucceeded,
                            hasPendingUploads = state.hasPendingUploads,
                            onSave = { viewModel.onIntent(EmulationIntent.SaveState) },
                            onLoad = { viewModel.onIntent(EmulationIntent.LoadState) },
                            onScreenshot = { viewModel.onIntent(EmulationIntent.TakeScreenshot) },
                            onToggleFastForward = { viewModel.onIntent(EmulationIntent.ToggleFastForward) },
                            onRewind = { viewModel.onIntent(EmulationIntent.RewindStep) },
                            onChallenge = { viewModel.onIntent(EmulationIntent.CreateChallenge) },
                            onCheats = { viewModel.onIntent(EmulationIntent.ShowCheatBrowser) },
                            onControls = {
                            viewModel.onIntent(
                                if (useGamepadConfig) EmulationIntent.ShowGamepadConfig else EmulationIntent.ShowKeyMapping
                            )
                        },
                            showButtonRemap = showButtonRemap,
                            onConfigureButtons = onConfigureButtons,
                        )
                    }

                    // Stuck-uploads banner (#804 phase 6 slice 4).
                    // Surfaces only when at least one queued save has
                    // failed STUCK_RETRY_THRESHOLD times so the user
                    // can tell stuck-on-error apart from
                    // in-flight-and-slow without digging into logs.
                    if (state.stuckUploadCount > 0) {
                        Spacer(Modifier.height(SpSpacing.Small))
                        val msg = if (state.stuckUploadCount == 1) {
                            "Sync paused — 1 save waiting"
                        } else {
                            "Sync paused — ${state.stuckUploadCount} saves waiting"
                        }
                        Text(
                            text = msg,
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = msg },
                        )
                    }

                    Spacer(Modifier.height(if (isLandscape) SpSpacing.Medium else SpSpacing.Large))

                    // Volume slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                    ) {
                        Icon(
                            imageVector = if (state.volume < 0.01f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = SpColor.OnBackgroundSecondary,
                        )
                        SpSlider(
                            value = state.volume,
                            onValueChange = { viewModel.onIntent(EmulationIntent.SetVolume(it)) },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(state.volume * 100).toInt()}%",
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundSecondary,
                        )
                    }

                    Spacer(Modifier.height(if (isLandscape) SpSpacing.Medium else SpSpacing.Large))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        SpSecondaryButton(
                            text = "Exit Game",
                            onClick = {
                                viewModel.onIntent(EmulationIntent.ShowExitConfirm)
                            },
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

/**
 * Pure resolver for the in-game overlay's save-state action gate.
 * Extracted so it can be unit-tested without rendering the full
 * Compose tree — the desktop module's component tests have an
 * independent compile failure (unrelated to #804) we don't want to
 * couple to. See [OverlayActionButtons]. #804 phase 4.
 */
internal fun shouldShowSaveStateActions(
    supportsSaveStates: Boolean,
    saveStatesOptedOut: Boolean,
): Boolean = supportsSaveStates && !saveStatesOptedOut

@Composable
fun RowScope.OverlayActionButtons(
    isFastForward: Boolean,
    supportsSaveStates: Boolean,
    saveStatesOptedOut: Boolean = false,
    rewindEnabled: Boolean = false,
    hasCheats: Boolean = false,
    isSaveInProgress: Boolean = false,
    saveStateError: String? = null,
    saveStateJustSucceeded: Boolean = false,
    hasPendingUploads: Boolean = false,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleFastForward: () -> Unit,
    onRewind: () -> Unit = {},
    onChallenge: () -> Unit,
    onCheats: () -> Unit = {},
    onControls: () -> Unit,
    showButtonRemap: Boolean = false,
    onConfigureButtons: () -> Unit = {},
) {
    // Save / Load / Challenge are all gated on the user's per-console
    // opt-out. The toggle is read-only at the overlay level on purpose
    // — flipping mid-session creates ambiguity about in-flight uploads
    // (#804 phase 4 spec point d). Discovery comes from the Settings
    // page (separate slice).
    val saveStatesAvailable = shouldShowSaveStateActions(
        supportsSaveStates = supportsSaveStates,
        saveStatesOptedOut = saveStatesOptedOut,
    )
    if (saveStatesAvailable) {
        // State-aware Save action: idle / in-progress / just-succeeded /
        // failed (#803). Success briefly flashes a checkmark before
        // returning to idle, so the user catches the confirmation even
        // if they dismiss the overlay before the toast renders.
        val saveLabel: String
        val saveIcon: ImageVector
        when {
            isSaveInProgress -> {
                saveLabel = "Saving…"
                saveIcon = Icons.Filled.Sync
            }
            hasPendingUploads -> {
                // The bytes are on disk + queued; the upload runs in
                // the background. Phase 6 of #804 — the user isn't
                // blocked on the network round-trip. The label flips
                // to "Synced" via saveStateJustSucceeded once the
                // queue empties.
                saveLabel = "Saved locally · syncing"
                saveIcon = Icons.Filled.Sync
            }
            saveStateJustSucceeded -> {
                // "Synced" rather than just "Saved" — communicates that
                // the bytes reached the server, not just local. SaveManager
                // only flips this flag after the upload drain settles,
                // so the label is accurate. (#803)
                saveLabel = "Synced"
                saveIcon = Icons.Filled.CloudDone
            }
            saveStateError != null -> {
                saveLabel = "Save failed"
                saveIcon = Icons.Filled.ErrorOutline
            }
            else -> {
                saveLabel = "Save"
                saveIcon = Icons.Filled.Save
            }
        }
        OverlayAction(label = saveLabel, icon = saveIcon, onClick = onSave)
        OverlayAction(label = "Load", icon = Icons.Filled.FolderOpen, onClick = onLoad)
    }
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
    if (saveStatesAvailable) {
        OverlayAction(label = "Challenge", icon = Icons.Filled.Flag, onClick = onChallenge)
    }
    if (hasCheats) {
        OverlayAction(label = "Cheats", icon = Icons.Filled.Code, onClick = onCheats)
    }
    OverlayAction(label = "Controls", icon = Icons.Filled.SportsEsports, onClick = onControls)
    // Positional button remap for the current console — same editor as Settings
    // (#1340). "Remap" (vs the adjacent "Controls" = controller list) to keep the
    // two gamepad actions distinct.
    if (showButtonRemap) {
        OverlayAction(label = "Remap", icon = Icons.Filled.Tune, onClick = onConfigureButtons)
    }
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

