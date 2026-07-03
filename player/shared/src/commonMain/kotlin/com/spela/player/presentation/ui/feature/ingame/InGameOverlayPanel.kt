package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.DisplayAspectChoice
import com.spela.player.domain.model.supportsWidescreenMode
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.ui.components.LocalScrollState
import com.spela.player.presentation.ui.components.SpDrawerButton
import com.spela.player.presentation.ui.components.SpDrawerIconButton
import com.spela.player.presentation.ui.components.SpSlider
import com.spela.player.presentation.ui.components.challenge.formatDuration
import com.spela.player.presentation.ui.components.fpsColor
import com.spela.player.presentation.ui.components.pingColor
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.LocalScrollFocusRegistry
import com.spela.player.presentation.ui.gamepad.ScrollFocusRegistry
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.screen.formatSessionDuration
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.EmulationViewModel
import kotlin.math.roundToInt

@Composable
internal fun InGameOverlayPanel(
    state: EmulationState,
    viewModel: EmulationViewModel,
    drawerInitialFocusRequester: FocusRequester,
    useGamepadConfig: Boolean = false,
    showButtonRemap: Boolean = false,
    onConfigureButtons: () -> Unit = {},
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
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
        val focusMemory = remember { mutableStateOf("") }
        val focusManager = LocalFocusManager.current

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(drawerWidth)
                .clip(drawerShape)
                .background(SpColor.DrawerSurface.copy(alpha = 0.97f))
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
                            focusManager.moveFocus(
                                if (event.key == Key.DirectionLeft) {
                                    FocusDirection.Left
                                } else {
                                    FocusDirection.Right
                                },
                            )
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
                        if (isLandscape) SpSpacing.Small else SpSpacing.Medium,
                    ),
                ) {
                    OverlayDrawerHeader(state = state)
                    OverlayShortcutRow(
                        volume = state.volume,
                        displayAspectChoice = state.displayAspectChoice,
                        displayAspectLabel = state.displayAspectLabel,
                        displayAspectStateDescription = state.displayAspectStateDescription,
                        renderScaleLabel = state.renderScaleLabel,
                        renderScaleStateDescription = state.renderScaleStateDescription,
                        consoleId = state.consoleId,
                        onVolumeChange = { viewModel.onIntent(EmulationIntent.SetVolume(it)) },
                        onDisplayAspectChoiceChange = {
                            viewModel.onIntent(EmulationIntent.SetDisplayAspectChoice(it))
                        },
                    )

                    when {
                        state.isNetplayMode -> NetplayOverlayActions(
                            viewModel = viewModel,
                            drawerInitialFocusRequester = drawerInitialFocusRequester,
                        )
                        state.isChallengeMode -> ChallengeOverlayActions(
                            state = state,
                            viewModel = viewModel,
                            drawerInitialFocusRequester = drawerInitialFocusRequester,
                        )
                        else -> NormalOverlayActions(
                            state = state,
                            viewModel = viewModel,
                            drawerInitialFocusRequester = drawerInitialFocusRequester,
                            useGamepadConfig = useGamepadConfig,
                            showButtonRemap = showButtonRemap,
                            onConfigureButtons = onConfigureButtons,
                        )
                    }

                    OverlayPerformanceFooter(state = state)
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
            color = SpColor.OnDrawer,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun NetplayOverlayActions(
    viewModel: EmulationViewModel,
    drawerInitialFocusRequester: FocusRequester,
) {
    OverlayAction(
        label = "Controls",
        icon = Icons.Filled.SportsEsports,
        isDefaultFocus = true,
        focusRequester = drawerInitialFocusRequester,
        onClick = { viewModel.onIntent(EmulationIntent.ShowKeyMapping) },
    )
    OverlayAction(
        label = "Leave Session",
        icon = Icons.Filled.Stop,
        onClick = { viewModel.onIntent(EmulationIntent.ShowNetplayLeaveConfirm) },
    )
    SpDrawerButton(
        text = "Resume",
        onClick = { viewModel.onIntent(EmulationIntent.ToggleOverlay) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRestoreItem(key = "overlay_resume"),
    )
}

@Composable
private fun ChallengeOverlayActions(
    state: EmulationState,
    viewModel: EmulationViewModel,
    drawerInitialFocusRequester: FocusRequester,
) {
    Text(
        text = formatDuration(state.challengeElapsedMs),
        style = SpTypography.DisplaySmall,
        color = SpColor.PrimaryDark,
        modifier = Modifier.semantics {
            contentDescription = "Challenge timer: ${formatDuration(state.challengeElapsedMs)}"
        },
    )
    if (state.challengeObjective.isNotBlank()) {
        Text(
            text = state.challengeObjective,
            style = SpTypography.LabelMedium,
            color = SpColor.OnDrawerSecondary,
        )
    }

    OverlayAction(
        label = "Mark Complete",
        icon = Icons.Filled.CheckCircle,
        isDefaultFocus = true,
        focusRequester = drawerInitialFocusRequester,
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

    SpDrawerButton(
        text = "Resume",
        onClick = {
            viewModel.onIntent(EmulationIntent.ToggleOverlay)
            viewModel.onIntent(EmulationIntent.ResumeGame)
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRestoreItem(key = "overlay_resume"),
    )
}

@Composable
private fun NormalOverlayActions(
    state: EmulationState,
    viewModel: EmulationViewModel,
    drawerInitialFocusRequester: FocusRequester,
    useGamepadConfig: Boolean,
    showButtonRemap: Boolean,
    onConfigureButtons: () -> Unit,
) {
    if (state.supportsSaveStates) {
        Text(
            text = "Slot ${state.activeSlot}",
            style = SpTypography.LabelMedium,
            color = SpColor.OnDrawerSecondary,
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
        drawerInitialFocusRequester = drawerInitialFocusRequester,
        showButtonRemap = showButtonRemap,
        onConfigureButtons = onConfigureButtons,
    )

    // Wii sessions: controller scheme picker (#1559).
    if (state.isWiiControlSchemeSelectable) {
        OverlayAction(
            label = "Wii Remote",
            icon = Icons.Filled.SettingsRemote,
            onClick = { viewModel.onIntent(EmulationIntent.ShowWiiControlSchemePicker) },
        )
    }

    if (state.stuckUploadCount > 0) {
        val msg = if (state.stuckUploadCount == 1) {
            "Sync paused — 1 save waiting"
        } else {
            "Sync paused — ${state.stuckUploadCount} saves waiting"
        }
        Text(
            text = msg,
            style = SpTypography.LabelSmall,
            color = SpColor.OnDrawerSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = msg },
        )
    }

    SpDrawerButton(
        text = "Continue",
        icon = Icons.Filled.PlayArrow,
        onClick = { viewModel.onIntent(EmulationIntent.ToggleOverlay) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRestoreItem(key = "overlay_continue"),
    )
    OverlayAction(
        label = "Exit Game",
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        onClick = { viewModel.onIntent(EmulationIntent.ShowExitConfirm) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayShortcutRow(
    volume: Float,
    displayAspectChoice: DisplayAspectChoice,
    displayAspectLabel: String,
    displayAspectStateDescription: String,
    renderScaleLabel: String,
    renderScaleStateDescription: String,
    consoleId: String,
    onVolumeChange: (Float) -> Unit,
    onDisplayAspectChoiceChange: (DisplayAspectChoice) -> Unit,
) {
    var showVolumePopover by remember { mutableStateOf(false) }
    var showDisplayAspectPopover by remember { mutableStateOf(false) }
    var showRenderScalePopover by remember { mutableStateOf(false) }
    val volumePopoverFocusRequester = remember { FocusRequester() }
    val displayAspectPopoverFocusRequester = remember { FocusRequester() }
    val renderScalePopoverFocusRequester = remember { FocusRequester() }
    val volumePercentText = formatVolumePercent(volume)
    val showWidescreenMode = supportsWidescreenMode(consoleId)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
    ) {
        OverlayShortcutItem(label = volumePercentText) {
            Box {
                SpDrawerIconButton(
                    icon = if (volume < 0.01f) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = "Volume",
                    tooltip = "Volume",
                    stateDescription = volumePercentText,
                    selected = showVolumePopover,
                    onClick = { showVolumePopover = true },
                    modifier = Modifier.focusRestoreItem(key = "overlay_shortcut_volume"),
                )
                DropdownMenu(
                    expanded = showVolumePopover,
                    onDismissRequest = { showVolumePopover = false },
                    modifier = Modifier.background(SpColor.DrawerSurface),
                ) {
                    OverlayVolumePopoverContent(
                        volume = volume,
                        onVolumeChange = onVolumeChange,
                        onDismiss = { showVolumePopover = false },
                        focusRequester = volumePopoverFocusRequester,
                    )
                }
            }
        }
        if (showWidescreenMode) {
            OverlayShortcutItem(label = displayAspectLabel) {
                Box {
                    SpDrawerIconButton(
                        icon = Icons.Filled.AspectRatio,
                        contentDescription = "Aspect ratio",
                        tooltip = "Aspect ratio",
                        stateDescription = displayAspectStateDescription,
                        selected = showDisplayAspectPopover,
                        onClick = { showDisplayAspectPopover = true },
                        modifier = Modifier.focusRestoreItem(key = "overlay_shortcut_display_aspect"),
                    )
                    DropdownMenu(
                        expanded = showDisplayAspectPopover,
                        onDismissRequest = { showDisplayAspectPopover = false },
                        modifier = Modifier.background(SpColor.DrawerSurface),
                    ) {
                        OverlayDisplayAspectPopoverContent(
                            displayAspectChoice = displayAspectChoice,
                            displayAspectStateDescription = displayAspectStateDescription,
                            onDisplayAspectChoiceChange = onDisplayAspectChoiceChange,
                            onDismiss = { showDisplayAspectPopover = false },
                            focusRequester = displayAspectPopoverFocusRequester,
                        )
                    }
                }
            }
        }
        OverlayShortcutItem(label = renderScaleLabel) {
            Box {
                SpDrawerIconButton(
                    icon = Icons.Filled.HighQuality,
                    contentDescription = "Resolution",
                    tooltip = "Resolution",
                    stateDescription = renderScaleStateDescription,
                    selected = showRenderScalePopover,
                    onClick = { showRenderScalePopover = true },
                    modifier = Modifier.focusRestoreItem(key = "overlay_shortcut_render_scale"),
                )
                DropdownMenu(
                    expanded = showRenderScalePopover,
                    onDismissRequest = { showRenderScalePopover = false },
                    modifier = Modifier.background(SpColor.DrawerSurface),
                ) {
                    OverlayRenderScalePopoverContent(
                        renderScaleLabel = renderScaleLabel,
                        renderScaleStateDescription = renderScaleStateDescription,
                        onDismiss = { showRenderScalePopover = false },
                        focusRequester = renderScalePopoverFocusRequester,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayShortcutItem(
    label: String,
    control: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
        modifier = Modifier.widthIn(max = 144.dp),
    ) {
        control()
        Text(
            text = label,
            style = SpTypography.LabelMedium,
            color = SpColor.OnDrawerSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp),
        )
    }
}

@Composable
private fun OverlayDisplayAspectPopoverContent(
    displayAspectChoice: DisplayAspectChoice,
    displayAspectStateDescription: String,
    onDisplayAspectChoiceChange: (DisplayAspectChoice) -> Unit,
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
) {
    val focusedChoice = displayAspectChoice

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .width(SpSpacing.DrawerPopoverWidth)
            .background(SpColor.DrawerSurface)
            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "Aspect ratio"
                stateDescription = displayAspectStateDescription
            },
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
    ) {
        DisplayAspectChoice.selectableChoices.forEach { choice ->
            SpDrawerButton(
                text = choice.optionLabel,
                icon = if (choice == displayAspectChoice) Icons.Filled.CheckCircle else null,
                selected = choice == displayAspectChoice,
                contentDescription = choice.optionLabel,
                stateDescription = if (choice == displayAspectChoice) {
                    "Selected. ${choice.description}"
                } else {
                    choice.description
                },
                onClick = {
                    onDisplayAspectChoiceChange(choice)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (choice == focusedChoice) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
    }
}

@Composable
private fun OverlayRenderScalePopoverContent(
    renderScaleLabel: String,
    renderScaleStateDescription: String,
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
) {
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .width(SpSpacing.DrawerPopoverWidth)
            .background(SpColor.DrawerSurface)
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .semantics {
                contentDescription = "Resolution"
                stateDescription = renderScaleStateDescription
            },
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
    ) {
        Text(
            text = "Resolution",
            style = SpTypography.LabelMedium,
            color = SpColor.OnDrawerSecondary,
        )
        Text(
            text = renderScaleLabel,
            style = SpTypography.LabelLarge,
            color = SpColor.OnDrawer,
        )
        Text(
            text = "Changes apply when a game starts",
            style = SpTypography.LabelMedium,
            color = SpColor.OnDrawerTertiary,
        )
    }
}

@Composable
private fun OverlayVolumePopoverContent(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
) {
    val volumePercentText = formatVolumePercent(volume)

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Row(
        modifier = Modifier
            .width(SpSpacing.DrawerPopoverWidth)
            .background(SpColor.DrawerSurface)
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionDown -> {
                        onVolumeChange((volume - VolumeStep).coerceIn(0f, 1f))
                        true
                    }
                    Key.DirectionRight, Key.DirectionUp -> {
                        onVolumeChange((volume + VolumeStep).coerceIn(0f, 1f))
                        true
                    }
                    Key.Escape -> {
                        onDismiss()
                        true
                    }
                    else -> false
                }
            }
            .semantics {
                contentDescription = "Volume slider"
                stateDescription = volumePercentText
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        Icon(
            imageVector = if (volume < 0.01f) {
                Icons.AutoMirrored.Filled.VolumeOff
            } else {
                Icons.AutoMirrored.Filled.VolumeUp
            },
            contentDescription = null,
            tint = SpColor.OnDrawerSecondary,
            modifier = Modifier.size(SpSpacing.IconDefault),
        )
        SpSlider(
            value = volume,
            onValueChange = onVolumeChange,
            modifier = Modifier.weight(1f),
            activeColor = SpColor.PrimaryDark,
            inactiveColor = SpColor.OnDrawerTertiary.copy(alpha = 0.35f),
            thumbColor = SpColor.OnDrawer,
        )
        Text(
            text = volumePercentText,
            style = SpTypography.LabelSmall,
            color = SpColor.OnDrawerSecondary,
        )
    }
}

private const val VolumeStep = 0.05f

private fun formatVolumePercent(volume: Float): String =
    "${(volume.coerceIn(0f, 1f) * 100).roundToInt()}%"

@Composable
private fun OverlayPerformanceFooter(state: EmulationState) {
    when {
        state.isNetplayMode -> {
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
                    color = drawerStatusColor(pingColor(state.netplayPeerLatencyMs)),
                )
                PerformanceBadge(
                    label = "Session",
                    value = formatSessionDuration(state.sessionElapsedSeconds),
                    color = SpColor.OnDrawerSecondary,
                )
            }
        }
        state.isRunning -> {
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
                    color = drawerStatusColor(fpsColor(state.fps)),
                )
                PerformanceBadge(
                    label = "Frame",
                    value = "%.1fms".format(state.frameTime),
                    color = SpColor.OnDrawerSecondary,
                )
            }
        }
    }
}

private fun drawerStatusColor(color: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color =
    when (color) {
        SpColor.Success -> SpColor.DrawerSuccess
        SpColor.Warning -> SpColor.DrawerWarning
        SpColor.Error -> SpColor.DrawerError
        else -> SpColor.OnDrawer
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
            color = SpColor.OnDrawerSecondary,
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
    drawerInitialFocusRequester: FocusRequester? = null,
    showButtonRemap: Boolean = false,
    onConfigureButtons: () -> Unit = {},
) {
    val saveStatesAvailable = shouldShowSaveStateActions(
        supportsSaveStates = supportsSaveStates,
        saveStatesOptedOut = saveStatesOptedOut,
    )
    val defaultFocusKey = when {
        saveStatesAvailable -> "overlay_action_save"
        rewindEnabled -> "overlay_action_rewind"
        else -> "overlay_action_screenshot"
    }

    @Composable
    fun DrawerAction(
        label: String,
        icon: ImageVector,
        focusKey: String,
        onClick: () -> Unit,
        isActive: Boolean = false,
    ) {
        val isDefaultFocus = drawerInitialFocusRequester != null && focusKey == defaultFocusKey
        OverlayAction(
            label = label,
            icon = icon,
            focusKey = focusKey,
            onClick = onClick,
            isActive = isActive,
            isDefaultFocus = isDefaultFocus,
            focusRequester = if (isDefaultFocus) drawerInitialFocusRequester else null,
        )
    }

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
        DrawerAction(
            label = saveLabel,
            icon = saveIcon,
            focusKey = "overlay_action_save",
            onClick = onSave,
        )
        DrawerAction(
            label = "Load",
            icon = Icons.Filled.FolderOpen,
            focusKey = "overlay_action_load",
            onClick = onLoad,
        )
    }
    if (rewindEnabled) {
        DrawerAction(
            label = "Rewind",
            icon = Icons.Filled.FastRewind,
            focusKey = "overlay_action_rewind",
            onClick = onRewind,
        )
    }
    DrawerAction(
        label = "Screenshot",
        icon = Icons.Filled.CameraAlt,
        focusKey = "overlay_action_screenshot",
        onClick = onScreenshot,
    )
    DrawerAction(
        label = if (isFastForward) "Normal" else "Fast",
        icon = if (isFastForward) Icons.Filled.PlayArrow else Icons.Filled.FastForward,
        focusKey = "overlay_action_fast_forward",
        onClick = onToggleFastForward,
        isActive = isFastForward,
    )
    if (saveStatesAvailable) {
        DrawerAction(
            label = "Challenge",
            icon = Icons.Filled.Flag,
            focusKey = "overlay_action_challenge",
            onClick = onChallenge,
        )
    }
    if (hasCheats) {
        DrawerAction(
            label = "Cheats",
            icon = Icons.Filled.Code,
            focusKey = "overlay_action_cheats",
            onClick = onCheats,
        )
    }
    DrawerAction(
        label = "Controls",
        icon = Icons.Filled.SportsEsports,
        focusKey = "overlay_action_controls",
        onClick = onControls,
    )
    if (showButtonRemap) {
        DrawerAction(
            label = "Remap",
            icon = Icons.Filled.Tune,
            focusKey = "overlay_action_remap",
            onClick = onConfigureButtons,
        )
    }
}

@Composable
internal fun OverlayAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isActive: Boolean = false,
    focusKey: String = "overlay_action_$label",
    isDefaultFocus: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    SpDrawerButton(
        text = label,
        onClick = onClick,
        icon = icon,
        selected = isActive,
        shape = RoundedCornerShape(SpSpacing.RadiusMedium),
        modifier = Modifier
            .fillMaxWidth()
            .focusRestoreItem(
                key = focusKey,
                isDefault = isDefaultFocus,
                requester = focusRequester,
            ),
    )
}
