package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.delay

/**
 * Full-screen overlay shown when a game fails to start. Carries the error
 * message and a single Exit action.
 *
 * The Exit button **auto-focuses on appear** (#1411): without this, a gamepad
 * has no focused target on the overlay, so pressing A activates whatever was
 * still focused on the screen underneath (e.g. the game-detail Play / "…"
 * actions) — the overlay is drawn on top but never claimed focus. The brief
 * layout-settle delay before `requestFocus()` follows the convention in
 * GAMEPAD_NAVIGATION.md (the button must be placed before it can take focus);
 * the request is best-effort and swallowed if the node isn't ready.
 */
@Composable
fun EmulationErrorOverlay(
    error: String,
    onExit: () -> Unit,
) {
    val exitFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(FOCUS_SETTLE_MS)
        try {
            exitFocusRequester.requestFocus()
        } catch (_: Exception) {
            // Node not placed yet / focus owner busy — best effort.
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(SpSpacing.XLarge),
        ) {
            Text(
                text = error,
                style = SpTypography.BodyMedium,
                color = SpColor.Error,
            )
            Spacer(Modifier.height(SpSpacing.Large))
            SpButton(
                text = "Exit",
                onClick = onExit,
                modifier = Modifier
                    .focusRequester(exitFocusRequester)
                    .testTag("emulation_error_exit"),
            )
        }
    }
}

private const val FOCUS_SETTLE_MS = 120L
