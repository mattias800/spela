package com.spela.player.presentation.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.ui.TestTags
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Standardised screen heading for gamepad mode (#1529).
 *
 * In gamepad mode [SpTopBar] is suppressed, so destination screens that are
 * just a grid or list of cards (Favorites, Play Later, All Games, Collections,
 * Downloads) give the user no on-screen label of what they're viewing. This
 * renders the screen title — matching the touch-mode [SpTopBar] title — and
 * renders nothing in touch mode.
 *
 * Title-only by design: back stays on the B button (the app-wide gamepad
 * pattern), and a focusable back affordance here would disturb d-pad focus
 * flow. Screens whose context is self-evident (Console detail branding,
 * Home, Settings) should not use this.
 *
 * Place it as the first item inside the screen's scroller so content keeps
 * scrolling under the floating section pill, or as a fixed element below
 * [SpScreenTopSpacer] on screens that already have a fixed header. The parent
 * controls outer spacing.
 */
@Composable
fun SpScreenHeading(
    title: String,
    modifier: Modifier = Modifier,
) {
    if (LocalInputMode.current != InputMode.GAMEPAD) return

    Text(
        text = title,
        style = SpTypography.HeadlineMedium,
        color = SpColor.OnBackground,
        modifier = modifier
            .testTag(TestTags.SCREEN_HEADING)
            .semantics { heading() },
    )
}
