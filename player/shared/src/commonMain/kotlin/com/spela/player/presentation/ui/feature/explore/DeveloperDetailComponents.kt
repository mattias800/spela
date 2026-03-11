package com.spela.player.presentation.ui.feature.explore

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.domain.model.CompanyInfo
import com.spela.player.domain.model.DeveloperDetail
import com.spela.player.domain.model.DeveloperDetailPublisher
import com.spela.player.domain.model.DeveloperDetailUserStats
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpHeroCover
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatPlayTime
import com.spela.player.util.formatRating

// --- (a) Hero Banner ---

@Composable
internal fun DeveloperHeroBanner(
    detail: DeveloperDetail,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        if (detail.heroUrl != null) {
            SpHeroCover(
                imageUrl = detail.heroUrl,
                contentDescription = "${detail.name} hero image",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Dark gradient fallback
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                SpColor.Primary.copy(alpha = 0.3f),
                                SpColor.Background,
                            ),
                        ),
                    ),
            )
        }

        // Content overlaid on bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = SpSpacing.ScreenHorizontal)
                .padding(bottom = SpSpacing.Medium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                // Company logo or letter avatar fallback
                val logoUrl = detail.companyInfo?.logoUrl
                if (logoUrl != null) {
                    SubcomposeAsyncImage(
                        model = logoUrl,
                        contentDescription = "${detail.name} logo",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .testTag("developer_company_logo"),
                        contentScale = ContentScale.Fit,
                        loading = {
                            LetterAvatar(detail.name)
                        },
                        error = {
                            LetterAvatar(detail.name)
                        },
                    )
                } else {
                    LetterAvatar(
                        name = detail.name,
                        modifier = Modifier.testTag("developer_letter_avatar"),
                    )
                }

                Column {
                    Text(
                        text = detail.name,
                        style = SpTypography.DisplaySmall,
                        color = SpColor.OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("developer_hero_name"),
                    )
                }
            }

            Spacer(Modifier.height(SpSpacing.Small))

            // Stats row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                modifier = Modifier.testTag("developer_stats_row"),
            ) {
                Text(
                    text = "${detail.gameCount} games",
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundSecondary,
                    modifier = Modifier.testTag("developer_game_count"),
                )
                if (detail.avgRating > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SpColor.Rating,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = formatRating(detail.avgRating),
                            style = SpTypography.BodyMedium,
                            color = SpColor.OnBackgroundSecondary,
                            modifier = Modifier.testTag("developer_avg_rating"),
                        )
                    }
                }
                if (detail.consoles.isNotEmpty()) {
                    Text(
                        text = "${detail.consoles.size} platforms",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundSecondary,
                        modifier = Modifier.testTag("developer_platform_count"),
                    )
                }
            }
        }
    }
}

// --- (b) Top Rated Row ---

@Composable
internal fun DeveloperTopRatedRow(
    topGames: List<Game>,
    onGameSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = SpSpacing.Large)) {
        Text(
            text = "Top Rated",
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground,
            modifier = Modifier
                .padding(horizontal = SpSpacing.ScreenHorizontal)
                .testTag("developer_top_rated_header"),
        )
        Spacer(Modifier.height(SpSpacing.Medium))
        LazyRow(
            contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            items(
                items = topGames,
                key = { "top_${it.id}" },
            ) { game ->
                TopRatedGameCard(
                    game = game,
                    onClick = { onGameSelected(game.id) },
                )
            }
        }
        Spacer(Modifier.height(SpSpacing.Large))
    }
}

@Composable
internal fun TopRatedGameCard(
    game: Game,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .width(SpSpacing.CoverMediumWidth)
            .testTag("developer_top_game_${game.id}")
            .semantics {
                contentDescription = "${game.title}, rated ${formatRating(game.rating)}"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Column {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover art",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = SpSpacing.RadiusLarge, topEnd = SpSpacing.RadiusLarge)),
                cornerRadius = 0.dp,
            )
            Column(
                modifier = Modifier.padding(SpSpacing.Small),
            ) {
                Text(
                    text = game.title,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (game.rating > 0) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SpColor.Rating,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = formatRating(game.rating),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }
        }
    }
}

// --- (c) Genre Breakdown ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeveloperGenreBreakdown(
    genres: List<Pair<String, Int>>,
    totalGames: Int,
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = SpSpacing.ScreenHorizontal),
    ) {
        Text(
            text = "Genres",
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground,
            modifier = Modifier.testTag("developer_genre_header"),
        )
        Spacer(Modifier.height(SpSpacing.Medium))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            // "All" chip
            SpChip(
                text = "All ($totalGames)",
                onClick = { onGenreSelected(null) },
                isSelected = selectedGenre == null,
                modifier = Modifier
                    .testTag("developer_genre_chip_all")
                    .semantics {
                        contentDescription = "All, $totalGames games"
                        role = Role.Button
                    },
            )

            genres.forEach { (genre, count) ->
                val isSelected = genre.equals(selectedGenre, ignoreCase = true)
                SpChip(
                    text = "$genre ($count)",
                    onClick = { onGenreSelected(if (isSelected) null else genre) },
                    isSelected = isSelected,
                    modifier = Modifier
                        .testTag("developer_genre_chip_$genre")
                        .semantics {
                            contentDescription = "$genre, $count games"
                            role = Role.Button
                        },
                )
            }
        }
        Spacer(Modifier.height(SpSpacing.Large))
    }
}

// --- (d) User Stats Card ---

@Composable
internal fun DeveloperUserStatsCard(
    userStats: DeveloperDetailUserStats,
    totalGames: Int,
    onGameSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = SpSpacing.ScreenHorizontal),
    ) {
        Text(
            text = "Your Stats",
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground,
            modifier = Modifier.testTag("developer_user_stats_header"),
        )
        Spacer(Modifier.height(SpSpacing.Medium))
        SpCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("developer_user_stats_card"),
        ) {
            Column(
                modifier = Modifier.padding(SpSpacing.Default),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    UserStatItem(
                        icon = Icons.Filled.Timer,
                        value = formatPlayTime(userStats.totalPlayTime),
                        label = "Play time",
                        modifier = Modifier.testTag("developer_user_stat_playtime"),
                    )
                    UserStatItem(
                        icon = Icons.Filled.SportsEsports,
                        value = "${userStats.gamesPlayed}/$totalGames",
                        label = "Played",
                        modifier = Modifier.testTag("developer_user_stat_played"),
                    )
                    UserStatItem(
                        icon = Icons.Filled.Favorite,
                        value = "${userStats.favoriteCount}",
                        label = "Favorites",
                        modifier = Modifier.testTag("developer_user_stat_favorites"),
                    )
                }

                if (userStats.mostPlayedGame != null) {
                    Spacer(Modifier.height(SpSpacing.Medium))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        Text(
                            text = "Most played:",
                            style = SpTypography.LabelMedium,
                            color = SpColor.OnBackgroundTertiary,
                        )
                        SpCard(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("developer_most_played_game"),
                            onClick = { onGameSelected(userStats.mostPlayedGame.id) },
                        ) {
                            Row(
                                modifier = Modifier.padding(SpSpacing.Small),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                            ) {
                                SpCoverArt(
                                    imageUrl = userStats.mostPlayedGame.coverUrl,
                                    contentDescription = "${userStats.mostPlayedGame.title} cover",
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(SpSpacing.RadiusSmall)),
                                    aspectRatio = 0.75f,
                                )
                                Text(
                                    text = userStats.mostPlayedGame.title,
                                    style = SpTypography.TitleSmall,
                                    color = SpColor.OnCard,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(SpSpacing.Large))
    }
}

@Composable
internal fun UserStatItem(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpColor.Primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(SpSpacing.XSmall))
        Text(
            text = value,
            style = SpTypography.TitleLarge,
            color = SpColor.OnBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

// --- Game Item ---

@Composable
internal fun DeveloperGameItem(
    game: Game,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.XSmall,
            )
            .testTag("developer_game_${game.id}")
            .semantics {
                contentDescription = "${game.title}, ${game.consoleName}"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover art",
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(SpSpacing.RadiusSmall)),
                aspectRatio = 0.75f,
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = game.title,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(SpSpacing.XXSmall))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    Text(
                        text = game.consoleName,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )

                    if (game.rating > 0) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SpColor.Rating,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = formatRating(game.rating),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }
        }
    }
}

// --- (f) Publishers Section ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeveloperPublishersSection(
    publishers: List<DeveloperDetailPublisher>,
    onPublisherSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = SpSpacing.ScreenHorizontal)
            .padding(top = SpSpacing.Large),
    ) {
        Text(
            text = "Publishers",
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground,
            modifier = Modifier.testTag("developer_publishers_header"),
        )
        Spacer(Modifier.height(SpSpacing.Medium))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            publishers.forEach { publisher ->
                SpChip(
                    text = "${publisher.name} (${publisher.count})",
                    onClick = { onPublisherSelected(publisher.name) },
                    modifier = Modifier
                        .testTag("developer_publisher_chip_${publisher.name}")
                        .semantics {
                            contentDescription = "${publisher.name}, ${publisher.count} games"
                            role = Role.Button
                        },
                )
            }
        }
    }
}

// --- Letter Avatar (extracted for reuse) ---

@Composable
private fun LetterAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(SpColor.Primary.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnPrimary,
        )
    }
}

// --- Company Description Section ---

@Composable
internal fun DeveloperCompanyDescription(
    companyInfo: CompanyInfo,
    modifier: Modifier = Modifier,
) {
    val description = companyInfo.description ?: return

    var expanded by remember { mutableStateOf(false) }
    var hasVisualOverflow by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .padding(horizontal = SpSpacing.ScreenHorizontal),
    ) {
        Text(
            text = "About",
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground,
            modifier = Modifier.testTag("developer_company_about_header"),
        )
        Spacer(Modifier.height(SpSpacing.Medium))

        Column(
            modifier = Modifier.animateContentSize(),
        ) {
            Text(
                text = description,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!expanded) hasVisualOverflow = result.hasVisualOverflow
                },
                modifier = Modifier.testTag("developer_company_description"),
            )

            if (hasVisualOverflow || expanded) {
                Spacer(Modifier.height(SpSpacing.XSmall))

                Text(
                    text = if (expanded) "Show less" else "Show more",
                    style = SpTypography.LabelMedium,
                    color = SpColor.Primary,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .testTag("developer_company_description_toggle"),
                )
            }
        }

        // Metadata line: "Founded {year} — {country}"
        val metaParts = buildList {
            companyInfo.foundedYear?.let { add("Founded $it") }
            companyInfo.country?.let { add(it) }
        }
        if (metaParts.isNotEmpty()) {
            Spacer(Modifier.height(SpSpacing.Medium))
            Text(
                text = metaParts.joinToString(" \u2014 "),
                style = SpTypography.LabelMedium,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.testTag("developer_company_metadata"),
            )
        }

        // External links
        val websiteUrl = companyInfo.websiteUrl
        val wikipediaUrl = companyInfo.wikipediaUrl
        if (!websiteUrl.isNullOrBlank() || !wikipediaUrl.isNullOrBlank()) {
            Spacer(Modifier.height(SpSpacing.Medium))
            Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium)) {
                if (!websiteUrl.isNullOrBlank()) {
                    Text(
                        text = "Website",
                        style = SpTypography.LabelMedium,
                        color = SpColor.Primary,
                        modifier = Modifier
                            .clickable { uriHandler.openUri(websiteUrl) }
                            .testTag("developer_company_website_link"),
                    )
                }
                if (!wikipediaUrl.isNullOrBlank()) {
                    Text(
                        text = "Wikipedia",
                        style = SpTypography.LabelMedium,
                        color = SpColor.Primary,
                        modifier = Modifier
                            .clickable { uriHandler.openUri(wikipediaUrl) }
                            .testTag("developer_company_wikipedia_link"),
                    )
                }
            }
        }

        Spacer(Modifier.height(SpSpacing.Large))
    }
}
