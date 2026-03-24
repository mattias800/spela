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
import androidx.compose.material.icons.filled.Layers
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

/**
 * A compact vertical list of console stats shown in the [ConsoleHeroBanner].
 * Each row shows an icon, a bold value, and a small contextual label below it.
 *
 * Data is sourced from [getConsoleInfo] in ConsoleMetadata.kt.
 */
@Composable
internal fun ConsoleInfoSection(
    console: Console,
    modifier: Modifier = Modifier,
    showFeatureBadges: Boolean = false,
) {
    val info = getConsoleInfo(console.abbreviation) ?: return

    val stats = buildList {
        add(Stat(Icons.Filled.CalendarToday, info.releaseYear.toString(), "Released"))
        add(Stat(Icons.Filled.Business,      info.manufacturer,           "Maker"))
        if (info.generation.isNotEmpty()) {
            add(Stat(Icons.Filled.Layers, info.generation, "Generation"))
        }
        add(Stat(Icons.Filled.SdCard, info.mediaType, "Media"))
        if (info.unitsSold.isNotEmpty()) {
            add(Stat(Icons.Filled.Group, info.unitsSold, "Units sold"))
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
