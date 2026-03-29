package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * A small label badge rendered over cover art.
 *
 * Intended for use with [SpGameCard]'s `coverBadge` slot.
 */
@Composable
fun SpCoverBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black.copy(alpha = 0.6f),
    textColor: Color = Color.White,
) {
    Text(
        text = text,
        style = SpTypography.LabelSmall,
        color = textColor,
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
