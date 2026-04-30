package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.runtime.Composable
import com.spela.player.presentation.ui.components.SpDecisionAction
import com.spela.player.presentation.ui.components.SpDecisionActionStyle
import com.spela.player.presentation.ui.components.SpDecisionSheet

/**
 * First-launch save-state opt-in dialog (#804 phase 4b spec point b).
 *
 * Fires the first time the user launches a large-tier console game
 * (GameCube, Wii, PS2) when their per-console policy is still the
 * default `ask-once`. Three actions, each records a deliberate
 * console-level choice so the dialog never re-fires:
 *
 *   primary   "Yes, enable" → consoleSaveStatePolicies[abbr] = enabled
 *   secondary "No, battery saves only" → consoleSaveStatePolicies[abbr] = disabled
 *   tertiary  "Decide per game" → consoleSaveStatePolicies[abbr] = enabled
 *             (per-game toggle on game-detail handles the case-by-case
 *             work; this option just unblocks the dialog)
 *
 * The copy intentionally distinguishes save states (snapshots) from
 * the in-game battery save — the spec calls this out as the most
 * dangerous misread, since users panic if they think the toggle
 * threatens their actual game progress.
 */
@Composable
fun SaveStateOptInDialog(
    consoleName: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDeferToPerGame: () -> Unit,
) {
    val displayName = consoleName.ifBlank { "this console" }
    SpDecisionSheet(
        title = "Enable save states for $displayName?",
        body = "$displayName save states are typically 30–100 MB each and count " +
            "against your storage quota. This is separate from your in-game save " +
            "(battery / memory card) — that always syncs regardless.",
        primary = SpDecisionAction(
            label = "Yes, enable",
            onClick = onAccept,
            style = SpDecisionActionStyle.Primary,
        ),
        secondary = SpDecisionAction(
            label = "No, battery saves only",
            onClick = onReject,
            style = SpDecisionActionStyle.Secondary,
        ),
        tertiary = SpDecisionAction(
            label = "Decide per game",
            onClick = onDeferToPerGame,
            style = SpDecisionActionStyle.Ghost,
        ),
        // Back-button / scrim-tap defers to per-game so the user
        // doesn't accidentally lock the entire console to disabled by
        // mishandling the prompt — the per-game toggle gives them a
        // less destructive way to deal with it later.
        onDismiss = onDeferToPerGame,
        testTagName = "save-state-opt-in-dialog",
    )
}
