package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun GameActionsMenu(
    isFavorite: Boolean,
    isInPlayLater: Boolean,
    onToggleFavorite: () -> Unit,
    onTogglePlayLater: () -> Unit,
    onAddToCollection: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SpButton(
            text = "",
            onClick = { expanded = true },
            style = SpButtonStyle.Secondary,
            modifier = Modifier.semantics {
                contentDescription = "More actions"
                role = Role.Button
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isFavorite) "Unfavorite" else "Favorite",
                        style = SpTypography.BodyMedium,
                    )
                },
                onClick = {
                    expanded = false
                    onToggleFavorite()
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isInPlayLater) "Remove from Play Later" else "Play Later",
                        style = SpTypography.BodyMedium,
                    )
                },
                onClick = {
                    expanded = false
                    onTogglePlayLater()
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isInPlayLater) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Add to Collection",
                        style = SpTypography.BodyMedium,
                    )
                },
                onClick = {
                    expanded = false
                    onAddToCollection()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.LibraryAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
    }
}
