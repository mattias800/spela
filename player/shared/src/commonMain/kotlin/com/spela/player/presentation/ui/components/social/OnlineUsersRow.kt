package com.spela.player.presentation.ui.components.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.OnlineUser
import androidx.compose.ui.focus.focusRequester
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun OnlineUsersRow(
    users: List<OnlineUser>,
    modifier: Modifier = Modifier,
    onUserSelected: (String) -> Unit = {},
) {
    SpCarousel(
        itemCount = users.size,
        modifier = modifier,
        memoryKey = "home_online_users",
        itemKey = { users[it].id },
    ) { index, focusRequester ->
        OnlineUserItem(
            user = users[index],
            onUserSelected = onUserSelected,
            modifier = Modifier.focusRequester(focusRequester),
        )
    }
}

@Composable
private fun OnlineUserItem(
    user: OnlineUser,
    onUserSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = if (user.currentGame != null) {
        "${user.username} playing ${user.currentGame.title}"
    } else {
        "${user.username} online"
    }

    Column(
        modifier = modifier
            .width(64.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable { onUserSelected(user.id) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            // Avatar
            SpAvatar(
                username = user.username,
                avatarUrl = user.avatarUrl,
                size = 48.dp,
            )

            // Online indicator dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .clip(CircleShape)
                    .background(SpColor.Background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(SpColor.Success),
            )
        }

        Text(
            text = user.username,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SpSpacing.XSmall),
        )

        if (user.currentGame != null) {
            Text(
                text = user.currentGame.title,
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

