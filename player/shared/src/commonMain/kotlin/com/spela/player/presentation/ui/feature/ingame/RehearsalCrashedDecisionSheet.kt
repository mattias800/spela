package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.runtime.Composable
import com.spela.player.presentation.state.CoreDecision
import com.spela.player.presentation.ui.components.SpDecisionAction
import com.spela.player.presentation.ui.components.SpDecisionActionStyle
import com.spela.player.presentation.ui.components.SpDecisionSheet

/**
 * Sheet D (ROLE component, Layer 3) — shown when the libretro bridge
 * surfaces a core crash or `retro_unserialize` failure during the
 * rehearsal run, or when an `RehearsalCrashPending` sentinel from a
 * previous session is detected at relaunch (relaunch wiring lands in
 * a follow-up). Spec body intentionally reassures the user that their
 * save is fine — only the core run was unhealthy. See
 * `design-proposals/core-upgrade-decision-spec.md` §"Sheet D — That
 * didn't go well".
 */
@Composable
fun RehearsalCrashedDecisionSheet(
    decision: CoreDecision.RehearsalCrashed,
    onLockOldVersion: () -> Unit,
    onStartFresh: () -> Unit,
    onReport: () -> Unit,
    onTryLater: () -> Unit,
) {
    val coreLabel = decision.coreDisplayName.ifEmpty { decision.coreName }
    val body = "$coreLabel ran into a problem while loading your save. " +
        "Your save itself is fine — we'll go back to the older version."

    SpDecisionSheet(
        title = "That didn't go well",
        body = body,
        primary = SpDecisionAction(
            label = "Lock to the older version",
            onClick = onLockOldVersion,
            style = SpDecisionActionStyle.Primary,
        ),
        secondary = SpDecisionAction(
            label = "Start fresh on the new version",
            onClick = onStartFresh,
            style = SpDecisionActionStyle.Secondary,
        ),
        moreOptions = listOf(
            SpDecisionAction(
                label = "Report this to the server admin",
                onClick = onReport,
                style = SpDecisionActionStyle.Ghost,
            ),
            SpDecisionAction(
                label = "Just go back — I'll try again later",
                onClick = onTryLater,
                style = SpDecisionActionStyle.Ghost,
            ),
        ),
        // Back-button maps to "try later" — least destructive: no
        // server change, user remains in the crashed-emulator state.
        onDismiss = onTryLater,
        testTagName = "core-upgrade-sheet-d",
    )
}
