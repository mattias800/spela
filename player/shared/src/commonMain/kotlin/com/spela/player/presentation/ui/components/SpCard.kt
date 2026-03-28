package com.spela.player.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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

/**
 * DESIGN component — defines the visual look of a card.
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Provides: rounded corners, border, background, click/hover/focus states.
 * Has no domain knowledge — does not know what a "game" or "console" is.
 *
 * Used by content components like [SpGameCard] and [SpTitledSection].
 * Screens should rarely use SpCard directly — prefer a content or role component.
 */
@Composable
fun SpCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = SpSpacing.CardCornerRadius,
    backgroundColor: Color = SpColor.Card,
    onGradient: Boolean = false,
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
        label = "cardScale",
    )

    val shape = RoundedCornerShape(cornerRadius)

    val resolvedBg = if (onGradient) {
        val alpha by animateFloatAsState(
            targetValue = when {
                isPressed -> 0.05f
                isHovered || isFocused -> 0.14f
                else -> 0.09f
            },
            animationSpec = tween(150),
            label = "cardGradientAlpha",
        )
        Color.White.copy(alpha = alpha)
    } else {
        if (isHovered || isFocused) SpColor.CardHovered else backgroundColor
    }

    val borderColor = if (isFocused) {
        Color.White.copy(alpha = 0.85f)
    } else if (onGradient) {
        Color.White.copy(alpha = 0.08f)
    } else {
        SpColor.Divider.copy(alpha = 0.5f)
    }

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
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
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
                    ).focusable(interactionSource = interactionSource)
                } else Modifier
            )
    ) {
        content()
    }
}

/**
 * A card intended to be nested inside [SpTitledSection] or other gradient-backed surfaces.
 *
 * Where [SpTitledSection] composites a semi-transparent *black* overlay to slightly darken
 * the background, [SpInnerCard] composites a semi-transparent *white* overlay to slightly
 * lighten it. The result is a card that always reads as one level above its container
 * regardless of the gradient colour behind it — adapting naturally to any console theme.
 *
 * Use this instead of [SpCard] when the card sits inside a coloured or gradient surface
 * and a fixed opaque background would look out of place.
 */
@Composable
fun SpInnerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = SpSpacing.CardCornerRadius,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgAlpha by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.05f
            isHovered || isFocused -> 0.11f
            else -> 0.07f
        },
        animationSpec = tween(150),
        label = "innerCardAlpha",
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) SpColor.Primary.copy(alpha = 0.85f)
                        else Color.White.copy(alpha = 0.08f),
                shape = shape,
            )
            .clip(shape)
            .background(Color.White.copy(alpha = bgAlpha))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).focusable(interactionSource = interactionSource) else Modifier
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
                color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                shape = shape,
            )
            .clip(shape)
            .background(Brush.linearGradient(gradientColors))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).focusable(interactionSource = interactionSource) else Modifier
            )
    ) {
        content()
    }
}

@Composable
fun SpGameCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onGradient: Boolean = false,
    coverContent: @Composable () -> Unit,
    infoContent: @Composable () -> Unit,
) {
    SpCard(
        modifier = modifier,
        onClick = onClick,
        onGradient = onGradient,
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
