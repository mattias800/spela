package com.spela.player.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * DESIGN component — text styled as a clickable link.
 *
 * Layer 1 in the component hierarchy (Design → Content → Role).
 * Uses underline decoration for universal affordance on any background.
 * Does NOT use accent colors — works on all gradient backgrounds.
 *
 * Use for inline clickable text (developer name, publisher name, "Show more",
 * "Browse Gallery", "See all", etc.). Do NOT use for buttons — use [SpButton] instead.
 */
@Composable
fun SpLinkText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onGradient: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        style = SpTypography.BodyMedium,
        color = if (onGradient) Color.White else SpColor.OnBackground,
        textDecoration = TextDecoration.Underline,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clickable(onClick = onClick),
    )
}
