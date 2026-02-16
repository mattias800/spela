package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SpSpacing.XXLarge)
            .semantics { contentDescription = "$title. $message" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Decorative icon badge
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SpColor.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SpColor.OnBackgroundTertiary,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.height(SpSpacing.XLarge))

        Text(
            text = title,
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackgroundSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(SpSpacing.Small))

        Text(
            text = message,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.8f),
        )

        if (action != null) {
            Spacer(Modifier.height(SpSpacing.XLarge))
            action()
        }
    }
}

object SpEmptyStates {

    @Composable
    fun NoGamesDownloaded(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = Icons.Filled.Download,
            title = "No games downloaded",
            message = "Browse your library and download games to play them offline",
            modifier = modifier,
        )
    }

    @Composable
    fun NoFavorites(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = Icons.Filled.FavoriteBorder,
            title = "No favorites yet",
            message = "Tap the heart on any game to add it to your favorites for quick access",
            modifier = modifier,
        )
    }

    @Composable
    fun NoSaveStates(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = Icons.Filled.Save,
            title = "No save states",
            message = "Save your progress in-game and it will appear here",
            modifier = modifier,
        )
    }

    @Composable
    fun NoSearchResults(query: String, modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = Icons.Filled.Search,
            title = "No results for \"$query\"",
            message = "Try a different search term or browse by console",
            modifier = modifier,
        )
    }

    @Composable
    fun NoGamesInConsole(consoleName: String, modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = Icons.Filled.Gamepad,
            title = "No $consoleName games",
            message = "Games will appear here once the server scans your library",
            modifier = modifier,
        )
    }

    @Composable
    fun NoActiveDownloads(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = Icons.Filled.CheckCircle,
            title = "All clear",
            message = "No active downloads. Browse your library to download games for offline play.",
            modifier = modifier,
        )
    }

    @Composable
    fun EmptyLibrary(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            title = "Your library is empty",
            message = "Add games to your server and they will appear here. Pull down to refresh.",
            modifier = modifier,
        )
    }

    @Composable
    fun NoPlayLater(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = Icons.Filled.FavoriteBorder,
            title = "No games in Play Later",
            message = "Tap 'Play Later' on any game to save it for later",
            modifier = modifier,
        )
    }

    @Composable
    fun NoCollections(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            title = "No collections yet",
            message = "Create a collection to organize your favorite games",
            modifier = modifier,
        )
    }

    @Composable
    fun NoNetplaySessions(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = Icons.Filled.SportsEsports,
            title = "No netplay sessions yet",
            message = "Create one to play with a friend.",
            modifier = modifier,
        )
    }
}
