package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit = {},
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge),
        ) {
            Text(
                text = title,
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
            )

            Spacer(Modifier.height(SpSpacing.Default))

            content()

            Spacer(Modifier.height(SpSpacing.XLarge))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                SpButton(
                    text = confirmText,
                    onClick = onConfirm,
                    style = if (isDestructive) SpButtonStyle.Secondary else SpButtonStyle.Primary,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isLoading,
                    enabled = !isLoading,
                )
                SpButton(
                    text = dismissText,
                    onClick = onDismiss,
                    style = SpButtonStyle.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                )
            }
        }
    }
}

@Composable
fun SpConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "Confirm",
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
) {
    SpDialog(
        title = title,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmText = confirmText,
        isDestructive = isDestructive,
        isLoading = isLoading,
    ) {
        Text(
            text = message,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
        )
    }
}
