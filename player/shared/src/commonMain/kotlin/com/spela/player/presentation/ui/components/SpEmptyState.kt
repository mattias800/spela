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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpEmptyState(
    icon: String,
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
            Text(
                text = icon,
                style = SpTypography.DisplayMedium,
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
            icon = "\uD83D\uDCE5",
            title = "No games downloaded",
            message = "Browse your library and download games to play them offline",
            modifier = modifier,
        )
    }

    @Composable
    fun NoFavorites(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = "\u2661",
            title = "No favorites yet",
            message = "Tap the heart on any game to add it to your favorites for quick access",
            modifier = modifier,
        )
    }

    @Composable
    fun NoSaveStates(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = "\uD83D\uDCBE",
            title = "No save states",
            message = "Save your progress in-game and it will appear here",
            modifier = modifier,
        )
    }

    @Composable
    fun NoSearchResults(query: String, modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = "\uD83D\uDD0D",
            title = "No results for \"$query\"",
            message = "Try a different search term or browse by console",
            modifier = modifier,
        )
    }

    @Composable
    fun NoGamesInConsole(consoleName: String, modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = "\uD83C\uDFAE",
            title = "No $consoleName games",
            message = "Games will appear here once the server scans your library",
            modifier = modifier,
        )
    }

    @Composable
    fun NoActiveDownloads(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = "\u2713",
            title = "All clear",
            message = "No active downloads. Browse your library to download games for offline play.",
            modifier = modifier,
        )
    }

    @Composable
    fun EmptyLibrary(modifier: Modifier = Modifier) {
        SpEmptyState(
            icon = "\uD83D\uDCDA",
            title = "Your library is empty",
            message = "Add games to your server and they will appear here. Pull down to refresh.",
            modifier = modifier,
        )
    }
}
