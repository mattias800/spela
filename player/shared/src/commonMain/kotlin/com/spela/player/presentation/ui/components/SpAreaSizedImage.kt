package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import kotlin.math.sqrt

/**
 * DESIGN component — renders an image at a consistent visual area size.
 *
 * Instead of a fixed height or width, this component targets a consistent
 * visual area (width × height in dp²). Wide images become shorter, tall
 * images become narrower — but they all occupy roughly the same visual space.
 *
 * This solves the problem where logos of different aspect ratios look
 * inconsistently sized when constrained by height alone.
 *
 * @param imageUrl The URL of the image to load.
 * @param contentDescription Accessibility description.
 * @param targetArea The target visual area in dp² (default: 8000 = ~89×89dp square equivalent).
 * @param maxHeight Maximum height constraint to prevent extremely tall images.
 * @param maxWidth Maximum width constraint to prevent extremely wide images.
 * @param minHeight Minimum height to ensure very wide images remain visible.
 * @param modifier Modifier for the outer container.
 * @param loading Composable shown while the image loads.
 * @param error Composable shown if loading fails.
 */
@Composable
fun SpAreaSizedImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    targetArea: Float = 8000f,
    maxHeight: Dp = 120.dp,
    maxWidth: Dp = 200.dp,
    minHeight: Dp = 48.dp,
    loading: @Composable () -> Unit = {},
    error: @Composable () -> Unit = {},
) {
    // Track the resolved aspect ratio from the loaded image
    var aspectRatio by remember { mutableFloatStateOf(0f) }

    // Compute dimensions from target area and aspect ratio
    // area = w * h, aspectRatio = w / h → w = sqrt(area * ar), h = sqrt(area / ar)
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val computedHeight = if (aspectRatio > 0f) {
            val h = sqrt(targetArea / aspectRatio).dp
            h.coerceIn(minHeight, maxHeight)
        } else {
            // Before image loads, use a reasonable default
            sqrt(targetArea).dp.coerceIn(minHeight, maxHeight)
        }

        val computedWidth = if (aspectRatio > 0f) {
            val w = sqrt(targetArea * aspectRatio).dp
            w.coerceAtMost(maxWidth)
        } else {
            maxWidth
        }

        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .heightIn(max = computedHeight)
                .widthIn(max = computedWidth),
            contentScale = ContentScale.Fit,
            onSuccess = { result ->
                val painter = result.painter
                val intrinsicSize = painter.intrinsicSize
                if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                    aspectRatio = intrinsicSize.width / intrinsicSize.height
                }
            },
            loading = { loading() },
            error = { error() },
        )
    }
}
