package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatRating

/**
 * CONTENT component — defines how a developer looks on a card.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Composes [SpCard] into a fixed layout:
 * developer name → game count → rating.
 *
 * Parallel to [SpGameCard] (for games) and [SpWideGameCard] (for wide game cards).
 *
 * Does NOT accept a modifier parameter — the layout is strict.
 *
 * Role components (ConsoleDeveloperCard, etc.) should delegate to
 * this — never duplicate this layout.
 */
@Composable
fun SpDeveloperCard(
    name: String,
    gameCount: Int,
    avgRating: Double = 0.0,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    SpCard(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it }
            .semantics {
                contentDescription = "$name, $gameCount games"
                role = Role.Button
            },
        onClick = onClick,
        onGradient = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XSmall))
                Text(
                    text = "$gameCount ${if (gameCount == 1) "game" else "games"}",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundSecondary,
                )
            }
            if (avgRating > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = SpColor.Rating,
                        modifier = Modifier.size(SpSpacing.IconSmall),
                    )
                    Text(
                        text = formatRating(avgRating),
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundSecondary,
                    )
                }
            }
        }
    }
}
