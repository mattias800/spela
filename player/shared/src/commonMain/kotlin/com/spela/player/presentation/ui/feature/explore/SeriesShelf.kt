package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.FeaturedSeries
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Series gradient colors -- each series gets a distinctive gradient.
 */
private val seriesGradients = listOf(
    listOf(Color(0xFF1B5E20), Color(0xFF2E7D32)),  // Forest green
    listOf(Color(0xFF0D47A1), Color(0xFF1565C0)),  // Deep blue
    listOf(Color(0xFF4A148C), Color(0xFF6A1B9A)),  // Deep purple
    listOf(Color(0xFFBF360C), Color(0xFFD84315)),  // Deep orange
    listOf(Color(0xFF006064), Color(0xFF00838F)),  // Teal
    listOf(Color(0xFF880E4F), Color(0xFFAD1457)),  // Pink
    listOf(Color(0xFF1A237E), Color(0xFF283593)),  // Indigo
    listOf(Color(0xFFE65100), Color(0xFFEF6C00)),  // Orange
)

@Composable
fun SeriesShelf(
    series: List<FeaturedSeries>,
    onSeriesSelected: (seriesId: String, seriesName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        itemCount = series.size,
        modifier = modifier.testTag("series_shelf"),
    ) { index, focusRequester ->
        val item = series[index]
        val gradientIndex = index % seriesGradients.size
        Box(modifier = Modifier.focusRequester(focusRequester)) {
            SeriesCard(
                series = item,
                gradientColors = seriesGradients[gradientIndex],
                onClick = { onSeriesSelected(item.id, item.name) },
            )
        }
    }
}

@Composable
private fun SeriesCard(
    series: FeaturedSeries,
    gradientColors: List<Color>,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)

    SpCard(
        modifier = Modifier
            .width(200.dp)
            .height(100.dp)
            .testTag("series_card_${series.id}")
            .semantics {
                contentDescription = "${series.name}, ${series.libraryGames} of ${series.totalGames} games across ${series.consoleCount} consoles"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(shape)
                .background(Brush.linearGradient(gradientColors)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(SpSpacing.Medium),
            ) {
                Text(
                    text = series.name,
                    style = SpTypography.TitleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "${series.libraryGames}/${series.totalGames} games",
                    style = SpTypography.LabelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
                if (series.consoleCount > 0) {
                    Text(
                        text = "across ${series.consoleCount} consoles",
                        style = SpTypography.LabelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesShelfSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 4,
) {
    LazyRow(
        modifier = modifier.testTag("series_shelf_skeleton"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(count) {
            com.spela.player.presentation.ui.components.SpShimmer(
                modifier = Modifier
                    .width(200.dp)
                    .clip(RoundedCornerShape(SpSpacing.CardCornerRadius)),
                width = 200.dp,
                height = 100.dp,
            )
        }
    }
}
