package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * CONTENT component — a navigation tile that represents a mood/genre.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Composes [SpTileCard] into a fixed layout: emoji icon + name + description.
 * Used for "What are you in the mood for?" sections.
 *
 * Does NOT accept a modifier parameter — the layout is strict.
 *
 * Role components (MoodCard, etc.) should delegate to
 * this — never duplicate this layout.
 */
@Composable
fun SpMoodTile(
    icon: String,
    name: String,
    description: String,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    val brush = if (gradientColors.size >= 2) Brush.linearGradient(gradientColors)
                else Brush.linearGradient(listOf(Color(0xFF424242), Color(0xFF616161)))

    SpTileCard(
        onClick = onClick,
        backgroundBrush = brush,
        width = 200.dp,
        modifier = Modifier
            .let { if (testTag != null) it.testTag(testTag) else it }
            .semantics {
                contentDescription = "$name, $description"
                role = Role.Button
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(SpSpacing.Medium),
        ) {
            Text(
                text = icon,
                style = SpTypography.HeadlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = name,
                style = SpTypography.TitleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = SpTypography.BodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
