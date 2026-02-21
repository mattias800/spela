package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.ui.screen.formatSessionDuration
import com.spela.player.presentation.ui.components.EmulationActionButton
import com.spela.player.presentation.ui.components.fpsColor
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.LibretroController
import org.koin.compose.koinInject

private const val BURN_IN_IDLE_TIMEOUT_MS = 15_000L
private const val BURN_IN_FADE_DURATION_MS = 5_000

/**
 * Content composable displayed on the secondary screen during gameplay.
 *
 * Layout for ~3.92" screen:
 * ```
 * ┌─────────────────────────┐
 * │  Game Title    00:45:12  │  <- Game info bar
 * ├─────────────────────────┤
 * │                         │
 * │    [Touch Controls]     │  <- Main area: platform touch gamepad
 * │                         │
 * ├─────────────────────────┤
 * │ Save Load Shot FF │ FPS │  <- Quick actions + FPS
 * └─────────────────────────┘
 * ```
 */
@Composable
fun SecondaryScreenContent(
    viewModel: EmulationViewModel = koinInject(),
    controller: LibretroController = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val contentAlpha = if (state.isPaused) 0.4f else 1f

    // OLED burn-in protection: fade to black after idle timeout (single-screen games only)
    var touchResetKey by remember { mutableIntStateOf(0) }
    var isDimmed by remember { mutableStateOf(false) }
    val burnInAlpha by animateFloatAsState(
        targetValue = if (isDimmed) 0f else 1f,
        animationSpec = if (isDimmed) tween(BURN_IN_FADE_DURATION_MS) else snap(),
        label = "burnInAlpha",
    )

    if (!state.isDualScreenConsole) {
        LaunchedEffect(touchResetKey, state.isPaused) {
            isDimmed = false
            delay(BURN_IN_IDLE_TIMEOUT_MS)
            isDimmed = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background)
            .then(
                if (!state.isDualScreenConsole) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                touchResetKey++
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        if (state.isDualScreenConsole) {
            // DS/3DS mode: only show the bottom screen, no UI chrome
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha },
            ) {
                PlatformDsTouchScreen(
                    controller = controller,
                    splitY = state.dualScreenSplitY,
                    selectedShader = state.selectedShader,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha * burnInAlpha },
            ) {
                // Game info bar
                GameInfoBar(
                    gameTitle = state.gameTitle,
                    sessionElapsedSeconds = state.sessionElapsedSeconds,
                )

                // Main content area (takes remaining space)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    // Normal mode: touch gamepad controls
                    PlatformTouchControls(
                        controller = controller,
                    )
                }

                // Quick action bar + performance HUD
                QuickActionBar(
                    fps = state.fps,
                    frameTime = state.frameTime,
                    isFastForward = state.isFastForward,
                    onSave = { viewModel.onIntent(EmulationIntent.SaveState) },
                    onLoad = { viewModel.onIntent(EmulationIntent.LoadState) },
                    onScreenshot = { viewModel.onIntent(EmulationIntent.TakeScreenshot) },
                    onToggleFastForward = { viewModel.onIntent(EmulationIntent.ToggleFastForward) },
                )
            }
        }

        // Paused overlay
        if (state.isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = burnInAlpha },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "PAUSED",
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground.copy(alpha = 0.8f),
                )
            }
        }

        // Touch-blocking overlay when screen is dimmed (prevents accidental button presses)
        if (burnInAlpha < 0.5f && !state.isDualScreenConsole) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                    .semantics {
                        contentDescription = "Screen dimmed for OLED protection"
                    },
            )
        }
    }
}

@Composable
private fun GameInfoBar(
    gameTitle: String,
    sessionElapsedSeconds: Long,
) {
    val timeText = formatSessionDuration(sessionElapsedSeconds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpColor.SurfaceVariant)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "Now playing: $gameTitle"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = gameTitle,
            style = SpTypography.TitleMedium,
            color = SpColor.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(SpSpacing.Small))
        Text(
            text = timeText,
            style = SpTypography.LabelLarge,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}

@Composable
private fun QuickActionBar(
    fps: Float,
    frameTime: Float,
    isFastForward: Boolean,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleFastForward: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpColor.SurfaceVariant)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Quick action buttons
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            QuickActionButton(
                icon = Icons.Filled.Save,
                label = "Save",
                onClick = onSave,
            )
            QuickActionButton(
                icon = Icons.Filled.FolderOpen,
                label = "Load",
                onClick = onLoad,
            )
            QuickActionButton(
                icon = Icons.Filled.CameraAlt,
                label = "Screenshot",
                onClick = onScreenshot,
            )
            QuickActionButton(
                icon = if (isFastForward) Icons.Filled.PlayArrow else Icons.Filled.FastForward,
                label = if (isFastForward) "Normal" else "Fast",
                isActive = isFastForward,
                onClick = onToggleFastForward,
            )
        }

        Spacer(Modifier.width(SpSpacing.Small))

        // Performance HUD
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.semantics {
                contentDescription = "%.0f FPS, %.1f ms frame time".format(fps, frameTime)
            },
        ) {
            Text(
                text = "%.0f FPS".format(fps),
                style = SpTypography.LabelLarge,
                color = fpsColor(fps),
            )
            Text(
                text = "%.1fms".format(frameTime),
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    EmulationActionButton(
        icon = icon,
        label = label,
        onClick = onClick,
        isActive = isActive,
        buttonSize = 48.dp,
        iconSize = 24.dp,
        showLabel = false,
        useFocusRing = false,
    )
}
