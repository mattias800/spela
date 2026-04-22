package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.runtime.Composable
import com.spela.player.presentation.state.CoreDecision
import com.spela.player.presentation.ui.components.SpDecisionAction
import com.spela.player.presentation.ui.components.SpDecisionActionStyle
import com.spela.player.presentation.ui.components.SpDecisionSheet
import com.spela.player.presentation.ui.components.social.formatRelativeTime

/**
 * Sheet A (ROLE component, Layer 3) — the "We updated {core}" sheet
 * surfaced before emulation starts when the session's pinned core sha
 * differs from the server's current sha, the session isn't already
 * locked, and the user hasn't opted into silent auto-updates.
 *
 * See `design-proposals/core-upgrade-decision-spec.md` §"Sheet A" for
 * the canonical copy and decision flow. This composable delegates all
 * chrome to [SpDecisionSheet]; its only job is to map the
 * [CoreDecision.UpgradeAvailable] payload onto the sheet's parameters
 * and fire the correct resolution intent through the passed-in
 * lambdas.
 *
 * Resolution intents live on `EmulationIntent.ResolveCoreDecisionX`.
 * The VM applies the spec's pin-advancement rules when those intents
 * fire; this composable doesn't need to care.
 */
@Composable
fun CoreUpgradeDecisionSheet(
    decision: CoreDecision.UpgradeAvailable,
    onTryWithMySave: () -> Unit,
    onKeepNewVersion: () -> Unit,
    onLockOldVersion: () -> Unit,
    onRemindLater: () -> Unit,
) {
    // Spec copy keys:
    //   core_upd.sheet_a.title          → uses {core}
    //   core_upd.sheet_a.title_fallback → uses {console}
    // When neither is available we fall back once more to a
    // generic label so the sheet never renders an empty header.
    val title = when {
        decision.coreDisplayName.isNotEmpty() ->
            "We updated ${decision.coreDisplayName}"
        decision.consoleName.isNotEmpty() ->
            "The ${decision.consoleName} core has a new version"
        else ->
            "The core has a new version"
    }
    val body = "Your save for ${decision.gameTitle} was made with an " +
        "earlier version of the core. It will probably load fine — but " +
        "we'd like you to try it first so you can decide what to do."
    val metaLine = buildMetaLine(decision)

    SpDecisionSheet(
        title = title,
        body = body,
        metaLine = metaLine,
        primary = SpDecisionAction(
            label = "Try with my save",
            onClick = onTryWithMySave,
            style = SpDecisionActionStyle.Primary,
        ),
        secondary = SpDecisionAction(
            label = "Keep the new version anyway",
            onClick = onKeepNewVersion,
            style = SpDecisionActionStyle.Secondary,
        ),
        moreOptions = listOf(
            SpDecisionAction(
                label = "Lock this session to the older version",
                onClick = onLockOldVersion,
                style = SpDecisionActionStyle.Secondary,
            ),
            SpDecisionAction(
                label = "Remind me the next time I play this",
                onClick = onRemindLater,
                style = SpDecisionActionStyle.Ghost,
            ),
        ),
        // Back / Esc / gamepad-B on the sheet routes through the
        // Compose Dialog's onDismissRequest. Matches the spec's
        // "Remind me next time" semantics — no durable server change.
        onDismiss = onRemindLater,
        testTagName = "core-upgrade-sheet-a",
    )
}

/**
 * Returns the `v-abcd` abbreviation for a 64-char hex sha, or null
 * when the value is too short / missing. Hyphen is intentional — we
 * want the label to read as a version tag, not a fake semver.
 */
private fun versionLabel(sha: String): String? {
    if (sha.length < 4) return null
    return "v-${sha.take(4).lowercase()}"
}

/**
 * Spec copy: `"Last played {relative}  ·  Saved with version v-{abbrev}"`.
 * When either half is unknown we render only the known half; when
 * both are missing we return null so the sheet elides the row.
 */
private fun buildMetaLine(decision: CoreDecision.UpgradeAvailable): String? {
    val lastPlayed = decision.lastPlayedAt?.let {
        "Last played ${formatRelativeTime(it)}"
    }
    val version = versionLabel(decision.oldSha)?.let { "Saved with version $it" }
    return when {
        lastPlayed != null && version != null -> "$lastPlayed  ·  $version"
        lastPlayed != null -> lastPlayed
        version != null -> version
        else -> null
    }
}
