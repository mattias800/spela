package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpGameCardSkeleton
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel
import com.spela.player.util.formatRating

@Composable
fun ExploreDeveloperScreen(
    name: String,
    isDeveloper: Boolean = true,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.developerDetailState.collectAsState()

    LaunchedEffect(name, isDeveloper) {
        if (isDeveloper) {
            viewModel.loadDeveloperDetail(name)
        } else {
            viewModel.loadPublisherDetail(name)
        }
    }

    Box(modifier = Modifier.fillMaxSize().testTag("developer_detail_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = name,
                showBack = true,
                onBack = onBack,
            )

            when {
                state.isLoading && state.detail == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(SpSpacing.ScreenHorizontal)
                            .testTag("developer_detail_loading"),
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

                state.detail != null -> {
                    val detail = state.detail!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = SpSpacing.XXLarge),
                    ) {
                        // Stats banner
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                SpColor.Primary.copy(alpha = 0.3f),
                                                SpColor.Background,
                                            ),
                                        ),
                                    )
                                    .testTag("developer_stats_banner"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = detail.name,
                                        style = SpTypography.DisplaySmall,
                                        color = SpColor.OnBackground,
                                    )
                                    Spacer(Modifier.height(SpSpacing.Small))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                                    ) {
                                        Text(
                                            text = "${detail.gameCount} games",
                                            style = SpTypography.BodyMedium,
                                            color = SpColor.OnBackgroundSecondary,
                                            modifier = Modifier.testTag("developer_game_count"),
                                        )
                                        if (detail.avgRating > 0) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Star,
                                                    contentDescription = null,
                                                    tint = SpColor.Rating,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Text(
                                                    text = formatRating(detail.avgRating),
                                                    style = SpTypography.BodyMedium,
                                                    color = SpColor.OnBackgroundSecondary,
                                                    modifier = Modifier.testTag("developer_avg_rating"),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Console filter chips
                        if (detail.consoles.isNotEmpty()) {
                            item {
                                DeveloperConsoleFilterRow(
                                    consoles = detail.consoles,
                                    totalGames = detail.gameCount,
                                    selectedConsole = state.consoleFilter,
                                    onConsoleSelected = { console ->
                                        viewModel.setDeveloperConsoleFilter(
                                            if (console != null && state.consoleFilter == console) null else console,
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                                        .testTag("developer_console_filters"),
                                )
                                Spacer(Modifier.height(SpSpacing.Large))
                            }
                        }

                        // Games grid
                        val filteredGames = state.filteredGames
                        if (filteredGames.isEmpty() && !state.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SpSpacing.XXLarge),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SpEmptyState(
                                        icon = Icons.Filled.Code,
                                        title = "No games found",
                                        message = "No games match the selected filter.",
                                        modifier = Modifier.testTag("developer_empty_state"),
                                    )
                                }
                            }
                        } else {
                            items(
                                items = filteredGames,
                                key = { it.id },
                            ) { game ->
                                DeveloperGameItem(
                                    game = game,
                                    onClick = { onGameSelected(game.id) },
                                )
                            }
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.Code,
                            title = if (isDeveloper) "Developer not found" else "Publisher not found",
                            message = "Could not load details.",
                            modifier = Modifier.testTag("developer_error_state"),
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
                    onAction = { viewModel.dismissDeveloperDetailError() },
                )
            },
            onDismiss = { viewModel.dismissDeveloperDetailError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperConsoleFilterRow(
    consoles: List<String>,
    totalGames: Int,
    selectedConsole: String?,
    onConsoleSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        // "All" chip
        SpChip(
            text = "All ($totalGames)",
            onClick = { onConsoleSelected(null) },
            isSelected = selectedConsole == null,
            modifier = Modifier
                .testTag("developer_console_chip_all")
                .semantics {
                    contentDescription = "All, $totalGames games"
                    role = Role.Button
                },
        )

        consoles.forEach { console ->
            val isSelected = console.equals(selectedConsole, ignoreCase = true)

            SpChip(
                text = console,
                onClick = { onConsoleSelected(console) },
                isSelected = isSelected,
                modifier = Modifier
                    .testTag("developer_console_chip_$console")
                    .semantics {
                        contentDescription = console
                        role = Role.Button
                    },
            )
        }
    }
}

@Composable
private fun DeveloperGameItem(
    game: Game,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.XSmall,
            )
            .testTag("developer_game_${game.id}")
            .semantics {
                contentDescription = "${game.title}, ${game.consoleName}"
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
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover art",
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(SpSpacing.RadiusSmall)),
                aspectRatio = 0.75f,
            )

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

                Spacer(Modifier.height(SpSpacing.XXSmall))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    Text(
                        text = game.consoleName,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )

                    if (game.rating > 0) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SpColor.Rating,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = formatRating(game.rating),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }
        }
    }
}
