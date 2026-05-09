package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.DeveloperSummary
import com.spela.player.presentation.ui.components.SpDeveloperCard
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.gamepad.rememberFocus
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.ExploreViewModel

@Composable
fun ConsoleEssentials(
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
) {
    val state by viewModel.consoleShowcaseState.collectAsState()
    val showcase = state.showcase ?: return
    if (showcase.essentials.isEmpty()) return

    SpTitledSection(
        title = "Essentials",
        edgeToEdgeContent = true,
        modifier = Modifier
            .rememberFocus("section_essentials")
            .testTag("console_essentials_section"),
    ) {
        GameShelf(
            games = showcase.essentials,
            onGameSelected = onGameSelected,
            memoryKey = "console_showcase_essentials",
        )
    }
}

@Composable
fun ConsoleHiddenGems(
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
) {
    val state by viewModel.consoleShowcaseState.collectAsState()
    val showcase = state.showcase ?: return
    if (showcase.hiddenGems.isEmpty()) return

    SpTitledSection(
        title = "Hidden Gems",
        edgeToEdgeContent = true,
        modifier = Modifier
            .rememberFocus("section_hidden_gems")
            .testTag("console_hidden_gems_section"),
    ) {
        GameShelf(
            games = showcase.hiddenGems,
            onGameSelected = onGameSelected,
            memoryKey = "console_showcase_hidden_gems",
        )
    }
}

@Composable
fun ConsoleLaunchGames(
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
) {
    val state by viewModel.consoleShowcaseState.collectAsState()
    val showcase = state.showcase ?: return
    if (showcase.launchGames.isEmpty()) return

    SpTitledSection(
        title = "Launch Games",
        edgeToEdgeContent = true,
        modifier = Modifier
            .rememberFocus("section_launch_games")
            .testTag("console_launch_games_section"),
    ) {
        GameShelf(
            games = showcase.launchGames,
            onGameSelected = onGameSelected,
            memoryKey = "console_showcase_launch_games",
        )
    }
}

@Composable
fun ConsoleTopDevelopers(
    viewModel: ExploreViewModel,
    onDeveloperSelected: (String) -> Unit,
) {
    val state by viewModel.consoleShowcaseState.collectAsState()
    val showcase = state.showcase ?: return
    if (showcase.topDevelopers.isEmpty()) return

    SpTitledSection(
        title = "Top Developers",
        modifier = Modifier
            .rememberFocus("section_top_developers")
            .testTag("console_top_developers_section"),
    ) {
        TopDevelopersList(
            developers = showcase.topDevelopers,
            onDeveloperSelected = onDeveloperSelected,
        )
    }
}

@Composable
fun ConsoleRecentlyPlayed(
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
) {
    val state by viewModel.consoleShowcaseState.collectAsState()
    val showcase = state.showcase ?: return
    if (showcase.recentlyPlayed.isEmpty()) return

    SpTitledSection(
        title = "Recently Played",
        edgeToEdgeContent = true,
        modifier = Modifier
            .rememberFocus("section_recently_played")
            .testTag("console_recently_played_section"),
    ) {
        GameShelf(
            games = showcase.recentlyPlayed,
            onGameSelected = onGameSelected,
            memoryKey = "console_showcase_recently_played",
        )
    }
}

@Composable
fun ConsoleRecentlyAdded(
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
) {
    val state by viewModel.consoleShowcaseState.collectAsState()
    val showcase = state.showcase ?: return
    if (showcase.recentlyAdded.isEmpty()) return

    SpTitledSection(
        title = "Recently Added",
        edgeToEdgeContent = true,
        modifier = Modifier
            .rememberFocus("section_recently_added")
            .testTag("console_recently_added_section"),
    ) {
        GameShelf(
            games = showcase.recentlyAdded,
            onGameSelected = onGameSelected,
            memoryKey = "console_showcase_recently_added",
        )
    }
}

@Composable
internal fun TopDevelopersList(
    developers: List<DeveloperSummary>,
    onDeveloperSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag("top_developers_list"),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        developers.forEach { developer ->
            ConsoleDeveloperCard(
                developer = developer,
                onClick = { onDeveloperSelected(developer.name) },
            )
        }
    }
}

/** ROLE component — a developer card in the console "Top Developers" section. Delegates to [SpDeveloperCard]. */
@Composable
private fun ConsoleDeveloperCard(
    developer: DeveloperSummary,
    onClick: () -> Unit,
) {
    SpDeveloperCard(
        name = developer.name,
        gameCount = developer.gameCount,
        avgRating = developer.avgRating,
        onClick = onClick,
        testTag = "developer_card_${developer.name}",
    )
}
