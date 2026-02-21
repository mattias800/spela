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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private val OfflineBannerBackground = Color(0xFF92400E)
private val OfflineBannerText = Color(0xFFFEF3C7)

@Composable
fun SpOfflineBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isOffline,
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
                text = "Offline \u2014 Using cached data",
                style = SpTypography.LabelSmall,
                color = OfflineBannerText,
            )
        }
    }
}
