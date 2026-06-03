package com.spela.player.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Linux window chrome for the undecorated-window path.
 *
 * JBR's WindowDecorations/CustomTitleBar API is not implemented on Linux
 * (JBR-6413; verified: JBR.getWindowDecorations() == null on JBR 21 X11), so
 * the transparent-title-bar look used on macOS/Windows is achieved here with
 * an undecorated window plus this Compose-drawn strip: the app's content and
 * background extend behind it (the strip itself is transparent), with
 * caption buttons drawn as plain glyphs on the right.
 *
 * Dragging uses JBR's WindowMove service when available — a native WM move
 * (_NET_WM_MOVERESIZE), so KWin/GNOME snapping and tiling work — and the
 * caller provides a fallback for non-JBR runtimes. Edge resizing is handled
 * by Compose's built-in UndecoratedWindowResizer (active for undecorated +
 * resizable windows); this strip only owns move/min/max/close.
 *
 * Pure presentation: window operations are injected as callbacks so the
 * desktop test suite can drive it without a real window.
 */
@Composable
fun LinuxTitleBarChrome(
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    /** Begin a window move; return true when a native move was engaged. */
    onDragStart: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.testTag("linux-titlebar")) {
        // Drag region: everything left of the caption buttons. Double-click
        // toggles maximize (standard CSD affordance); a drag hands the window
        // to the WM via onDragStart.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("linux-titlebar-drag")
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onToggleMaximize() })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, _ -> change.consume() },
                    )
                },
        )
        CaptionButton(
            tag = "linux-titlebar-minimize",
            onClick = onMinimize,
        ) { color ->
            val y = size.height * 0.55f
            drawLine(color, Offset(size.width * 0.32f, y), Offset(size.width * 0.68f, y), strokeWidth = density)
        }
        CaptionButton(
            tag = "linux-titlebar-maximize",
            onClick = onToggleMaximize,
        ) { color ->
            val s = size.minDimension
            if (isMaximized) {
                // Restore: two offset squares
                drawRect(
                    color,
                    topLeft = Offset(s * 0.38f, s * 0.30f),
                    size = androidx.compose.ui.geometry.Size(s * 0.30f, s * 0.30f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = density),
                )
                drawRect(
                    color,
                    topLeft = Offset(s * 0.30f, s * 0.38f),
                    size = androidx.compose.ui.geometry.Size(s * 0.30f, s * 0.30f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = density),
                )
            } else {
                drawRect(
                    color,
                    topLeft = Offset(s * 0.32f, s * 0.32f),
                    size = androidx.compose.ui.geometry.Size(s * 0.36f, s * 0.36f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = density),
                )
            }
        }
        CaptionButton(
            tag = "linux-titlebar-close",
            onClick = onClose,
            hoverColor = Color(0xFFE81123),
        ) { color ->
            val s = size.minDimension
            drawLine(color, Offset(s * 0.34f, s * 0.34f), Offset(s * 0.66f, s * 0.66f), strokeWidth = density)
            drawLine(color, Offset(s * 0.66f, s * 0.34f), Offset(s * 0.34f, s * 0.66f), strokeWidth = density)
        }
    }
}

/**
 * A single caption button: fixed-width hover-highlighted hit area with a
 * Canvas-drawn glyph. Glyphs are drawn (not icon assets) so the chrome has
 * no icon-set dependency and matches the thin-line CSD style. Excluded from
 * the gamepad focus system — window chrome is pointer-only.
 */
@Composable
private fun CaptionButton(
    tag: String,
    onClick: () -> Unit,
    hoverColor: Color = Color.White.copy(alpha = 0.12f),
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(color: Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .testTag(tag)
            .focusProperties { canFocus = false }
            .hoverable(interaction)
            .background(if (hovered) hoverColor else Color.Transparent)
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        // Chrome palette is fixed (the app is dark-themed and the strip sits
        // outside the app's MaterialTheme): white glyphs, slightly dimmed
        // until hovered. Matches the #0A0A10 fallback background in Main.kt.
        val glyphColor = if (hovered) Color.White else Color.White.copy(alpha = 0.75f)
        Canvas(modifier = Modifier.fillMaxSize()) { glyph(glyphColor) }
    }
}

/* ===== JBR WindowMove (native WM drag) ===== */

private val jbrWindowMove: Any? by lazy {
    runCatching {
        Class.forName("com.jetbrains.JBR").getMethod("getWindowMove").invoke(null)
    }.getOrNull()
}

private val jbrStartMovingMethod: java.lang.reflect.Method? by lazy {
    runCatching {
        Class.forName("com.jetbrains.WindowMove")
            .getMethod("startMovingTogetherWithMouse", java.awt.Window::class.java, Integer.TYPE)
    }.getOrNull()
}

/**
 * Hand the in-progress mouse drag to the window manager as a native window
 * move (JBR WindowMove, X11 _NET_WM_MOVERESIZE). Returns false off-JBR or
 * when the service is unavailable; callers fall back to manual moving.
 */
fun startNativeWindowMove(window: java.awt.Window): Boolean {
    val service = jbrWindowMove ?: return false
    val method = jbrStartMovingMethod ?: return false
    return runCatching {
        method.invoke(service, window, java.awt.event.MouseEvent.BUTTON1)
        true
    }.getOrDefault(false)
}

/**
 * True when JBR offers the WindowDecorations (CustomTitleBar) service —
 * i.e. the existing applyJbrTransparentTitleBar path can work. On Linux JBR
 * this is currently always false (JBR-6413); checked up front so the window
 * can be created undecorated before AWT realizes it.
 */
fun isJbrWindowDecorationsAvailable(): Boolean =
    runCatching {
        Class.forName("com.jetbrains.JBR").getMethod("getWindowDecorations").invoke(null) != null
    }.getOrDefault(false)

/**
 * True when JBR offers the WindowMove service (native WM drag). The custom
 * Linux chrome is only enabled when this holds — without it an undecorated
 * window couldn't be dragged at all, which is a worse degradation than the
 * standard title bar (non-JBR dev runs keep the decorated window).
 */
fun isJbrWindowMoveAvailable(): Boolean = jbrWindowMove != null && jbrStartMovingMethod != null
