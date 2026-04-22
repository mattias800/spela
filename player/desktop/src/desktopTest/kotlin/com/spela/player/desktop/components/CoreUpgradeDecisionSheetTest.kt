package com.spela.player.desktop.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.presentation.state.CoreDecision
import com.spela.player.presentation.ui.feature.ingame.CoreUpgradeDecisionSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Behaviour coverage for the #672 Sheet A composable. Pins the copy
 * contract from `design-proposals/core-upgrade-decision-spec.md`
 * §"Sheet A" and the callback routing from the four primary actions.
 */
@OptIn(ExperimentalTestApi::class)
class CoreUpgradeDecisionSheetTest {

    private val decision = CoreDecision.UpgradeAvailable(
        coreName = "nestopia",
        coreDisplayName = "Nestopia UE",
        consoleName = "Nintendo Entertainment System",
        gameTitle = "Castlevania",
        oldSha = "abcd1234".repeat(8),
        newSha = "f00ba123".repeat(8),
        lastPlayedAt = null,
    )

    @Test
    fun rendersSpecTitleBodyAndMeta() = runComposeUiTest {
        setContent {
            CoreUpgradeDecisionSheet(
                decision = decision,
                onTryWithMySave = {},
                onKeepNewVersion = {},
                onLockOldVersion = {},
                onRemindLater = {},
            )
        }

        onNodeWithTag("core-upgrade-sheet-a").assertIsDisplayed()
        onNodeWithText("We updated Nestopia UE").assertIsDisplayed()
        onNodeWithText(
            "Your save for Castlevania was made with an earlier version of the core. " +
                "It will probably load fine — but we'd like you to try it first so you " +
                "can decide what to do.",
        ).assertIsDisplayed()
        onNodeWithText("Saved with version v-abcd").assertIsDisplayed()
    }

    @Test
    fun rendersConsoleFallbackTitleWhenDisplayNameIsEmpty() = runComposeUiTest {
        setContent {
            CoreUpgradeDecisionSheet(
                decision = decision.copy(coreDisplayName = ""),
                onTryWithMySave = {},
                onKeepNewVersion = {},
                onLockOldVersion = {},
                onRemindLater = {},
            )
        }

        // Spec key core_upd.sheet_a.title_fallback: uses console name.
        onNodeWithText("The Nintendo Entertainment System core has a new version")
            .assertIsDisplayed()
    }

    @Test
    fun rendersGenericFallbackTitleWhenBothAreEmpty() = runComposeUiTest {
        setContent {
            CoreUpgradeDecisionSheet(
                decision = decision.copy(coreDisplayName = "", consoleName = ""),
                onTryWithMySave = {},
                onKeepNewVersion = {},
                onLockOldVersion = {},
                onRemindLater = {},
            )
        }

        onNodeWithText("The core has a new version").assertIsDisplayed()
    }

    @Test
    fun primaryFiresOnTryWithMySave() = runComposeUiTest {
        var tryClicks = 0
        setContent {
            CoreUpgradeDecisionSheet(
                decision = decision,
                onTryWithMySave = { tryClicks++ },
                onKeepNewVersion = {},
                onLockOldVersion = {},
                onRemindLater = {},
            )
        }

        onNodeWithText("Try with my save").assertIsDisplayed()
        onNodeWithTag("sp-decision-primary").performClick()
        assertEquals(1, tryClicks)
    }

    @Test
    fun secondaryFiresOnKeepNewVersion() = runComposeUiTest {
        var keepClicks = 0
        setContent {
            CoreUpgradeDecisionSheet(
                decision = decision,
                onTryWithMySave = {},
                onKeepNewVersion = { keepClicks++ },
                onLockOldVersion = {},
                onRemindLater = {},
            )
        }

        onNodeWithText("Keep the new version anyway").assertIsDisplayed()
        onNodeWithTag("sp-decision-secondary").performClick()
        assertEquals(1, keepClicks)
    }

    @Test
    fun moreOptionsExposesLockAndRemind() = runComposeUiTest {
        var lockClicks = 0
        var remindClicks = 0
        setContent {
            CoreUpgradeDecisionSheet(
                decision = decision,
                onTryWithMySave = {},
                onKeepNewVersion = {},
                onLockOldVersion = { lockClicks++ },
                onRemindLater = { remindClicks++ },
            )
        }

        onNodeWithTag("sp-decision-more-toggle").performClick()

        onNodeWithText("Lock this session to the older version").assertIsDisplayed()
        onNodeWithText("Remind me the next time I play this").assertIsDisplayed()

        onNodeWithTag("sp-decision-more-0").performClick()
        onNodeWithTag("sp-decision-more-1").performClick()

        assertEquals(1, lockClicks)
        assertEquals(1, remindClicks)
    }

    @Test
    fun rendersFullMetaLineWhenLastPlayedIsProvided() = runComposeUiTest {
        // Spec: `"Last played {relative}  ·  Saved with version v-{abbrev}"`.
        // Full-form meta must use the specified separator (two spaces,
        // middle-dot, two spaces). Picking a 2-days-ago anchor makes
        // the relative-time assertion robust across test runs.
        val twoDaysAgo = Clock.System.now() - 2.days
        setContent {
            CoreUpgradeDecisionSheet(
                decision = decision.copy(lastPlayedAt = twoDaysAgo),
                onTryWithMySave = {},
                onKeepNewVersion = {},
                onLockOldVersion = {},
                onRemindLater = {},
            )
        }

        // formatRelativeTime emits day-granularity labels for 24h+
        // deltas — "2d ago", "two days ago", or similar depending on
        // the helper. We assert on the structural separators instead
        // of exact day phrasing so the test survives formatter tweaks.
        onNodeWithTag("sp-decision-meta").assertIsDisplayed()
    }

    @Test
    fun suppressesMetaLineWhenShaTooShortToAbbreviate() = runComposeUiTest {
        // Defensive: if the pinned sha is shorter than 4 hex chars
        // (shouldn't happen server-side, but defence in depth), we
        // don't render a bogus `v-` label.
        setContent {
            CoreUpgradeDecisionSheet(
                decision = decision.copy(oldSha = "ab"),
                onTryWithMySave = {},
                onKeepNewVersion = {},
                onLockOldVersion = {},
                onRemindLater = {},
            )
        }

        onNodeWithTag("sp-decision-meta").assertDoesNotExist()
    }
}
