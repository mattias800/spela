package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.state.SecondaryToastData
import com.spela.player.presentation.state.SecondaryToastType
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.delay

private const val TOAST_DISPLAY_DURATION_MS = 2_000L
private const val TOAST_ENTER_DURATION_MS = 250
private const val TOAST_EXIT_DURATION_MS = 200

/**
 * Brief toast notification overlay for the secondary screen.
 *
 * Displays a compact pill-shaped toast near the bottom of the screen when
 * save/load operations complete. Auto-dismisses after [TOAST_DISPLAY_DURATION_MS].
 * Does NOT block touch events on the underlying content.
 *
 * @param toast The current toast data, or null when no toast should be shown.
 * @param onToastShown Called when a new toast begins displaying, so the parent
 *   can reset burn-in timers or perform other side effects.
 * @param onDismiss Called when the toast auto-dismisses, so the parent can clear
 *   the toast state.
 */
@Composable
fun SecondaryToast(
    toast: SecondaryToastData?,
    onToastShown: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    var isVisible by remember { mutableStateOf(false) }

    // Cache the last non-null toast so content persists during exit animation.
    var lastToast by remember { mutableStateOf<SecondaryToastData?>(null) }

    LaunchedEffect(toast) {
        if (toast != null) {
            lastToast = toast
            isVisible = true
            onToastShown()
            delay(TOAST_DISPLAY_DURATION_MS)
            isVisible = false
            // Allow exit animation to complete before clearing
            delay(TOAST_EXIT_DURATION_MS.toLong())
            onDismiss()
        } else {
            isVisible = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(TOAST_ENTER_DURATION_MS),
            ) + fadeIn(tween(TOAST_ENTER_DURATION_MS)),
            exit = fadeOut(tween(TOAST_EXIT_DURATION_MS)),
            modifier = Modifier.padding(bottom = SpSpacing.XXLarge),
        ) {
            val displayToast = lastToast ?: return@AnimatedVisibility
            ToastPill(data = displayToast)
        }
    }
}

/**
 * Compact pill-shaped toast with icon and message text.
 * Uses [SpColor.Success] (green) for save toasts and [SpColor.Accent] (blue) for load toasts.
 */
@Composable
private fun ToastPill(data: SecondaryToastData) {
    val accentColor = when (data.type) {
        SecondaryToastType.SAVE -> SpColor.Success
        SecondaryToastType.LOAD -> SpColor.Accent
    }
    val icon = when (data.type) {
        SecondaryToastType.SAVE -> Icons.Filled.Check
        SecondaryToastType.LOAD -> Icons.Filled.FolderOpen
    }
    val iconDescription = when (data.type) {
        SecondaryToastType.SAVE -> "Save"
        SecondaryToastType.LOAD -> "Load"
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusPill))
            .background(SpColor.Surface.copy(alpha = 0.92f))
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "${data.message} toast"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = accentColor,
            modifier = Modifier.size(SpSpacing.IconSmall),
        )
        Spacer(Modifier.width(SpSpacing.Small))
        Text(
            text = data.message,
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackground,
        )
    }
}
