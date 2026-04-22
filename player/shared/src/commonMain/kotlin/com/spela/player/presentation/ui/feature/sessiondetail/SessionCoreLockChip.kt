package com.spela.player.presentation.ui.feature.sessiondetail

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpLinkText
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * ROLE component (Layer 3) — shown on a session detail header when
 * the session is locked to a specific core binary via the #672
 * core-upgrade decision flow. Delegates the chip visuals to [SpChip]
 * and the unlock link to [SpLinkText]; no custom painting.
 *
 * The `Locked to v-{abbrev}` chip is non-interactive: it's an
 * information badge. The unlock action is the secondary link
 * underneath it, because "tap the lock to unlock" is not discoverable
 * (and tap-to-unlock is a destructive one-click that the user should
 * not trigger by accident).
 *
 * Abbreviates the sha to the first 4 hex characters with a
 * real hyphen (`v-a3f9`) — see copy rules in
 * `design-proposals/core-upgrade-decision-spec.md`. When the caller
 * cannot supply a sha (edge case: flag set without a pin), the chip
 * falls back to the version-less label so the unlock escape hatch
 * still renders.
 */
@Composable
fun SessionCoreLockChip(
    pinnedCoreSha256: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipLabel = if (pinnedCoreSha256.length >= 4) {
        "Locked to version v-${pinnedCoreSha256.take(4).lowercase()}"
    } else {
        // Fallback: missing sha shouldn't happen in normal flow but we
        // render a valid label instead of `v-` so the escape hatch
        // below stays usable.
        "Locked to this core version"
    }
    Column(
        modifier = modifier.testTag("session-core-lock-chip"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpChip(
                text = chipLabel,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = SpColor.OnBackgroundSecondary,
                        modifier = Modifier.size(SpSpacing.IconSmall),
                    )
                },
            )
        }
        // focusable() + Role.Button at the use site so the unlock link
        // receives DPAD/gamepad focus — SpLinkText itself wraps a
        // `clickable` Text without semantic role, which Compose can
        // skip during focus traversal.
        SpLinkText(
            text = "Use the latest version instead",
            onClick = onUnlock,
            modifier = Modifier
                .testTag("session-core-unlock-link")
                .semantics { role = Role.Button }
                .focusable(),
        )
    }
}

