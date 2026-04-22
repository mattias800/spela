package com.spela.player.desktop.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.presentation.ui.feature.sessiondetail.SessionCoreLockChip
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviour coverage for [SessionCoreLockChip] — the header chrome
 * on a locked session ("Locked to version v-a3f9" chip + "Use the
 * latest version instead" unlock link). Part of the #672
 * core-upgrade decision UX.
 */
@OptIn(ExperimentalTestApi::class)
class SessionCoreLockChipTest {

    @Test
    fun rendersAbbreviatedPinnedVersion() = runComposeUiTest {
        setContent {
            SessionCoreLockChip(
                pinnedCoreSha256 = "a3f912b40000000000000000000000000000000000000000000000000000000a",
                onUnlock = {},
            )
        }

        onNodeWithTag("session-core-lock-chip").assertIsDisplayed()
        // Abbreviation rule: first 4 hex chars, lowercased, with a
        // real hyphen.
        onNodeWithText("Locked to version v-a3f9").assertIsDisplayed()
    }

    @Test
    fun abbreviationLowercasesUppercaseHex() = runComposeUiTest {
        setContent {
            SessionCoreLockChip(
                pinnedCoreSha256 = "ABCDEF01234567890000000000000000000000000000000000000000000000AB",
                onUnlock = {},
            )
        }

        onNodeWithText("Locked to version v-abcd").assertIsDisplayed()
    }

    @Test
    fun unlockLinkIsVisibleAndFiresOnClick() = runComposeUiTest {
        var unlockClicks = 0
        setContent {
            SessionCoreLockChip(
                pinnedCoreSha256 = "abcd1234567890ef0000000000000000000000000000000000000000000000aa",
                onUnlock = { unlockClicks++ },
            )
        }

        onNodeWithText("Use the latest version instead").assertIsDisplayed()
        onNodeWithTag("session-core-unlock-link").performClick()
        assertEquals(1, unlockClicks)
    }

    @Test
    fun fallsBackToVersionlessLabelWhenShaIsShorterThanFourChars() = runComposeUiTest {
        // Server shouldn't hand us anything shorter than 64 chars, but
        // the UI must not render "v-ab" (a misleading 2-char version
        // label). Fall back to the version-less copy so the unlock
        // escape hatch still makes sense.
        setContent {
            SessionCoreLockChip(
                pinnedCoreSha256 = "ab",
                onUnlock = {},
            )
        }

        onNodeWithText("Locked to this core version").assertIsDisplayed()
    }

    @Test
    fun fallsBackToVersionlessLabelWhenShaIsEmpty() = runComposeUiTest {
        // Edge case: `userLockedCoreVersion = true` on a session whose
        // pin was never set (shouldn't happen server-side, defensive
        // path). Chip must still render so the user can reach the
        // unlock link.
        var unlockClicks = 0
        setContent {
            SessionCoreLockChip(
                pinnedCoreSha256 = "",
                onUnlock = { unlockClicks++ },
            )
        }

        onNodeWithText("Locked to this core version").assertIsDisplayed()
        onNodeWithTag("session-core-unlock-link").performClick()
        assertEquals(1, unlockClicks,
            "empty-sha fallback must still expose a working unlock action")
    }
}
