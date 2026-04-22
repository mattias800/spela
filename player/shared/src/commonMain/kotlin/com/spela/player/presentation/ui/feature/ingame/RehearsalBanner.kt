package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.presentation.state.CoreDecision
import com.spela.player.presentation.ui.components.SpDecisionAction
import com.spela.player.presentation.ui.components.SpDecisionActionStyle
import com.spela.player.presentation.ui.components.SpInGameBanner

/**
 * ROLE component (Layer 3) — the #672 rehearsal banner shown over the
 * emulator surface while the user is trialling a new core against
 * their save. Thin wrapper over [SpInGameBanner] that fixes the copy
 * from the spec and exposes a single "Did this work?" action that
 * opens Sheet C. Outer spacing / placement is the caller's
 * responsibility (see `IMPROVEMENTS.md` on component outer-spacing rule).
 *
 * Spec key `core_upd.banner.trying`:
 *   `Trying {core} — your save is untouched.`
 *
 * Spec key `core_upd.banner.did_work_btn`:
 *   `Did this work?`
 */
@Composable
fun RehearsalBanner(
    decision: CoreDecision.RehearsalPrompt,
    onDidThisWork: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coreLabel = decision.coreDisplayName.ifEmpty { decision.coreName }
    SpInGameBanner(
        text = "Trying $coreLabel — your save is untouched.",
        icon = Icons.Filled.Science,
        modifier = modifier,
        primaryAction = SpDecisionAction(
            label = "Did this work?",
            onClick = onDidThisWork,
            style = SpDecisionActionStyle.Primary,
        ),
        testTagName = "core-upgrade-rehearsal-banner",
    )
}
