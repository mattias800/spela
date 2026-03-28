package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val NavButtonColor = Color.White.copy(alpha = 0.12f)
private val NavButtonContentColor = Color.White.copy(alpha = 0.9f)
private val NavButtonDisabledColor = Color.White.copy(alpha = 0.04f)
private val NavButtonDisabledContentColor = Color.White.copy(alpha = 0.25f)

/**
 * Reduces touchSlop to 0 so mouse drag gestures fire immediately on desktop.
 * Without this, the default desktop touchSlop (18px) prevents small mouse
 * movements from being detected as drags.
 */
@Composable
private fun WithReducedTouchSlop(content: @Composable () -> Unit) {
    val original = LocalViewConfiguration.current
    val modified = remember(original) {
        object : ViewConfiguration by original {
            override val touchSlop: Float get() = 0f
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides modified) {
        content()
    }
}

@Composable
fun ScreenshotLightbox(
    visible: Boolean,
    screenshotUrls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        WithReducedTouchSlop {
            val pagerState = rememberPagerState(
                initialPage = initialIndex,
                pageCount = { screenshotUrls.size },
            )
            val coroutineScope = rememberCoroutineScope()
            var scale by remember { mutableFloatStateOf(1f) }
            var verticalDragOffset by remember { mutableFloatStateOf(0f) }
            val dismissProgress = (abs(verticalDragOffset) / 150f).coerceIn(0f, 1f)

            LaunchedEffect(visible, initialIndex) {
                if (visible) pagerState.scrollToPage(initialIndex)
            }

            // Reset zoom on page change
            LaunchedEffect(pagerState.currentPage) {
                scale = 1f
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f * (1f - dismissProgress * 0.4f))),
            ) {
                // Pager with mouse-drag support (fullscreen)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, verticalDragOffset.roundToInt()) }
                        .alpha(1f - dismissProgress * 0.3f)
                        .mouseDragPager(
                            pagerState = pagerState,
                            coroutineScope = coroutineScope,
                            onDismiss = onDismiss,
                            onVerticalDrag = { verticalDragOffset = it },
                            onDragEnd = { verticalDragOffset = 0f },
                        ),
                ) { page ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = screenshotUrls[page],
                            contentDescription = "Screenshot ${page + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = SpSpacing.XLarge)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                // Scroll-wheel zoom (Initial pass — doesn't block drag)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (event.type == PointerEventType.Scroll) {
                                                val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                                if (scrollY != 0f) {
                                                    val factor = if (scrollY < 0) 1.15f else 0.87f
                                                    scale = (scale * factor).coerceIn(1f, 5f)
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    }
                                },
                        )
                    }
                }

                // Counter
                Text(
                    text = "${pagerState.currentPage + 1} / ${screenshotUrls.size}",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackground,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = SpSpacing.XLarge),
                )

                // Close button
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = NavButtonColor,
                        contentColor = NavButtonContentColor,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(SpSpacing.Medium)
                        .spFocusRing(shape = androidx.compose.foundation.shape.CircleShape),
                ) {
                    Icon(Icons.Filled.Close, "Close", Modifier.size(24.dp))
                }

                // Nav arrows
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterStart)
                        .padding(horizontal = SpSpacing.Medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val hasPrev = pagerState.currentPage > 0
                    IconButton(
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = hasPrev,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = NavButtonColor, contentColor = NavButtonContentColor,
                            disabledContainerColor = NavButtonDisabledColor, disabledContentColor = NavButtonDisabledContentColor,
                        ),
                        modifier = Modifier.spFocusRing(shape = androidx.compose.foundation.shape.CircleShape),
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous", Modifier.size(36.dp)) }

                    val hasNext = pagerState.currentPage < screenshotUrls.size - 1
                    IconButton(
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        enabled = hasNext,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = NavButtonColor, contentColor = NavButtonContentColor,
                            disabledContainerColor = NavButtonDisabledColor, disabledContentColor = NavButtonDisabledContentColor,
                        ),
                        modifier = Modifier.spFocusRing(shape = androidx.compose.foundation.shape.CircleShape),
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", Modifier.size(36.dp)) }
                }

                // Hint
                Text(
                    text = "Drag to navigate \u00b7 Scroll to zoom \u00b7 Swipe up/down to close",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundSecondary,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = SpSpacing.XLarge),
                )
            }
        }
    }
}

/**
 * Intercepts mouse drag and feeds it into the pager's scrollBy for smooth
 * drag-follows-cursor navigation. On drag end, snaps to the nearest page.
 * Vertical drag beyond threshold dismisses the lightbox.
 */
private fun Modifier.mouseDragPager(
    pagerState: PagerState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalDragX = 0f
            var totalDragY = 0f
            var wasDragged = false

            drag(down.id) { change ->
                val delta = change.positionChange()
                totalDragX += delta.x
                totalDragY += delta.y
                wasDragged = true
                change.consume()
                coroutineScope.launch {
                    pagerState.scrollBy(-delta.x)
                }
                onVerticalDrag(totalDragY)
            }

            if (!wasDragged) {
                // Was a click, not a drag — dismiss
                onDismiss()
            } else if (abs(totalDragY) > 150f && abs(totalDragY) > abs(totalDragX)) {
                // Vertical swipe — dismiss
                onDismiss()
            } else {
                // Horizontal — snap to nearest page
                coroutineScope.launch {
                    val offset = pagerState.currentPageOffsetFraction
                    when {
                        offset > 0.15f -> pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        offset < -0.15f -> pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        else -> pagerState.animateScrollToPage(pagerState.currentPage)
                    }
                }
            }
            onDragEnd()
        }
    }
}
