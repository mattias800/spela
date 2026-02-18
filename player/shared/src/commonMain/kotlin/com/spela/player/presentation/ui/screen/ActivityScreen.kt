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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ActivityEvent
import com.spela.player.presentation.intent.SocialIntent
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.social.formatRelativeTime
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: SocialViewModel,
    onGameSelected: (String) -> Unit,
    onUserSelected: (String) -> Unit,
    onNavigateToStats: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(SocialIntent.LoadFullActivityFeed)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(
            title = "Activity",
            actions = {
                SpIconButton(
                    icon = Icons.Filled.BarChart,
                    contentDescription = "Stats",
                    onClick = onNavigateToStats,
                )
            },
        )

        if (state.isLoadingFullActivity && state.fullActivityEvents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SpLoadingIndicator(message = "Loading activity...")
            }
        } else {
            PullToRefreshBox(
                isRefreshing = state.isLoadingFullActivity && state.fullActivityEvents.isNotEmpty(),
                onRefresh = { viewModel.onIntent(SocialIntent.LoadFullActivityFeed) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.fullActivityEvents.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        NoActivity()
                    }
                } else {
                    val listState = rememberLazyListState()

                    // Load more when nearing the end
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisibleIndex >= state.fullActivityEvents.size - 5
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore && state.hasMoreActivity && !state.isLoadingFullActivity) {
                            viewModel.onIntent(SocialIntent.LoadMoreActivity)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = SpSpacing.Default),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                    ) {
                        items(
                            state.fullActivityEvents,
                            key = { it.id },
                        ) { event ->
                            ActivityFeedItem(
                                event = event,
                                onGameSelected = onGameSelected,
                                onUserSelected = onUserSelected,
                            )
                        }

                        if (state.isLoadingFullActivity) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(SpSpacing.Large),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = SpColor.Primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityFeedItem(
    event: ActivityEvent,
    onGameSelected: (String) -> Unit,
    onUserSelected: (String) -> Unit,
) {
    val actionText = formatActivityAction(event)
    val description = "${event.username} $actionText"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                event.gameId?.let { onGameSelected(it) }
            }
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Medium,
            )
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.Top,
    ) {
        // User avatar
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onUserSelected(event.userId) },
        ) {
            SpAvatar(
                username = event.username,
                avatarUrl = event.userAvatarUrl,
                size = 40.dp,
                placeholderTextStyle = SpTypography.TitleMedium,
            )
        }

        Spacer(Modifier.width(SpSpacing.Medium))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, iconTint) = getEventIconAndColor(event.eventType)
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(SpSpacing.XSmall))

                val annotated = buildAnnotatedString {
                    withStyle(SpTypography.BodyMedium.copy(color = SpColor.OnBackground).toSpanStyle()) {
                        append(event.username)
                    }
                    withStyle(SpTypography.BodyMedium.copy(color = SpColor.OnBackgroundSecondary).toSpanStyle()) {
                        append(" $actionText")
                    }
                }
                Text(
                    text = annotated,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(SpSpacing.XXSmall))

            // Console badge + timestamp
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!event.gameConsoleName.isNullOrBlank()) {
                    Text(
                        text = event.gameConsoleName,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
                            .background(SpColor.SurfaceVariant)
                            .padding(horizontal = SpSpacing.XSmall, vertical = SpSpacing.XXSmall),
                    )
                    Spacer(Modifier.width(SpSpacing.Small))
                }
                Text(
                    text = formatRelativeTime(event.createdAt),
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }

        // Game cover thumbnail
        if (event.gameCoverUrl != null) {
            Spacer(Modifier.width(SpSpacing.Medium))
            SpCoverArt(
                imageUrl = event.gameCoverUrl,
                contentDescription = event.gameTitle?.let { "$it cover" } ?: "Game cover",
                modifier = Modifier.size(width = 40.dp, height = 56.dp),
                cornerRadius = SpSpacing.RadiusDefault,
            )
        }
    }
}

@Composable
private fun NoActivity() {
    SpEmptyState(
        icon = Icons.Filled.PlayArrow,
        title = "No activity yet",
        message = "Play some games to see activity here",
    )
}

private fun formatActivityAction(event: ActivityEvent): String {
    val gameName = event.gameTitle ?: "a game"
    return when (event.eventType) {
        "started_playing" -> "started playing $gameName"
        "favorited_game" -> "favorited $gameName"
        "rated_game" -> "rated $gameName"
        "shared_save" -> "shared a save for $gameName"
        "queued_play_later" -> "added $gameName to Play Later"
        "challenge_created" -> "created a challenge for $gameName"
        "challenge_completed" -> "completed a challenge for $gameName"
        "challenge_record" -> "set a new record on a challenge for $gameName"
        else -> "did something with $gameName"
    }
}

private fun getEventIconAndColor(eventType: String): Pair<ImageVector, androidx.compose.ui.graphics.Color> {
    return when (eventType) {
        "started_playing" -> Icons.Filled.PlayArrow to SpColor.Success
        "favorited_game" -> Icons.Filled.Favorite to SpColor.Secondary
        "rated_game" -> Icons.Filled.Star to SpColor.Warning
        "shared_save" -> Icons.Filled.Save to SpColor.Accent
        "queued_play_later" -> Icons.Filled.WatchLater to SpColor.Accent
        "challenge_created" -> Icons.Filled.Flag to SpColor.Primary
        "challenge_completed" -> Icons.Filled.Flag to SpColor.Success
        "challenge_record" -> Icons.Filled.Timer to SpColor.Warning
        else -> Icons.Filled.PlayArrow to SpColor.OnBackgroundTertiary
    }
}
