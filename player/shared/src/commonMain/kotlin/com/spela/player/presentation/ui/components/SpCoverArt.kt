package com.spela.player.presentation.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/** Standard portrait game cover aspect ratio (5 × 7). */
private const val COVER_ASPECT_RATIO: Float = 5f / 7f

@Composable
fun SpCoverArt(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = SpSpacing.RadiusLarge,
    aspectRatio: Float? = COVER_ASPECT_RATIO,
    // True while a scrape is in progress — shows an animated shimmer instead of
    // the static placeholder so the user knows something is happening.
    isLoading: Boolean = false,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val boxModifier = if (aspectRatio != null) {
        modifier.aspectRatio(aspectRatio).clip(shape)
    } else {
        modifier.clip(shape)
    }
    val scale = if (aspectRatio != null) ContentScale.Crop else ContentScale.FillWidth

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CoverShimmer()
            imageUrl != null -> SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth(),
                contentScale = scale,
                loading = { CoverShimmer() },
                error = { CoverPlaceholder(contentDescription) },
            )
            else -> CoverPlaceholder(contentDescription)
        }
    }
}

@Composable
fun SpHeroCover(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
                loading = {
                    CoverShimmer()
                },
                error = {
                    CoverPlaceholder(contentDescription)
                },
            )
        } else {
            CoverPlaceholder(contentDescription)
        }

        // Gradient overlay at the bottom for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SpColor.Background.copy(alpha = 0f),
                            SpColor.Background.copy(alpha = 0.3f),
                            SpColor.Background.copy(alpha = 0.85f),
                            SpColor.Background,
                        ),
                        startY = 0f,
                    )
                ),
        )
    }
}

@Composable
private fun CoverShimmer() {
    val alpha = if (LocalAnimationsEnabled.current) {
        val transition = rememberInfiniteTransition(label = "coverShimmer")
        val animatedAlpha by transition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "coverShimmerAlpha",
        )
        animatedAlpha
    } else {
        0.25f
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.SurfaceBright.copy(alpha = alpha)),
    )
}

@Composable
private fun CoverPlaceholder(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(SpColor.SurfaceVariant, SpColor.SurfaceElevated)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.take(2).uppercase(),
            style = SpTypography.DisplaySmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}
