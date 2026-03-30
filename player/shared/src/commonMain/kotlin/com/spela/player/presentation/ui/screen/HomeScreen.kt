package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.NetplaySession
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.intent.SocialIntent
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpMainContentPadding
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScrollableContent
import com.spela.player.presentation.ui.gamepad.autoFocus
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.rememberFocus
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.components.SpSectionList
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTextField
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
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GameListViewModel
import com.spela.player.presentation.viewmodel.SettingsIntent
import com.spela.player.presentation.viewmodel.SettingsViewModel
import com.spela.player.presentation.viewmodel.SocialViewModel
import org.jetbrains.compose.resources.painterResource
import spela_player.shared.generated.resources.Res
import spela_player.shared.generated.resources.spela_icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GameListViewModel,
    socialViewModel: SocialViewModel,
    settingsViewModel: SettingsViewModel? = null,
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
    onSearchSelected: () -> Unit = {},
    hasActiveDownloads: Boolean = false,
    activeNetplaySessions: List<NetplaySession> = emptyList(),
) {
    val state by viewModel.state.collectAsState()
    val socialState by socialViewModel.state.collectAsState()
    // Track whether the first load has completed to avoid flashing "empty" state.
    // If the ViewModel already has data (revisit), start as loaded immediately.
    val hasDataOnMount = state.consoles.isNotEmpty() || state.recentGames.isNotEmpty()
    var sawLoading by remember { mutableStateOf(false) }
    var hasInitiallyLoaded by remember { mutableStateOf(hasDataOnMount) }
    if (state.isLoading) sawLoading = true
    if (sawLoading && !state.isLoading) hasInitiallyLoaded = true

    LaunchedEffect(Unit) {
        println("[HomeScreen] LaunchedEffect(Unit) fired — loading dashboard")
        viewModel.onIntent(GameListIntent.LoadDashboard)
        socialViewModel.onIntent(SocialIntent.RefreshAll)
        settingsViewModel?.onIntent(SettingsIntent.LoadSettings)
    }

    // Retry if dashboard loaded empty (server may still be scanning on startup)
    val isEmpty = state.consoles.isEmpty() && state.recentGames.isEmpty() && !state.isLoading
    LaunchedEffect(isEmpty) {
        if (isEmpty) {
            println("[HomeScreen] Dashboard empty, retrying in 3s (consoles=${state.consoles.size}, recent=${state.recentGames.size}, loading=${state.isLoading})")
            kotlinx.coroutines.delay(3000)
            println("[HomeScreen] Retrying dashboard load")
            viewModel.onIntent(GameListIntent.LoadDashboard)
            socialViewModel.onIntent(SocialIntent.RefreshAll)
        }
    }

    val gradientColors = listOf(
        SpColor.Primary.darken(0.70f),
        SpColor.Accent.darken(0.75f),
    )
    SpScreen(gradientColors = gradientColors) {
            if (!hasInitiallyLoaded) {
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
                    val isEmpty = state.consoles.isEmpty() &&
                            state.recentGames.isEmpty() &&
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
                        val focusMemory = rememberFocusMemoryState()
                        SpScrollableContent {
                        SpMainContentPadding {
                        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
                        SpSectionList(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Scrollable heading with app icon
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = SpSpacing.Default),
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
                                SpIconButton(
                                    icon = Icons.Filled.Search,
                                    contentDescription = "Search",
                                    onClick = onSearchSelected,
                                    modifier = Modifier.autoFocus(),
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

                            // Device name banner (only after settings loaded)
                            if (settingsViewModel != null) {
                                val settingsState by settingsViewModel.state.collectAsState()
                                // Only show when settings are loaded (userId non-empty means LoadSettings completed)
                                if (settingsState.userId.isNotEmpty()) {
                                    DeviceNameBanner(
                                        deviceName = settingsState.deviceName,
                                        onSave = { name ->
                                            settingsViewModel.onIntent(SettingsIntent.UpdateDeviceName(name))
                                        },
                                    )
                                }
                            }

                            // ── ZONE 1: Your Games ───────────────────────────

                            // Netplay section (time-sensitive, shown first when active)
                            if (activeNetplaySessions.isNotEmpty()) {
                                SpTitledSection(
                                    title = "Netplay",
                                    icon = Icons.Filled.WifiTethering,
                                    modifier = Modifier.rememberFocus("section_netplay"),
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

                            // Continue Playing section
                            if (state.recentGames.isNotEmpty()) {
                                SpTitledSection(
                                    title = "Continue Playing",
                                    icon = Icons.Filled.PlayArrow,
                                    edgeToEdgeContent = true,
                                    modifier = Modifier.rememberFocus("section_continue_playing"),
                                ) {
                                    ContinuePlayingRow(
                                        games = state.recentGames.take(6),
                                        onGameSelected = onGameSelected,
                                    )
                                }
                            }

                            // Play Later section
                            if (state.playLaterGames.isNotEmpty()) {
                                SpTitledSection(
                                    title = "Play Later",
                                    icon = Icons.Filled.WatchLater,
                                    edgeToEdgeContent = true,
                                    modifier = Modifier.rememberFocus("section_play_later"),
                                    titleTrailing = {
                                        SeeAllLink(
                                            label = "Play Later",
                                            onClick = onNavigateToPlayLater,
                                        )
                                    },
                                ) {
                                    GameCarouselRow(
                                        games = state.playLaterGames.take(6),
                                        onGameSelected = onGameSelected,
                                        keyPrefix = "playLater",
                                    )
                                }
                            }

                            // Favorites section
                            if (state.favoriteGames.isNotEmpty()) {
                                SpTitledSection(
                                    title = "Favorites",
                                    icon = Icons.Filled.Favorite,
                                    edgeToEdgeContent = true,
                                    modifier = Modifier.rememberFocus("section_favorites"),
                                    titleTrailing = {
                                        SeeAllLink(
                                            label = "Favorites",
                                            onClick = onNavigateToFavorites,
                                        )
                                    },
                                ) {
                                    GameCarouselRow(
                                        games = state.favoriteGames.take(6),
                                        onGameSelected = onGameSelected,
                                        keyPrefix = "favorites",
                                    )
                                }
                            }

                            // Recently Added section
                            if (state.recentlyAddedGames.isNotEmpty()) {
                                SpTitledSection(
                                    title = "Recently Added",
                                    icon = Icons.Filled.NewReleases,
                                    edgeToEdgeContent = true,
                                    modifier = Modifier.rememberFocus("section_recently_added"),
                                ) {
                                    GameCarouselRow(
                                        games = state.recentlyAddedGames.take(6),
                                        onGameSelected = onGameSelected,
                                        keyPrefix = "recentlyAdded",
                                    )
                                }
                            }

                            // ── ZONE 2: Discover ─────────────────────────────

                            // Recent Achievements section
                            if (state.recentAchievements.isNotEmpty()) {
                                SpTitledSection(
                                    title = "Recent Achievements",
                                    icon = Icons.Filled.EmojiEvents,
                                    edgeToEdgeContent = true,
                                    modifier = Modifier.rememberFocus("section_recent_achievements"),
                                    titleTrailing = {
                                        SeeAllLink(
                                            label = "Recent Achievements",
                                            onClick = onNavigateToStats,
                                        )
                                    },
                                ) {
                                    RecentAchievementsRow(achievements = state.recentAchievements)
                                }
                            }

                            // Trending Challenges section
                            if (state.trendingChallenges.isNotEmpty()) {
                                SpTitledSection(
                                    title = "Trending Challenges",
                                    icon = Icons.Filled.Whatshot,
                                    edgeToEdgeContent = true,
                                    modifier = Modifier.rememberFocus("section_trending_challenges"),
                                    titleTrailing = {
                                        SeeAllLink(
                                            label = "Trending Challenges",
                                            onClick = onNavigateToChallenges,
                                        )
                                    },
                                ) {
                                    TrendingChallengesRow(
                                        challenges = state.trendingChallenges,
                                        onChallengeSelected = onChallengeSelected,
                                    )
                                }
                            }

                            // Top Rated section
                            if (state.topRatedGames.isNotEmpty()) {
                                SpTitledSection(
                                    title = "Top Rated",
                                    icon = Icons.Filled.Star,
                                    edgeToEdgeContent = true,
                                    modifier = Modifier.rememberFocus("section_top_rated"),
                                ) {
                                    TopRatedRow(
                                        games = state.topRatedGames,
                                        onGameSelected = onGameSelected,
                                    )
                                }
                            }

                            // ── ZONE 3: Community & Reflection ───────────────

                            // Online Now section
                            if (socialState.onlineUsers.isNotEmpty()) {
                                SpTitledSection(
                                    title = "Online Now",
                                    icon = Icons.Filled.People,
                                    edgeToEdgeContent = true,
                                    modifier = Modifier.rememberFocus("section_online_now"),
                                ) {
                                    OnlineUsersRow(
                                        users = socialState.onlineUsers,
                                        onUserSelected = onUserSelected,
                                    )
                                }
                            }

                            // Recent Activity section
                            if (socialState.activityEvents.isNotEmpty()) {
                                SpTitledSection(
                                    title = "Recent Activity",
                                    icon = Icons.Filled.History,
                                    modifier = Modifier.rememberFocus("section_recent_activity"),
                                    titleTrailing = {
                                        SeeAllLink(
                                            label = "Recent Activity",
                                            onClick = onNavigateToActivity,
                                        )
                                    },
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

                            // Personal Stats section
                            if (state.personalStats != null) {
                                SpTitledSection(
                                    title = "Your Stats",
                                    icon = Icons.Filled.BarChart,
                                    modifier = Modifier.rememberFocus("section_your_stats"),
                                    titleTrailing = {
                                        SeeAllLink(
                                            label = "Your Stats",
                                            onClick = onNavigateToStats,
                                        )
                                    },
                                ) {
                                    PersonalStatsCard(stats = state.personalStats!!)
                                }
                            }

                            // Bottom spacer
                            Spacer(Modifier.height(SpSpacing.XLarge))
                        } // SpSectionList
                        } // CompositionLocalProvider
                        } // SpMainContentPadding
                        } // SpScrollableContent
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
private fun DeviceNameBanner(
    deviceName: String,
    onSave: (String) -> Unit,
) {
    var nameInput by remember { mutableStateOf("") }

    AnimatedVisibility(
        visible = deviceName.isBlank(),
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = Modifier.testTag("device_name_banner"),
    ) {
        SpCard(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Name this device banner" },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpSpacing.Default),
            ) {
                Text(
                    text = "Name this device",
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnCard,
                )
                Spacer(Modifier.height(SpSpacing.XSmall))
                Text(
                    text = "Give this device a name so you can identify it across your account.",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
                Spacer(Modifier.height(SpSpacing.Medium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    SpTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder = "My device",
                        modifier = Modifier.weight(1f),
                    )
                    SpButton(
                        text = "Save",
                        onClick = {
                            if (nameInput.isNotBlank()) {
                                onSave(nameInput)
                            }
                        },
                        style = SpButtonStyle.Primary,
                        enabled = nameInput.isNotBlank(),
                    )
                }
            }
        }
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
        color = SpColor.Link,
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
