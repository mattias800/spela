package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ActiveNowItem
import com.spela.player.domain.model.CommunityTopGame
import com.spela.player.domain.model.CultClassicGame
import com.spela.player.domain.model.RecentReviewItem
import com.spela.player.domain.model.TrendingGame
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private val CardWidth = 140.dp

@Composable
fun TrendingSection(
    games: List<TrendingGame>,
    onGameSelected: (gameId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        itemCount = games.size,
        modifier = modifier.testTag("trending_row"),
    ) { index, focusRequester ->
        val item = games[index]
        Column(
            modifier = Modifier
                .focusRequester(focusRequester)
                .semantics { contentDescription = "${item.game.title}, ${item.playersThisWeek} players this week" },
        ) {
            ExploreGameCard(
                game = item.game,
                onClick = { onGameSelected(item.game.id) },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${item.playersThisWeek} player${if (item.playersThisWeek != 1) "s" else ""} this week",
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("trending_players_${item.game.id}"),
            )
        }
    }
}

@Composable
fun CommunityTopSection(
    games: List<CommunityTopGame>,
    onGameSelected: (gameId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        itemCount = games.size,
        modifier = modifier.testTag("community_top_row"),
    ) { index, focusRequester ->
        val item = games[index]
        Column(
            modifier = Modifier
                .focusRequester(focusRequester)
                .width(CardWidth)
                .semantics { contentDescription = "${item.game.title}, rated ${item.avgRating} out of 5" },
        ) {
            ExploreGameCard(
                game = item.game,
                onClick = { onGameSelected(item.game.id) },
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "★ ${String.format("%.1f", item.avgRating)}/5",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundSecondary,
                )
                Text(
                    text = " (${item.ratingCount})",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }
    }
}

@Composable
fun CultClassicsSection(
    games: List<CultClassicGame>,
    onGameSelected: (gameId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        itemCount = games.size,
        modifier = modifier.testTag("cult_classics_row"),
    ) { index, focusRequester ->
        val item = games[index]
        Column(
            modifier = Modifier
                .focusRequester(focusRequester)
                .width(CardWidth)
                .semantics {
                    contentDescription = "${item.game.title}, community ${item.communityRating} vs IGDB ${item.igdbCriticsRating.toInt()}"
                },
        ) {
            ExploreGameCard(
                game = item.game,
                onClick = { onGameSelected(item.game.id) },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "★ ${String.format("%.1f", item.communityRating)}/5 vs IGDB ${item.igdbCriticsRating.toInt()}/100",
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RecentlyReviewedSection(
    reviews: List<RecentReviewItem>,
    onGameSelected: (gameId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        itemCount = reviews.size,
        modifier = modifier.testTag("recently_reviewed_row"),
    ) { index, focusRequester ->
        val item = reviews[index]
        Column(
            modifier = Modifier
                .focusRequester(focusRequester)
                .width(200.dp)
                .semantics {
                    contentDescription = "${item.game.title}, reviewed by ${item.reviewerName}"
                },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                ExploreGameCard(
                    game = item.game,
                    onClick = { onGameSelected(item.game.id) },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.game.title,
                        style = SpTypography.TitleSmall,
                        color = SpColor.OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "★".repeat(item.rating) + "☆".repeat(5 - item.rating),
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundSecondary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.review,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("review_text_${item.game.id}"),
                    )
                    Text(
                        text = "— ${item.reviewerName}",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveNowSection(
    games: List<ActiveNowItem>,
    onGameSelected: (gameId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        itemCount = games.size,
        modifier = modifier.testTag("active_now_row"),
    ) { index, focusRequester ->
        val item = games[index]
        Column(
            modifier = Modifier
                .focusRequester(focusRequester)
                .width(CardWidth)
                .semantics {
                    val parts = mutableListOf<String>()
                    if (item.activeSessions > 0) parts.add("${item.activeSessions} sessions")
                    if (item.activeChallenges > 0) parts.add("${item.activeChallenges} challenges")
                    contentDescription = "${item.game.title}, ${parts.joinToString(", ")}"
                },
        ) {
            ExploreGameCard(
                game = item.game,
                onClick = { onGameSelected(item.game.id) },
            )
            Spacer(Modifier.height(4.dp))
            val badges = buildList {
                if (item.activeSessions > 0) add("${item.activeSessions} session${if (item.activeSessions != 1) "s" else ""}")
                if (item.activeChallenges > 0) add("${item.activeChallenges} challenge${if (item.activeChallenges != 1) "s" else ""}")
            }
            Text(
                text = badges.joinToString(" · "),
                style = SpTypography.LabelSmall,
                color = SpColor.Link,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("active_badge_${item.game.id}"),
            )
        }
    }
}

@Composable
fun SocialSectionSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
) {
    LazyRow(
        modifier = modifier.testTag("social_skeleton"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(count) {
            SpShimmer(
                modifier = Modifier
                    .width(CardWidth)
                    .clip(RoundedCornerShape(SpSpacing.CardCornerRadius)),
                width = CardWidth,
                height = (CardWidth.value * 1.33f).dp,
            )
        }
    }
}
