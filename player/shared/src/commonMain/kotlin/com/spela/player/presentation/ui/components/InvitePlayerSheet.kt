package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.domain.model.UserSearchResult
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * A dialog for searching and inviting players.
 * Used by both Netplay Lobby and Shared Session Detail screens.
 */
@Composable
fun InvitePlayerSheet(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<UserSearchResult>,
    searchTotal: Long,
    searchPage: Int,
    pageSize: Int = 20,
    recentPartners: List<UserSearchResult>,
    isSearching: Boolean,
    isLoadingRecentPartners: Boolean,
    invitingUsername: String?,
    invitedUsernames: List<String>,
    onInvite: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Invite Player",
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = SpColor.OnBackgroundTertiary,
                    )
                }
            }

            Spacer(Modifier.height(SpSpacing.Default))

            // Search field
            SpTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Search users...",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(SpSpacing.Default))

            // Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                // Recent partners section (only when query is empty and partners exist)
                if (searchQuery.isEmpty() && recentPartners.isNotEmpty()) {
                    item {
                        Text(
                            text = "Previous",
                            style = SpTypography.TitleLarge,
                            color = SpColor.OnBackgroundSecondary,
                        )
                        Spacer(Modifier.height(SpSpacing.Small))
                    }
                    items(recentPartners, key = { "partner-${it.id}" }) { user ->
                        UserInviteRow(
                            user = user,
                            isInvited = user.username in invitedUsernames,
                            isSendingThisUser = invitingUsername == user.username,
                            onInvite = { onInvite(user.username) },
                        )
                    }
                    item {
                        Spacer(Modifier.height(SpSpacing.Default))
                    }
                }

                if (isLoadingRecentPartners && searchQuery.isEmpty() && recentPartners.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = SpSpacing.Default),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = SpColor.Primary,
                            )
                        }
                    }
                }

                // User list section
                item {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Results" else "All Users",
                        style = SpTypography.TitleLarge,
                        color = SpColor.OnBackgroundSecondary,
                    )
                    Spacer(Modifier.height(SpSpacing.Small))
                }

                if (isSearching) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = SpSpacing.Default),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = SpColor.Primary,
                            )
                        }
                    }
                } else {
                    if (searchResults.isEmpty()) {
                        item {
                            Text(
                                text = "No users found",
                                style = SpTypography.BodyMedium,
                                color = SpColor.OnBackgroundTertiary,
                                modifier = Modifier.padding(vertical = SpSpacing.Default),
                            )
                        }
                    } else {
                        items(searchResults, key = { "user-${it.id}" }) { user ->
                            UserInviteRow(
                                user = user,
                                isInvited = user.username in invitedUsernames,
                                isSendingThisUser = invitingUsername == user.username,
                                onInvite = { onInvite(user.username) },
                            )
                        }
                    }
                }

                // Pagination controls
                val totalPages = if (pageSize > 0) ((searchTotal + pageSize - 1) / pageSize).toInt() else 1
                if (totalPages > 1) {
                    item {
                        Spacer(Modifier.height(SpSpacing.Default))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SpButton(
                                text = "Previous",
                                onClick = { onPageChange(searchPage - 1) },
                                style = SpButtonStyle.Ghost,
                                enabled = searchPage > 1,
                            )
                            Spacer(Modifier.width(SpSpacing.Small))
                            Text(
                                text = "$searchPage / $totalPages",
                                style = SpTypography.BodyMedium,
                                color = SpColor.OnBackgroundSecondary,
                            )
                            Spacer(Modifier.width(SpSpacing.Small))
                            SpButton(
                                text = "Next",
                                onClick = { onPageChange(searchPage + 1) },
                                style = SpButtonStyle.Ghost,
                                enabled = searchPage < totalPages,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserInviteRow(
    user: UserSearchResult,
    isInvited: Boolean,
    isSendingThisUser: Boolean,
    onInvite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SpSpacing.XSmall)
            .semantics {
                contentDescription = if (isInvited) {
                    "${user.username}, already invited"
                } else {
                    "${user.username}, tap to invite"
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpAvatar(
            username = user.username,
            avatarUrl = user.avatarUrl,
            size = 36.dp,
        )
        Spacer(Modifier.width(SpSpacing.Medium))
        Text(
            text = user.username,
            style = SpTypography.TitleLarge,
            color = SpColor.OnBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isInvited) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Invited",
                tint = SpColor.Success,
                modifier = Modifier.size(24.dp),
            )
        } else {
            SpSecondaryButton(
                text = "Invite",
                onClick = onInvite,
                enabled = !isSendingThisUser,
                isLoading = isSendingThisUser,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}
