package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.DefaultKeyMappings
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.ui.feature.ingame.ChallengeCompletedDialog
import com.spela.player.presentation.ui.feature.ingame.CheatsDialog
import com.spela.player.presentation.ui.feature.ingame.CoreMismatchDialog
import com.spela.player.presentation.ui.feature.ingame.CoreMismatchSaveDialog
import com.spela.player.presentation.ui.feature.ingame.ChallengeTimerHud
import com.spela.player.presentation.ui.gamepad.ComposeFocusBridge
import com.spela.player.presentation.ui.feature.ingame.FpsHud
import com.spela.player.presentation.ui.feature.ingame.InGameOverlayPanel
import com.spela.player.presentation.ui.feature.ingame.NetplayPauseOverlay
import com.spela.player.presentation.ui.feature.ingame.NetplaySessionExpiredOverlay
import com.spela.player.presentation.ui.feature.ingame.OverlayConfirmDialog
import com.spela.player.presentation.ui.feature.ingame.OverlayToast
import com.spela.player.presentation.ui.components.AchievementPopup
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpCountdownOverlay
import com.spela.player.presentation.ui.components.challenge.ChallengeCreationPanel
import com.spela.player.presentation.ui.components.gamepad.GamepadConfigDialog
import com.spela.player.presentation.ui.components.keymapping.KeyMappingDialog
import com.spela.player.presentation.ui.components.keymapping.PresetPickerDialog
import com.spela.player.presentation.ui.components.keymapping.platformKeyName
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.GamepadConfigIntent
import com.spela.player.util.currentPlatform
import com.spela.player.presentation.viewmodel.GamepadConfigViewModel
import com.spela.player.presentation.viewmodel.GamepadMappingIntent
import com.spela.player.presentation.viewmodel.GamepadMappingViewModel
import com.spela.player.presentation.ui.components.gamepad.GamepadMappingDialog
import com.spela.player.presentation.viewmodel.KeyMappingViewModel

@Composable
fun InGameOverlay(
    viewModel: EmulationViewModel,
    keyMappingViewModel: KeyMappingViewModel,
    gamepadConfigViewModel: GamepadConfigViewModel? = null,
    gamepadMappingViewModel: GamepadMappingViewModel? = null,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val drawerInitialFocusRequester = remember { FocusRequester() }
    var drawerOpenGeneration by remember { mutableStateOf(0) }
    val hasGamepadConfig = gamepadConfigViewModel != null
    // Positional button-mapping editor (#1340): the same per-console hold-to-bind
    // dialog used in Settings, opened from the overlay menu. Dismissing it returns
    // to the overlay menu (it's a modal dialog rendered over the panel).
    var showButtonMapping by remember { mutableStateOf(false) }

    LaunchedEffect(state.showOverlay) {
        if (state.showOverlay) {
            drawerOpenGeneration += 1
        }
    }

    // #1087: top-anchored achievement banner on the primary in-game
    // surface. Reads `state.achievementEvent`, which the VM pumps from
    // the RetroAchievements controller during initAchievements; before
    // this mount, the popup primitive existed but was never wired up —
    // only the secondary-screen celebration fired, and the user is
    // usually looking at the primary surface during gameplay. Auto-
    // dismiss + slide animation belong to AchievementPopup itself.
    //
    // Rendered first so every later overlay (pause menu, dialogs,
    // sheets) renders on top of the banner — i.e. opening the pause
    // menu while a banner is on screen hides the banner until the
    // menu closes (or until the 4 s timer expires).
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        AchievementPopup(
            event = state.achievementEvent,
            onDismiss = { viewModel.onIntent(EmulationIntent.DismissAchievement) },
        )
    }

    AnimatedVisibility(
        visible = state.showOverlay,
        enter = fadeIn(),
        exit = fadeOut(),
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
        )
    }

    AnimatedVisibility(
        visible = state.showOverlay,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
    ) {
        // Anchor d-pad focus on the panel when it opens, so the overlay has a
        // focus target and gamepad navigation can reach every action (#1410).
        // A short settle lets the panel place (and finish sliding in) before we
        // request — requestFocus from a LaunchedEffect during an
        // AnimatedVisibility enter is otherwise unreliable.
        LaunchedEffect(drawerOpenGeneration) {
            delay(150)
            ComposeFocusBridge.requestKeyboardMode?.invoke()
            try { drawerInitialFocusRequester.requestFocus() } catch (_: Exception) {}
        }
        key(drawerOpenGeneration) {
            InGameOverlayPanel(
                state = state,
                viewModel = viewModel,
                drawerInitialFocusRequester = drawerInitialFocusRequester,
                useGamepadConfig = hasGamepadConfig,
                showButtonRemap = hasGamepadConfig && gamepadMappingViewModel != null,
                onConfigureButtons = { showButtonMapping = true },
            )
        }
    }

    // Performance HUD — opt-in via Settings (showPerformanceOverlay).
    // Off by default since casual users don't need to see frame timing
    // while playing. Hidden when secondary display is active (HUD moves
    // there). Hidden during the main overlay so it doesn't double-render.
    if (state.isRunning
        && state.showPerformanceOverlay
        && !state.showOverlay
        && !state.secondaryDisplayActive
    ) {
        FpsHud(
            fps = state.fps,
            isNetplayMode = state.isNetplayMode,
            netplayPeerUsername = state.netplayPeerUsername,
            netplayPeerLatencyMs = state.netplayPeerLatencyMs,
            onToggleOverlay = { viewModel.onIntent(EmulationIntent.ToggleOverlay) },
        )
    }

    // Netplay pause attribution overlay
    val pausedBy = state.netplayPausedByUsername
    if (state.isNetplayMode && pausedBy != null && !state.showOverlay) {
        NetplayPauseOverlay(
            pausedByUsername = pausedBy,
            pauseElapsedSeconds = state.netplayPauseElapsedSeconds,
            onResume = { viewModel.onIntent(EmulationIntent.ResumeGame) },
            onLeaveSession = { viewModel.onIntent(EmulationIntent.ShowNetplayLeaveConfirm) },
        )
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
                SpSecondaryButton(
                    text = "Leave Session",
                    onClick = {
                        viewModel.onIntent(EmulationIntent.ConfirmNetplayLeave)
                        onExit()
                    },
                )
            },
        )
    }

    // Netplay session expiration warning (AC-12: 15-minute max session)
    if (state.isNetplayMode && state.netplaySessionExpired) {
        NetplaySessionExpiredOverlay(
            onLeaveSession = {
                viewModel.onIntent(EmulationIntent.ConfirmNetplayLeave)
                onExit()
            },
        )
    }

    // Netplay leave confirmation dialog
    if (state.netplayShowLeaveConfirm) {
        OverlayConfirmDialog(
            title = "Leave Netplay Session?",
            message = "The other player will be disconnected.",
            cancelText = "Keep Playing",
            confirmText = "Leave Session",
            onCancel = { viewModel.onIntent(EmulationIntent.DismissNetplayLeaveConfirm) },
            onConfirm = {
                viewModel.onIntent(EmulationIntent.ConfirmNetplayLeave)
                onExit()
            },
        )
    }

    // Exit confirmation dialog (non-netplay)
    if (state.showExitConfirm && !state.isNetplayMode) {
        OverlayConfirmDialog(
            title = "Exit without saving?",
            message = "This game doesn't support save states. Any unsaved progress will be lost.",
            cancelText = "Keep Playing",
            confirmText = "Exit Anyway",
            onCancel = { viewModel.onIntent(EmulationIntent.DismissExitConfirm) },
            onConfirm = {
                viewModel.onIntent(EmulationIntent.ConfirmExit)
                onExit()
            },
        )
    }

    // Status message toast (auto-dismisses after 2 seconds)
    state.statusMessage?.let { message ->
        LaunchedEffect(message) {
            delay(2000)
            viewModel.onIntent(EmulationIntent.DismissStatus)
        }
        OverlayToast(message = message)
    }

    // #962: SaveManager raises critical save errors via state.error
    // (auto-save quota exceeded, slot save/load failures, hardcore-
    // mode save block). Pre-fix nothing read state.error in this
    // overlay, so the user lost their save silently. Mirror state.error
    // through the same toast surface as statusMessage so the message
    // actually reaches the screen.
    state.error?.let { error ->
        LaunchedEffect(error) {
            delay(4000) // longer than status — these are real failures
            viewModel.onIntent(EmulationIntent.DismissError)
        }
        OverlayToast(message = error)
    }

    // Challenge timer HUD (visible during challenge gameplay, top left)
    if (state.isChallengeMode && state.isRunning && !state.showOverlay) {
        ChallengeTimerHud(challengeElapsedMs = state.challengeElapsedMs)
    }

    // Challenge give up confirmation dialog
    if (state.showGiveUpConfirm) {
        OverlayConfirmDialog(
            title = "Give Up Challenge?",
            message = "Your current attempt will be abandoned.",
            cancelText = "Keep Playing",
            confirmText = "Give Up",
            onCancel = { viewModel.onIntent(EmulationIntent.DismissGiveUpConfirm) },
            onConfirm = {
                viewModel.onIntent(EmulationIntent.ConfirmGiveUp)
                onExit()
            },
        )
    }

    // Challenge creation panel
    if (state.showChallengeCreation) {
        ChallengeCreationPanel(
            gameTitle = state.gameTitle,
            isSubmitting = state.isCreatingChallenge,
            onSubmit = { name, description, type, difficulty ->
                viewModel.onIntent(EmulationIntent.SubmitChallenge(name, description, type, difficulty))
            },
            onDismiss = { viewModel.onIntent(EmulationIntent.DismissChallengeCreation) },
        )
    }

    // Challenge creation success toast. 5 s is comfortable read time
    // and gives Android E2E (`ChallengeCreationTest.createChallengeSuccessfully`)
    // a usable window to assert the toast text — at 2 s the test
    // raced cold-container POST latency and missed the toast (#837).
    if (state.challengeCreationSuccess) {
        // Key on the success counter so a second challenge creation within
        // the 5s window of the first re-launches the dismiss timer. Keying
        // on Unit (or just the boolean flag) would leave the original
        // timer pointing at the second toast and dismiss it prematurely.
        LaunchedEffect(state.challengeCreationSuccessCount) {
            delay(5000)
            viewModel.onIntent(EmulationIntent.DismissChallengeCreation)
        }
        OverlayToast(message = "Challenge created!")
    }

    // Challenge completed result dialog
    state.challengeCompletedAttempt?.let { attempt ->
        ChallengeCompletedDialog(
            durationMs = attempt.durationMs,
            isBest = attempt.isBest,
            onDone = {
                viewModel.onIntent(EmulationIntent.DismissChallengeResult)
                viewModel.onIntent(EmulationIntent.StopGame)
                onExit()
            },
        )
    }

    // First-launch save-state opt-in prompt for large-tier consoles
    // (#804 phase 4b spec point b). Renders before the core-mismatch
    // dialog so the user makes the higher-level "do I want save
    // states for this console at all" decision before any save-state
    // mechanics kick in.
    if (state.showSaveStatePrompt) {
        com.spela.player.presentation.ui.feature.ingame.SaveStateOptInDialog(
            consoleName = state.saveStatePromptConsoleName,
            onAccept = { viewModel.onIntent(EmulationIntent.AcceptSaveStatesForConsole) },
            onReject = { viewModel.onIntent(EmulationIntent.RejectSaveStatesForConsole) },
            onDeferToPerGame = { viewModel.onIntent(EmulationIntent.DeferSaveStateChoiceToPerGame) },
        )
    }

    // Slot-primary save/load picker for medium / large console tiers
    // (#804 phase 5). Tapping Save or Load on the in-game overlay
    // sets state.slotPickerMode; this renders the modal in front of
    // everything else so the user picks a slot rather than performing
    // a free-form named save.
    state.slotPickerMode?.let { mode ->
        val tier = state.consoleSaveStatePolicyTier
        val slotCount = when (tier) {
            com.spela.player.domain.model.SaveStatePolicyTier.Large -> 5
            else -> 10
        }
        // Medium tier (10 slots) gets the "Save with name…" link as a
        // secondary affordance under the slot grid (#830). Large tier
        // is slot-only by phase 5 spec — pass null to hide the link.
        // Small tier never opens the slot picker (named saves are
        // already its primary metaphor).
        val onSaveWithName: (() -> Unit)? = if (
            tier == com.spela.player.domain.model.SaveStatePolicyTier.Medium &&
            mode == com.spela.player.presentation.state.SlotPickerMode.Save
        ) {
            { viewModel.onIntent(EmulationIntent.ShowNamedSaveDialog) }
        } else {
            null
        }
        com.spela.player.presentation.ui.feature.ingame.InGameSlotPickerDialog(
            mode = mode,
            slotCount = slotCount,
            saveSlots = state.saveSlots,
            onSaveToSlot = { slot -> viewModel.onIntent(EmulationIntent.SaveToSlot(slot)) },
            onLoadFromSlot = { slot -> viewModel.onIntent(EmulationIntent.LoadFromSlot(slot)) },
            onDismiss = { viewModel.onIntent(EmulationIntent.DismissSlotPicker) },
            onSaveWithName = onSaveWithName,
            onSlotLongPress = { slot -> viewModel.onIntent(EmulationIntent.ShowSlotActionsSheet(slot)) },
        )
    }

    // Wii control scheme picker (#1559)
    if (state.showWiiControlSchemePicker) {
        com.spela.player.presentation.ui.feature.ingame.InGameWiiControlSchemeDialog(
            currentScheme = state.wiiControlScheme,
            currentIrSource = state.wiiIrSource,
            onSelect = { scheme ->
                viewModel.onIntent(EmulationIntent.SelectWiiControlScheme(scheme))
            },
            onSelectIrSource = { source ->
                viewModel.onIntent(EmulationIntent.SelectWiiIrSource(source))
            },
            onDismiss = { viewModel.onIntent(EmulationIntent.DismissWiiControlSchemePicker) },
        )
    }

    // Named-save dialog reachable from the slot picker on medium tier
    // (#830). Slot-primary stays the default; this is the power-user
    // marker affordance.
    if (state.showNamedSaveDialog) {
        com.spela.player.presentation.ui.feature.ingame.InGameNamedSaveDialog(
            onConfirm = { name -> viewModel.onIntent(EmulationIntent.SaveWithName(name)) },
            onDismiss = { viewModel.onIntent(EmulationIntent.DismissNamedSaveDialog) },
        )
    }

    // Slot manage modals (#831). Stack on top of the slot picker so a
    // long-press doesn't dismiss it — the sheet/dialog hides the picker
    // behind a scrim while open and dismisses back to it on cancel.
    state.slotActionsSheetSlot?.let { slot ->
        com.spela.player.presentation.ui.feature.ingame.InGameSlotActionsSheet(
            slot = slot,
            slotInfo = state.saveSlots[slot],
            onRename = { viewModel.onIntent(EmulationIntent.ShowSlotRenameDialog(slot)) },
            onDelete = { viewModel.onIntent(EmulationIntent.ShowSlotDeleteConfirm(slot)) },
            onDismiss = { viewModel.onIntent(EmulationIntent.DismissSlotActionsSheet) },
        )
    }
    state.slotRenameTarget?.let { slot ->
        com.spela.player.presentation.ui.feature.ingame.InGameSlotRenameDialog(
            slot = slot,
            slotInfo = state.saveSlots[slot],
            onConfirm = { name -> viewModel.onIntent(EmulationIntent.RenameSlot(slot, name)) },
            onDismiss = { viewModel.onIntent(EmulationIntent.DismissSlotRenameDialog) },
        )
    }
    state.slotDeleteConfirmSlot?.let { slot ->
        com.spela.player.presentation.ui.feature.ingame.InGameSlotDeleteConfirmDialog(
            slot = slot,
            onConfirm = { viewModel.onIntent(EmulationIntent.DeleteSlot(slot)) },
            onDismiss = { viewModel.onIntent(EmulationIntent.DismissSlotDeleteConfirm) },
        )
    }

    // Core mismatch dialog
    if (state.showCoreMismatchDialog) {
        CoreMismatchDialog(
            saveCoreName = state.coreMismatchSaveCoreName,
            currentCoreName = state.coreMismatchCurrentCoreName,
            onTryAnyway = { viewModel.onIntent(EmulationIntent.CoreMismatchTryAnyway) },
            onGameSaveOnly = { viewModel.onIntent(EmulationIntent.CoreMismatchGameSaveOnly) },
            onStartFresh = { viewModel.onIntent(EmulationIntent.CoreMismatchStartFresh) },
        )
    }

    // #672 core-upgrade decision sheets + rehearsal banner / sheet
    when (val coreDecision = state.coreDecision) {
        is com.spela.player.presentation.state.CoreDecision.UpgradeAvailable ->
            com.spela.player.presentation.ui.feature.ingame.CoreUpgradeDecisionSheet(
                decision = coreDecision,
                onTryWithMySave = { viewModel.onIntent(EmulationIntent.ResolveCoreDecisionTry) },
                onKeepNewVersion = { viewModel.onIntent(EmulationIntent.ResolveCoreDecisionKeepNew) },
                onLockOldVersion = { viewModel.onIntent(EmulationIntent.ResolveCoreDecisionLockOld) },
                onRemindLater = { viewModel.onIntent(EmulationIntent.ResolveCoreDecisionRemindLater) },
            )
        is com.spela.player.presentation.state.CoreDecision.PinPruned ->
            com.spela.player.presentation.ui.feature.ingame.CorePinPrunedDecisionSheet(
                decision = coreDecision,
                onTryWithMySave = { viewModel.onIntent(EmulationIntent.ResolveCoreDecisionTry) },
                onStartFresh = { viewModel.onIntent(EmulationIntent.ResolveCoreDecisionStartFresh) },
                onRemindLater = { viewModel.onIntent(EmulationIntent.ResolveCoreDecisionRemindLater) },
            )
        is com.spela.player.presentation.state.CoreDecision.RehearsalPrompt ->
            // Explicit top-center placement over the emulator surface.
            // Wrapping in a fillMaxSize Box with Alignment.TopCenter
            // makes the intent obvious (and survives future parent
            // alignment changes). Spec: banner sits top-aligned while
            // the user evaluates live gameplay underneath.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                com.spela.player.presentation.ui.feature.ingame.RehearsalBanner(
                    decision = coreDecision,
                    onDidThisWork = { viewModel.onIntent(EmulationIntent.RehearsalShowConfirmation) },
                    modifier = Modifier.padding(
                        horizontal = SpSpacing.ScreenHorizontal,
                        vertical = SpSpacing.Default,
                    ),
                )
            }
        is com.spela.player.presentation.state.CoreDecision.RehearsalCrashed ->
            com.spela.player.presentation.ui.feature.ingame.RehearsalCrashedDecisionSheet(
                decision = coreDecision,
                onLockOldVersion = { viewModel.onIntent(EmulationIntent.RehearsalCompletedLockOld) },
                onStartFresh = { viewModel.onIntent(EmulationIntent.RehearsalCrashStartFresh) },
                onReport = { viewModel.onIntent(EmulationIntent.RehearsalCrashDismiss) },
                onTryLater = { viewModel.onIntent(EmulationIntent.RehearsalCrashDismiss) },
            )
        null -> Unit
    }

    // Sheet C — rendered on top of the rehearsal banner when the user
    // has tapped "Did this work?". Kept as a separate boolean so the
    // banner stays composed behind the sheet (the user is evaluating
    // live gameplay while answering).
    if (state.showRehearsalConfirmSheet) {
        com.spela.player.presentation.ui.feature.ingame.RehearsalCompleteDecisionSheet(
            onKeepNewVersion = { viewModel.onIntent(EmulationIntent.RehearsalCompletedKeepNew) },
            onLockOldVersion = { viewModel.onIntent(EmulationIntent.RehearsalCompletedLockOld) },
            onTryLonger = { viewModel.onIntent(EmulationIntent.RehearsalContinueLonger) },
        )
    }

    // Core mismatch save warning dialog
    if (state.showCoreMismatchSaveDialog) {
        CoreMismatchSaveDialog(
            originalCoreName = state.mismatchedOriginalCore,
            currentCoreName = state.coreMismatchCurrentCoreName.ifEmpty { "current core" },
            onSaveAnyway = { viewModel.onIntent(EmulationIntent.ConfirmCoreMismatchSave) },
            onSkipSaveState = { viewModel.onIntent(EmulationIntent.SkipCoreMismatchSave) },
        )
    }

    // Cheats browser dialog
    if (state.showCheatBrowser && state.cheats.isNotEmpty()) {
        CheatsDialog(
            cheats = state.cheats,
            onToggle = { cheatId, enabled ->
                viewModel.onIntent(EmulationIntent.ToggleCheatInGame(cheatId, enabled))
            },
            onDismiss = { viewModel.onIntent(EmulationIntent.HideCheatBrowser) },
        )
    }

    // Gamepad config dialog (controller list with activity indicators)
    if (state.showGamepadConfig && gamepadConfigViewModel != null) {
        val gamepadConfigState by gamepadConfigViewModel.state.collectAsState()
        val configAssignments = gamepadConfigState.portAssignments

        GamepadConfigDialog(
            state = gamepadConfigState,
            onConfigurePort = { port ->
                val assignment = configAssignments.find { it.port == port }
                val portLabel = if (assignment != null) {
                    "Player ${port + 1} \u2014 ${assignment.deviceName}"
                } else null
                // Load mapping for the selected port and show key mapping dialog
                val consoleId = state.consoleId
                val gameId = state.gameId
                keyMappingViewModel.onIntent(
                    if (gameId.isNotEmpty()) {
                        KeyMappingIntent.LoadGameMapping(gameId, consoleId)
                    } else {
                        KeyMappingIntent.LoadMapping(consoleId, port)
                    }
                )
                gamepadConfigViewModel.onIntent(GamepadConfigIntent.SelectPortForMapping(port))
                viewModel.onIntent(EmulationIntent.HideGamepadConfig)
                viewModel.onIntent(EmulationIntent.ShowKeyMapping)
            },
            onSwapUp = { port ->
                gamepadConfigViewModel.onIntent(GamepadConfigIntent.SwapPorts(port - 1, port))
            },
            onSwapDown = { port ->
                gamepadConfigViewModel.onIntent(GamepadConfigIntent.SwapPorts(port, port + 1))
            },
            onSetStyleOverride = { port, style ->
                gamepadConfigViewModel.onIntent(GamepadConfigIntent.SetStyleOverride(port, style))
            },
            // Keyboard key-mapping is desktop-only (Android gamepad input is positional).
            showConfigureButton = currentPlatform() != "android",
            onDismiss = {
                viewModel.onIntent(EmulationIntent.HideGamepadConfig)
            },
        )
    }

    // Key mapping dialog
    if (state.showKeyMapping) {
        val keyMappingState by keyMappingViewModel.state.collectAsState()
        val consoleId = state.consoleId
        val layout = remember(consoleId) { DefaultKeyMappings.getLayoutForConsole(consoleId) }

        val gameId = state.gameId
        LaunchedEffect(gameId, consoleId) {
            if (gameId.isNotEmpty()) {
                keyMappingViewModel.onIntent(KeyMappingIntent.LoadGameMapping(gameId, consoleId))
            } else {
                keyMappingViewModel.onIntent(KeyMappingIntent.LoadMapping(consoleId))
            }
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
            onLoadPreset = {
                keyMappingViewModel.onIntent(KeyMappingIntent.ShowPresetPicker)
            },
            onCancelMapping = {
                keyMappingViewModel.onIntent(KeyMappingIntent.CancelMapping)
            },
            onClearBinding = {
                keyMappingViewModel.onIntent(KeyMappingIntent.ClearCurrentBinding)
            },
            // Per-game override affordance (#1336): only when a game is loaded.
            onSaveGameOverride = if (gameId.isNotEmpty()) {
                { keyMappingViewModel.onIntent(KeyMappingIntent.SaveAsGameOverride(gameId)) }
            } else null,
            onClearGameOverride = if (gameId.isNotEmpty()) {
                { keyMappingViewModel.onIntent(KeyMappingIntent.ClearGameOverride(gameId)) }
            } else null,
            hasGameOverride = keyMappingState.hasGameOverride,
            onDismiss = {
                keyMappingViewModel.onIntent(KeyMappingIntent.FinishMapping)
                viewModel.onIntent(EmulationIntent.HideKeyMapping)
            },
            keyNameResolver = ::platformKeyName,
        )

        if (keyMappingState.showPresetPicker) {
            PresetPickerDialog(
                presets = keyMappingState.availablePresets,
                activePresetId = keyMappingState.activePresetId,
                onSelectPreset = { presetId ->
                    keyMappingViewModel.onIntent(KeyMappingIntent.ApplyPreset(presetId))
                },
                onDismiss = {
                    keyMappingViewModel.onIntent(KeyMappingIntent.DismissPresetPicker)
                },
            )
        }
    }

    // Positional controller-buttons editor (#1340): the same per-console
    // hold-to-bind dialog as Settings, opened from the overlay menu. Rendered over
    // the panel, so Done/back returns to the overlay menu.
    if (showButtonMapping && gamepadMappingViewModel != null) {
        val gamepadMappingState by gamepadMappingViewModel.state.collectAsState()
        val consoleId = state.consoleId
        LaunchedEffect(consoleId) {
            gamepadMappingViewModel.onIntent(GamepadMappingIntent.Load(consoleId))
        }
        GamepadMappingDialog(
            state = gamepadMappingState,
            onStartBinding = { output ->
                gamepadMappingViewModel.onIntent(GamepadMappingIntent.StartBinding(output))
            },
            onBindKey = { position, pressed ->
                gamepadMappingViewModel.onIntent(GamepadMappingIntent.ReportBindInput(position, pressed))
            },
            onCancelBinding = {
                gamepadMappingViewModel.onIntent(GamepadMappingIntent.CancelBinding)
            },
            onResetToDefaults = {
                gamepadMappingViewModel.onIntent(GamepadMappingIntent.ResetAll)
            },
            onDismiss = {
                gamepadMappingViewModel.onIntent(GamepadMappingIntent.CancelBinding)
                showButtonMapping = false
            },
        )
    }
}

internal fun formatSessionDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
