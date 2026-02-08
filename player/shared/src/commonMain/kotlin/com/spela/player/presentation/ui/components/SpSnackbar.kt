package com.spela.player.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.delay

enum class SpSnackbarType {
    Info, Success, Warning, Error
}

data class SpSnackbarData(
    val message: String,
    val type: SpSnackbarType = SpSnackbarType.Info,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMs: Long = 3000L,
)

@Composable
fun SpSnackbar(
    data: SpSnackbarData?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(data) {
        if (data != null) {
            visible = true
            delay(data.durationMs)
            visible = false
            delay(300)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible && data != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        data?.let { snackData ->
            val backgroundColor = when (snackData.type) {
                SpSnackbarType.Info -> SpColor.SurfaceElevated
                SpSnackbarType.Success -> SpColor.SuccessContainer
                SpSnackbarType.Warning -> SpColor.WarningContainer
                SpSnackbarType.Error -> SpColor.ErrorContainer
            }

            val accentColor = when (snackData.type) {
                SpSnackbarType.Info -> SpColor.Primary
                SpSnackbarType.Success -> SpColor.Success
                SpSnackbarType.Warning -> SpColor.Warning
                SpSnackbarType.Error -> SpColor.Error
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .padding(SpSpacing.Default),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = snackData.message,
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackground,
                        modifier = Modifier.weight(1f),
                    )
                    if (snackData.actionLabel != null) {
                        Spacer(Modifier.width(SpSpacing.Small))
                        Text(
                            text = snackData.actionLabel,
                            style = SpTypography.LabelLarge,
                            color = accentColor,
                            modifier = Modifier.clickable {
                                snackData.onAction?.invoke()
                            },
                        )
                    }
                }
            }
        }
    }
}
