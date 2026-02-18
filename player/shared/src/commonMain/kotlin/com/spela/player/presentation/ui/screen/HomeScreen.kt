package com.spela.player.presentation.ui.screen

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.NetplaySession
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSectionHeader
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.home.ContinuePlayingRow
import com.spela.player.presentation.ui.feature.home.GameCarouselRow
import com.spela.player.presentation.ui.feature.home.NetplaySessionCard
import com.spela.player.presentation.intent.SocialIntent
import com.spela.player.presentation.ui.components.social.ActivityEventItem
import com.spela.player.presentation.ui.components.social.OnlineUsersRow
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GameListViewModel
import com.spela.player.presentation.viewmodel.SocialViewModel

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

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(title = "Spela") {
            SpIconButton(
                icon = Icons.Filled.Download,
                contentDescription = "Downloads",
                onClick = onNavigateToDownloads,
                badge = if (hasActiveDownloads) {
                    {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(SpColor.Primary),
                        )
                    }
                } else null,
            )
        }

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
                        state.playLaterGames.isEmpty()

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
                        contentPadding = PaddingValues(vertical = SpSpacing.Default),
                    ) {
                        // Netplay section (AC-15)
                        if (activeNetplaySessions.isNotEmpty()) {
                            item {
                                SpSectionHeader(
                                    title = "Netplay",
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Medium))
                            }
                            items(
                                activeNetplaySessions,
                                key = { "netplay-${it.id}" },
                            ) { session ->
                                NetplaySessionCard(
                                    session = session,
                                    onClick = { onNetplaySessionSelected(session.id) },
                                )
                            }
                            item {
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Continue Playing section
                        if (state.recentGames.isNotEmpty()) {
                            item {
                                SpSectionHeader(
                                    title = "Continue Playing",
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Medium))
                                ContinuePlayingRow(
                                    games = state.recentGames.take(10),
                                    onGameSelected = onGameSelected,
                                )
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Play Later section
                        if (state.playLaterGames.isNotEmpty()) {
                            item {
                                SectionHeaderWithSeeAll(
                                    title = "Play Later",
                                    onSeeAll = onNavigateToPlayLater,
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Medium))
                                GameCarouselRow(
                                    games = state.playLaterGames.take(6),
                                    onGameSelected = onGameSelected,
                                )
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Online Now section
                        if (socialState.onlineUsers.isNotEmpty()) {
                            item {
                                SpSectionHeader(
                                    title = "Online Now",
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Medium))
                                OnlineUsersRow(users = socialState.onlineUsers, onUserSelected = onUserSelected)
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Recent Activity section
                        if (socialState.activityEvents.isNotEmpty()) {
                            item {
                                SectionHeaderWithSeeAll(
                                    title = "Recent Activity",
                                    onSeeAll = onNavigateToActivity,
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Small))
                            }
                            items(
                                socialState.activityEvents.take(3),
                                key = { "activity-${it.id}" },
                            ) { event ->
                                ActivityEventItem(event = event)
                            }
                            item {
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Favorites section
                        if (state.favoriteGames.isNotEmpty()) {
                            item {
                                SectionHeaderWithSeeAll(
                                    title = "Favorites",
                                    onSeeAll = onNavigateToFavorites,
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.Medium))
                                GameCarouselRow(
                                    games = state.favoriteGames.take(6),
                                    onGameSelected = onGameSelected,
                                )
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

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
    } // outer Box
}

@Composable
private fun SectionHeaderWithSeeAll(
    title: String,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = SpTypography.HeadlineLarge,
            color = SpColor.OnBackground,
            modifier = Modifier.semantics { contentDescription = "$title section" },
        )
        Text(
            text = "See all",
            style = SpTypography.LabelLarge,
            color = SpColor.Primary,
            modifier = Modifier
                .clip(RoundedCornerShape(SpSpacing.Small))
                .clickable(onClick = onSeeAll)
                .padding(SpSpacing.Small)
                .semantics {
                    contentDescription = "See all $title"
                    role = Role.Button
                },
        )
    }
}
