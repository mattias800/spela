package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.ui.components.LocalScrollState
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpSlider
import com.spela.player.presentation.ui.components.challenge.formatDuration
import com.spela.player.presentation.ui.components.fpsColor
import com.spela.player.presentation.ui.components.pingColor
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.LocalScrollFocusRegistry
import com.spela.player.presentation.ui.gamepad.ScrollFocusRegistry
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import com.spela.player.presentation.ui.screen.formatSessionDuration
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
            .background(SpColor.ScrimLight.copy(alpha = 0.18f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { viewModel.onIntent(EmulationIntent.ToggleOverlay) },
            )
            .semantics { contentDescription = "Game overlay, tap to dismiss" },
        contentAlignment = Alignment.CenterStart,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            val drawerWidth = if (isLandscape) 0.36f else 0.84f
            val drawerShape = RoundedCornerShape(
                topStart = 0.dp,
                topEnd = SpSpacing.RadiusXLarge,
                bottomEnd = SpSpacing.RadiusXLarge,
                bottomStart = 0.dp,
            )
            val scrollState = rememberScrollState()
            val scrollFocusRegistry = remember { ScrollFocusRegistry() }
            val focusMemory = rememberFocusMemoryState()
            val focusManager = LocalFocusManager.current

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(drawerWidth)
                    .clip(drawerShape)
                    .background(SpColor.SurfaceElevated.copy(alpha = 0.96f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // Prevent click-through to the scrim.
                    )
                    .focusGroup()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> {
                                if (scrollFocusRegistry.redirectIfFocusedOffscreen()) return@onPreviewKeyEvent true
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            Key.DirectionDown -> {
                                if (scrollFocusRegistry.redirectIfFocusedOffscreen()) return@onPreviewKeyEvent true
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            Key.DirectionLeft, Key.DirectionRight -> {
                                scrollFocusRegistry.redirectIfFocusedOffscreen()
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                CompositionLocalProvider(
                    LocalScrollState provides scrollState,
                    LocalScrollFocusRegistry provides scrollFocusRegistry,
                    LocalFocusMemory provides focusMemory,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned {
                                scrollFocusRegistry.viewportTopInRoot = it.positionInRoot().y
                                scrollFocusRegistry.viewportHeight = it.size.height.toFloat()
                            }
                            .verticalScroll(scrollState)
                            .padding(SpSpacing.XLarge),
                        verticalArrangement = Arrangement.spacedBy(
                            if (isLandscape) SpSpacing.Medium else SpSpacing.Large,
                        ),
                    ) {
                        OverlayDrawerHeader(state = state)

                        when {
                            state.isNetplayMode -> NetplayOverlayActions(
                                viewModel = viewModel,
                                continueFocusRequester = continueFocusRequester,
                            )
                            state.isChallengeMode -> ChallengeOverlayActions(
                                state = state,
                                viewModel = viewModel,
                                continueFocusRequester = continueFocusRequester,
                            )
                            else -> NormalOverlayActions(
                                state = state,
                                viewModel = viewModel,
                                continueFocusRequester = continueFocusRequester,
                                useGamepadConfig = useGamepadConfig,
                                showButtonRemap = showButtonRemap,
                                onConfigureButtons = onConfigureButtons,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayDrawerHeader(state: EmulationState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        Text(
            text = state.gameTitle,
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground,
            modifier = Modifier.semantics { heading() },
        )

        if (state.isNetplayMode) {
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
                        state.fps,
                        state.frameTime,
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
    }
}

@Composable
private fun NetplayOverlayActions(
    viewModel: EmulationViewModel,
    continueFocusRequester: FocusRequester,
) {
    OverlayAction(
        label = "Controls",
        icon = Icons.Filled.SportsEsports,
        onClick = { viewModel.onIntent(EmulationIntent.ShowKeyMapping) },
    )
    OverlayAction(
        label = "Leave Session",
        icon = Icons.Filled.Stop,
        onClick = { viewModel.onIntent(EmulationIntent.ShowNetplayLeaveConfirm) },
    )
    SpButton(
        text = "Resume",
        onClick = { viewModel.onIntent(EmulationIntent.ToggleOverlay) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRestoreItem(
                key = "overlay_resume",
                isDefault = true,
                requester = continueFocusRequester,
            ),
    )
}

@Composable
private fun ChallengeOverlayActions(
    state: EmulationState,
    viewModel: EmulationViewModel,
    continueFocusRequester: FocusRequester,
) {
    Text(
        text = formatDuration(state.challengeElapsedMs),
        style = SpTypography.DisplaySmall,
        color = SpColor.Primary,
        modifier = Modifier.semantics {
            contentDescription = "Challenge timer: ${formatDuration(state.challengeElapsedMs)}"
        },
    )
    if (state.challengeObjective.isNotBlank()) {
        Text(
            text = state.challengeObjective,
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackgroundSecondary,
        )
    }

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

    SpButton(
        text = "Resume",
        onClick = {
            viewModel.onIntent(EmulationIntent.ToggleOverlay)
            viewModel.onIntent(EmulationIntent.ResumeGame)
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRestoreItem(
                key = "overlay_resume",
                isDefault = true,
                requester = continueFocusRequester,
            ),
    )
}

@Composable
private fun NormalOverlayActions(
    state: EmulationState,
    viewModel: EmulationViewModel,
    continueFocusRequester: FocusRequester,
    useGamepadConfig: Boolean,
    showButtonRemap: Boolean,
    onConfigureButtons: () -> Unit,
) {
    if (state.supportsSaveStates) {
        Text(
            text = "Slot ${state.activeSlot}",
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackgroundSecondary,
            modifier = Modifier.semantics {
                contentDescription = "Active quick-save slot: ${state.activeSlot}"
            },
        )
    }

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
                if (useGamepadConfig) {
                    EmulationIntent.ShowGamepadConfig
                } else {
                    EmulationIntent.ShowKeyMapping
                },
            )
        },
        showButtonRemap = showButtonRemap,
        onConfigureButtons = onConfigureButtons,
    )

    if (state.stuckUploadCount > 0) {
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

    OverlayVolumeRow(
        volume = state.volume,
        onVolumeChange = { viewModel.onIntent(EmulationIntent.SetVolume(it)) },
    )

    OverlayAction(
        label = "Exit Game",
        icon = Icons.Filled.Stop,
        onClick = { viewModel.onIntent(EmulationIntent.ShowExitConfirm) },
    )
    SpButton(
        text = "Continue",
        onClick = { viewModel.onIntent(EmulationIntent.ToggleOverlay) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRestoreItem(
                key = "overlay_continue",
                isDefault = true,
                requester = continueFocusRequester,
            ),
    )
}

@Composable
private fun OverlayVolumeRow(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
            .background(SpColor.SurfaceBright.copy(alpha = 0.45f))
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        Icon(
            imageVector = if (volume < 0.01f) {
                Icons.AutoMirrored.Filled.VolumeOff
            } else {
                Icons.AutoMirrored.Filled.VolumeUp
            },
            contentDescription = "Volume",
            tint = SpColor.OnBackgroundSecondary,
            modifier = Modifier.size(SpSpacing.IconDefault),
        )
        SpSlider(
            value = volume,
            onValueChange = onVolumeChange,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(volume * 100).toInt()}%",
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundSecondary,
        )
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
fun OverlayActionButtons(
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
    val saveStatesAvailable = shouldShowSaveStateActions(
        supportsSaveStates = supportsSaveStates,
        saveStatesOptedOut = saveStatesOptedOut,
    )
    if (saveStatesAvailable) {
        val saveLabel: String
        val saveIcon: ImageVector
        when {
            isSaveInProgress -> {
                saveLabel = "Saving…"
                saveIcon = Icons.Filled.Sync
            }
            hasPendingUploads -> {
                saveLabel = "Saved locally · syncing"
                saveIcon = Icons.Filled.Sync
            }
            saveStateJustSucceeded -> {
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
    SpButton(
        text = label,
        onClick = onClick,
        style = if (isActive) SpButtonStyle.Secondary else SpButtonStyle.Ghost,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) SpColor.Primary else SpColor.OnBackgroundSecondary,
                modifier = Modifier.size(SpSpacing.IconDefault),
            )
        },
        shape = RoundedCornerShape(SpSpacing.RadiusMedium),
        modifier = Modifier
            .fillMaxWidth()
            .focusRestoreItem(key = "overlay_action_$label")
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            },
    )
}
