package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * CONTENT component — a navigation tile that represents a console.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Composes [SpTileCard] into a fixed layout: logo + name + game count.
 * Used for "Browse by Console" sections.
 *
 * Does NOT accept a modifier parameter — the layout is strict.
 *
 * Role components (ConsoleQuickJumpCard, etc.) should delegate to
 * this — never duplicate this layout.
 */
@Composable
fun SpConsoleTile(
    name: String,
    gameCount: Int,
    accentColor: Color,
    logoUrl: String,
    onClick: () -> Unit,
    testTag: String? = null,
    logoAspectRatio: Float? = null,
) {
    SpTileCard(
        onClick = onClick,
        backgroundColor = accentColor,
        modifier = Modifier
            .let { if (testTag != null) it.testTag(testTag) else it }
            .semantics {
                contentDescription = "$name, $gameCount ${if (gameCount == 1) "game" else "games"}"
                role = Role.Button
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(SpSpacing.Default),
        ) {
            if (logoUrl.isNotEmpty()) {
                SpAreaSizedImage(
                    imageUrl = logoUrl,
                    contentDescription = name,
                    targetArea = 3000f,
                    maxHeight = 36.dp,
                    maxWidth = 120.dp,
                    minHeight = 20.dp,
                    initialAspectRatio = logoAspectRatio,
                    error = {
                        Text(
                            text = name,
                            style = SpTypography.TitleSmall,
                            color = SpColor.OnCard,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            } else {
                Text(
                    text = name,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = "$gameCount ${if (gameCount == 1) "game" else "games"}",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundSecondary,
                maxLines = 1,
            )
        }
    }
}
