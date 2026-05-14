package com.spela.player.presentation.ui.feature.explore

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.CompanyInfo
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.MakerDetail
import com.spela.player.domain.model.MakerUserStats
import androidx.compose.ui.focus.focusRequester
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpImage
import com.spela.player.presentation.ui.components.SpLinkText
import com.spela.player.presentation.ui.components.SpCarouselGameCard
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatPlayTime
import com.spela.player.util.formatRating

// Hero banner alpha constants (matching ConsoleInfoSection pattern)
private val HeroIconTint = Color.White.copy(alpha = 0.45f)
private val HeroValueText = Color.White.copy(alpha = 0.90f)
private val HeroLabelText = Color.White.copy(alpha = 0.40f)

// --- (a) Hero Banner ---

@Composable
internal fun DeveloperHeroBanner(
    detail: MakerDetail,
    modifier: Modifier = Modifier,
) {
    val companyInfo = detail.companyInfo

    Box(
        modifier = modifier
            .testTag("developer_hero_banner")
            .fillMaxWidth()
            .height(200.dp + SpSpacing.TopBarHeight + SpSpacing.Large),
    ) {
        // Background: hero image or gradient fallback
        if (detail.heroUrl != null) {
            SpImage(
                model = detail.heroUrl,
                contentDescription = "${detail.name} hero image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                staggerMs = 0L,
                placeholder = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SpColor.Background),
                    )
                },
                error = {
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
                },
            )
        } else {
            // Gradient fallback when no hero image
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

        // Gradient overlay: transparent at top, SpColor.Background at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.4f to SpColor.Background.copy(alpha = 0.3f),
                            1.0f to SpColor.Background,
                        ),
                    ),
                ),
        )

        // Content: stats on left, logo + info centered (console banner pattern)
        // Top padding clears the overlaid back button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = SpSpacing.TopBarHeight + SpSpacing.Large,
                    start = SpSpacing.XLarge,
                    end = SpSpacing.XLarge,
                ),
        ) {
            // LEFT side — "At a Glance" data table with contrast backdrop
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = SpSpacing.Small)
                    .background(
                        Color.Black.copy(alpha = 0.4f),
                        RoundedCornerShape(SpSpacing.CardCornerRadius),
                    )
                    .padding(SpSpacing.Medium)
                    .widthIn(max = 120.dp),
            ) {
                DeveloperInfoSection(
                    detail = detail,
                    modifier = Modifier.testTag("developer_info_section"),
                )
            }

            // CENTER — Logo/name in a contrast backdrop
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            RoundedCornerShape(SpSpacing.CardCornerRadius),
                        )
                        .padding(SpSpacing.Large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Company logo or name text. Use a fixed-size
                    // container so ContentScale.Fit actually scales small
                    // logos UP — widthIn/heightIn (upper bounds only)
                    // let a 100×30 logo render at 100×30 inside a much
                    // larger slot.
                    val logoUrl = companyInfo?.logoUrl
                    if (logoUrl != null) {
                        SpImage(
                            model = logoUrl,
                            contentDescription = "${detail.name} logo",
                            modifier = Modifier
                                .size(width = 200.dp, height = 80.dp)
                                .testTag("developer_company_logo"),
                            contentScale = ContentScale.Fit,
                            staggerMs = 0L,
                            placeholder = {
                                Text(
                                    text = detail.name,
                                    style = SpTypography.HeadlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            },
                            error = {
                                Text(
                                    text = detail.name,
                                    style = SpTypography.HeadlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            },
                        )
                    } else {
                        Text(
                            text = detail.name,
                            style = SpTypography.HeadlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.testTag("developer_letter_avatar"),
                        )
                    }

                    // Metadata line: "Founded {year} · {country}"
                    val metaParts = buildList {
                        companyInfo?.foundedYear?.let { add("Founded $it") }
                        companyInfo?.country?.let { add(it) }
                    }
                    if (metaParts.isNotEmpty()) {
                        Spacer(Modifier.height(SpSpacing.Small))
                        Text(
                            text = metaParts.joinToString(" \u00b7 "),
                            style = SpTypography.LabelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.testTag("developer_company_metadata"),
                        )
                    }
                }
            }
        }
    }
}

// --- Developer Info Section (console-style stat table on left side of hero) ---

@Composable
private fun DeveloperInfoSection(
    detail: MakerDetail,
    modifier: Modifier = Modifier,
) {
    data class Stat(val icon: ImageVector, val value: String, val label: String)

    val stats = buildList {
        add(Stat(Icons.Filled.Gamepad, "${detail.gameCount}", "Games"))

        val activeYears = detail.activeYears
        if (activeYears != null && activeYears.first > 0) {
            val yearRange = if (activeYears.first == activeYears.last) {
                "${activeYears.first}"
            } else {
                "${activeYears.first}-${activeYears.last}"
            }
            add(Stat(Icons.Filled.CalendarMonth, yearRange, "Active"))
        }

        val primaryGenre = detail.primaryGenre
        if (!primaryGenre.isNullOrBlank()) {
            add(Stat(Icons.Filled.Category, primaryGenre, "Genre"))
        }

        if (detail.avgRating > 0) {
            add(Stat(Icons.Filled.Star, formatRating(detail.avgRating), "Avg rating"))
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        stats.forEach { stat ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = null,
                    tint = HeroIconTint,
                    modifier = Modifier.size(SpSpacing.IconSmall),
                )
                Spacer(Modifier.width(SpSpacing.XSmall))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = stat.value,
                        style = SpTypography.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = HeroValueText,
                    )
                    Text(
                        text = stat.label,
                        style = SpTypography.LabelSmall,
                        color = HeroLabelText,
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
    /**
     * When true, the first item in this carousel claims the screen's
     * default focus on first entry. Use on the publisher/developer
     * detail pages to land focus on the top of the content rather
     * than dropping into "All games" further down (which would scroll
     * the page on first paint).
     */
    isDefaultFocusGroup: Boolean = false,
) {
    SpCarousel(
        itemCount = topGames.size,
        modifier = modifier,
        memoryKey = "explore_developer_top_rated",
        itemKey = { topGames[it].id },
        isDefaultFocusGroup = isDefaultFocusGroup,
    ) { index, focusRequester ->
        Box(modifier = Modifier.focusRequester(focusRequester)) {
            DeveloperTopRatedCard(
                game = topGames[index],
                onGameSelected = onGameSelected,
            )
        }
    }
}

/** ROLE component -- developer's top-rated game card. Delegates to [SpCarouselGameCard]. */
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
        rating = game.communityRating,
        isFavorite = game.isFavorite,
        isInPlayLater = game.isInPlayLater,
        testTag = "developer_top_game_${game.id}",
    )
}

// --- (d) User Stats Card ---

@Composable
internal fun DeveloperUserStatsCard(
    userStats: MakerUserStats,
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

        val mostPlayedGame = userStats.mostPlayedGame
        if (mostPlayedGame != null) {
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
                    onClick = { onGameSelected(mostPlayedGame.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(SpSpacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                    ) {
                        SpCoverArt(
                            imageUrl = mostPlayedGame.coverUrl,
                            contentDescription = "${mostPlayedGame.title} cover",
                            modifier = Modifier
                                .width(32.dp)
                                .height(42.dp)
                                .clip(RoundedCornerShape(SpSpacing.RadiusSmall)),
                            aspectRatio = 0.75f,
                        )
                        Text(
                            text = mostPlayedGame.title,
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
            modifier = Modifier.size(SpSpacing.IconDefault),
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
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SpColor.Primary.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = if (size >= 72.dp) SpTypography.HeadlineLarge else SpTypography.HeadlineMedium,
            color = SpColor.OnPrimary,
        )
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

// --- Company Description (collapsible about section) ---

@Composable
internal fun DeveloperCompanyDescription(
    companyInfo: CompanyInfo,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.animateContentSize(),
    ) {
        Text(
            text = companyInfo.description ?: "",
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) hasOverflow = result.hasVisualOverflow
            },
            modifier = Modifier.testTag("developer_company_description"),
        )

        if (hasOverflow || expanded) {
            SpLinkText(
                text = if (expanded) "Show less" else "Show more",
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .padding(top = SpSpacing.XSmall)
                    .testTag("developer_company_description_toggle"),
            )
        }
    }
}
