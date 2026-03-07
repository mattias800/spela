package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.NetplaySession
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.intent.SocialIntent
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.social.ActivityEventItem
import com.spela.player.presentation.ui.components.social.OnlineUsersRow
import com.spela.player.presentation.ui.feature.home.ContinuePlayingRow
import com.spela.player.presentation.ui.feature.home.GameCarouselRow
import com.spela.player.presentation.ui.feature.home.NetplaySessionCard
import com.spela.player.presentation.ui.feature.home.PersonalStatsCard
import com.spela.player.presentation.ui.feature.home.RecentAchievementsRow
import com.spela.player.presentation.ui.feature.home.TopRatedRow
import com.spela.player.presentation.ui.feature.home.TrendingChallengesRow
import com.spela.player.presentation.ui.feature.library.darken
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GameListViewModel
import com.spela.player.presentation.viewmodel.SocialViewModel
import org.jetbrains.compose.resources.painterResource
import spela_player.shared.generated.resources.Res
import spela_player.shared.generated.resources.spela_icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GameListViewModel,
    socialViewModel: SocialViewModel,
    onGameSelected: (String) -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToPlayLater: () -> Unit = {},
    onNavigateToActivity: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToChallenges: () -> Unit = {},
    onChallengeSelected: (String) -> Unit = {},
    onNetplaySessionSelected: (String) -> Unit = {},
    onUserSelected: (String) -> Unit = {},
    hasActiveDownloads: Boolean = false,
    activeNetplaySessions: List<NetplaySession> = emptyList(),
) {
    val state by viewModel.state.collectAsState()
    val socialState by socialViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(GameListIntent.LoadDashboard)
        socialViewModel.onIntent(SocialIntent.RefreshAll)
    }

    val gradientColors = listOf(
        SpColor.Primary.darken(0.70f),
        SpColor.Accent.darken(0.75f),
    )
    val titleBarInset = LocalTitleBarInset.current

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val d = (size.width + size.height) * 0.25f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = gradientColors,
                            start = Offset(cx - d, cy - d),
                            end = Offset(cx + d, cy + d),
                        ),
                    )
                },
        ) {
            if (state.isLoading && state.recentGames.isEmpty() && state.favoriteGames.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading your library...")
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = {
                        viewModel.onIntent(GameListIntent.LoadDashboard)
                        socialViewModel.onIntent(SocialIntent.RefreshAll)
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val isEmpty = state.recentGames.isEmpty() &&
                            state.favoriteGames.isEmpty() &&
                            state.playLaterGames.isEmpty() &&
                            state.recentAchievements.isEmpty() &&
                            state.trendingChallenges.isEmpty() &&
                            socialState.onlineUsers.isEmpty() &&
                            socialState.activityEvents.isEmpty()

                    if (isEmpty && !state.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpEmptyStates.EmptyLibrary()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = titleBarInset,
                                bottom = SpSpacing.Default,
                            ),
                        ) {
                            // Scrollable heading with app icon
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = SpSpacing.ScreenHorizontal,
                                            vertical = SpSpacing.Default,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                ) {
                                    Image(
                                        painter = painterResource(Res.drawable.spela_icon),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape),
                                    )
                                    Text(
                                        text = "Spela",
                                        style = SpTypography.HeadlineMedium,
                                        color = SpColor.OnBackground,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (hasActiveDownloads) {
                                        SpIconButton(
                                            icon = Icons.Filled.Download,
                                            contentDescription = "Downloads",
                                            onClick = onNavigateToDownloads,
                                            badge = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(SpSpacing.Small)
                                                        .align(Alignment.TopEnd)
                                                        .clip(CircleShape)
                                                        .background(SpColor.Primary),
                                                )
                                            },
                                        )
                                    }
                                }
                            }

                            // ── ZONE 1: Your Games ───────────────────────────

                            // Netplay section (time-sensitive, shown first when active)
                            if (activeNetplaySessions.isNotEmpty()) {
                                item {
                                    SpTitledSection(
                                        title = "Netplay",
                                        icon = Icons.Filled.WifiTethering,
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                        ) {
                                            activeNetplaySessions.forEach { session ->
                                                NetplaySessionCard(
                                                    session = session,
                                                    onClick = { onNetplaySessionSelected(session.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Continue Playing section
                            if (state.recentGames.isNotEmpty()) {
                                item {
                                    SpTitledSection(
                                        title = "Continue Playing",
                                        icon = Icons.Filled.PlayArrow,
                                        edgeToEdgeContent = true,
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    ) {
                                        ContinuePlayingRow(
                                            games = state.recentGames.take(6),
                                            onGameSelected = onGameSelected,
                                        )
                                    }
                                }
                            }

                            // Play Later section
                            if (state.playLaterGames.isNotEmpty()) {
                                item {
                                    SpTitledSection(
                                        title = "Play Later",
                                        icon = Icons.Filled.WatchLater,
                                        edgeToEdgeContent = true,
                                        titleTrailing = {
                                            SeeAllLink(
                                                label = "Play Later",
                                                onClick = onNavigateToPlayLater,
                                            )
                                        },
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    ) {
                                        GameCarouselRow(
                                            games = state.playLaterGames.take(6),
                                            onGameSelected = onGameSelected,
                                        )
                                    }
                                }
                            }

                            // Favorites section
                            if (state.favoriteGames.isNotEmpty()) {
                                item {
                                    SpTitledSection(
                                        title = "Favorites",
                                        icon = Icons.Filled.Favorite,
                                        edgeToEdgeContent = true,
                                        titleTrailing = {
                                            SeeAllLink(
                                                label = "Favorites",
                                                onClick = onNavigateToFavorites,
                                            )
                                        },
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    ) {
                                        GameCarouselRow(
                                            games = state.favoriteGames.take(6),
                                            onGameSelected = onGameSelected,
                                        )
                                    }
                                }
                            }

                            // Recently Added section
                            if (state.recentlyAddedGames.isNotEmpty()) {
                                item {
                                    SpTitledSection(
                                        title = "Recently Added",
                                        icon = Icons.Filled.NewReleases,
                                        edgeToEdgeContent = true,
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    ) {
                                        GameCarouselRow(
                                            games = state.recentlyAddedGames.take(6),
                                            onGameSelected = onGameSelected,
                                        )
                                    }
                                }
                            }

                            // ── ZONE 2: Discover ─────────────────────────────

                            // Recent Achievements section
                            if (state.recentAchievements.isNotEmpty()) {
                                item {
                                    SpTitledSection(
                                        title = "Recent Achievements",
                                        icon = Icons.Filled.EmojiEvents,
                                        edgeToEdgeContent = true,
                                        titleTrailing = {
                                            SeeAllLink(
                                                label = "Recent Achievements",
                                                onClick = onNavigateToStats,
                                            )
                                        },
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    ) {
                                        RecentAchievementsRow(achievements = state.recentAchievements)
                                    }
                                }
                            }

                            // Trending Challenges section
                            if (state.trendingChallenges.isNotEmpty()) {
                                item {
                                    SpTitledSection(
                                        title = "Trending Challenges",
                                        icon = Icons.Filled.Whatshot,
                                        edgeToEdgeContent = true,
                                        titleTrailing = {
                                            SeeAllLink(
                                                label = "Trending Challenges",
                                                onClick = onNavigateToChallenges,
                                            )
                                        },
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    ) {
                                        TrendingChallengesRow(
                                            challenges = state.trendingChallenges,
                                            onChallengeSelected = onChallengeSelected,
                                        )
                                    }
                                }
                            }

                            // Top Rated section
                            if (state.topRatedGames.isNotEmpty()) {
                                item {
                                    SpTitledSection(
                                        title = "Top Rated",
                                        icon = Icons.Filled.Star,
                                        edgeToEdgeContent = true,
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    ) {
                                        TopRatedRow(
                                            games = state.topRatedGames,
                                            onGameSelected = onGameSelected,
                                        )
                                    }
                                }
                            }

                            // ── ZONE 3: Community & Reflection ───────────────

                            // Online Now section
                            if (socialState.onlineUsers.isNotEmpty()) {
                                item {
                                    SpTitledSection(
                                        title = "Online Now",
                                        icon = Icons.Filled.People,
                                        edgeToEdgeContent = true,
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    ) {
                                        OnlineUsersRow(
                                            users = socialState.onlineUsers,
                                            onUserSelected = onUserSelected,
                                        )
                                    }
                                }
                            }

                            // Recent Activity section
                            if (socialState.activityEvents.isNotEmpty()) {
                                item {
                                    SpTitledSection(
                                        title = "Recent Activity",
                                        icon = Icons.Filled.History,
                                        titleTrailing = {
                                            SeeAllLink(
                                                label = "Recent Activity",
                                                onClick = onNavigateToActivity,
                                            )
                                        },
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                                        ) {
                                            socialState.activityEvents.take(2).forEach { event ->
                                                ActivityEventItem(event = event)
                                            }
                                        }
                                    }
                                }
                            }

                            // Personal Stats section
                            if (state.personalStats != null) {
                                item {
                                    SpTitledSection(
                                        title = "Your Stats",
                                        icon = Icons.Filled.BarChart,
                                        titleTrailing = {
                                            SeeAllLink(
                                                label = "Your Stats",
                                                onClick = onNavigateToStats,
                                            )
                                        },
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    ) {
                                        PersonalStatsCard(stats = state.personalStats!!)
                                    }
                                }
                            }

                            // Bottom spacer
                            item { Spacer(Modifier.height(SpSpacing.XLarge)) }
                        }
                    }
                }
            }
        }

        // Error snackbar
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.onIntent(GameListIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(GameListIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SeeAllLink(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = "See all",
        style = SpTypography.LabelLarge,
        color = SpColor.Primary,
        modifier = Modifier
            .clip(RoundedCornerShape(SpSpacing.Small))
            .clickable(onClick = onClick)
            .padding(SpSpacing.Small)
            .semantics {
                contentDescription = "See all $label"
                role = Role.Button
            },
    )
}
