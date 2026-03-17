package com.spela.player.presentation.ui.feature.gamedetail

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatBytes

/**
 * A 2-column metadata grid matching the web UI's MetaItem layout.
 * Displays game metadata with icon + label + value items.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MetadataGrid(
    game: Game,
    modifier: Modifier = Modifier,
    onGradient: Boolean = false,
    isDemoConsole: Boolean = false,
) {
    val items = buildList {
        game.developer?.takeIf { it.isNotBlank() }?.let {
            add(MetaItem(Icons.Filled.Domain, if (isDemoConsole) "Group" else "Developer", it))
        }
        if (!isDemoConsole) {
            game.publisher?.takeIf { it.isNotBlank() }?.let {
                add(MetaItem(Icons.Filled.Domain, "Publisher", it))
            }
        }
        game.releaseDate?.takeIf { it.isNotBlank() }?.let {
            add(MetaItem(Icons.Filled.CalendarMonth, "Released", it))
        }
        game.genre?.takeIf { it.isNotBlank() }?.let {
            add(MetaItem(Icons.Filled.Star, if (isDemoConsole) "Type" else "Genre", it))
        }
        if (isDemoConsole && game.partyInfo.isNotBlank()) {
            add(MetaItem(Icons.Filled.EmojiEvents, "Party", game.partyInfo))
        }
        if (!isDemoConsole && game.players > 0) {
            add(MetaItem(Icons.Filled.Group, "Players", "${game.players}"))
        }
        if (game.fileSize > 0) {
            add(MetaItem(Icons.Filled.SdStorage, "Size", formatBytes(game.fileSize)))
        }
        if (game.discs.size > 1) {
            add(MetaItem(Icons.Filled.Album, "Discs", "${game.discs.size}"))
        }
    }

    if (items.isEmpty()) return

    Column(modifier = modifier.testTag("metadata_grid")) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            maxItemsInEachRow = 2,
        ) {
            items.forEach { item ->
                MetadataItem(
                    icon = item.icon,
                    label = item.label,
                    value = item.value,
                    modifier = Modifier.weight(1f),
                    onGradient = onGradient,
                )
            }
        }
    }
}

private data class MetaItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

@Composable
private fun MetadataItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onGradient: Boolean = false,
) {
    val secondaryColor = if (onGradient) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.50f) else SpColor.OnBackgroundTertiary
    Row(
        modifier = modifier.padding(vertical = SpSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = secondaryColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(SpSpacing.Small))
        Column {
            Text(
                text = label,
                style = SpTypography.LabelSmall,
                color = secondaryColor,
            )
            Text(
                text = value,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackground,
            )
        }
    }
}
