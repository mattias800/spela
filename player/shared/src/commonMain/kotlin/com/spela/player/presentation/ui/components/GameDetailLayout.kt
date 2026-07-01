package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.spela.player.presentation.ui.gamepad.LazyListCenterInfo
import com.spela.player.presentation.ui.gamepad.LocalLazyListCenterInfo
import com.spela.player.presentation.ui.gamepad.RightStickScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Defines the structural layout for a game detail screen.
 *
 * Uses a side-by-side hero pattern matching the web UI:
 * - **Landscape**: Cover art on the LEFT, info/sections on the RIGHT in a Row.
 *   [fullWidthSections] render below the Row at full width.
 *   Both scroll together in one unified scroll container.
 * - **Portrait**: Hero banner at the top with cover art + title overlay,
 *   all sections below in a single column.
 *
 * The top bar floats over content with a transparent background.
 *
 * @param topBar Composable rendered floating over content.
 * @param coverArt Composable for the cover art image.
 * @param coverExtra Optional composable rendered below the cover art.
 * @param heroUrl URL for the hero banner background image (portrait only).
 * @param heroContent Composable rendered next to the cover art in the hero banner (portrait only).
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
    heroUrl: String? = null,
    heroContent: @Composable () -> Unit = {},
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
        val isLandscape = maxWidth > maxHeight && maxWidth > 700.dp

        if (isLandscape) {
            val isSmallLandscape = maxHeight < 500.dp
            // #1109 — middle ground between #1097's bigger sizes and the
            // pre-#1097 originals. Closer to original than to #1097.
            // Compact branch (AYN Thor in landscape, ~700 dp tall) stays
            // conservative so vertical real estate isn't eaten by the hero.
            val coverWidth = if (isSmallLandscape) 240.dp else 320.dp
            LandscapeLayout(
                coverWidth = coverWidth,
                isCompact = isSmallLandscape,
                topBar = topBar,
                coverArt = coverArt,
                coverExtra = coverExtra,
                heroUrl = heroUrl,
                heroContent = heroContent,
                backgroundColors = backgroundColors,
                sections = sections,
                fullWidthSections = fullWidthSections,
            )
        } else {
            PortraitLayout(
                screenHeight = maxHeight,
                topBar = topBar,
                coverArt = coverArt,
                coverExtra = coverExtra,
                heroUrl = heroUrl,
                heroContent = heroContent,
                backgroundColors = backgroundColors,
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
    heroUrl: String? = null,
    heroContent: @Composable () -> Unit = {},
    backgroundColors: List<Color> = listOf(SpColor.Background, SpColor.Background),
    sections: @Composable () -> Unit,
    fullWidthSections: @Composable () -> Unit,
) {
    val verticalPad = if (isCompact) SpSpacing.Medium else SpSpacing.XLarge
    // #1109 — banner sized to host the cover (max-height 240/300 dp
    // depending on isCompact) plus top/bottom padding. Middle ground
    // between #1097 (280/360) and pre-#1097 (200 fixed).
    val coverMaxHeight = if (isCompact) 240.dp else 300.dp
    val bannerHeight = coverMaxHeight + SpSpacing.TopBarHeight + SpSpacing.Large

    // Vertical centring of focused descendants (see #1180/#1182).
    // gamepadFocusable.centerOnFocus reads LocalLazyListCenterInfo to
    // scroll this LazyColumn so the focused element (a card inside a
    // carousel, a session row, a Top Player, a metadata link) lands
    // at the viewport's vertical centre. Without this provider the
    // centerOnFocus branch no-ops on this screen.
    val landscapeListState = rememberLazyListState()
    val landscapeCenterInfo = remember(landscapeListState) { LazyListCenterInfo(landscapeListState) }
    CompositionLocalProvider(LocalLazyListCenterInfo provides landscapeCenterInfo) {
    // Continuous right-stick scrolling in gamepad mode (#1362).
    RightStickScroll(landscapeListState)
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = landscapeListState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_detail_content")
                .onGloballyPositioned { coordinates ->
                    landscapeCenterInfo.containerTopInRoot = coordinates.positionInRoot().y
                },
            contentPadding = PaddingValues(bottom = verticalPad),
        ) {
            // Hero banner — full-width with hero art
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = bannerHeight),
                ) {
                    // Background: hero image pinned to banner height (doesn't grow with content)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bannerHeight)
                            .align(Alignment.TopStart),
                    ) {
                        if (heroUrl != null) {
                            SpImage(
                                model = heroUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                staggerMs = 0L,
                                placeholder = {
                                    Box(modifier = Modifier.fillMaxSize().drawBehind {
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        val d = (size.width + size.height) * 0.25f
                                        drawRect(brush = Brush.linearGradient(backgroundColors, Offset(cx - d, cy - d), Offset(cx + d, cy + d)))
                                    })
                                },
                                error = {
                                    Box(modifier = Modifier.fillMaxSize().drawBehind {
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        val d = (size.width + size.height) * 0.25f
                                        drawRect(brush = Brush.linearGradient(backgroundColors, Offset(cx - d, cy - d), Offset(cx + d, cy + d)))
                                    })
                                },
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().drawBehind {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val d = (size.width + size.height) * 0.25f
                                drawRect(brush = Brush.linearGradient(backgroundColors, Offset(cx - d, cy - d), Offset(cx + d, cy + d)))
                            })
                        }

                        // Gradient overlay
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.4f to SpColor.Background.copy(alpha = 0.3f),
                                        1.0f to SpColor.Background,
                                    ),
                                ),
                            ),
                        )
                    }

                    // Content: cover art (left) + title/badges (right) in contrast backdrop
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = SpSpacing.TopBarHeight + SpSpacing.Large,
                                start = SpSpacing.XLarge,
                                end = SpSpacing.XLarge,
                                bottom = SpSpacing.Large,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Large),
                    ) {
                        // Cover art — max width and height, dynamic sizing, no crop
                        val coverShape = RoundedCornerShape(SpSpacing.CardCornerRadius)
                        Box(
                            modifier = Modifier
                                .widthIn(max = coverWidth)
                                .heightIn(max = coverMaxHeight)
                                .clip(coverShape),
                        ) {
                            coverArt(Modifier.fillMaxWidth(), false)
                        }

                        // Title + badges in contrast backdrop
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    Color.Black.copy(alpha = 0.4f),
                                    RoundedCornerShape(SpSpacing.CardCornerRadius),
                                )
                                .padding(SpSpacing.Large),
                        ) {
                            heroContent()
                        }
                    }
                }
            }

            // Content sections below the banner
            item {
                Spacer(Modifier.height(SpSpacing.Large))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpSpacing.ScreenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXLarge),
                ) {
                    // Left: cover extra (rating, etc.)
                    Column(
                        modifier = Modifier.width(coverWidth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        coverExtra(false)
                    }

                    // Right: Content column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
                    ) {
                        sections()
                    }
                }
            }

            // Full-width sections below
            item {
                Spacer(Modifier.height(SpSpacing.Large))
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
}

@Composable
private fun PortraitLayout(
    screenHeight: Dp,
    topBar: @Composable () -> Unit,
    coverArt: @Composable (modifier: Modifier, isPortrait: Boolean) -> Unit,
    coverExtra: @Composable (isPortrait: Boolean) -> Unit,
    heroUrl: String?,
    heroContent: @Composable () -> Unit,
    backgroundColors: List<Color>,
    sections: @Composable () -> Unit,
    fullWidthSections: @Composable () -> Unit,
) {
    // Banner height is dynamic — wraps content (cover art + info box)

    val portraitListState = rememberLazyListState()
    val portraitCenterInfo = remember(portraitListState) { LazyListCenterInfo(portraitListState) }
    CompositionLocalProvider(LocalLazyListCenterInfo provides portraitCenterInfo) {
    // Continuous right-stick scrolling in gamepad mode (#1362).
    RightStickScroll(portraitListState)
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = portraitListState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_detail_content")
                .onGloballyPositioned { coordinates ->
                    portraitCenterInfo.containerTopInRoot = coordinates.positionInRoot().y
                },
        ) {
            // Hero banner item
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Background: hero image — sized by the content Column below via matchParentSize
                    if (heroUrl != null) {
                        SpImage(
                            model = heroUrl,
                            contentDescription = "Hero banner",
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                            staggerMs = 0L,
                            placeholder = {
                                Box(modifier = Modifier.fillMaxSize().background(SpColor.Background))
                            },
                            error = {
                                Box(modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(listOf(
                                        backgroundColors.first().copy(alpha = 0.5f),
                                        backgroundColors.last(),
                                    ))
                                ))
                            },
                        )
                    } else {
                        Box(modifier = Modifier.matchParentSize().background(
                            Brush.verticalGradient(listOf(
                                backgroundColors.first().copy(alpha = 0.5f),
                                backgroundColors.last(),
                            ))
                        ))
                    }

                    // Gradient overlay
                    Box(modifier = Modifier.matchParentSize().background(
                        Brush.verticalGradient(colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.4f to backgroundColors.last().copy(alpha = 0.3f),
                            1.0f to backgroundColors.last(),
                        ))
                    ))

                    // Content: cover art centered above, info below
                    // This Column defines the banner's height (wraps content)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = SpSpacing.TopBarHeight + SpSpacing.Large,
                                start = SpSpacing.XLarge,
                                end = SpSpacing.XLarge,
                                bottom = SpSpacing.Large,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        // Cover art centered. #1109 — middle ground
                        // (290 dp) between pre-#1097 (250 dp) and #1097
                        // (340 dp), closer to the smaller side per user
                        // feedback that 340 was too dominant.
                        val coverShape = RoundedCornerShape(SpSpacing.CardCornerRadius)
                        Box(
                            modifier = Modifier
                                .widthIn(max = 290.dp)
                                .shadow(12.dp, coverShape)
                                .clip(coverShape)
                                .border(2.dp, Color.White.copy(alpha = 0.15f), coverShape),
                        ) {
                            coverArt(Modifier.fillMaxWidth(), true)
                        }

                        // Title and badges below cover
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color.Black.copy(alpha = 0.4f),
                                    RoundedCornerShape(SpSpacing.CardCornerRadius),
                                )
                                .padding(SpSpacing.Medium),
                        ) {
                            heroContent()
                        }
                    }
                }
            }

            // Sections below the banner
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                        .padding(top = SpSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
                ) {
                    coverExtra(true)
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
}
