package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * A card with a main tappable area and a separated trailing action.
 * Uses a gradient border with a vertical divider between the content and the action.
 *
 * Example: server card with content on the left and a delete icon on the right.
 */
@Composable
fun SpActionCard(
    onClick: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            SpColor.GradientStart.copy(alpha = 0.4f),
            SpColor.GradientMid.copy(alpha = 0.4f),
            SpColor.GradientEnd.copy(alpha = 0.4f),
        ),
    )
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(1.dp, borderBrush, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Main content — tappable
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        // Divider
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.1f)),
        )

        // Trailing action — separate tap target
        Box(
            modifier = Modifier
                .clickable(onClick = onAction)
                .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Default),
            contentAlignment = Alignment.Center,
        ) {
            action()
        }
    }
}
