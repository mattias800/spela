package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spela.player.presentation.ui.TestTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Base class for every Android E2E test.
 *
 * Contract:
 *   Entry (per test method): user is logged in, on the Home screen,
 *                            backend reset to seed state.
 *   Exit  (per test method): user is logged in, on the Home screen.
 *
 * The entry contract frees each test from re-running login logic.
 * The exit contract means a crashed test doesn't leak its screen
 * state to the following test.
 *
 * Rules run outermost-first (order=0 before order=1):
 *   0. KoinResetRule — fail-fast gate (anyTestFailed) + preCacheCores
 *      + isTestMode flip. Must run BEFORE the Activity is created.
 *   1. createAndroidComposeRule — launches MainActivity.
 *
 * The existing FailureDiagnosticsListener is registered globally via
 * testInstrumentationRunnerArguments["listener"] in build.gradle.kts,
 * so failures here still produce screenshot + ui.xml + logcat +
 * state.json + failure.txt + repro.txt under /sdcard/spela-test-failures/
 * — the base class does not touch that machinery.
 *
 * Tests that need a different entry state (e.g. EstablishSessionTest,
 * which is about the first-install server-connect UX) override
 * baseSetUp() and skip or modify the login step. Keep the @Before
 * annotation when overriding, otherwise JUnit silently won't call it.
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseE2ETest {

    @get:Rule(order = 0)
    val koinResetRule = KoinResetRule()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    open fun baseSetUp() {
        // 1. Reset backend to seed state. Hard-fails loudly if the
        //    endpoint is unreachable — this is load-bearing.
        resetServerState()

        // 2. Ensure we're logged in. ensureLoggedIn() fast-paths when
        //    Home is already visible (every test after the first),
        //    and falls back to add-server + login when SQLDelight is
        //    empty (first test of the run) or the JWT was invalidated
        //    by the reset (rare — happens when a prior test rotated
        //    tokens).
        rule.ensureLoggedIn()

        // 3. Force the active tab to Home so each test starts on a
        //    deterministic stack. ensureLoggedIn can return when ANY
        //    logged-in screen is visible (Home, Settings, etc.) — if
        //    a previous test ended on a different tab and the @After
        //    teardown was skipped (e.g. process restart), the next
        //    test's first tap can race against an unexpected tab.
        runCatching { rule.tapOnTag(TestTags.NAV_HOME, fallbackLabel = "Home") }

        // 4. Contract check. If we're not on Home, something in the
        //    setup path is broken — surface it now, not 30s into the
        //    test body.
        rule.assertOnHome()
    }

    @After
    open fun baseTearDown() {
        // Best-effort: return to Home so the next test starts from a
        // known state without having to run slow screen detection.
        // navigateBackToHome handles in-game overlays, settings
        // sub-screens, and arbitrary deep links; it stops at auth
        // screens to avoid exiting the Activity. Then explicitly
        // switch to the Home tab — navigateBackToHome only presses
        // back, so a previous test that ended on the Settings or
        // Consoles tab leaves the activeTab there and the next test
        // starts with the wrong tab on top.
        //
        // runCatching because a failure here must not mask the real
        // assertion failure — FailureDiagnosticsListener has already
        // captured artefacts at the moment of the @Test failure.
        runCatching { rule.navigateBackToHome() }
        runCatching { rule.tapOnTag(TestTags.NAV_HOME, fallbackLabel = "Home") }
    }
}
