package com.spela.player.presentation.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.presentation.ui.components.social.formatRelativeTime
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatPlayTime

/**
 * Displays play time and last-played metadata as a single compact text line.
 *
 * Examples: "1h 23m · 2d ago", "45m · 3h ago · by alice", "2d ago"
 */
@Composable
fun SpPlayInfo(
    totalPlayTime: Long = 0,
    lastPlayedAt: String? = null,
    lastPlayedByUsername: String? = null,
    modifier: Modifier = Modifier,
) {
    val text = buildString {
        if (totalPlayTime > 0) append(formatPlayTime(totalPlayTime))
        if (lastPlayedAt != null) {
            if (isNotEmpty()) append(" \u00b7 ")
            append(formatRelativeTime(lastPlayedAt))
        }
        if (lastPlayedByUsername != null) {
            if (isNotEmpty()) append(" \u00b7 ")
            append("by $lastPlayedByUsername")
        }
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
            modifier = modifier,
        )
    }
}
