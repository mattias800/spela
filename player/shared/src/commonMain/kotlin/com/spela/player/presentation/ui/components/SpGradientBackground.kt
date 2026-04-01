package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// SNES button colors for the ambient glow
private val SnesRed = Color(0xFFCC2232)
private val SnesYellow = Color(0xFFC4B208)
private val SnesBlue = Color(0xFF2C2CAA)
private val SnesGreen = Color(0xFF00843D)

/** The dark background color used on branded screens (login, server connection). */
val SpBrandedBackgroundColor = Color(0xFF0F0F1A)

/**
 * Full-screen branded background with SNES-colored ambient glow blobs.
 * Used on login and server connection screens.
 */
@Composable
fun SpGradientBackground(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpBrandedBackgroundColor),
        contentAlignment = contentAlignment,
    ) {
        SpAmbientGlowBlobs()
        content()
    }
}

/** SNES-colored ambient glow blobs. Must be used inside a BoxScope. */
@Composable
fun BoxScope.SpAmbientGlowBlobs() {
    // Red — top-left
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(SnesRed.copy(alpha = 0.25f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0f, 0f),
                    radius = 600f,
                ),
            ),
    )
    // Blue — bottom-right
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(SnesBlue.copy(alpha = 0.25f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(Float.MAX_VALUE, Float.MAX_VALUE),
                    radius = 600f,
                ),
            ),
    )
    // Green — bottom-center
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(SnesGreen.copy(alpha = 0.20f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(Float.MAX_VALUE / 2f, Float.MAX_VALUE),
                    radius = 500f,
                ),
            ),
    )
    // Yellow — top-right
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(SnesYellow.copy(alpha = 0.15f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(Float.MAX_VALUE, 0f),
                    radius = 500f,
                ),
            ),
    )
}
