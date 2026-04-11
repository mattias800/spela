package com.spela.player.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.data.remote.ConnectionState
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private val OfflineBannerBackground = SpColor.SurfaceVariant
private val OfflineBannerText = SpColor.OnBackgroundSecondary

@Composable
fun SpOfflineBanner(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = connectionState is ConnectionState.Offline,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(OfflineBannerBackground)
                .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
                .semantics { contentDescription = "Offline banner" },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "No internet \u2014 Playing offline",
                style = SpTypography.LabelSmall,
                color = OfflineBannerText,
            )
        }
    }
}
