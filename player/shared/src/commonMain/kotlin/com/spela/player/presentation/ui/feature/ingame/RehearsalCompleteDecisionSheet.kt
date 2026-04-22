package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.runtime.Composable
import com.spela.player.presentation.ui.components.SpDecisionAction
import com.spela.player.presentation.ui.components.SpDecisionActionStyle
import com.spela.player.presentation.ui.components.SpDecisionSheet

/**
 * Sheet C (ROLE component, Layer 3) — opened from the rehearsal banner
 * when the user taps "Did this work?". Asks them to confirm whether
 * the new core loaded their save correctly. The three actions cover
 * keep-the-new-version (primary), lock-to-the-older-version
 * (secondary), and "let me look a bit longer" (ghost — closes the
 * sheet without exiting rehearsal). See
 * `design-proposals/core-upgrade-decision-spec.md` §"Sheet C — Did
 * your save load correctly?" for the canonical copy and decision flow.
 */
@Composable
fun RehearsalCompleteDecisionSheet(
    onKeepNewVersion: () -> Unit,
    onLockOldVersion: () -> Unit,
    onTryLonger: () -> Unit,
) {
    SpDecisionSheet(
        title = "Did your save load correctly?",
        body = "If the screen looks right and the controls feel normal, the new version works.",
        primary = SpDecisionAction(
            label = "Yes, keep the new version",
            onClick = onKeepNewVersion,
            style = SpDecisionActionStyle.Primary,
        ),
        secondary = SpDecisionAction(
            label = "No, lock to the older version",
            onClick = onLockOldVersion,
            style = SpDecisionActionStyle.Secondary,
        ),
        // Spec keeps `Let me try a bit longer` as a peer action, not
        // hidden inside a "More options" disclosure — discoverability
        // matters here because the user is mid-decision and needs the
        // escape hatch visible without a second tap.
        tertiary = SpDecisionAction(
            label = "Let me try a bit longer",
            onClick = onTryLonger,
            style = SpDecisionActionStyle.Ghost,
        ),
        // Back-button / scrim-tap maps to "try a bit longer" so the
        // banner re-emerges and the user stays in rehearsal mode.
        onDismiss = onTryLonger,
        testTagName = "core-upgrade-sheet-c",
    )
}
