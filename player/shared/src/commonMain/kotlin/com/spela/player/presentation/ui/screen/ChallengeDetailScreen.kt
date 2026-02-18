package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.presentation.intent.ChallengeIntent
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.challenge.ChallengeLeaderboard
import com.spela.player.presentation.ui.components.challenge.SpChallengeTypeChip
import com.spela.player.presentation.ui.components.challenge.SpDifficultyChip
import com.spela.player.presentation.ui.components.challenge.formatDuration
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ChallengeDetailViewModel

@Composable
fun ChallengeDetailScreen(
    viewModel: ChallengeDetailViewModel,
    challengeId: String,
    onAttempt: (challengeId: String, gameId: String) -> Unit,
    onUserSelected: (String) -> Unit,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(challengeId) {
        viewModel.onIntent(ChallengeIntent.LoadChallengeDetail(challengeId))
    }

    // Handle delete success
    LaunchedEffect(state.deleteSuccess) {
        if (state.deleteSuccess) onBack()
    }

    PlatformBackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(
            title = state.challenge?.name ?: "Challenge",
            showBack = true,
            onBack = onBack,
        )

        if (state.isLoading && state.challenge == null) {
            ChallengeDetailSkeleton()
        } else if (state.challenge != null) {
            val challenge = state.challenge ?: return

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Screenshot hero
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .background(SpColor.SurfaceVariant),
                ) {
                    if (challenge.screenshotUrl != null) {
                        SubcomposeAsyncImage(
                            model = challenge.screenshotUrl,
                            contentDescription = "Challenge screenshot",
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Flag,
                            contentDescription = null,
                            tint = SpColor.OnBackgroundTertiary,
                            modifier = Modifier.size(SpSpacing.IconHero).align(Alignment.Center),
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(SpSpacing.ScreenHorizontal),
                ) {
                    Spacer(Modifier.height(SpSpacing.Large))

                    // Title
                    Text(
                        text = challenge.name,
                        style = SpTypography.HeadlineMedium,
                        color = SpColor.OnBackground,
                        modifier = Modifier.semantics { heading() },
                    )

                    Spacer(Modifier.height(SpSpacing.XSmall))

                    // Game title
                    Text(
                        text = challenge.gameTitle,
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundSecondary,
                    )

                    Spacer(Modifier.height(SpSpacing.Small))

                    // Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                    ) {
                        SpDifficultyChip(difficulty = challenge.difficulty)
                        SpChallengeTypeChip(type = challenge.type)
                    }

                    Spacer(Modifier.height(SpSpacing.Medium))

                    // Creator info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics {
                            contentDescription = "Created by ${challenge.creatorUsername}"
                        },
                    ) {
                        SpAvatar(
                            username = challenge.creatorUsername,
                            avatarUrl = challenge.creatorAvatarUrl,
                            size = SpSpacing.AvatarMedium,
                        )
                        Spacer(Modifier.width(SpSpacing.Small))
                        Text(
                            text = challenge.creatorUsername,
                            style = SpTypography.BodyMedium,
                            color = SpColor.OnBackgroundSecondary,
                        )
                    }

                    // Description
                    if (challenge.description.isNotBlank()) {
                        Spacer(Modifier.height(SpSpacing.Large))
                        Text(
                            text = challenge.description,
                            style = SpTypography.BodyMedium,
                            color = SpColor.OnBackgroundSecondary,
                        )
                    }

                    Spacer(Modifier.height(SpSpacing.Medium))

                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Large),
                    ) {
                        StatItem(
                            label = "Attempts",
                            value = "${challenge.attemptCount}",
                        )
                        StatItem(
                            label = "Completed",
                            value = "${challenge.completionCount}",
                        )
                        if (challenge.bestTimeMs != null) {
                            StatItem(
                                label = "Best Time",
                                value = formatDuration(challenge.bestTimeMs),
                            )
                        }
                    }

                    Spacer(Modifier.height(SpSpacing.XLarge))

                    // Attempt button
                    SpButton(
                        text = "Attempt Challenge",
                        onClick = { onAttempt(challenge.id, challenge.gameId) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(SpSpacing.XXLarge))

                    // Leaderboard section
                    Text(
                        text = "Leaderboard",
                        style = SpTypography.TitleLarge,
                        color = SpColor.OnBackground,
                        modifier = Modifier.semantics { heading() },
                    )

                    Spacer(Modifier.height(SpSpacing.Medium))

                    if (state.leaderboard.isEmpty() && !state.isLoadingLeaderboard) {
                        SpEmptyStates.NoAttempts()
                    } else {
                        ChallengeLeaderboard(
                            entries = state.leaderboard,
                            onUserClick = onUserSelected,
                        )

                        if (state.hasMoreLeaderboard) {
                            Spacer(Modifier.height(SpSpacing.Medium))
                            SpButton(
                                text = "Load More",
                                onClick = { viewModel.onIntent(ChallengeIntent.LoadMoreLeaderboard(challengeId)) },
                                style = SpButtonStyle.Ghost,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Spacer(Modifier.height(SpSpacing.XXLarge))
                }
            }
        }
    }
}

@Composable
private fun ChallengeDetailSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Screenshot hero shimmer
        SpShimmer(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
        )

        Column(modifier = Modifier.padding(SpSpacing.ScreenHorizontal)) {
            Spacer(Modifier.height(SpSpacing.Large))
            SpShimmer(width = 220.dp, height = 28.dp) // Title
            Spacer(Modifier.height(SpSpacing.Small))
            Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                SpShimmer(width = 60.dp, height = 20.dp) // Difficulty chip
                SpShimmer(width = 80.dp, height = 20.dp) // Type chip
            }
            Spacer(Modifier.height(SpSpacing.Medium))
            SpShimmer(width = 140.dp, height = 16.dp) // Creator
            Spacer(Modifier.height(SpSpacing.XLarge))
            SpShimmer(modifier = Modifier.fillMaxWidth(), height = 48.dp) // Button
            Spacer(Modifier.height(SpSpacing.XXLarge))
            SpShimmer(width = 120.dp, height = 20.dp) // Leaderboard header
            Spacer(Modifier.height(SpSpacing.Medium))
            repeat(3) {
                SpShimmer(modifier = Modifier.fillMaxWidth(), height = 40.dp)
                Spacer(Modifier.height(SpSpacing.XSmall))
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = SpTypography.TitleLarge,
            color = SpColor.OnBackground,
        )
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}
