package com.spela.player.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusBorder = Modifier.border(
        width = if (isFocused) 2.dp else 0.dp,
        color = if (isFocused) SpColor.PrimaryLight else Color.Transparent,
        shape = shape,
    )
    val isIconOnly = text.isEmpty() && leadingIcon != null
    val defaultPadding = PaddingValues(horizontal = SpSpacing.XLarge, vertical = SpSpacing.Medium)
    val iconOnlyPadding = PaddingValues(SpSpacing.Medium)

    when (style) {
        SpButtonStyle.Primary -> {
            val containerColor by animateColorAsState(
                targetValue = when {
                    !enabled -> SpColor.SurfaceBright
                    onGradient -> Color.White.copy(alpha = 0.15f)
                    else -> SpColor.Primary
                },
                animationSpec = tween(200),
                label = "primaryContainerColor",
            )
            val contentColor = if (onGradient && enabled) Color.White else SpColor.OnPrimary
            Button(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier.heightIn(min = 48.dp).then(focusBorder),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                    disabledContainerColor = SpColor.SurfaceBright,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                contentPadding = if (isIconOnly) iconOnlyPadding else defaultPadding,
            ) {
                ButtonContent(text, isLoading, leadingIcon, if (onGradient && enabled) Color.White else SpColor.OnPrimary)
            }
        }

        SpButtonStyle.Secondary -> {
            Button(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier.heightIn(min = 48.dp).then(focusBorder),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (onGradient) Color.White.copy(alpha = 0.12f) else SpColor.Secondary,
                    contentColor = if (onGradient) Color.White else SpColor.OnSecondary,
                    disabledContainerColor = SpColor.SurfaceBright,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                contentPadding = if (isIconOnly) iconOnlyPadding else defaultPadding,
            ) {
                ButtonContent(text, isLoading, leadingIcon, if (onGradient) Color.White else SpColor.OnSecondary)
            }
        }

        SpButtonStyle.Outlined -> {
            OutlinedButton(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier.heightIn(min = 48.dp).then(focusBorder),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                border = BorderStroke(
                    1.dp,
                    when {
                        !enabled -> SpColor.Divider
                        onGradient -> Color.White.copy(alpha = 0.25f)
                        else -> SpColor.OnBackgroundSecondary.copy(alpha = 0.5f)
                    },
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (onGradient) Color.White else SpColor.OnBackgroundSecondary,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                contentPadding = if (isIconOnly) iconOnlyPadding else defaultPadding,
            ) {
                ButtonContent(text, isLoading, leadingIcon, if (onGradient) Color.White else SpColor.Primary)
            }
        }

        SpButtonStyle.Ghost -> {
            TextButton(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier.heightIn(min = 48.dp).then(focusBorder),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (onGradient) Color.White else SpColor.Primary,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                contentPadding = if (isIconOnly) iconOnlyPadding else PaddingValues(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium),
            ) {
                ButtonContent(text, isLoading, leadingIcon, if (onGradient) Color.White else SpColor.Primary)
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
