package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.LibretroController
import org.koin.compose.koinInject

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha },
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
                if (state.isDualScreenConsole) {
                    // DS mode: render bottom screen with touch input
                    PlatformDsTouchScreen(
                        controller = controller,
                        splitY = state.dualScreenSplitY,
                        selectedShader = state.selectedShader,
                    )
                } else {
                    // Normal mode: touch gamepad controls
                    PlatformTouchControls(
                        controller = controller,
                    )
                }
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

        // Paused overlay
        if (state.isPaused) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "PAUSED",
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun GameInfoBar(
    gameTitle: String,
    sessionElapsedSeconds: Long,
) {
    val hours = sessionElapsedSeconds / 3600
    val minutes = (sessionElapsedSeconds % 3600) / 60
    val seconds = sessionElapsedSeconds % 60
    val timeText = "%02d:%02d:%02d".format(hours, minutes, seconds)

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
                color = when {
                    fps >= 55f -> SpColor.Success
                    fps >= 30f -> SpColor.Warning
                    else -> SpColor.Error
                },
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha = when {
        isPressed -> 0.6f
        isActive -> 1f
        else -> 0.85f
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(
                when {
                    isPressed -> SpColor.Primary.copy(alpha = 0.3f)
                    isActive -> SpColor.Primary.copy(alpha = 0.2f)
                    else -> SpColor.SurfaceBright
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive || isPressed) SpColor.Primary else SpColor.OnBackground,
            modifier = Modifier.size(24.dp),
        )
    }
}
