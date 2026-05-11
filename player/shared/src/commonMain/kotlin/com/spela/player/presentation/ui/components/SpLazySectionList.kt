package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Lazy-virtualised counterpart to [SpSectionList].
 *
 * Use this when a screen has many sections (e.g. Explore's 19
 * sections of carousels, each with its own data flow + cover-image
 * grid). The [Column] used by [SpSectionList] composes every section
 * up-front, which on Explore meant hundreds of game-card composables
 * materialising in a single composition pass on first paint.
 *
 * Same visual contract as [SpSectionList] (an [SpSpacing.Large]
 * vertical gap between items), but only sections within the viewport
 * actually compose; off-screen sections defer until scrolled to.
 *
 * Callers wrap each section in `item { ... }`:
 *
 *   SpLazySectionList {
 *     item { SearchBar(...) }
 *     item { ExploreSection(title = "...") }
 *     ...
 *   }
 *
 * Migrate from [SpSectionList] when the screen has 5+ sections OR
 * any section eagerly composes heavy content (cover-art grids, image
 * carousels). For small fixed-layout screens (3-4 simple sections),
 * the plain [SpSectionList] is fine and the LazyColumn overhead isn't
 * worth it.
 */
@Composable
fun SpLazySectionList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
        contentPadding = contentPadding,
        content = content,
    )
}
