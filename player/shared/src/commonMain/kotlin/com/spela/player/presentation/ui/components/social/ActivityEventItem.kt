package com.spela.player.presentation.ui.components.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.domain.model.ActivityEvent
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlin.time.Clock
import kotlin.time.Duration

@Composable
fun ActivityEventItem(
    event: ActivityEvent,
    modifier: Modifier = Modifier,
) {
    val actionText = formatActivityAction(event)
    val description = "${event.username} $actionText"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Small,
            )
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // User avatar
        Box(modifier = Modifier.size(36.dp)) {
            if (event.userAvatarUrl != null) {
                SubcomposeAsyncImage(
                    model = event.userAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    loading = { SmallAvatarPlaceholder(event.username) },
                    error = { SmallAvatarPlaceholder(event.username) },
                )
            } else {
                SmallAvatarPlaceholder(event.username)
            }
        }

        Spacer(Modifier.width(SpSpacing.Medium))

        // Event icon
        val (icon, iconTint) = getEventIconAndColor(event.eventType)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )

        Spacer(Modifier.width(SpSpacing.Small))

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            val annotated = buildAnnotatedString {
                withStyle(SpTypography.BodyMedium.copy(color = SpColor.OnBackground).toSpanStyle()) {
                    append(event.username)
                }
                withStyle(SpTypography.BodyMedium.copy(color = SpColor.OnBackgroundSecondary).toSpanStyle()) {
                    append(" $actionText")
                }
            }
            Text(
                text = annotated,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = formatRelativeTime(event.createdAt),
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.padding(top = SpSpacing.XXSmall),
            )
        }
    }
}

@Composable
private fun SmallAvatarPlaceholder(username: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(SpColor.SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = username.take(1).uppercase(),
            style = SpTypography.TitleSmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}

private fun formatActivityAction(event: ActivityEvent): String {
    val gameName = event.gameTitle ?: "a game"
    return when (event.eventType) {
        "started_playing" -> "started playing $gameName"
        "favorited_game" -> "favorited $gameName"
        "rated_game" -> "rated $gameName"
        "shared_save" -> "shared a save for $gameName"
        else -> "did something with $gameName"
    }
}

private fun getEventIconAndColor(eventType: String): Pair<ImageVector, androidx.compose.ui.graphics.Color> {
    return when (eventType) {
        "started_playing" -> Icons.Filled.PlayArrow to SpColor.Success
        "favorited_game" -> Icons.Filled.Favorite to SpColor.Secondary
        "rated_game" -> Icons.Filled.Star to SpColor.Warning
        "shared_save" -> Icons.Filled.Save to SpColor.Accent
        else -> Icons.Filled.PlayArrow to SpColor.OnBackgroundTertiary
    }
}

internal fun formatRelativeTime(isoTimestamp: String): String {
    return try {
        val instant = kotlin.time.Instant.parse(isoTimestamp)
        val now = Clock.System.now()
        val duration: Duration = now - instant
        val seconds = duration.inWholeSeconds
        when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            seconds < 604800 -> "${seconds / 86400}d ago"
            else -> "${seconds / 604800}w ago"
        }
    } catch (_: Exception) {
        ""
    }
}
