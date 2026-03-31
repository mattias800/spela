package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Console
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

// Named alpha constants for elements rendered over coloured hero banner backgrounds.
private val HeroIconTint  = Color.White.copy(alpha = 0.45f)
private val HeroValueText = Color.White.copy(alpha = 0.90f)
private val HeroLabelText = Color.White.copy(alpha = 0.40f)

private data class Stat(val icon: ImageVector, val value: String, val label: String)

/** Formats a units-sold count (stored in thousands) into a human-readable string like "61.9M units". */
private fun formatUnitsSold(units: Long): String {
    return when {
        units >= 1_000_000 -> {
            val millions = units / 1_000_000.0
            val formatted = if (millions == millions.toLong().toDouble()) {
                "${millions.toLong()}M"
            } else {
                "${"%.1f".format(millions).trimEnd('0').trimEnd('.')}M"
            }
            "$formatted units"
        }
        units >= 1_000 -> {
            val thousands = units / 1_000.0
            val formatted = if (thousands == thousands.toLong().toDouble()) {
                "${thousands.toLong()}K"
            } else {
                "${"%.1f".format(thousands).trimEnd('0').trimEnd('.')}K"
            }
            "$formatted units"
        }
        else -> "$units units"
    }
}

/**
 * A compact vertical list of console stats shown in the [ConsoleHeroBanner].
 * Each row shows an icon, a bold value, and a small contextual label below it.
 *
 * Data is sourced from the Console domain model (populated from the API).
 */
@Composable
internal fun ConsoleInfoSection(
    console: Console,
    modifier: Modifier = Modifier,
    showFeatureBadges: Boolean = false,
) {
    // If no metadata fields are available, don't render anything
    if (console.releaseYear == null && console.makerName == null && console.mediaTypeName == null) return

    val stats = buildList {
        if (console.releaseYear != null) {
            add(Stat(Icons.Filled.CalendarToday, console.releaseYear.toString(), "Released"))
        }
        if (console.makerName != null) {
            add(Stat(Icons.Filled.Business, console.makerName, "Maker"))
        }
        if (console.mediaTypeName != null) {
            add(Stat(Icons.Filled.SdCard, console.mediaTypeName, "Media"))
        }
        if (console.unitsSold != null && console.unitsSold > 0) {
            val formatted = formatUnitsSold(console.unitsSold)
            add(Stat(Icons.Filled.Group, formatted, "Units sold"))
        }
        if (showFeatureBadges) {
            if (console.saveStateSupport) {
                add(Stat(Icons.Filled.Check, "Yes", "Save states"))
            }
            if (console.browserPlayable) {
                add(Stat(Icons.Filled.Language, "Yes", "Browser play"))
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        stats.forEach { stat ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = null,
                    tint = HeroIconTint,
                    modifier = Modifier.size(SpSpacing.IconSmall),
                )
                Spacer(Modifier.width(SpSpacing.XSmall))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = stat.value,
                        style = SpTypography.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = HeroValueText,
                    )
                    Text(
                        text = stat.label,
                        style = SpTypography.LabelSmall,
                        color = HeroLabelText,
                    )
                }
            }
        }
    }
}
