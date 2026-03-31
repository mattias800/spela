package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Neon pill showing the connected server URL with a status dot and "Switch" action.
 * Used on the login screen as a compact server indicator.
 */
@Composable
fun SpServerPill(
    serverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(percent = 50)
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            SpColor.GradientStart.copy(alpha = 0.4f),
            SpColor.GradientMid.copy(alpha = 0.4f),
            SpColor.GradientEnd.copy(alpha = 0.4f),
        ),
    )

    Row(
        modifier = modifier
            .clip(pillShape)
            .border(1.dp, gradientBrush, pillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Green status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(SpColor.Success),
        )

        // Server URL
        Text(
            text = serverUrl.ifEmpty { "No server" },
            style = SpTypography.BodySmall,
            color = SpColor.AccentPurpleLight,
            modifier = Modifier.padding(start = 8.dp),
        )

        // Vertical divider
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .width(1.dp)
                .height(16.dp)
                .background(Color.White.copy(alpha = 0.12f)),
        )

        // Switch label
        Text(
            text = "Switch",
            style = SpTypography.LabelSmall,
            color = SpColor.AccentPurple,
        )
    }
}
