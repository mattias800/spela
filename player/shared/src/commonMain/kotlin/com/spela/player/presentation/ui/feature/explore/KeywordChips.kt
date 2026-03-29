package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Keyword
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpSpacing

@Composable
fun KeywordChips(
    keywords: List<Keyword>,
    onKeywordSelected: (keywordId: String, keywordName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        modifier = modifier.testTag("keyword_chips"),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        items(keywords, key = { it.id }) { keyword ->
            SpChip(
                text = "${keyword.name} (${keyword.gameCount})",
                onClick = { onKeywordSelected(keyword.id, keyword.name) },
                modifier = Modifier.testTag("keyword_chip_${keyword.id}"),
            )
        }
    }
}

@Composable
fun KeywordChipsSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 6,
) {
    LazyRow(
        modifier = modifier.testTag("keyword_chips_skeleton"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        items(count) {
            SpShimmer(
                width = 100.dp,
                height = 32.dp,
            )
        }
    }
}
