package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpDatabaseErrorScreen(
    message: String,
    onResetApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpColor.Background)
            .semantics { contentDescription = "Database error screen" },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(SpSpacing.XLarge),
        ) {
            Text(
                text = "Database Error",
                style = SpTypography.HeadlineMedium,
                color = SpColor.Error,
            )
            Spacer(Modifier.height(SpSpacing.Default))
            Text(
                text = message,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )
            Spacer(Modifier.height(SpSpacing.Default))
            Text(
                text = "This usually happens after an app update. Resetting will clear local data but your saves are safe on the server.",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
            Spacer(Modifier.height(SpSpacing.XLarge))
            SpButton(
                text = "Reset App",
                onClick = onResetApp,
                style = SpButtonStyle.Secondary,
            )
        }
    }
}
