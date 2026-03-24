package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.AchievementGameItem
import com.spela.player.domain.model.AlmostDoneGame
import com.spela.player.domain.model.ExploreChallenge
import com.spela.player.domain.model.FreshChallengeGame
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private val CardWidth = 140.dp

@Composable
internal fun EasyToCompleteSection(
    games: List<AchievementGameItem>,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("easy_to_complete_row"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(games, key = { it.game.id }) { item ->
            Column(
                modifier = Modifier
                    .width(CardWidth)
                    .semantics {
                        contentDescription = "${item.game.title}, ${item.avgCompletion.toInt()}% avg completion"
                    },
            ) {
                ExploreGameCard(
                    game = item.game,
                    onClick = { onGameClick(item.game.id) },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${item.avgCompletion.toInt()}% avg completion",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("easy_completion_${item.game.id}"),
                )
            }
        }
    }
}

@Composable
internal fun HardestGamesSection(
    games: List<AchievementGameItem>,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("hardest_games_row"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(games, key = { it.game.id }) { item ->
            Column(
                modifier = Modifier
                    .width(CardWidth)
                    .semantics {
                        contentDescription = "${item.game.title}, ${item.avgCompletion.toInt()}% avg completion"
                    },
            ) {
                ExploreGameCard(
                    game = item.game,
                    onClick = { onGameClick(item.game.id) },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${item.avgCompletion.toInt()}% avg · ${item.playersCompleted}/${item.playersAttempted} completed",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("hardest_stat_${item.game.id}"),
                )
            }
        }
    }
}

@Composable
internal fun AlmostDoneSection(
    games: List<AlmostDoneGame>,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("almost_done_row"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(games, key = { it.game.id }) { item ->
            Column(
                modifier = Modifier
                    .width(CardWidth)
                    .semantics {
                        contentDescription = "${item.game.title}, ${item.unlockedCount}/${item.totalCount} (${item.completionPercent.toInt()}%)"
                    },
            ) {
                ExploreGameCard(
                    game = item.game,
                    onClick = { onGameClick(item.game.id) },
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { item.completionPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${item.unlockedCount}/${item.totalCount} (${item.completionPercent.toInt()}%)",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("almost_done_stat_${item.game.id}"),
                )
            }
        }
    }
}

@Composable
internal fun FreshChallengesSection(
    games: List<FreshChallengeGame>,
    onGameClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("fresh_challenges_row"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(games, key = { it.game.id }) { item ->
            Column(
                modifier = Modifier
                    .semantics {
                        contentDescription = "${item.game.title}, ${item.totalAchievements} achievements, ${item.totalPoints} pts"
                    },
            ) {
                ExploreGameCard(
                    game = item.game,
                    onClick = { onGameClick(item.game.id) },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${item.totalAchievements} achievements \u00b7 ${item.totalPoints} pts",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("fresh_stat_${item.game.id}"),
                )
            }
        }
    }
}

@Composable
internal fun ActiveChallengesSection(
    challenges: List<ExploreChallenge>,
    onChallengeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("active_challenges_row"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(challenges, key = { it.id }) { ch ->
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onChallengeClick(ch.id) }
                    .padding(12.dp)
                    .semantics {
                        contentDescription = "${ch.name}, ${ch.type} challenge, ${ch.difficulty} difficulty"
                    },
            ) {
                Text(
                    text = ch.name,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = ch.gameTitle,
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${ch.type} \u00b7 ${ch.difficulty}",
                    style = SpTypography.LabelSmall,
                    color = SpColor.Link,
                    maxLines = 1,
                )
                Text(
                    text = "${ch.completionCount}/${ch.attemptCount} completed",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                )
                if (ch.creatorUsername.isNotEmpty()) {
                    Text(
                        text = "by ${ch.creatorUsername}",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AchievementSectionSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
) {
    LazyRow(
        modifier = modifier.testTag("achievement_skeleton"),
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
