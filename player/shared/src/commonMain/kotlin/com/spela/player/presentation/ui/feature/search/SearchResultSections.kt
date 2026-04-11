package com.spela.player.presentation.ui.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.spela.player.domain.model.SearchCollectionResult
import com.spela.player.domain.model.SearchConsoleResult
import com.spela.player.domain.model.SearchFranchiseResult
import com.spela.player.domain.model.SearchGameResult
import com.spela.player.domain.model.SearchSeriesResult
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpLinkText
import com.spela.player.presentation.ui.components.SpWideGameCard
import com.spela.player.presentation.ui.components.SpWideIconCard
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatRating

@Composable
fun SearchSectionHeader(
    title: String,
    total: Int,
    displayedCount: Int,
    isExpanded: Boolean = false,
    onSeeAll: (() -> Unit)? = null,
    onShowLess: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = SpTypography.TitleMedium,
            color = SpColor.OnBackground,
            modifier = Modifier.weight(1f),
        )
        when {
            isExpanded && onShowLess != null -> {
                SpLinkText(
                    text = "Show less",
                    onClick = onShowLess,
                    modifier = Modifier
                        .padding(
                            horizontal = SpSpacing.Small,
                            vertical = SpSpacing.XSmall,
                        )
                        .testTag("search_section_show_less_$title"),
                )
            }
            total > displayedCount && onSeeAll != null -> {
                SpLinkText(
                    text = "See all $total",
                    onClick = onSeeAll,
                    modifier = Modifier
                        .padding(
                            horizontal = SpSpacing.Small,
                            vertical = SpSpacing.XSmall,
                        )
                        .testTag("search_section_see_all_$title"),
                )
            }
            total > displayedCount -> {
                Text(
                    text = "$total total",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }
    }
}

@Composable
fun GameSearchResultItem(
    game: SearchGameResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SpWideGameCard(
            title = game.title,
            subtitle = game.consoleName,
            coverUrl = game.coverUrl,
            onClick = onClick,
            coverAspectRatio = game.coverAspectRatio,
            fillWidth = true,
            testTag = "search_result_game_${game.id}",
            extraContent = if (!game.developer.isNullOrBlank()) {
                {
                    Text(
                        text = game.developer,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else null,
        )
    }
}

@Composable
fun ConsoleSearchResultItem(
    console: SearchConsoleResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SpWideIconCard(
            title = console.name,
            subtitle = "${console.gameCount} games",
            onClick = onClick,
            testTag = "search_result_console_${console.id}",
            icon = {
                if (console.iconUrl.isNotBlank()) {
                    AsyncImage(
                        model = console.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Gamepad,
                        contentDescription = null,
                        tint = SpColor.OnBackgroundSecondary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
        )
    }
}

@Composable
fun CompanySearchResultItem(
    name: String,
    gameCount: Int,
    avgRating: Double,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SpWideIconCard(
            title = name,
            subtitle = "$gameCount games",
            onClick = onClick,
            testTag = "search_result_${label.lowercase()}_$name",
            icon = {
                Text(
                    text = name.take(2).uppercase(),
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnBackgroundSecondary,
                )
            },
            extraContent = if (avgRating > 0) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SpColor.Rating,
                            modifier = Modifier.size(SpSpacing.IconXSmall),
                        )
                        Text(
                            text = formatRating(avgRating),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            } else null,
        )
    }
}

@Composable
fun CollectionSearchResultItem(
    collection: SearchCollectionResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SpWideIconCard(
            title = collection.name,
            subtitle = "${collection.username} \u00B7 ${collection.gameCount} games",
            onClick = onClick,
            testTag = "search_result_collection_${collection.id}",
            icon = {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = SpColor.OnBackgroundSecondary,
                    modifier = Modifier.size(28.dp),
                )
            },
        )
    }
}

@Composable
fun GroupSearchResultItem(
    name: String,
    subtitle: String,
    avatarText: String,
    testTagPrefix: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SpWideIconCard(
            title = name,
            subtitle = subtitle,
            onClick = onClick,
            testTag = "search_result_${testTagPrefix}",
            icon = {
                Text(
                    text = avatarText,
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnBackgroundSecondary,
                )
            },
        )
    }
}

@Composable
fun SeriesSearchResultItem(
    series: SearchSeriesResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupSearchResultItem(
        name = series.name,
        subtitle = "${series.libraryGames} of ${series.totalGames} in library",
        avatarText = series.name.take(2).uppercase(),
        testTagPrefix = "series_${series.id}",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun FranchiseSearchResultItem(
    franchise: SearchFranchiseResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupSearchResultItem(
        name = franchise.name,
        subtitle = "${franchise.libraryGames} of ${franchise.totalGames} in library",
        avatarText = franchise.name.take(2).uppercase(),
        testTagPrefix = "franchise_${franchise.id}",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun SearchResultSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        SpShimmer(width = 80.dp, height = 16.dp)
        Spacer(Modifier.height(SpSpacing.Medium))
        repeat(4) {
            SpCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.XXSmall),
            ) {
                Row(
                    modifier = Modifier.padding(SpSpacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpShimmer(width = 40.dp, height = 54.dp)
                    Column {
                        SpShimmer(width = 160.dp, height = 14.dp)
                        Spacer(Modifier.height(SpSpacing.XSmall))
                        SpShimmer(width = 100.dp, height = 12.dp)
                    }
                }
            }
        }
    }
}
