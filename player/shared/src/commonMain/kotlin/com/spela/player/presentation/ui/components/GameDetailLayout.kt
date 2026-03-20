package com.spela.player.presentation.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Defines the structural layout for a game detail screen.
 *
 * Uses a side-by-side hero pattern matching the web UI:
 * - **Landscape**: Cover art on the LEFT, info/sections on the RIGHT in a Row.
 *   [fullWidthSections] render below the Row at full width.
 *   Both scroll together in one unified scroll container.
 * - **Portrait**: Cover art centered above, all sections below in a single column.
 *
 * The top bar floats over content with a transparent background.
 *
 * @param topBar Composable rendered floating over content.
 * @param coverArt Composable for the cover art image.
 * @param coverExtra Optional composable rendered below the cover art.
 * @param backgroundColors Gradient colors for the screen background.
 * @param sections Content rendered beside the cover art in landscape (right column).
 * @param fullWidthSections Content rendered below the hero row at full width in landscape.
 *        In portrait, rendered after [sections] in the same column.
 */
@Composable
fun GameDetailLayout(
    topBar: @Composable () -> Unit,
    coverArt: @Composable (modifier: Modifier, isPortrait: Boolean) -> Unit,
    coverExtra: @Composable (isPortrait: Boolean) -> Unit = {},
    backgroundColors: List<Color> = listOf(SpColor.Background, SpColor.Background),
    sections: @Composable () -> Unit,
    fullWidthSections: @Composable () -> Unit = {},
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
                fullWidthSections = fullWidthSections,
            )
        } else {
            PortraitLayout(
                screenHeight = maxHeight,
                topBar = topBar,
                coverArt = coverArt,
                coverExtra = coverExtra,
                sections = sections,
                fullWidthSections = fullWidthSections,
            )
        }
    }
}

@Composable
private fun LandscapeLayout(
    coverWidth: Dp,
    isCompact: Boolean,
    topBar: @Composable () -> Unit,
    coverArt: @Composable (modifier: Modifier, isPortrait: Boolean) -> Unit,
    coverExtra: @Composable (isPortrait: Boolean) -> Unit,
    sections: @Composable () -> Unit,
    fullWidthSections: @Composable () -> Unit,
) {
    val verticalPad = if (isCompact) SpSpacing.Medium else SpSpacing.XLarge

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_detail_content"),
            contentPadding = PaddingValues(
                top = SpSpacing.TopBarHeight + LocalTitleBarInset.current + verticalPad,
                bottom = verticalPad,
            ),
        ) {
            // Hero row: cover art (left) + sections (right)
            item {
                val coverShape = RoundedCornerShape(SpSpacing.CardCornerRadius)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SpSpacing.ScreenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXLarge),
                ) {
                    // Left: Cover art column
                    Column(
                        modifier = Modifier.width(coverWidth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, coverShape)
                                .clip(coverShape)
                                .border(1.dp, SpColor.Divider, coverShape),
                        ) {
                            coverArt(Modifier.fillMaxWidth(), false)
                        }
                        Spacer(Modifier.height(SpSpacing.XLarge))
                        coverExtra(false)
                    }

                    // Right: Content column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = SpSpacing.ScreenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
                    ) {
                        sections()
                    }
                }
            }

            // Full-width sections below the hero row
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpSpacing.ScreenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
                ) {
                    fullWidthSections()
                }
            }
        }

        // Floating top bar overlaid on content
        topBar()
    }
}

@Composable
private fun PortraitLayout(
    screenHeight: Dp,
    topBar: @Composable () -> Unit,
    coverArt: @Composable (modifier: Modifier, isPortrait: Boolean) -> Unit,
    coverExtra: @Composable (isPortrait: Boolean) -> Unit,
    sections: @Composable () -> Unit,
    fullWidthSections: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("game_detail_content"),
            contentPadding = PaddingValues(top = SpSpacing.TopBarHeight + LocalTitleBarInset.current),
        ) {
            item {
                val coverShape = RoundedCornerShape(SpSpacing.CardCornerRadius)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(SpSpacing.Small))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = screenHeight * 0.5f)
                            .padding(horizontal = SpSpacing.XXLarge)
                            .shadow(8.dp, coverShape)
                            .clip(coverShape)
                            .border(1.dp, SpColor.Divider, coverShape),
                    ) {
                        coverArt(Modifier.fillMaxWidth(), true)
                    }
                    Spacer(Modifier.height(SpSpacing.Medium))
                    coverExtra(true)
                    Spacer(Modifier.height(SpSpacing.Large))
                }
            }

            item {
                Spacer(Modifier.height(SpSpacing.Large))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpSpacing.ScreenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
                ) {
                    sections()
                    fullWidthSections()
                }
            }

            item {
                Spacer(Modifier.height(SpSpacing.XXXLarge))
            }
        }

        // Floating top bar overlaid on content
        topBar()
    }
}
