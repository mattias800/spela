package com.spela.player.presentation.ui.feature.explore

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.domain.model.CompanyInfo
import com.spela.player.domain.model.DeveloperDetail
import com.spela.player.domain.model.DeveloperDetailUserStats
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.LocalAnimationsEnabled
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpCarouselGameCard
import com.spela.player.presentation.ui.components.SpHeroCover
import com.spela.player.presentation.ui.components.SpShimmer
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
                AnimatedStatText(
                    targetValue = detail.gameCount,
                    suffix = " games",
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
                    AnimatedStatText(
                        targetValue = detail.consoles.size,
                        suffix = " platforms",
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
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(
            items = topGames,
            key = { "top_${it.id}" },
        ) { game ->
            DeveloperTopRatedCard(
                game = game,
                onGameSelected = onGameSelected,
            )
        }
    }
}

/** ROLE component — developer's top-rated game card. Delegates to [SpCarouselGameCard]. */
@Composable
internal fun DeveloperTopRatedCard(
    game: Game,
    onGameSelected: (String) -> Unit,
) {
    SpCarouselGameCard(
        title = game.title,
        subtitle = game.consoleName,
        coverUrl = game.coverUrl,
        onClick = { onGameSelected(game.id) },
        rating = game.rating,
        isFavorite = game.isFavorite,
        isInPlayLater = game.isInPlayLater,
        testTag = "developer_top_game_${game.id}",
    )
}

// --- (d) User Stats Card ---

@Composable
internal fun DeveloperUserStatsCard(
    userStats: DeveloperDetailUserStats,
    totalGames: Int,
    onGameSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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

    Column(modifier = modifier) {
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
                    color = SpColor.Link,
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
                        color = SpColor.Link,
                        modifier = Modifier
                            .clickable { uriHandler.openUri(websiteUrl) }
                            .testTag("developer_company_website_link"),
                    )
                }
                if (!wikipediaUrl.isNullOrBlank()) {
                    Text(
                        text = "Wikipedia",
                        style = SpTypography.LabelMedium,
                        color = SpColor.Link,
                        modifier = Modifier
                            .clickable { uriHandler.openUri(wikipediaUrl) }
                            .testTag("developer_company_wikipedia_link"),
                    )
                }
            }
        }
    }
}

// --- At a Glance Stats Row ---

@Composable
internal fun DeveloperAtAGlance(
    detail: DeveloperDetail,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "At a Glance",
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground,
            modifier = Modifier.testTag("developer_at_a_glance_header"),
        )
        Spacer(Modifier.height(SpSpacing.Medium))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("developer_at_a_glance_row"),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            AnimatedGlanceStatItem(
                icon = Icons.Filled.Gamepad,
                targetValue = detail.gameCount,
                label = "Games",
                modifier = Modifier.testTag("developer_glance_games"),
            )

            val activeYears = detail.activeYears
            if (activeYears != null && activeYears.first > 0) {
                val yearRange = if (activeYears.first == activeYears.last) {
                    "${activeYears.first}"
                } else {
                    "${activeYears.first}-${activeYears.last}"
                }
                GlanceStatItem(
                    icon = Icons.Filled.CalendarMonth,
                    value = yearRange,
                    label = "Active",
                    modifier = Modifier.testTag("developer_glance_active_years"),
                )
            }

            val primaryGenre = detail.primaryGenre
            if (!primaryGenre.isNullOrBlank()) {
                GlanceStatItem(
                    icon = Icons.Filled.Category,
                    value = primaryGenre,
                    label = "Primary genre",
                    modifier = Modifier.testTag("developer_glance_primary_genre"),
                )
            }

            if (detail.consoles.isNotEmpty()) {
                AnimatedGlanceStatItem(
                    icon = Icons.Filled.SportsEsports,
                    targetValue = detail.consoles.size,
                    label = "Platforms",
                    modifier = Modifier.testTag("developer_glance_platforms"),
                )
            }

            if (detail.avgRating > 0) {
                GlanceStatItem(
                    icon = Icons.Filled.Star,
                    value = formatRating(detail.avgRating),
                    label = "Avg rating",
                    modifier = Modifier.testTag("developer_glance_avg_rating"),
                )
            }
        }
    }
}

@Composable
private fun GlanceStatItem(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    SpCard(
        modifier = modifier.width(IntrinsicSize.Min),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SpSpacing.Medium,
                vertical = SpSpacing.Small,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SpColor.Primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(SpSpacing.XXSmall))
            Text(
                text = value,
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- Animated Stat Counter ---

/**
 * Animates an integer value from 0 to [targetValue] on first composition.
 * Respects [LocalAnimationsEnabled] — when false (e.g. in tests), the final value
 * is shown immediately without animation.
 */
@Composable
private fun AnimatedStatText(
    targetValue: Int,
    suffix: String = "",
    style: androidx.compose.ui.text.TextStyle = SpTypography.BodyMedium,
    color: Color = SpColor.OnBackgroundSecondary,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(targetValue) {
        if (animationsEnabled && targetValue > 0) {
            animatable.snapTo(0f)
            animatable.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = tween(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            animatable.snapTo(targetValue.toFloat())
        }
    }

    Text(
        text = "${animatable.value.toInt()}$suffix",
        style = style,
        color = color,
        modifier = modifier,
    )
}

/**
 * GlanceStatItem variant that animates a numeric value from 0 to [targetValue].
 */
@Composable
private fun AnimatedGlanceStatItem(
    icon: ImageVector,
    targetValue: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(targetValue) {
        if (animationsEnabled && targetValue > 0) {
            animatable.snapTo(0f)
            animatable.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = tween(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            animatable.snapTo(targetValue.toFloat())
        }
    }

    SpCard(
        modifier = modifier.width(IntrinsicSize.Min),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SpSpacing.Medium,
                vertical = SpSpacing.Small,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SpColor.Primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(SpSpacing.XXSmall))
            Text(
                text = "${animatable.value.toInt()}",
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- Enhanced Skeleton Loading ---

@Composable
internal fun DeveloperDetailSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("developer_detail_loading"),
    ) {
        // Hero banner skeleton
        SpShimmer(
            modifier = Modifier.fillMaxWidth(),
            width = 400.dp,
            height = 200.dp,
        )

        Spacer(Modifier.height(SpSpacing.Large))

        // At a Glance row skeleton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            repeat(4) {
                SpShimmer(width = 80.dp, height = 60.dp)
            }
        }

        Spacer(Modifier.height(SpSpacing.Large))

        // Section header skeleton
        SpShimmer(
            width = 120.dp,
            height = 20.dp,
            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
        )

        Spacer(Modifier.height(SpSpacing.Medium))

        // Game card skeletons
        repeat(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SpSpacing.ScreenHorizontal,
                        vertical = SpSpacing.XSmall,
                    ),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpShimmer(width = 48.dp, height = 64.dp)
                Column {
                    SpShimmer(width = 160.dp, height = 14.dp)
                    Spacer(Modifier.height(SpSpacing.XSmall))
                    SpShimmer(width = 80.dp, height = 12.dp)
                }
            }
        }
    }
}
