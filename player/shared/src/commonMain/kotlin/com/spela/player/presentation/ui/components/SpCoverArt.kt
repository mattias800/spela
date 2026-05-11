package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
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
    onAspectRatioResolved: ((Float) -> Unit)? = null,
    // True while a scrape is in progress — surfaces the placeholder
    // unconditionally (no Coil call yet) so the user knows the cover
    // hasn't been fetched.
    isLoading: Boolean = false,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val boxModifier = if (aspectRatio != null) {
        modifier.aspectRatio(aspectRatio).clip(shape)
    } else {
        modifier.clip(shape)
    }
    val scale = if (aspectRatio != null) ContentScale.Crop else ContentScale.Fit

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> SpImageDefaults.Placeholder()
            else -> SpImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = scale,
                placeholder = { CoverPlaceholder(contentDescription) },
                error = { CoverPlaceholder(contentDescription) },
                onSuccess = { state ->
                    if (onAspectRatioResolved != null) {
                        val size = state.result.image.run { width.toFloat() / height.toFloat() }
                        if (size.isFinite() && size > 0f) {
                            onAspectRatioResolved(size)
                        }
                    }
                },
            )
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
        SpImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillWidth,
            placeholder = { CoverPlaceholder(contentDescription) },
            error = { CoverPlaceholder(contentDescription) },
        )

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
private fun CoverPlaceholder(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.take(2).uppercase(),
            style = SpTypography.DisplaySmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}
