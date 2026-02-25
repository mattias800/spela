package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.AchievementPlayerRanking
import com.spela.player.domain.model.AchievementProgress
import com.spela.player.domain.model.AchievementTimelineData
import com.spela.player.domain.model.GameAchievement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import com.spela.player.presentation.state.AchievementsViewMode
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpProgressBar
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameAchievementsSection(
    achievements: List<GameAchievement>,
    progress: List<AchievementProgress>,
    timeline: AchievementTimelineData?,
    leaderboard: List<AchievementPlayerRanking>,
    viewMode: AchievementsViewMode,
    isLoading: Boolean,
    onToggleView: (AchievementsViewMode) -> Unit,
    achievementsWarning: String? = null,
    modifier: Modifier = Modifier,
) {
    if (achievements.isEmpty() && !isLoading) return

    val progressMap = progress.associateBy { it.achievementId }
    val unlockedCount = progress.count { it.unlockedAt != null }
    val totalCount = achievements.size
    val totalPoints = achievements.sumOf { it.points }
    val earnedPoints = achievements.filter { progressMap[it.id]?.unlockedAt != null }
        .sumOf { it.points }
    val completionFraction = if (totalCount > 0) unlockedCount.toFloat() / totalCount else 0f
    val isComplete = totalCount > 0 && unlockedCount == totalCount

    SpTitledSection(
        title = "Achievements",
        icon = Icons.Outlined.EmojiEvents,
        modifier = modifier.testTag("achievements_section"),
    ) {
        // Progress bar + summary
        SpProgressBar(
            progress = completionFraction,
            progressColors = if (isComplete) {
                listOf(SpColor.Gold, SpColor.Gold)
            } else {
                listOf(SpColor.Primary, SpColor.Accent)
            },
            showPercentage = true,
            label = "$unlockedCount of $totalCount unlocked ($earnedPoints/$totalPoints pts)",
        )

        if (isComplete) {
            Spacer(Modifier.height(SpSpacing.Small))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
                    .background(SpColor.Gold.copy(alpha = 0.15f))
                    .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XXSmall)
                    .testTag("achievements_complete_badge"),
            ) {
                Text(
                    text = "100% Complete!",
                    style = SpTypography.LabelMedium,
                    color = SpColor.Gold,
                )
            }
        }

        // Achievements warning (e.g. PSP CHD created with createdvd)
        if (achievementsWarning != null) {
            Spacer(Modifier.height(SpSpacing.Medium))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
                    .background(SpColor.Warning.copy(alpha = 0.1f))
                    .padding(SpSpacing.Default)
                    .testTag("achievements_warning_banner"),
            ) {
                Text(
                    text = achievementsWarning,
                    style = SpTypography.BodySmall,
                    color = SpColor.Warning,
                )
            }
        }

        Spacer(Modifier.height(SpSpacing.Medium))

        // View mode toggle
        ViewModeToggle(
            currentMode = viewMode,
            onModeSelected = onToggleView,
        )

        Spacer(Modifier.height(SpSpacing.Medium))

        // Content based on view mode
        when (viewMode) {
            AchievementsViewMode.GRID -> {
                AchievementGrid(
                    achievements = achievements,
                    progressMap = progressMap,
                )
            }
            AchievementsViewMode.TIMELINE -> {
                if (timeline != null) {
                    TimelineView(timeline = timeline)
                } else if (isLoading) {
                    Text(
                        text = "Loading timeline...",
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }
            AchievementsViewMode.LEADERBOARD -> {
                if (leaderboard.isNotEmpty()) {
                    LeaderboardView(
                        leaderboard = leaderboard,
                        totalAchievements = totalCount,
                    )
                } else if (isLoading) {
                    Text(
                        text = "Loading leaderboard...",
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                } else {
                    Text(
                        text = "No leaderboard data yet.",
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewModeToggle(
    currentMode: AchievementsViewMode,
    onModeSelected: (AchievementsViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
            .background(SpColor.SurfaceVariant)
            .testTag("achievements_view_toggle"),
    ) {
        AchievementsViewMode.entries.forEach { mode ->
            val isSelected = mode == currentMode
            val label = when (mode) {
                AchievementsViewMode.GRID -> "Grid"
                AchievementsViewMode.TIMELINE -> "Timeline"
                AchievementsViewMode.LEADERBOARD -> "Leaderboard"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
                    .background(if (isSelected) SpColor.Primary else SpColor.SurfaceVariant)
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
                    .testTag("achievements_tab_${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = SpTypography.LabelMedium,
                    color = if (isSelected) SpColor.OnPrimary else SpColor.OnBackgroundSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementGrid(
    achievements: List<GameAchievement>,
    progressMap: Map<Long, AchievementProgress>,
) {
    // Sort: unlocked first (by unlock time), then locked (by display order)
    val sorted = achievements.sortedWith(
        compareByDescending<GameAchievement> { progressMap[it.id]?.unlockedAt != null }
            .thenBy { it.displayOrder ?: Int.MAX_VALUE }
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("achievements_grid"),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        maxItemsInEachRow = 2,
    ) {
        sorted.forEach { achievement ->
            val isUnlocked = progressMap[achievement.id]?.unlockedAt != null
            AchievementCard(
                achievement = achievement,
                isUnlocked = isUnlocked,
                modifier = Modifier.weight(1f),
            )
        }
        // If odd number, add spacer to fill the last row
        if (sorted.size % 2 != 0) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TimelineView(timeline: AchievementTimelineData) {
    Column(modifier = Modifier.testTag("achievements_timeline")) {
        // Stats summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            TimelineStatCard(
                value = "${timeline.unlockedCount}/${timeline.totalAchievements}",
                label = "unlocked",
                modifier = Modifier.weight(1f),
            )
            TimelineStatCard(
                value = "${timeline.earnedPoints}/${timeline.totalPoints}",
                label = "points",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(SpSpacing.Medium))

        if (timeline.timeline.isEmpty()) {
            Text(
                text = "No achievements unlocked yet.",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
        } else {
            timeline.timeline.forEach { entry ->
                TimelineEntryRow(
                    entry = entry,
                    modifier = Modifier.padding(vertical = SpSpacing.XXSmall),
                )
            }
        }
    }
}

@Composable
private fun TimelineStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    SpInnerCard(modifier = modifier) {
        Column(modifier = Modifier.padding(SpSpacing.Default)) {
            Text(
                text = value,
                style = SpTypography.TitleLarge,
                color = SpColor.Accent,
            )
            Text(
                text = label,
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
    }
}

@Composable
private fun LeaderboardView(
    leaderboard: List<AchievementPlayerRanking>,
    totalAchievements: Int,
) {
    Column(
        modifier = Modifier.testTag("achievements_leaderboard"),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
    ) {
        leaderboard.forEachIndexed { index, player ->
            LeaderboardRow(
                rank = index + 1,
                player = player,
                totalAchievements = totalAchievements,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
