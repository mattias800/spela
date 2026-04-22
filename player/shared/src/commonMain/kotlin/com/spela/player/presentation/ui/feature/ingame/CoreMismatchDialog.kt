package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.runtime.Composable
import com.spela.player.presentation.ui.components.SpDecisionAction
import com.spela.player.presentation.ui.components.SpDecisionActionStyle
import com.spela.player.presentation.ui.components.SpDecisionSheet

/**
 * Post-load core mismatch dialog. Fires when auto-load successfully
 * downloaded a save state but `retro_unserialize` rejected it because
 * the save was made with a different core. Distinct from the #672
 * pre-load decision flow (Sheets A–D) which runs *before* the core is
 * loaded — this one runs *after* a load attempt fails.
 *
 * Ported from the raw `Box + Scrim + clickable + SurfaceElevated`
 * pattern to [SpDecisionSheet] in #672 PR 3c.iii. Copy was refreshed
 * to match the spec's no-warning / no-corruption tone (`design-proposals/
 * core-upgrade-decision-spec.md` §"Tone rules").
 */
@Composable
fun CoreMismatchDialog(
    saveCoreName: String,
    currentCoreName: String,
    onTryAnyway: () -> Unit,
    onGameSaveOnly: () -> Unit,
    onStartFresh: () -> Unit,
) {
    val saveLabel = saveCoreName.ifEmpty { "the original core" }
    val currentLabel = currentCoreName.ifEmpty { "the current core" }
    val body = "This save was made with $saveLabel — we're now running " +
        "$currentLabel. Loading might still work, but you can also start " +
        "from your in-game save or begin a fresh run."

    SpDecisionSheet(
        title = "We couldn't load that save state",
        body = body,
        primary = SpDecisionAction(
            label = "Try loading anyway",
            onClick = onTryAnyway,
            style = SpDecisionActionStyle.Primary,
        ),
        secondary = SpDecisionAction(
            label = "Start with game save only",
            onClick = onGameSaveOnly,
            style = SpDecisionActionStyle.Secondary,
        ),
        tertiary = SpDecisionAction(
            label = "Start fresh",
            onClick = onStartFresh,
            style = SpDecisionActionStyle.Ghost,
        ),
        // Back-button maps to the safest non-destructive choice —
        // "game save only" preserves the user's progress without
        // attempting the risky save-state load.
        onDismiss = onGameSaveOnly,
        testTagName = "core-mismatch-dialog",
    )
}
