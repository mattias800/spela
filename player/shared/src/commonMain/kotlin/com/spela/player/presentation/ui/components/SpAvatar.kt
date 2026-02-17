package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Displays a user avatar with image loading and a letter-initial placeholder fallback.
 *
 * @param username The username used for the fallback initial letter
 * @param avatarUrl Optional URL for the user's avatar image
 * @param size The diameter of the avatar circle
 * @param modifier Modifier for the root Box
 * @param placeholderTextStyle Text style for the placeholder initial. Defaults to TitleSmall
 *        for avatars <= 40.dp, TitleLarge for larger avatars.
 */
@Composable
fun SpAvatar(
    username: String,
    avatarUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    placeholderTextStyle: TextStyle = if (size <= 40.dp) SpTypography.TitleSmall else SpTypography.TitleLarge,
) {
    Box(modifier = modifier.size(size)) {
        if (avatarUrl != null) {
            SubcomposeAsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = {
                    AvatarPlaceholder(username = username, textStyle = placeholderTextStyle)
                },
                error = {
                    AvatarPlaceholder(username = username, textStyle = placeholderTextStyle)
                },
            )
        } else {
            AvatarPlaceholder(username = username, textStyle = placeholderTextStyle)
        }
    }
}

@Composable
private fun AvatarPlaceholder(
    username: String,
    textStyle: TextStyle,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(SpColor.SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = username.take(1).uppercase(),
            style = textStyle,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}
