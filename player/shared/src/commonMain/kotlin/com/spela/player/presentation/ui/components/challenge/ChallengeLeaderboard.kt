package com.spela.player.presentation.ui.components.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.spela.player.domain.model.ChallengeLeaderboardEntry
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun ChallengeLeaderboard(
    entries: List<ChallengeLeaderboardEntry>,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
    ) {
        entries.forEach { entry ->
            LeaderboardRow(
                entry = entry,
                onClick = { onUserClick(entry.userId) },
            )
        }
    }
}

@Composable
private fun LeaderboardRow(
    entry: ChallengeLeaderboardEntry,
    onClick: () -> Unit,
) {
    val rankColor = when (entry.rank) {
        1 -> SpColor.Gold
        2 -> SpColor.Silver
        3 -> SpColor.Bronze
        else -> SpColor.OnBackgroundTertiary
    }
    val isHighlighted = entry.isCurrentUser

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
            .background(if (isHighlighted) SpColor.Primary.copy(alpha = 0.08f) else SpColor.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "Rank ${entry.rank}: ${entry.username}, ${formatDuration(entry.durationMs)}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rank badge
        Box(
            modifier = Modifier.size(SpSpacing.IconLarge),
            contentAlignment = Alignment.Center,
        ) {
            val rankText = when (entry.rank) {
                1 -> "\uD83E\uDD47"
                2 -> "\uD83E\uDD48"
                3 -> "\uD83E\uDD49"
                else -> "#${entry.rank}"
            }
            Text(
                text = if (entry.rank <= 3) rankText else "#${entry.rank}",
                style = if (entry.rank <= 3) SpTypography.TitleMedium else SpTypography.LabelSmall,
                color = rankColor,
            )
        }

        Spacer(Modifier.width(SpSpacing.Medium))

        // Avatar
        SpAvatar(
            username = entry.username,
            avatarUrl = entry.avatarUrl,
            size = SpSpacing.AvatarDefault,
        )

        Spacer(Modifier.width(SpSpacing.Small))

        // Username
        Text(
            text = entry.username,
            style = SpTypography.BodyMedium,
            color = if (isHighlighted) SpColor.Primary else SpColor.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Duration
        Text(
            text = formatDuration(entry.durationMs),
            style = SpTypography.BodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = if (isHighlighted) SpColor.Primary else SpColor.OnBackgroundSecondary,
        )
    }
}

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val tenths = (durationMs % 1000) / 100
    return if (hours > 0) {
        "$hours:%02d:%02d.%d".format(minutes, seconds, tenths)
    } else {
        "$minutes:%02d.%d".format(seconds, tenths)
    }
}
