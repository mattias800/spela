package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Defines the structural layout for a game detail screen.
 *
 * This composable owns the responsive portrait/landscape decision and the
 * placement of the cover, top bar, and scrollable content — but not the
 * content itself. Both the real screen and its loading skeleton can share
 * this layout so the transition between them is seamless.
 *
 * @param topBar Composable rendered over the cover area (typically [SpTopBar] with back button).
 * @param cover  Composable for the hero cover image area. Receives a [Modifier] pre-configured
 *               with the correct fill behaviour for the current orientation.
 * @param sections Builder lambda that adds [LazyListScope] items for the scrollable content
 *                 below (portrait) or beside (landscape) the cover.
 */
@Composable
fun GameDetailLayout(
    topBar: @Composable () -> Unit,
    cover: @Composable (Modifier) -> Unit,
    sections: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        val constraintsMaxWidth = maxWidth
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            val isSmallLandscape = maxHeight < 500.dp
            val coverWidth = if (isSmallLandscape) {
                constraintsMaxWidth * 0.3f
            } else {
                constraintsMaxWidth * 0.4f
            }
            LandscapeLayout(
                coverWidth = coverWidth,
                isCompact = isSmallLandscape,
                topBar = topBar,
                cover = cover,
                sections = sections,
            )
        } else {
            PortraitLayout(
                topBar = topBar,
                cover = cover,
                sections = sections,
            )
        }
    }
}

@Composable
private fun LandscapeLayout(
    coverWidth: androidx.compose.ui.unit.Dp,
    isCompact: Boolean,
    topBar: @Composable () -> Unit,
    cover: @Composable (Modifier) -> Unit,
    sections: LazyListScope.() -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left: Cover art
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(coverWidth),
        ) {
            cover(Modifier.fillMaxSize())
            topBar()
        }
        // Right: Scrollable content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("game_detail_content"),
            contentPadding = if (isCompact) {
                PaddingValues(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium)
            } else {
                PaddingValues(SpSpacing.XLarge)
            },
        ) {
            sections()
        }
    }
}

@Composable
private fun PortraitLayout(
    topBar: @Composable () -> Unit,
    cover: @Composable (Modifier) -> Unit,
    sections: LazyListScope.() -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("game_detail_content")) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                cover(Modifier.fillMaxSize())
                topBar()
            }
        }

        sections()

        item {
            Spacer(Modifier.height(SpSpacing.XXXLarge))
        }
    }
}
