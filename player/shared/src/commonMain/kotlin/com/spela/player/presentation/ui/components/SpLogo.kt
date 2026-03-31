package com.spela.player.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import spela_player.shared.generated.resources.Res
import spela_player.shared.generated.resources.spela_logo

@Composable
fun SpLogo(
    modifier: Modifier = Modifier,
    size: Dp = 192.dp,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.spela_logo),
            contentDescription = "Spela",
            modifier = Modifier.height(size),
        )
    }
}
