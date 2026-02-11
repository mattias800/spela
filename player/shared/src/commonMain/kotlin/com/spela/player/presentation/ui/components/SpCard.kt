package com.spela.player.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

@Composable
fun SpCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = SpSpacing.CardCornerRadius,
    backgroundColor: Color = SpColor.Card,
    content: @Composable () -> Unit,
) {
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
    )

    val shape = RoundedCornerShape(cornerRadius)
    val resolvedBg = if (isHovered || isFocused) SpColor.CardHovered else backgroundColor

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isHovered || isFocused) 12.dp else 4.dp,
                shape = shape,
                ambientColor = SpColor.Primary.copy(alpha = 0.15f),
                spotColor = SpColor.Primary.copy(alpha = 0.1f),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) SpColor.Primary.copy(alpha = 0.85f) else Color.Transparent,
                shape = shape,
            )
            .clip(shape)
            .background(resolvedBg)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
    ) {
        content()
    }
}

@Composable
fun SpGradientCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    gradientColors: List<Color> = listOf(SpColor.PrimaryDark, SpColor.Primary),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .shadow(8.dp, shape)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) SpColor.Primary.copy(alpha = 0.85f) else Color.Transparent,
                shape = shape,
            )
            .clip(shape)
            .background(Brush.linearGradient(gradientColors))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            )
    ) {
        content()
    }
}

@Composable
fun SpGameCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    coverContent: @Composable () -> Unit,
    infoContent: @Composable () -> Unit,
) {
    SpCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        Column {
            coverContent()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpSpacing.Medium),
            ) {
                infoContent()
            }
        }
    }
}
