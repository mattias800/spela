package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.delay

/**
 * Upper bound on dialog width. `fillMaxWidth(0.85f)` keeps dialogs
 * comfortably inset on phones, but on a wide desktop window 85% is
 * enormous for a short confirmation; cap it so dialogs stay compact
 * on large screens. (#1258)
 */
private val SpDialogMaxWidth = 420.dp

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
    showConfirmButton: Boolean = true,
    // When true, anchor d-pad/keyboard focus on the Cancel button when the
    // dialog opens so it's navigable with a gamepad (no mouse) — e.g. Steam
    // Deck Gaming Mode. Cancel is the safe default for destructive prompts.
    // Off by default so dialogs with text-field content (which should focus
    // the field, not a button) are unaffected. See #1439.
    autoFocusDismiss: Boolean = false,
    // Optional focus anchor for picker-style dialogs whose safest first target
    // is the currently selected row/chip instead of the dismiss button. See #1450.
    initialFocusRequester: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    val dismissFocusRequester = remember { FocusRequester() }
    val focusRequester = initialFocusRequester ?: if (autoFocusDismiss) dismissFocusRequester else null
    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (focusRequester != null) {
            LaunchedEffect(focusRequester) {
                // Brief settle so the button is placed before we request —
                // requestFocus on a just-composed node is otherwise unreliable.
                delay(120)
                try {
                    focusRequester.requestFocus()
                } catch (_: Exception) {
                    // Node not ready / focus owner busy — best effort.
                }
            }
        }
        Column(
            modifier = modifier
                // widthIn first so it caps the available width, then take
                // 85% of that. Reversed, fillMaxWidth would fix the width
                // before the cap could shrink it (the dialog stayed wide on
                // desktop). (#1258)
                .widthIn(max = SpDialogMaxWidth)
                .fillMaxWidth(0.85f)
                .testTag("sp_dialog_surface")
                .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge)
                // Keep d-pad focus traversal inside the dialog's buttons.
                .focusGroup(),
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
                if (showConfirmButton) {
                    SpButton(
                        text = confirmText,
                        onClick = onConfirm,
                        style = if (isDestructive) SpButtonStyle.Secondary else SpButtonStyle.Primary,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_confirm"),
                        isLoading = isLoading,
                        enabled = !isLoading,
                    )
                }
                SpButton(
                    text = dismissText,
                    onClick = onDismiss,
                    style = SpButtonStyle.Ghost,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (autoFocusDismiss) Modifier.focusRequester(dismissFocusRequester) else Modifier)
                        .testTag("dialog_dismiss"),
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
        // Confirm dialogs are message-only (no text input), so anchor focus on
        // Cancel for gamepad/keyboard navigation (Steam Deck Gaming Mode). #1439
        autoFocusDismiss = true,
    ) {
        Text(
            text = message,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
        )
    }
}
