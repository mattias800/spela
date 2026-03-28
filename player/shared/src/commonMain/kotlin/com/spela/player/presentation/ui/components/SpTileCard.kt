package com.spela.player.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * DESIGN component — a colored/gradient navigation tile.
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Unlike [SpCard] which is a content container with subtle background,
 * SpTileCard is a bold, colored tile that acts as a navigation shortcut.
 *
 * Provides: rounded corners, colored/gradient fill, click/hover/focus states,
 * centered content slot.
 *
 * Used by content components like [SpConsoleTile] and [SpMoodTile].
 */
@Composable
fun SpTileCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = SpColor.Primary,
    backgroundBrush: Brush? = null,
    width: Dp = 160.dp,
    height: Dp = 100.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.97f
            isHovered || isFocused -> 1.02f
            else -> 1f
        },
        animationSpec = tween(150),
        label = "tileScale",
    )

    val borderColor = if (isFocused) Color.White.copy(alpha = 0.85f)
                      else Color.White.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(
                if (backgroundBrush != null) Modifier.background(backgroundBrush)
                else Modifier.background(backgroundColor.copy(alpha = 0.15f))
            )
            .border(1.dp, borderColor, shape)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
