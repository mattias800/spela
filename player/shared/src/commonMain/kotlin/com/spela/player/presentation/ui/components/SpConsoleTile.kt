package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.feature.library.getConsoleGradient
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
    colorTheme: String?,
    logoUrl: String,
    onClick: () -> Unit,
    testTag: String? = null,
    logoAspectRatio: Float? = null,
) {
    // Brand gradient derived from the server's colorTheme — same
    // (brand, darkStop) pair the ConsoleHeroBanner uses. The tile and
    // the destination's hero banner therefore render with the same
    // sweep, so the tile reads as a preview of the page you're about
    // to open. Brush.linearGradient with default start/end resolves
    // to a top-left → bottom-right diagonal at the tile's own size.
    val (gradientFrom, gradientTo) = getConsoleGradient(colorTheme)
    val backgroundBrush = Brush.linearGradient(listOf(gradientFrom, gradientTo))

    SpTileCard(
        onClick = onClick,
        backgroundBrush = backgroundBrush,
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
                // Use the PNG variant of the logo for carousel tiles.
                // The SVG endpoint at "/logo" serves Adobe-Illustrator-
                // exported SVGs that are often 100-300 KB each
                // (Atari 5200 = 226 KB, Virtual Boy = 297 KB) — Coil's
                // SVG decoder chokes on those at small render sizes,
                // and even when it succeeds the parse cost is many
                // hundreds of ms per logo, in serial. The PNG endpoint
                // at "/logo.png" serves 800-px rasters pre-built from
                // those same SVGs, typically 10-50 KB each and instant
                // to decode. The detail screen keeps SVG for crisp
                // scaling at hero-banner size.
                val pngUrl = if (logoUrl.endsWith("/logo")) "$logoUrl.png" else logoUrl
                SpAreaSizedImage(
                    imageUrl = pngUrl,
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
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            } else {
                Text(
                    text = name,
                    style = SpTypography.TitleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = "$gameCount ${if (gameCount == 1) "game" else "games"}",
                style = SpTypography.BodySmall,
                // Tile is now a saturated brand-gradient surface
                // matching the hero banner — text colours follow the
                // hero-banner palette (full white for primary,
                // semi-opaque white for secondary) rather than the
                // SpColor.OnCard / OnBackgroundSecondary tokens that
                // were tuned for the previous faded background.
                color = Color.White.copy(alpha = 0.70f),
                maxLines = 1,
            )
        }
    }
}
