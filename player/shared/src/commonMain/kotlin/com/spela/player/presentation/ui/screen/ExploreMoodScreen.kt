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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpGameCardSkeleton
import com.spela.player.presentation.ui.components.SpScreenContentList
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.sectionPillClearance
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel
import com.spela.player.util.parseHexColor

@Composable
fun ExploreMoodScreen(
    moodId: String,
    moodName: String,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.moodDetailState.collectAsState()

    LaunchedEffect(moodId) {
        viewModel.loadMoodGames(moodId, moodName)
    }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD
    val focusMemory = rememberFocusMemoryState()

    SpScreen(modifier = Modifier.testTag("explore_mood_screen")) {
        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (!isGamepad) {
                SpTopBar(
                    title = moodName,
                    showBack = true,
                    onBack = onBack,
                )
            }

            when {
                state.isLoading && state.games.isEmpty() -> {
                    // Loading skeleton
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = SpSpacing.ScreenHorizontal)
                            .testTag("mood_detail_loading"),
                    ) {
                        Spacer(Modifier.height(SpSpacing.Large))
                        repeat(4) {
                            SpGameCardSkeleton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = SpSpacing.Medium),
                            )
                        }
                    }
                }

                state.games.isNotEmpty() -> {
                    SpScreenContentList(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = sectionPillClearance(),
                            bottom = SpSpacing.XXLarge,
                        ),
                    ) {
                        // Mood banner with gradient
                        state.mood?.let { mood ->
                            item {
                                val gradientColors = mood.gradient.map { parseHexColor(it, Color(0xFF424242)) }.ifEmpty {
                                    listOf(SpColor.Primary.copy(alpha = 0.3f), SpColor.Background)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .background(Brush.verticalGradient(gradientColors + SpColor.Background))
                                        .testTag("mood_hero_banner"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = mood.icon,
                                            style = SpTypography.DisplaySmall,
                                        )
                                        Spacer(Modifier.height(SpSpacing.Small))
                                        Text(
                                            text = mood.description,
                                            style = SpTypography.BodyMedium,
                                            color = Color.White.copy(alpha = 0.85f),
                                        )
                                    }
                                }
                            }
                        }

                        // Game count
                        item {
                            Text(
                                text = "${state.games.size} games",
                                style = SpTypography.BodyMedium,
                                color = SpColor.OnBackgroundSecondary,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .padding(bottom = SpSpacing.Medium)
                                    .testTag("mood_game_count"),
                            )
                        }

                        // Game list
                        items(
                            items = state.games,
                            key = { it.id },
                        ) { game ->
                            val itemModifier = Modifier
                                .padding(horizontal = SpSpacing.ScreenHorizontal)
                                .focusRestoreItem(
                                    key = "mood_${moodId}_${game.id}",
                                    isDefault = game == state.games.firstOrNull(),
                                )
                            MoodGameItem(
                                game = game,
                                onClick = { onGameSelected(game.id) },
                                modifier = itemModifier,
                            )
                        }
                    }
                }

                state.error == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.SentimentSatisfied,
                            title = "No games found",
                            message = "No games match this mood right now.",
                            modifier = Modifier.testTag("mood_empty_state"),
                        )
                    }
                }
            }
        }

        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.dismissMoodDetailError() },
                )
            },
            onDismiss = { viewModel.dismissMoodDetailError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        } // CompositionLocalProvider
    }
}

@Composable
private fun MoodGameItem(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SpSpacing.XSmall)
            .testTag("mood_game_${game.id}")
            .semantics {
                contentDescription = buildString {
                    append(game.title)
                    if (game.consoleName.isNotEmpty()) append(", ${game.consoleName}")
                }
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            // Cover art
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover art",
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(SpSpacing.RadiusSmall)),
                aspectRatio = game.coverAspectRatio,
            )

            // Game info
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = game.title,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (game.consoleName.isNotEmpty()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = game.consoleName,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }

                game.genre?.let { genre ->
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = genre,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }
        }
    }
}
