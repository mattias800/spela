package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpBrandHero(
    logoSize: Dp = 192.dp,
    modifier: Modifier = Modifier,
    tagline: String? = "\"Nu spelar vi!\"",
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpLogo(size = logoSize)
        if (tagline != null) {
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = tagline,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
            )
        }
    }
}
