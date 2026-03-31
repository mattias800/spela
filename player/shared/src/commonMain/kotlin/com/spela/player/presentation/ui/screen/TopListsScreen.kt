package com.spela.player.presentation.ui.screen

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.LongestGame
import com.spela.player.domain.model.TopListGame
import com.spela.player.domain.model.TopListTab
import com.spela.player.presentation.intent.TopListsIntent
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.gamedetail.formatTimeToBeat
import com.spela.player.presentation.ui.feature.stats.RankBadge
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.autoFocus
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.rememberFocus
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.TopListsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopListsScreen(
    viewModel: TopListsViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(TopListsIntent.LoadTopLists)
    }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    SpScreen(modifier = Modifier.testTag("top_lists_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (isGamepad) {
                SpScreenTopSpacer()
            } else {
                SpTopBar(
                    title = "Top Lists",
                    showBack = true,
                    onBack = onBack,
                )
            }

            // Tab bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                SpChip(
                    text = "Top Rated",
                    isSelected = state.selectedTab == TopListTab.TOP_RATED,
                    onClick = { viewModel.onIntent(TopListsIntent.SelectTab(TopListTab.TOP_RATED)) },
                    color = SpColor.Warning,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (state.selectedTab == TopListTab.TOP_RATED) SpColor.Warning else SpColor.OnBackgroundSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    modifier = Modifier.autoFocus().testTag("tab_top_rated"),
                )
                SpChip(
                    text = "Longest",
                    isSelected = state.selectedTab == TopListTab.LONGEST,
                    onClick = { viewModel.onIntent(TopListsIntent.SelectTab(TopListTab.LONGEST)) },
                    color = SpColor.Accent,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = if (state.selectedTab == TopListTab.LONGEST) SpColor.Accent else SpColor.OnBackgroundSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    modifier = Modifier.testTag("tab_longest"),
                )
            }

            val isContentEmpty = when (state.selectedTab) {
                TopListTab.TOP_RATED -> state.games.isEmpty()
                TopListTab.LONGEST -> state.longestGames.isEmpty()
            }

            if (state.isLoading && isContentEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading top lists...")
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.onIntent(TopListsIntent.LoadTopLists) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (isContentEmpty && !state.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize().testTag("top_lists_empty"),
                            contentAlignment = Alignment.Center,
                        ) {
                            val (emptyIcon, emptyTitle, emptyMessage) = when (state.selectedTab) {
                                TopListTab.TOP_RATED -> Triple(
                                    Icons.Filled.EmojiEvents,
                                    "No top rated games yet",
                                    "Top rated games from your library will appear here",
                                )
                                TopListTab.LONGEST -> Triple(
                                    Icons.Filled.AccessTime,
                                    "No time-to-beat data yet",
                                    "Games with time-to-beat information will appear here",
                                )
                            }
                            SpEmptyState(
                                icon = emptyIcon,
                                title = emptyTitle,
                                message = emptyMessage,
                            )
                        }
                    } else {
                        val focusMemory = rememberFocusMemoryState()
                        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
                        when (state.selectedTab) {
                            TopListTab.TOP_RATED -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().testTag("top_lists_content"),
                                    contentPadding = PaddingValues(vertical = SpSpacing.Default),
                                ) {
                                    itemsIndexed(
                                        state.games,
                                        key = { _, item -> "toplist-${item.gameId}" },
                                    ) { _, item ->
                                        TopListGameItem(
                                            game = item,
                                            onClick = { onGameSelected(item.gameId) },
                                            modifier = Modifier.rememberFocus("toplist_${item.gameId}"),
                                        )
                                    }
                                }
                            }
                            TopListTab.LONGEST -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().testTag("longest_games_content"),
                                    contentPadding = PaddingValues(vertical = SpSpacing.Default),
                                ) {
                                    itemsIndexed(
                                        state.longestGames,
                                        key = { _, item -> "longest-${item.gameId}" },
                                    ) { _, item ->
                                        LongestGameItem(
                                            game = item,
                                            onClick = { onGameSelected(item.gameId) },
                                            modifier = Modifier.rememberFocus("longest_${item.gameId}"),
                                        )
                                    }
                                }
                            }
                        }
                        } // CompositionLocalProvider
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
                    onAction = { viewModel.onIntent(TopListsIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(TopListsIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopListGameItem(
    game: TopListGame,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall)
            .testTag("top_list_item_${game.gameId}")
            .semantics {
                contentDescription = "Rank ${game.rank}: ${game.name}, rated %.1f".format(game.rating)
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(game.rank)
            Spacer(Modifier.width(SpSpacing.Medium))
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.name} cover",
                modifier = Modifier.height(64.dp),
                cornerRadius = SpSpacing.RadiusMedium,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (game.consoleName.isNotBlank()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = game.consoleName,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(SpSpacing.XSmall))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = SpColor.Warning,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "%.1f".format(game.rating),
                        style = SpTypography.LabelMedium.copy(fontWeight = FontWeight.Bold),
                        color = SpColor.OnCard,
                    )
                }
            }
        }
    }
}

@Composable
private fun LongestGameItem(
    game: LongestGame,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryTime = when {
        game.timeToBeatNormally > 0 -> game.timeToBeatNormally
        game.timeToBeatHastily > 0 -> game.timeToBeatHastily
        else -> game.timeToBeatCompletely
    }

    SpCard(
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall)
            .testTag("longest_game_item_${game.gameId}")
            .semantics {
                contentDescription = "Rank ${game.rank}: ${game.name}, ${formatTimeToBeat(primaryTime)} to beat"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(game.rank)
            Spacer(Modifier.width(SpSpacing.Medium))
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.name} cover",
                modifier = Modifier.height(64.dp),
                cornerRadius = SpSpacing.RadiusMedium,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (game.consoleName.isNotBlank()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = game.consoleName,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(SpSpacing.XSmall))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    // Primary time (normally)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = SpColor.Accent,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = formatTimeToBeat(primaryTime),
                            style = SpTypography.LabelMedium.copy(fontWeight = FontWeight.Bold),
                            color = SpColor.OnCard,
                        )
                    }
                    // Secondary times
                    if (game.timeToBeatHastily > 0 && game.timeToBeatHastily != primaryTime) {
                        Text(
                            text = formatTimeToBeat(game.timeToBeatHastily),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                    if (game.timeToBeatCompletely > 0 && game.timeToBeatCompletely != primaryTime) {
                        Text(
                            text = "${formatTimeToBeat(game.timeToBeatCompletely)} (100%)",
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }
        }
    }
}
