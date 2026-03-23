package com.spela.player.presentation.ui.screen

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.HeatmapEntry
import com.spela.player.domain.model.PublicProfile
import com.spela.player.domain.model.PublicProfileGame
import com.spela.player.presentation.intent.SocialIntent
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpCard

import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpPlayHeatmap
import com.spela.player.presentation.ui.components.SpSectionList
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.SpWideGameCard
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.theme.spScreenBackground
import com.spela.player.presentation.viewmodel.SocialViewModel
import com.spela.player.util.formatPlayTime

private val MonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * Format an ISO 8601 date string (e.g. "2024-01-15T10:30:00Z") to "Month Year"
 * (e.g. "January 2024"). Falls back to the raw string if parsing fails.
 */
private fun formatMemberSince(raw: String): String {
    return try {
        // Take "YYYY-MM" from the ISO string
        val yearMonth = raw.take(7) // "2024-01"
        val parts = yearMonth.split("-")
        if (parts.size >= 2) {
            val year = parts[0]
            val monthIndex = parts[1].toIntOrNull()?.minus(1) ?: return raw
            if (monthIndex in 0..11) {
                "${MonthNames[monthIndex]} $year"
            } else {
                raw
            }
        } else {
            raw
        }
    } catch (_: Exception) {
        raw
    }
}

@Composable
fun UserProfileScreen(
    userId: String,
    socialViewModel: SocialViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by socialViewModel.state.collectAsState()

    LaunchedEffect(userId) {
        socialViewModel.onIntent(SocialIntent.LoadPublicProfile(userId))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .spScreenBackground()
            .testTag("user_profile_screen"),
    ) {
        SpTopBar(
            title = state.publicProfile?.username ?: "Profile",
            showBack = true,
            onBack = onBack,
        )

        when {
            state.isLoadingProfile -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading profile...")
                }
            }
            state.publicProfile != null -> {
                val profile = state.publicProfile ?: return
                ProfileContent(
                    profile = profile,
                    heatmapData = state.heatmapData,
                    onGameSelected = onGameSelected,
                )
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Profile not found",
                        style = SpTypography.BodyLarge,
                        color = SpColor.OnBackgroundSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    profile: PublicProfile,
    heatmapData: List<HeatmapEntry>,
    onGameSelected: (String) -> Unit,
) {
    val formattedMemberSince = remember(profile.memberSince) {
        formatMemberSince(profile.memberSince)
    }

    SpSectionList(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Avatar + name + online status
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(64.dp)) {
                    SpAvatar(
                        username = profile.username,
                        avatarUrl = profile.avatarUrl,
                        size = 64.dp,
                        placeholderTextStyle = SpTypography.HeadlineLarge,
                    )
                    if (profile.isOnline) {
                        Box(
                            modifier = Modifier
                                .size(SpSpacing.Default)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(SpColor.Background)
                                .padding(SpSpacing.XXSmall)
                                .clip(CircleShape)
                                .background(SpColor.Success)
                                .semantics { contentDescription = "Online" },
                        )
                    }
                }
                Spacer(Modifier.width(SpSpacing.Default))
                Column {
                    Text(
                        text = profile.username,
                        style = SpTypography.HeadlineLarge,
                        color = SpColor.OnBackground,
                    )
                    if (profile.isOnline && profile.currentGame != null) {
                        Text(
                            text = "Playing ${profile.currentGame.title}",
                            style = SpTypography.BodySmall,
                            color = SpColor.Primary,
                        )
                    } else if (profile.isOnline) {
                        Text(
                            text = "Online",
                            style = SpTypography.BodySmall,
                            color = SpColor.Success,
                        )
                    }
                    if (profile.memberSince.isNotBlank()) {
                        Text(
                            text = "Member since $formattedMemberSince",
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }
        }

        // Stats row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_profile_stats"),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                StatCard(
                    label = "Play Time",
                    value = formatPlayTime(profile.totalPlayTime),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Games Played",
                    value = profile.gamesPlayed.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Activity Heatmap
        if (heatmapData.isNotEmpty()) {
            item {
                SpTitledSection(title = "Activity") {
                    SpPlayHeatmap(
                        entries = heatmapData,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Most Played
        if (profile.topGames.isNotEmpty()) {
            item {
                SpTitledSection(
                    title = "Most Played",
                    modifier = Modifier.testTag("user_profile_most_played"),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                    ) {
                        profile.topGames.forEach { game ->
                            ProfileGameItem(
                                game = game,
                                showPlayTime = true,
                                onClick = { onGameSelected(game.id) },
                            )
                        }
                    }
                }
            }
        }

        // Favorites
        if (profile.favoriteGames.isNotEmpty()) {
            item {
                SpTitledSection(
                    title = "Favorites",
                    modifier = Modifier.testTag("user_profile_favorites"),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                    ) {
                        profile.favoriteGames.forEach { game ->
                            ProfileGameItem(
                                game = game,
                                showPlayTime = false,
                                onClick = { onGameSelected(game.id) },
                            )
                        }
                    }
                }
            }
        }

        // Recently Played
        if (profile.recentGames.isNotEmpty()) {
            item {
                SpTitledSection(
                    title = "Recently Played",
                    modifier = Modifier.testTag("user_profile_recently_played"),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                    ) {
                        profile.recentGames.forEach { game ->
                            ProfileGameItem(
                                game = game,
                                showPlayTime = true,
                                onClick = { onGameSelected(game.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    SpCard(onGradient = true, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = SpTypography.HeadlineLarge,
                color = SpColor.OnBackground,
            )
            Spacer(Modifier.height(SpSpacing.XXSmall))
            Text(
                text = label,
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundSecondary,
            )
        }
    }
}

@Composable
private fun ProfileGameItem(
    game: PublicProfileGame,
    showPlayTime: Boolean,
    onClick: () -> Unit,
) {
    SpWideGameCard(
        title = game.title,
        subtitle = game.consoleName,
        coverUrl = game.coverUrl,
        onClick = onClick,
        fillWidth = true,
        extraContent = if (showPlayTime && game.playTime > 0) {
            {
                Text(
                    text = formatPlayTime(game.playTime),
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundSecondary,
                )
            }
        } else null,
    )
}
