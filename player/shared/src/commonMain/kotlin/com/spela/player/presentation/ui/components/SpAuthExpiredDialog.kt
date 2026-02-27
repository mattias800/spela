package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier

@Composable
fun SpAuthExpiredDialog(
    onSignIn: () -> Unit,
    onContinueOffline: () -> Unit,
) {
    SpDialog(
        title = "Session Expired",
        onDismiss = onContinueOffline,
        confirmText = "Sign In",
        dismissText = "Continue Offline",
        onConfirm = onSignIn,
        modifier = Modifier.semantics { contentDescription = "Auth expired dialog" },
    ) {
        androidx.compose.material3.Text(
            text = "Your session has expired. Sign in again to sync your data, or continue using cached data.",
            style = com.spela.player.presentation.ui.theme.SpTypography.BodyMedium,
            color = com.spela.player.presentation.ui.theme.SpColor.OnBackgroundSecondary,
        )
    }
}
