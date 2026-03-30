package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.spScreenBackground

/**
 * Root layout for every screen. Provides the screen background,
 * fills all available space, and scrolls vertically.
 *
 * Uses a Box internally so screens can overlay content (snackbars, FABs)
 * on top of the scrollable content.
 *
 * Place edge-to-edge content (hero banners) directly as children.
 * Use [SpMainContentPadding] for content that needs consistent screen padding.
 */
@Composable
fun SpScreen(
    modifier: Modifier = Modifier,
    gradientColors: List<Color>? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val backgroundModifier = if (gradientColors != null) {
        Modifier.drawBehind {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val d = (size.width + size.height) * 0.25f
            drawRect(
                brush = Brush.linearGradient(
                    colors = gradientColors,
                    start = Offset(cx - d, cy - d),
                    end = Offset(cx + d, cy + d),
                ),
            )
        }
    } else {
        Modifier.spScreenBackground()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier),
        content = content,
    )
}

/**
 * Scrollable vertical content inside [SpScreen].
 *
 * This is the scrollable Column that holds the screen's main content.
 * Place this as the first child of [SpScreen], with overlays
 * (snackbars, FABs) as siblings.
 */
@Composable
fun SpScrollableContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}

/**
 * Adds consistent screen-level padding.
 *
 * - Horizontal: [SpSpacing.ScreenHorizontal] on both sides.
 * - Top: [SpSpacing.SectionIndicatorClearance] in gamepad mode
 *   (clears the pill), or 0 in touch mode (SpTopBar handles its own height).
 *
 * Place inside [SpScrollableContent], after any edge-to-edge content.
 */
@Composable
fun SpMainContentPadding(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = SpSpacing.ScreenHorizontal,
                end = SpSpacing.ScreenHorizontal,
                top = SpSpacing.Large,
            ),
        content = content,
    )
}

/**
 * Horizontal screen padding as a modifier. Use on individual items
 * that need consistent horizontal insets within an edge-to-edge container.
 */
fun Modifier.spScreenHorizontalPadding(): Modifier =
    this.padding(horizontal = SpSpacing.ScreenHorizontal)
