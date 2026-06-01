package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.TestTags
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun GameActionsMenu(
    isFavorite: Boolean,
    isInPlayLater: Boolean,
    onToggleFavorite: () -> Unit,
    onTogglePlayLater: () -> Unit,
    onAddToCollection: () -> Unit,
    onGradient: Boolean = false,
    /**
     * Optional "Save state settings" menu item — opens the per-game
     * save-state policy override sheet. Provided only for consoles
     * whose save-state tier is Medium or Large (where the override
     * is actually meaningful); for Small-tier consoles this is null
     * and no menu item renders, since the per-game opt-out is
     * pointless when the console itself never opted out.
     */
    onSaveStateSettings: (() -> Unit)? = null,
    /**
     * Optional "Show in folder" menu item — opens the downloaded game's
     * folder in the OS file manager. Provided only for downloaded games on
     * platforms that can reveal it (desktop); null on Android, where
     * downloads live in app-private storage. (#1259)
     */
    onOpenDownloadFolder: (() -> Unit)? = null,
    onAdminScrape: (() -> Unit)? = null,
    onAdminRefreshAchievements: (() -> Unit)? = null,
    isAdminActionLoading: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SpSecondaryButton(
            text = "",
            onClick = { expanded = true },
            onGradient = onGradient,
            modifier = Modifier
                .testTag(TestTags.GAME_DETAIL_MORE_ACTIONS)
                .semantics {
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
                modifier = Modifier.testTag(TestTags.GAME_DETAIL_MENU_FAVORITE),
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
                modifier = Modifier.testTag(TestTags.GAME_DETAIL_MENU_PLAY_LATER),
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
                modifier = Modifier.testTag(TestTags.GAME_DETAIL_MENU_ADD_TO_COLLECTION),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.LibraryAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            if (onOpenDownloadFolder != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Show in folder",
                            style = SpTypography.BodyMedium,
                        )
                    },
                    onClick = {
                        expanded = false
                        onOpenDownloadFolder()
                    },
                    modifier = Modifier.testTag("game_detail_menu_open_download_folder"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }

            if (onSaveStateSettings != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Save state settings",
                            style = SpTypography.BodyMedium,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSaveStateSettings()
                    },
                    modifier = Modifier.testTag("game_detail_menu_save_state_settings"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }

            // Admin actions
            if (onAdminScrape != null || onAdminRefreshAchievements != null) {
                HorizontalDivider()
                if (onAdminScrape != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isAdminActionLoading) "Scraping…" else "Scrape Metadata",
                                style = SpTypography.BodyMedium,
                            )
                        },
                        onClick = {
                            expanded = false
                            onAdminScrape()
                        },
                        enabled = !isAdminActionLoading,
                        leadingIcon = {
                            if (isAdminActionLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
                if (onAdminRefreshAchievements != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Update Achievements",
                                style = SpTypography.BodyMedium,
                            )
                        },
                        onClick = {
                            expanded = false
                            onAdminRefreshAchievements()
                        },
                        enabled = !isAdminActionLoading,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}
