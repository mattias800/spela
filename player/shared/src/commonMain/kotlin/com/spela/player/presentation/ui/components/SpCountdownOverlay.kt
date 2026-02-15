package com.spela.player.presentation.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.delay

@Composable
fun SpCountdownOverlay(
    title: String,
    subtitle: String,
    totalSeconds: Int,
    visible: Boolean,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    var remainingSeconds by remember(visible) { mutableIntStateOf(totalSeconds) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        remainingSeconds = totalSeconds
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
        onTimeout()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(SpColor.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "$title. $subtitle. Reconnection timeout in $remainingSeconds seconds."
                    },
            ) {
                Text(
                    text = title,
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(SpSpacing.Small))

                Text(
                    text = subtitle,
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(SpSpacing.XLarge))

                Text(
                    text = "${remainingSeconds}s",
                    style = SpTypography.DisplayMedium,
                    color = SpColor.Warning,
                )

                if (actions != null) {
                    Spacer(Modifier.height(SpSpacing.XLarge))
                    actions()
                }
            }
        }
    }
}
