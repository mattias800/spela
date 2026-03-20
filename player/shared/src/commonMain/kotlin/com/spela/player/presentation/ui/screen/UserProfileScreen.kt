package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.PublicProfile
import com.spela.player.domain.model.PublicProfileGame
import com.spela.player.presentation.intent.SocialIntent
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.theme.spScreenBackground
import com.spela.player.presentation.viewmodel.SocialViewModel
import com.spela.player.util.formatPlayTime

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
            .spScreenBackground(),
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
    onGameSelected: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SpSpacing.Default),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Default),
    ) {
        // Avatar + name + online status
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpSpacing.ScreenHorizontal),
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
                                .size(16.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(SpColor.Background)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(SpColor.Success),
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
                }
            }
        }

        // Stats row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpSpacing.ScreenHorizontal),
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

        // Most Played
        if (profile.topGames.isNotEmpty()) {
            item {
                GameSection(
                    title = "Most Played",
                    games = profile.topGames,
                    onGameSelected = onGameSelected,
                    showPlayTime = true,
                )
            }
        }

        // Favorites
        if (profile.favoriteGames.isNotEmpty()) {
            item {
                GameSection(
                    title = "Favorites",
                    games = profile.favoriteGames,
                    onGameSelected = onGameSelected,
                )
            }
        }

        // Recently Played
        if (profile.recentGames.isNotEmpty()) {
            item {
                GameSection(
                    title = "Recently Played",
                    games = profile.recentGames,
                    onGameSelected = onGameSelected,
                    showPlayTime = true,
                )
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
                color = SpColor.Primary,
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
private fun GameSection(
    title: String,
    games: List<PublicProfileGame>,
    onGameSelected: (String) -> Unit,
    showPlayTime: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal),
    ) {
        Text(
            text = title,
            style = SpTypography.TitleLarge,
            color = SpColor.OnBackground,
            modifier = Modifier.semantics { contentDescription = "$title section" },
        )
        Spacer(Modifier.height(SpSpacing.Small))
        games.forEach { game ->
            ProfileGameItem(
                game = game,
                showPlayTime = showPlayTime,
                onClick = { onGameSelected(game.id) },
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
    SpCard(
        onGradient = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SpSpacing.XXSmall),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover",
                modifier = Modifier.size(width = 40.dp, height = 56.dp),
                cornerRadius = SpSpacing.RadiusDefault,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = game.consoleName,
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                )
                if (showPlayTime && game.playTime > 0) {
                    Text(
                        text = formatPlayTime(game.playTime),
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundSecondary,
                    )
                }
            }
        }
    }
}

