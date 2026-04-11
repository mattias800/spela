package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * CONTENT component — a full-width hero banner with background art and overlaid content.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Renders a hero image (or gradient fallback) with a gradient overlay for readability,
 * and content overlaid in a semi-transparent contrast box.
 *
 * Used by developer/publisher screens, franchise/series screens, etc.
 *
 * @param heroUrl URL of the hero image. Falls back to gradient when null.
 * @param modifier Modifier for the banner root.
 * @param height Fixed height for the banner. Use null for dynamic height (wraps content).
 * @param topPadding Padding above the content (e.g. to clear a floating top bar).
 * @param content Content rendered inside the contrast backdrop box.
 */
@Composable
fun SpHeroBanner(
    heroUrl: String?,
    modifier: Modifier = Modifier,
    height: Dp? = null,
    topPadding: Dp = SpSpacing.TopBarHeight + SpSpacing.Large,
    content: @Composable () -> Unit,
) {
    val heightModifier = if (height != null) Modifier.height(height) else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(heightModifier),
    ) {
        // Background: hero image or gradient fallback
        if (heroUrl != null) {
            SubcomposeAsyncImage(
                model = heroUrl,
                contentDescription = null,
                modifier = if (height != null) Modifier.fillMaxSize() else Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = (if (height != null) Modifier.fillMaxSize() else Modifier.matchParentSize())
                            .background(SpColor.Background),
                    )
                },
                error = {
                    GradientFallback(if (height != null) Modifier.fillMaxSize() else Modifier.matchParentSize())
                },
            )
        } else {
            GradientFallback(if (height != null) Modifier.fillMaxSize() else Modifier.matchParentSize())
        }

        // Gradient overlay: transparent at top, background at bottom
        Box(
            modifier = (if (height != null) Modifier.fillMaxSize() else Modifier.matchParentSize())
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.4f to SpColor.Background.copy(alpha = 0.3f),
                            1.0f to SpColor.Background,
                        ),
                    ),
                ),
        )

        // Content in contrast backdrop — centered, wraps content width
        Box(
            modifier = Modifier
                .then(if (height != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(
                    top = topPadding,
                    start = SpSpacing.XLarge,
                    end = SpSpacing.XLarge,
                    bottom = SpSpacing.Large,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.4f),
                        RoundedCornerShape(SpSpacing.CardCornerRadius),
                    )
                    .padding(SpSpacing.Large),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun GradientFallback(modifier: Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    SpColor.Primary.copy(alpha = 0.3f),
                    SpColor.Background,
                ),
            ),
        ),
    )
}
