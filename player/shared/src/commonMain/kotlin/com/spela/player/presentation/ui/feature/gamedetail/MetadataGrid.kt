package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
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
    achievementTotal: Int = 0,
    achievementUnlocked: Int = 0,
    onDeveloperClick: ((name: String) -> Unit)? = null,
    onPublisherClick: ((name: String) -> Unit)? = null,
    onAchievementsClick: (() -> Unit)? = null,
) {
    val items = buildList {
        game.developer?.takeIf { it.isNotBlank() }?.let {
            add(MetaItem(Icons.Filled.Domain, if (isDemoConsole) "Group" else "Developer", it, onClick = onDeveloperClick?.let { cb -> { cb(game.developer) } }))
        }
        if (!isDemoConsole) {
            game.publisher?.takeIf { it.isNotBlank() }?.let {
                add(MetaItem(Icons.Filled.Domain, "Publisher", it, onClick = onPublisherClick?.let { cb -> { cb(game.publisher) } }))
            }
        }
        game.releaseDate?.takeIf { it.isNotBlank() }?.let {
            add(MetaItem(Icons.Filled.CalendarMonth, if (isDemoConsole) "Year" else "Released", it))
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
        if (achievementTotal > 0) {
            val value = if (achievementUnlocked > 0) {
                "$achievementUnlocked / $achievementTotal"
            } else {
                "$achievementTotal"
            }
            add(MetaItem(Icons.Filled.EmojiEvents, "Achievements", value, onClick = onAchievementsClick))
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
                    onClick = item.onClick,
                )
            }
        }
    }
}

private data class MetaItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun MetadataItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onGradient: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val secondaryColor = if (onGradient) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.50f) else SpColor.OnBackgroundTertiary
    val isClickable = onClick != null
    // Share one MutableInteractionSource between `.clickable` and
    // `.gamepadFocusable`. Without this the two modifiers each install
    // their own focus target with their own interaction source — focus
    // lands on .clickable's target (you see the default Material
    // darken), but gamepadFocusable's spFocusRing observes its OWN
    // source and never fires. Net result: invisible focus ring.
    // Same bug class is documented inline on `gamepadFocusable` (see
    // #1096). With a shared source we also set `addFocusable = false`
    // so we don't stack two focus targets.
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .padding(vertical = SpSpacing.XSmall)
            .then(
                if (onClick != null) Modifier
                    // Save focus per metadata link so back-nav from the
                    // developer / publisher detail screen restores focus
                    // to the link the user clicked, instead of dropping
                    // back to the screen's default-focus element.
                    .focusRestoreItem(key = "metadata_$label")
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                    .gamepadFocusable(
                        shape = RoundedCornerShape(SpSpacing.Small),
                        interactionSource = interactionSource,
                        addFocusable = false,
                    )
                else Modifier
            ),
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
                color = if (onGradient) androidx.compose.ui.graphics.Color.White else SpColor.OnBackground,
                textDecoration = if (isClickable) TextDecoration.Underline else TextDecoration.None,
            )
        }
    }
}
