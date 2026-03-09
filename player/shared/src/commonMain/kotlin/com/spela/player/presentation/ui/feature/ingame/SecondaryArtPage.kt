package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.presentation.ui.feature.library.getConsoleGradient
import com.spela.player.presentation.ui.screen.formatSessionDuration
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Art Display page for the secondary screen companion.
 *
 * When `heroUrl` is available, displays the game's hero artwork as a full-bleed
 * background with a subtle gradient overlay and game title + session timer at
 * the bottom-right.
 *
 * When `heroUrl` is null, falls back to a centered game title with the console
 * gradient as a subtle background accent ("Focus" mode).
 */
@Composable
fun SecondaryArtPage(
    heroUrl: String?,
    gameTitle: String,
    sessionElapsedSeconds: Long,
    consoleId: String,
    consoleColorTheme: String?,
) {
    val timeText = formatSessionDuration(sessionElapsedSeconds)

    if (heroUrl != null) {
        // Full-bleed hero artwork mode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background)
                .semantics {
                    contentDescription = "Art display: $gameTitle"
                },
        ) {
            // Hero image filling the entire page
            SubcomposeAsyncImage(
                model = heroUrl,
                contentDescription = "Hero artwork for $gameTitle",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SpColor.SurfaceVariant),
                    )
                },
                error = {
                    // On error, fall back to the gradient mode
                    FallbackArtContent(
                        gameTitle = gameTitle,
                        timeText = timeText,
                        consoleId = consoleId,
                        consoleColorTheme = consoleColorTheme,
                    )
                },
            )

            // Gradient overlay: transparent at top, dark at bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                SpColor.Background.copy(alpha = 0.4f),
                                SpColor.Background.copy(alpha = 0.85f),
                            ),
                        )
                    ),
            )

            // Title + timer overlaid at bottom-right
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(SpSpacing.Medium),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = gameTitle,
                    style = SpTypography.TitleLarge.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.7f),
                            offset = Offset(1f, 1f),
                            blurRadius = 4f,
                        ),
                    ),
                    color = SpColor.OnBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = timeText,
                    style = SpTypography.HeadlineSmall.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.7f),
                            offset = Offset(1f, 1f),
                            blurRadius = 4f,
                        ),
                    ),
                    color = SpColor.OnBackgroundSecondary,
                )
            }
        }
    } else {
        // Fallback "Focus" mode: console gradient background with centered title
        FallbackArtContent(
            gameTitle = gameTitle,
            timeText = timeText,
            consoleId = consoleId,
            consoleColorTheme = consoleColorTheme,
        )
    }
}

/**
 * Fallback content when no hero artwork is available.
 * Shows a subtle console gradient background with the game title centered.
 */
@Composable
private fun FallbackArtContent(
    gameTitle: String,
    timeText: String,
    consoleId: String,
    consoleColorTheme: String?,
) {
    val (gradientFrom, gradientTo) = getConsoleGradient(consoleId, consoleColorTheme)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        gradientFrom.copy(alpha = 0.15f),
                        SpColor.Background,
                    ),
                )
            )
            .semantics {
                contentDescription = "Art display: $gameTitle (no artwork)"
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = gameTitle,
                style = SpTypography.TitleLarge,
                color = SpColor.OnBackground,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeText,
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
            )
            if (consoleId.isNotEmpty()) {
                Text(
                    text = consoleId.uppercase(),
                    style = SpTypography.LabelMedium,
                    color = SpColor.OnBackgroundTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
