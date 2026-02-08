package com.spela.player.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
) {
    val shape = RoundedCornerShape(12.dp)

    when (style) {
        SpButtonStyle.Primary -> {
            val containerColor by animateColorAsState(
                targetValue = if (enabled) SpColor.Primary else SpColor.SurfaceBright,
                animationSpec = tween(200),
            )
            Button(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = SpColor.OnPrimary,
                    disabledContainerColor = SpColor.SurfaceBright,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                contentPadding = PaddingValues(horizontal = SpSpacing.XLarge, vertical = SpSpacing.Medium),
            ) {
                ButtonContent(text, isLoading, leadingIcon)
            }
        }

        SpButtonStyle.Secondary -> {
            Button(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpColor.Secondary,
                    contentColor = SpColor.OnSecondary,
                    disabledContainerColor = SpColor.SurfaceBright,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                contentPadding = PaddingValues(horizontal = SpSpacing.XLarge, vertical = SpSpacing.Medium),
            ) {
                ButtonContent(text, isLoading, leadingIcon)
            }
        }

        SpButtonStyle.Outlined -> {
            OutlinedButton(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = shape,
                border = BorderStroke(1.dp, if (enabled) SpColor.Primary else SpColor.Divider),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = SpColor.Primary,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                contentPadding = PaddingValues(horizontal = SpSpacing.XLarge, vertical = SpSpacing.Medium),
            ) {
                ButtonContent(text, isLoading, leadingIcon, SpColor.Primary)
            }
        }

        SpButtonStyle.Ghost -> {
            TextButton(
                onClick = { if (!isLoading) onClick() },
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = SpColor.Primary,
                    disabledContentColor = SpColor.OnBackgroundTertiary,
                ),
                contentPadding = PaddingValues(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium),
            ) {
                ButtonContent(text, isLoading, leadingIcon, SpColor.Primary)
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
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = indicatorColor,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(SpSpacing.Small))
    } else if (leadingIcon != null) {
        leadingIcon()
        Spacer(Modifier.width(SpSpacing.Small))
    }
    Text(
        text = text,
        style = SpTypography.LabelLarge,
    )
}
