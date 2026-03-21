package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * DESIGN component — defines the visual look of a chip/badge.
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Provides: pill shape, background, border, text styling.
 * Has no domain knowledge — does not know what a "console" is.
 *
 * Used by role components like [SpConsoleChip].
 */
@Composable
fun SpChip(
    text: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    color: Color = SpColor.Primary,
    leadingIcon: (@Composable () -> Unit)? = null,
    onGradient: Boolean = false,
) {
    val shape = RoundedCornerShape(SpSpacing.RadiusPill)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val backgroundColor = when {
        onGradient -> Color.White.copy(alpha = if (isSelected) 0.15f else 0.06f)
        isSelected -> color.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val borderColor = when {
        isFocused -> SpColor.PrimaryLight.copy(alpha = 0.85f)
        onGradient -> Color.White.copy(alpha = 0.25f)
        isSelected -> color.copy(alpha = 0.4f)
        else -> SpColor.Divider
    }
    val textColor = when {
        onGradient -> Color.White.copy(alpha = 0.90f)
        isSelected -> SpColor.OnBackgroundSecondary
        else -> SpColor.OnBackgroundSecondary
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(if (isFocused) 2.dp else 1.dp, borderColor, shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).focusable(interactionSource = interactionSource) else Modifier
            )
            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XSmall),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(SpSpacing.XSmall))
            }
            Text(
                text = text,
                style = SpTypography.LabelMedium,
                color = textColor,
            )
        }
    }
}

/**
 * ROLE component — a chip that represents a console platform.
 *
 * Layer 3 in the component hierarchy (Design → Content → Role).
 * Thin wrapper around [SpChip] — maps console domain data to chip parameters.
 * All console badges across the app must use this, never raw [SpChip].
 */
@Composable
fun SpConsoleChip(
    consoleName: String,
    consoleColor: Color,
    modifier: Modifier = Modifier,
    onGradient: Boolean = false,
) {
    SpChip(
        text = consoleName,
        color = consoleColor,
        isSelected = true,
        modifier = modifier,
        onGradient = onGradient,
    )
}
