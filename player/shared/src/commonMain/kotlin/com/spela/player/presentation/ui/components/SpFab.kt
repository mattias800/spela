package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Shared floating action button with focus ring support for gamepad navigation.
 * Round shape, white semi-transparent background that blends with any gradient.
 */
@Composable
fun SpFab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = "",
    size: Dp = 56.dp,
    iconSize: Dp = 24.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .spFocusRing(shape = CircleShape, scaleOnFocus = true)
            .focusable()
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}
