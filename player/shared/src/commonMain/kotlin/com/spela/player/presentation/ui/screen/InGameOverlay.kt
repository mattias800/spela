package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import com.spela.player.domain.model.DefaultKeyMappings
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCountdownOverlay
import com.spela.player.presentation.ui.components.challenge.ChallengeCreationPanel
import com.spela.player.presentation.ui.components.keymapping.KeyMappingDialog
import com.spela.player.presentation.ui.components.keymapping.platformKeyName
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
    val keyMappingViewModel: KeyMappingViewModel = koinInject()

    AnimatedVisibility(
        visible = state.showOverlay,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
    ) {
        LaunchedEffect(Unit) {
            try { continueFocusRequester.requestFocus() } catch (_: Exception) {}
        }
        InGameOverlayPanel(
            state = state,
            viewModel = viewModel,
            continueFocusRequester = continueFocusRequester,
        )
    }

    // Performance HUD (always visible when game is running, but small)
    // Hidden when secondary display is active (HUD moves there)
    if (state.isRunning && !state.showOverlay && !state.secondaryDisplayActive) {
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

    // Challenge creation success toast
    if (state.challengeCreationSuccess) {
        LaunchedEffect(Unit) {
            delay(2000)
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

    // Key mapping dialog
    if (state.showKeyMapping) {
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

internal fun formatSessionDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
