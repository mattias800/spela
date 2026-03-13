package com.spela.player.presentation.ui.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.spela.player.domain.model.SearchCollectionResult
import com.spela.player.domain.model.SearchConsoleResult
import com.spela.player.domain.model.SearchFranchiseResult
import com.spela.player.domain.model.SearchGameResult
import com.spela.player.domain.model.SearchSeriesResult
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatRating

@Composable
fun SearchSectionHeader(
    title: String,
    total: Int,
    displayedCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = SpTypography.TitleMedium,
            color = SpColor.OnBackground,
            modifier = Modifier.weight(1f),
        )
        if (total > displayedCount) {
            Text(
                text = "$total total",
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
    }
}

@Composable
fun GameSearchResultItem(
    game: SearchGameResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .spFocusRing(shape = RoundedCornerShape(SpSpacing.RadiusDefault))
            .clip(RoundedCornerShape(SpSpacing.RadiusDefault))
            .clickable(onClick = onClick)
            .focusable()
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Small,
            )
            .testTag("search_result_game_${game.id}")
            .semantics {
                contentDescription = "${game.title}, ${game.consoleName}"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        SpCoverArt(
            imageUrl = game.coverUrl,
            contentDescription = "${game.title} cover",
            modifier = Modifier
                .width(40.dp)
                .height(54.dp),
            aspectRatio = game.coverAspectRatio,
            cornerRadius = SpSpacing.RadiusSmall,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
            ) {
                Text(
                    text = game.consoleName,
                    style = SpTypography.LabelSmall,
                    color = SpColor.Primary,
                    maxLines = 1,
                )
                if (!game.developer.isNullOrBlank()) {
                    Text(
                        text = "\u00B7",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                    Text(
                        text = game.developer,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun ConsoleSearchResultItem(
    console: SearchConsoleResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .spFocusRing(shape = RoundedCornerShape(SpSpacing.RadiusDefault))
            .clip(RoundedCornerShape(SpSpacing.RadiusDefault))
            .clickable(onClick = onClick)
            .focusable()
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Small,
            )
            .testTag("search_result_console_${console.id}")
            .semantics {
                contentDescription = "${console.name}, ${console.gameCount} games"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
                .background(SpColor.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (console.iconUrl.isNotBlank()) {
                AsyncImage(
                    model = console.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Gamepad,
                    contentDescription = null,
                    tint = SpColor.OnBackgroundSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = console.name,
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${console.gameCount} games",
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .spFocusRing(shape = RoundedCornerShape(SpSpacing.RadiusDefault))
            .clip(RoundedCornerShape(SpSpacing.RadiusDefault))
            .clickable(onClick = onClick)
            .focusable()
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Small,
            )
            .testTag("search_result_${label.lowercase()}_$name")
            .semantics {
                contentDescription = "$name, $gameCount games"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
                .background(SpColor.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(2).uppercase(),
                style = SpTypography.LabelMedium,
                color = SpColor.OnBackgroundSecondary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
            ) {
                Text(
                    text = "$gameCount games",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                )
                if (avgRating > 0) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = SpColor.Rating,
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = formatRating(avgRating),
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionSearchResultItem(
    collection: SearchCollectionResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .spFocusRing(shape = RoundedCornerShape(SpSpacing.RadiusDefault))
            .clip(RoundedCornerShape(SpSpacing.RadiusDefault))
            .clickable(onClick = onClick)
            .focusable()
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Small,
            )
            .testTag("search_result_collection_${collection.id}")
            .semantics {
                contentDescription = "${collection.name}, ${collection.gameCount} games"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
                .background(SpColor.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = SpColor.OnBackgroundSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = collection.name,
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
            ) {
                Text(
                    text = collection.username,
                    style = SpTypography.LabelSmall,
                    color = SpColor.Primary,
                )
                Text(
                    text = "\u00B7 ${collection.gameCount} games",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .spFocusRing(shape = RoundedCornerShape(SpSpacing.RadiusDefault))
            .clip(RoundedCornerShape(SpSpacing.RadiusDefault))
            .clickable(onClick = onClick)
            .focusable()
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Small,
            )
            .testTag("search_result_${testTagPrefix}")
            .semantics {
                contentDescription = "$name, $subtitle"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
                .background(SpColor.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatarText,
                style = SpTypography.LabelMedium,
                color = SpColor.OnBackgroundSecondary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal),
    ) {
        // Mimic a section header
        SpShimmer(width = 80.dp, height = 16.dp)
        Spacer(Modifier.height(SpSpacing.Medium))

        // Mimic several result rows
        repeat(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                SpShimmer(width = 40.dp, height = 54.dp)
                Column(modifier = Modifier.weight(1f)) {
                    SpShimmer(width = 160.dp, height = 14.dp)
                    Spacer(Modifier.height(SpSpacing.XSmall))
                    SpShimmer(width = 100.dp, height = 12.dp)
                }
            }
        }

        Spacer(Modifier.height(SpSpacing.Large))

        // Second section
        SpShimmer(width = 100.dp, height = 16.dp)
        Spacer(Modifier.height(SpSpacing.Medium))

        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                SpShimmer(width = 40.dp, height = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    SpShimmer(width = 140.dp, height = 14.dp)
                    Spacer(Modifier.height(SpSpacing.XSmall))
                    SpShimmer(width = 80.dp, height = 12.dp)
                }
            }
        }
    }
}
