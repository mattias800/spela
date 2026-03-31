package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.DeveloperSummary
import com.spela.player.domain.model.GenreCount
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpDeveloperCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.gamepad.rememberFocus
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel
import com.spela.player.util.formatRating
import com.spela.player.util.parseHexColor

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
        )
    }
}

@Composable
fun ConsoleGenreBreakdown(
    viewModel: ExploreViewModel,
) {
    val state by viewModel.consoleShowcaseState.collectAsState()
    val showcase = state.showcase ?: return
    if (showcase.genreBreakdown.isEmpty()) return

    val accentColor = parseHexColor(showcase.console.colorTheme, SpColor.Primary)

    SpTitledSection(
        title = "Genre Breakdown",
        edgeToEdgeContent = true,
        modifier = Modifier
            .rememberFocus("section_genre_breakdown")
            .testTag("console_genre_breakdown_section"),
    ) {
        GenreBreakdownChips(
            genres = showcase.genreBreakdown,
            accentColor = accentColor,
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
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GenreBreakdownChips(
    genres: List<GenreCount>,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .testTag("genre_breakdown_chips"),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        genres.forEach { genre ->
            SpChip(
                text = "${genre.name} (${genre.gameCount})",
                color = accentColor,
                modifier = Modifier
                    .testTag("genre_chip_${genre.name}")
                    .semantics {
                        contentDescription = "${genre.name}, ${genre.gameCount} games"
                    },
            )
        }
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
