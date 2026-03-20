package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.spela.player.domain.model.DeveloperGame
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpGameCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun DeveloperGamesSection(
    games: List<DeveloperGame>,
    developerName: String?,
    onGameSelected: (String) -> Unit,
) {
    if (games.isEmpty()) return

    val title = if (developerName != null) "More from $developerName" else "More from Developer"

    SpTitledSection(
        title = title,
        icon = Icons.Outlined.Business,
        edgeToEdgeContent = true,
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = SpSpacing.XLarge),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            items(games, key = { it.id }) { game ->
                DeveloperGameCard(
                    game = game,
                    onClick = { onGameSelected(game.id) },
                )
            }
        }
    }
}

@Composable
private fun DeveloperGameCard(
    game: DeveloperGame,
    onClick: () -> Unit,
) {
    SpGameCard(
        title = game.title,
        subtitle = game.consoleName,
        coverUrl = game.coverUrl,
        onClick = onClick,
    )
}
