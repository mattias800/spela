package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.GameFranchiseLink
import com.spela.player.domain.model.GameSeriesLink
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Shows "Part of [Series]" and "Part of [Franchise]" links on the game detail screen.
 * Only renders when series or franchise data is available.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesFranchiseSection(
    series: List<GameSeriesLink>,
    franchises: List<GameFranchiseLink>,
    onSeriesSelected: ((seriesId: String, seriesName: String) -> Unit)? = null,
    onFranchiseSelected: ((franchiseId: String, franchiseName: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (series.isEmpty() && franchises.isEmpty()) return

    SpTitledSection(
        title = "Series & Franchises",
        icon = Icons.AutoMirrored.Filled.List,
        modifier = modifier.testTag("series_franchise_section"),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            series.forEach { seriesLink ->
                SpChip(
                    text = "Part of ${seriesLink.name} (${seriesLink.libraryGames}/${seriesLink.totalGames})",
                    onClick = if (onSeriesSelected != null) {
                        { onSeriesSelected(seriesLink.id, seriesLink.name) }
                    } else null,
                    color = SpColor.Primary,
                    onGradient = true,
                    modifier = Modifier.testTag("series_link_${seriesLink.id}"),
                )
            }
            franchises.forEach { franchiseLink ->
                SpChip(
                    text = "Part of ${franchiseLink.name} (${franchiseLink.gameCount} games)",
                    onClick = if (onFranchiseSelected != null) {
                        { onFranchiseSelected(franchiseLink.id, franchiseLink.name) }
                    } else null,
                    color = SpColor.Accent,
                    onGradient = true,
                    modifier = Modifier.testTag("franchise_link_${franchiseLink.id}"),
                )
            }
        }
        Spacer(Modifier.height(SpSpacing.Medium))
    }
}
