package com.spela.player.presentation.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Defines the structural layout for a game detail screen.
 *
 * Uses a side-by-side hero pattern matching the web UI:
 * - **Landscape**: Cover art (fixed ~256dp) on the LEFT in a scrollable column,
 *   info/sections scroll on the RIGHT in a LazyColumn.
 * - **Portrait**: Cover art centered above (~192dp wide), info below,
 *   all in a single LazyColumn.
 *
 * The top bar floats over content with a transparent background, matching the
 * console screen pattern. Content scrolls behind the top bar.
 *
 * @param topBar Composable rendered floating over content (typically [SpTopBar] with back button).
 * @param coverArt Composable for the cover art image. Receives a [Modifier] pre-configured
 *                 with the correct width for the current orientation, and a [Boolean] that is
 *                 `true` when in portrait mode (useful for showing full uncropped art).
 * @param coverExtra Optional composable rendered below the cover art (e.g. rating, download button).
 * @param backgroundColors Gradient colors for the screen background (default: flat SpColor.Background).
 * @param sections Builder lambda that adds [LazyListScope] items for the scrollable content.
 */
@Composable
fun GameDetailLayout(
    topBar: @Composable () -> Unit,
    coverArt: @Composable (modifier: Modifier, isPortrait: Boolean) -> Unit,
    coverExtra: @Composable (isPortrait: Boolean) -> Unit = {},
    backgroundColors: List<Color> = listOf(SpColor.Background, SpColor.Background),
    sections: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val d = (size.width + size.height) * 0.25f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = backgroundColors,
                        start = Offset(cx - d, cy - d),
                        end = Offset(cx + d, cy + d),
                    ),
                )
            },
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            val isSmallLandscape = maxHeight < 500.dp
            val coverWidth = if (isSmallLandscape) 200.dp else 256.dp
            LandscapeLayout(
                coverWidth = coverWidth,
                isCompact = isSmallLandscape,
                topBar = topBar,
                coverArt = coverArt,
                coverExtra = coverExtra,
                sections = sections,
            )
        } else {
            PortraitLayout(
                topBar = topBar,
                coverArt = coverArt,
                coverExtra = coverExtra,
                sections = sections,
            )
        }
    }
}

/**
 * Legacy overload for backward compatibility with [GameDetailSkeleton].
 * Delegates to the new signature with coverArt = cover and no coverExtra.
 */
@Composable
fun GameDetailLayout(
    topBar: @Composable () -> Unit,
    cover: @Composable (Modifier) -> Unit,
    sections: LazyListScope.() -> Unit,
) {
    GameDetailLayout(
        topBar = topBar,
        coverArt = { modifier, _ -> cover(modifier) },
        coverExtra = {},
        sections = sections,
    )
}

@Composable
private fun LandscapeLayout(
    coverWidth: Dp,
    isCompact: Boolean,
    topBar: @Composable () -> Unit,
    coverArt: @Composable (modifier: Modifier, isPortrait: Boolean) -> Unit,
    coverExtra: @Composable (isPortrait: Boolean) -> Unit,
    sections: LazyListScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left: Cover art column (scrollable)
            val coverShape = RoundedCornerShape(SpSpacing.CardCornerRadius)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(coverWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = SpSpacing.ScreenHorizontal,
                        end = SpSpacing.ScreenHorizontal,
                        top = SpSpacing.TopBarHeight,
                    ),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(SpSpacing.Small))
                Box(
                    modifier = Modifier
                        .width(coverWidth - SpSpacing.ScreenHorizontal * 2)
                        .shadow(8.dp, coverShape)
                        .clip(coverShape)
                        .border(1.dp, SpColor.Divider, coverShape),
                ) {
                    coverArt(Modifier.fillMaxWidth(), false)
                }
                Spacer(Modifier.height(SpSpacing.Medium))
                coverExtra(false)
                Spacer(Modifier.height(SpSpacing.XLarge))
            }

            // Right: Scrollable content
            val horizontalPad = if (isCompact) SpSpacing.Default else SpSpacing.XLarge
            val verticalPad = if (isCompact) SpSpacing.Medium else SpSpacing.XLarge
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("game_detail_content"),
                contentPadding = PaddingValues(
                    start = horizontalPad,
                    end = horizontalPad,
                    top = SpSpacing.TopBarHeight + verticalPad,
                    bottom = verticalPad,
                ),
            ) {
                sections()
            }
        }

        // Floating top bar overlaid on content
        topBar()
    }
}

@Composable
private fun PortraitLayout(
    topBar: @Composable () -> Unit,
    coverArt: @Composable (modifier: Modifier, isPortrait: Boolean) -> Unit,
    coverExtra: @Composable (isPortrait: Boolean) -> Unit,
    sections: LazyListScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("game_detail_content"),
            contentPadding = PaddingValues(top = SpSpacing.TopBarHeight),
        ) {
            item {
                val coverShape = RoundedCornerShape(SpSpacing.CardCornerRadius)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(SpSpacing.Small))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpSpacing.XXLarge)
                            .shadow(8.dp, coverShape)
                            .clip(coverShape)
                            .border(1.dp, SpColor.Divider, coverShape),
                    ) {
                        coverArt(Modifier.fillMaxWidth(), true)
                    }
                    Spacer(Modifier.height(SpSpacing.Medium))
                    coverExtra(true)
                    Spacer(Modifier.height(SpSpacing.XLarge))
                }
            }

            sections()

            item {
                Spacer(Modifier.height(SpSpacing.XXXLarge))
            }
        }

        // Floating top bar overlaid on content
        topBar()
    }
}
