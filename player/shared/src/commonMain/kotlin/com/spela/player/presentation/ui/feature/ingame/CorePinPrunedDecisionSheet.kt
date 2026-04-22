package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.runtime.Composable
import com.spela.player.presentation.state.CoreDecision
import com.spela.player.presentation.ui.components.SpDecisionAction
import com.spela.player.presentation.ui.components.SpDecisionActionStyle
import com.spela.player.presentation.ui.components.SpDecisionSheet

/**
 * Sheet B (ROLE component, Layer 3) — shown when the session was
 * locked to a specific core sha via #672 Sheet A but the server has
 * since pruned that binary out of its history retention window (3
 * versions / 90 days per #555). There's no way to honour the user's
 * original lock; they have to pick between trying the save against
 * the latest core or starting fresh.
 *
 * See `design-proposals/core-upgrade-decision-spec.md` §"Sheet B —
 * The older version isn't available anymore" for the canonical copy
 * and decision flow.
 */
@Composable
fun CorePinPrunedDecisionSheet(
    decision: CoreDecision.PinPruned,
    onTryWithMySave: () -> Unit,
    onStartFresh: () -> Unit,
    onRemindLater: () -> Unit,
) {
    val coreLabel = decision.coreDisplayName.ifEmpty { decision.coreName }
    // Spec key `core_upd.sheet_b.body` — verbatim. Sheet A carries the
    // game title; Sheet B intentionally does not (the user already saw
    // Sheet A or locked manually, so they know which save is at risk).
    val body = "We used to keep the exact core version your save was " +
        "made with, but it's been rotated out of the server's history. " +
        "We'll use the latest $coreLabel instead. Try your save first — " +
        "if it looks wrong you can start fresh."

    SpDecisionSheet(
        title = "The older version isn't available anymore",
        body = body,
        primary = SpDecisionAction(
            label = "Try with my save",
            onClick = onTryWithMySave,
            style = SpDecisionActionStyle.Primary,
        ),
        secondary = SpDecisionAction(
            label = "Start fresh anyway",
            onClick = onStartFresh,
            style = SpDecisionActionStyle.Secondary,
        ),
        moreOptions = listOf(
            SpDecisionAction(
                label = "Remind me the next time I play this",
                onClick = onRemindLater,
                style = SpDecisionActionStyle.Ghost,
            ),
        ),
        onDismiss = onRemindLater,
        testTagName = "core-upgrade-sheet-b",
    )
}
