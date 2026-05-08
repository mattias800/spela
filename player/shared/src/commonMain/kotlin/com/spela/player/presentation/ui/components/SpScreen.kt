package com.spela.player.presentation.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.spScreenBackground

/**
 * Provides the vertical [ScrollState] from [SpScrollableContent] to descendants.
 * Used by [centerOnFocus] to scroll the focused element to the viewport center.
 */
val LocalScrollState = compositionLocalOf<ScrollState?> { null }

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

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
            // Tap-outside-to-dismiss handler. `detectTapGestures` runs in
            // the bubble phase (PointerEventPass.Main), so any tap that
            // hits an inner `Modifier.clickable` / interactive composable
            // is consumed before reaching us — this only fires for taps
            // on bare screen background. The two effects together give
            // every screen the "click anywhere outside the form to
            // dismiss the keyboard" behaviour without per-screen wiring:
            //
            //   1. `keyboardController?.hide()` — Compose's cross-platform
            //      "hide IME" call (no-op on platforms without an IME).
            //   2. `focusManager.clearFocus()` — drops focus from the
            //      currently focused text field so it visibly de-focuses
            //      and the AndroidView+EditText path stops requesting
            //      input.
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                })
            },
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
    val scrollState = rememberScrollState()
    CompositionLocalProvider(LocalScrollState provides scrollState) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .focusGroup()
                .verticalScroll(scrollState),
            content = content,
        )
    }
}

/**
 * Adds consistent screen-level padding.
 *
 * - Horizontal: [SpSpacing.ScreenHorizontal] on both sides.
 * - Top gap: [SpSpacing.Large] (same as section spacing).
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
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
        content = content,
    )
}

