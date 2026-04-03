package com.spela.player.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.theme.spelaBrandGradient

enum class SpButtonStyle { Primary, Secondary, Outlined, Ghost }

@Composable
fun SpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SpButtonStyle = SpButtonStyle.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(SpSpacing.RadiusLarge),
    onGradient: Boolean = false,
    skipBackground: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusMods = Modifier
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && !isLoading && enabled) {
                when (event.key) {
                    Key.Enter, Key.Spacebar, Key.DirectionCenter -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            } else false
        }
        .gamepadFocusable(shape = shape, interactionSource = interactionSource)
    val isIconOnly = text.isEmpty() && leadingIcon != null
    val defaultPadding = PaddingValues(horizontal = SpSpacing.XLarge, vertical = SpSpacing.Medium)
    val iconOnlyPadding = PaddingValues(SpSpacing.Medium)

    when (style) {
        SpButtonStyle.Primary -> {
            val brush = spelaBrandGradient()
            Button(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .then(if (!skipBackground) Modifier.neonGlow(shape = shape, intense = true) else Modifier)
                    .then(if (!skipBackground) Modifier.background(
                        brush = if (enabled) brush else Brush.linearGradient(
                            listOf(SpColor.SurfaceBright, SpColor.SurfaceBright)
                        ),
                        shape = shape,
                    ) else Modifier)
                    .then(focusMods),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                contentPadding = if (isIconOnly) iconOnlyPadding else defaultPadding,
            ) {
                ButtonContent(text, isLoading, leadingIcon, Color.White)
            }
        }

        SpButtonStyle.Secondary -> {
            val brush = spelaBrandGradient()
            Button(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .neonGlow(shape = shape, intense = false)
                    .border(1.5.dp, if (enabled) brush else Brush.linearGradient(listOf(SpColor.Divider, SpColor.Divider)), shape)
                    .then(focusMods),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                contentPadding = if (isIconOnly) iconOnlyPadding else defaultPadding,
            ) {
                ButtonContent(text, isLoading, leadingIcon, Color.White)
            }
        }

        SpButtonStyle.Outlined -> {
            val brush = spelaBrandGradient()
            OutlinedButton(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .neonGlow(shape = shape, intense = false)
                    .then(focusMods),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                border = BorderStroke(
                    1.5.dp,
                    when {
                        !enabled -> Brush.linearGradient(listOf(SpColor.Divider, SpColor.Divider))
                        else -> brush
                    },
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                contentPadding = if (isIconOnly) iconOnlyPadding else defaultPadding,
            ) {
                ButtonContent(text, isLoading, leadingIcon, Color.White)
            }
        }

        SpButtonStyle.Ghost -> {
            val ghostColor = if (onGradient) Color.White else SpColor.OnBackground
            TextButton(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier.heightIn(min = 48.dp).then(focusMods),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = ghostColor,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                contentPadding = if (isIconOnly) iconOnlyPadding else PaddingValues(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium),
            ) {
                ButtonContent(text, isLoading, leadingIcon, ghostColor)
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
    indicatorColor: Color = SpColor.OnPrimary,
) {
    if (isLoading) {
        if (LocalAnimationsEnabled.current) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = indicatorColor,
                strokeWidth = 2.dp,
            )
        }
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(SpSpacing.Small))
        }
    } else if (leadingIcon != null) {
        leadingIcon()
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(SpSpacing.Small))
        }
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = SpTypography.LabelLarge,
        )
    }
}

/**
 * ROLE component — a secondary/outlined button.
 *
 * Layer 3 in the component hierarchy (Design → Content → Role).
 * Thin wrapper around [SpButton] with [SpButtonStyle.Outlined].
 * Use this for secondary actions instead of passing style manually.
 */
@Composable
fun SpSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    onGradient: Boolean = false,
) {
    SpButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        style = SpButtonStyle.Outlined,
        enabled = enabled,
        isLoading = isLoading,
        leadingIcon = leadingIcon,
        onGradient = onGradient,
    )
}

/**
 * Draws a neon glow effect behind the composable using two layered semi-transparent
 * rounded rect draws (purple glow + pink glow).
 *
 * @param shape The shape to use for the glow corner radius.
 * @param intense If true, uses a stronger glow (for primary buttons); if false, uses a subtler glow.
 */
private fun Modifier.neonGlow(
    shape: Shape = RoundedCornerShape(SpSpacing.RadiusLarge),
    intense: Boolean = true,
): Modifier = this.drawBehind {
    val cr = CornerRadius(SpSpacing.RadiusLarge.toPx())
    val alpha = if (intense) 0.25f else 0.15f
    val glowBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF6B8DD6).copy(alpha = alpha),
            Color(0xFFa855f7).copy(alpha = alpha),
            Color(0xFFE056A0).copy(alpha = alpha * 0.7f),
        ),
    )
    drawRoundRect(
        brush = glowBrush,
        cornerRadius = cr,
        size = size,
        style = Stroke(width = if (intense) 8.dp.toPx() else 6.dp.toPx()),
    )
}
