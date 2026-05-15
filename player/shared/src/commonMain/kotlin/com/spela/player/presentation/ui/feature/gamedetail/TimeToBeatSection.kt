package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Compact "Time to Beat" card matching the web UI's [time-to-beat-card.tsx]
 * after the #1110 redesign. Three labelled cells (Main / Extras / All)
 * in a single row, each showing an hours-string or an em-dash placeholder
 * if the tier is missing.
 *
 * The whole section is hidden if every tier is zero. Individual cells
 * always render so the three-column rhythm stays consistent.
 */
@Composable
internal fun TimeToBeatSection(
    game: Game,
    modifier: Modifier = Modifier,
) {
    val main = game.timeToBeatHastily
    val extras = game.timeToBeatNormally
    val all = game.timeToBeatCompletely

    if (main <= 0 && extras <= 0 && all <= 0) return

    SpTitledSection(
        title = "Time to Beat",
        icon = Icons.Filled.AccessTime,
        modifier = modifier.testTag("time_to_beat_section"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            TimeToBeatCell(
                label = "Main",
                description = "Time to finish the main story",
                seconds = main,
                modifier = Modifier
                    .weight(1f)
                    .testTag("time_to_beat_main"),
            )
            TimeToBeatCell(
                label = "Extras",
                description = "Main story plus side content",
                seconds = extras,
                modifier = Modifier
                    .weight(1f)
                    .testTag("time_to_beat_extras"),
            )
            TimeToBeatCell(
                label = "All",
                description = "Everything — 100% completion",
                seconds = all,
                modifier = Modifier
                    .weight(1f)
                    .testTag("time_to_beat_all"),
            )
        }
    }
}

@Composable
private fun TimeToBeatCell(
    label: String,
    description: String,
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    val hasValue = seconds > 0
    val display = if (hasValue) formatTimeToBeat(seconds) else "—"
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
            // White-with-alpha lifts a frost over whatever screen
            // background sits behind (console-themed gradients on
            // game-detail) instead of muddying it with a fixed dark
            // grey. Same trick as the hero banner's backdrop.
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = if (hasValue) {
                    "$label: $display — $description"
                } else {
                    "$label: not available — $description"
                }
            },
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
    ) {
        Text(
            text = label.uppercase(),
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = display,
            style = SpTypography.TitleLarge,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Default,
            color = if (hasValue) SpColor.OnBackground else SpColor.OnBackgroundTertiary,
            maxLines = 1,
        )
    }
}

/**
 * Formats time-to-beat seconds into a compact hours string matching
 * the web UI's [formatHours]:
 *
 *   - 0      → "" (caller should treat as missing and show "—")
 *   - <3600s → "<1h"
 *   - whole  → "Nh"
 *   - other  → "N.Nh"
 */
internal fun formatTimeToBeat(seconds: Int): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600.0
    if (hours < 1.0) return "<1h"
    return if (hours == hours.toLong().toDouble()) {
        "${hours.toLong()}h"
    } else {
        "${"%.1f".format(hours)}h"
    }
}
