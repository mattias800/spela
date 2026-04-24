package com.spela.player.android

import android.content.Context
import android.hardware.display.DisplayManager
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector

/**
 * Granular startup verification used by every Android E2E test. The goal
 * isn't to pass/fail — it's to emit a rich diagnostic report when
 * something is off so future debugging doesn't start from "Condition
 * still not satisfied after 60000 ms".
 *
 * Each check appends a short line to the report. `verifyCleanStart()`
 * throws an `AssertionError` with the full report if any check reports
 * a problem, so the test log surfaces the context directly instead of
 * making you reconstruct it from stack traces.
 *
 * Expected call site: at the top of `ensureLoggedIn()`, before any
 * interaction with the UI. Cost: ~100-300ms.
 *
 * Historical failures this catches:
 *
 *  - `com.odin.gameassistant` (AYN Thor game overlay) holds the
 *    accessibility bridge, which starves UiAutomator and causes
 *    `null root node returned by UiTestAutomationBridge` — every
 *    helper that falls back to UiAutomator then returns false
 *    silently, and tests time out waiting for a UI that's actually
 *    on screen.
 *  - Secondary displays (AYN Thor, DeX, DisplayLink) can pollute
 *    UiAutomator's tree with windows the app doesn't own.
 *  - An app instance left over from a previous run sits on an
 *    unexpected screen; `ensureLoggedIn()` navigates, but the first
 *    action assumes an add-server screen it never sees.
 *  - Fullscreen IME in landscape hides the actual composable tree
 *    behind an "extract view"; tests that type into a field succeed,
 *    but the next UiAutomator lookup fails because only the IME is
 *    visible.
 */
class StartupDiagnostics private constructor() {

    private val lines = mutableListOf<String>()
    private var problems = 0

    fun line(s: String) {
        lines.add(s)
    }

    fun problem(s: String) {
        lines.add("  [PROBLEM] $s")
        problems++
    }

    private fun report(): String = lines.joinToString("\n")

    fun throwIfAnyProblem() {
        if (problems > 0) {
            throw AssertionError(
                "verifyCleanStart detected $problems problem(s):\n\n" +
                    report() + "\n\n" +
                    "Fix these before the real assertion or the test will fail " +
                    "in a confusing place.",
            )
        }
    }

    companion object {

        /**
         * Run all diagnostics. Returns the populated instance; call
         * [throwIfAnyProblem] to abort if anything was off, or inspect
         * the report directly.
         */
        fun run(): StartupDiagnostics {
            val d = StartupDiagnostics()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            val ctx: Context = instrumentation.targetContext

            d.line("── verifyCleanStart report ──")

            // 1. Accessibility bridge — the #1 source of silent UiAutomator
            //    failures on physical devices. Any third-party service
            //    registered here competes with UiAutomator.
            val a11y = android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            d.line("Accessibility services enabled: '${a11y.ifEmpty { "<none>" }}'")
            if (a11y.isNotEmpty()) {
                d.problem(
                    "Third-party accessibility service '$a11y' is registered. " +
                        "Disable it with: " +
                        "adb shell \"settings put secure enabled_accessibility_services ''\"  " +
                        "(run-e2e.sh snapshots + restores this automatically).",
                )
            }

            // 2. UiAutomator bridge response — the direct symptom of the
            //    accessibility-service conflict. If the above is empty and
            //    this still fails, it's a different platform bug and the
            //    error mode will be very different.
            try {
                val root = device.findObject(UiSelector().packageName("com.spela.player"))
                // `exists()` returns false (no crash) when the bridge is
                // alive but the app package isn't the current window.
                // That's fine for this check — what we care about is that
                // the call doesn't throw with "null root node".
                d.line("UiAutomator bridge: responsive (exists=${root.exists()})")
            } catch (e: Throwable) {
                d.problem(
                    "UiAutomator bridge threw ${e.javaClass.simpleName}: " +
                        "${e.message}. Tests that call UiAutomator helpers " +
                        "(backHomeFromGame, scroll detectors) will fail.",
                )
            }

            // 3. Display count — multi-display devices sometimes place
            //    launcher content on a second display, which shows up in
            //    UiAutomator dumps and confuses queries.
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = dm.displays.filter { it.state == android.view.Display.STATE_ON }
            d.line("Displays ON: ${displays.size} (ids=${displays.map { it.displayId }})")
            if (displays.size > 1) {
                d.line(
                    "  note: secondary display detected; if tests flake " +
                        "on multi-match queries, check that the target " +
                        "composable isn't duplicated onto the secondary.",
                )
            }

            // 4. Current focus — if the launcher is focused the app isn't
            //    visible yet (activity rule may still be starting); tests
            //    then see an empty semantics tree.
            val focus = try {
                device.currentPackageName ?: "<null>"
            } catch (e: Throwable) {
                "<error: ${e.message}>"
            }
            d.line("Current top package: $focus")
            if (focus != "com.spela.player") {
                d.line(
                    "  note: current package is '$focus' (expected com.spela.player). " +
                        "The ActivityRule may still be launching; ensureLoggedIn " +
                        "will poll for a recognisable screen.",
                )
            }

            // 5. Fullscreen IME — when in "extract view", tests that try to
            //    read content on screen only see the IME's extract host.
            //    This check is best-effort because there's no public API;
            //    we infer from UiAutomator looking for IME package windows.
            try {
                val imeHost = device.findObject(
                    UiSelector().packageName("com.google.android.inputmethod.latin"),
                )
                if (imeHost.exists()) {
                    d.line(
                        "  note: IME surface is visible; if a previous " +
                            "test left a TextField focused in fullscreen " +
                            "IME mode, dismiss with " +
                            "`device.pressBack()` before asserting.",
                    )
                }
            } catch (_: Throwable) {
                // Not fatal — we just can't tell, skip.
            }

            d.line("── end report (problems=${d.problems}) ──")
            return d
        }

        /** Convenience — run + throw if anything's off, one-liner at test start. */
        fun assertClean() {
            run().throwIfAnyProblem()
        }
    }
}

/**
 * Asserts the semantics tree isn't suspiciously empty. A just-launched
 * activity with no composables is usually a sign the theme / activity
 * crashed silently; the symptom is otherwise a generic `not found
 * within 8000ms` on the first `waitForText`.
 */
fun ComposeRule.assertSemanticsTreeHasContent(minNodes: Int = 3) {
    val all: SemanticsNodeInteractionCollection = onAllNodesWithText("", substring = true)
    val count = try {
        all.fetchSemanticsNodes().size
    } catch (e: Throwable) {
        throw AssertionError(
            "Could not fetch semantics nodes: ${e.message}. The Compose " +
                "hierarchy is probably not attached yet — check that " +
                "createAndroidComposeRule was created with the right " +
                "Activity class and the activity finished onCreate.",
        )
    }
    if (count < minNodes) {
        throw AssertionError(
            "Semantics tree only has $count node(s) (min $minNodes). The " +
                "activity likely rendered a blank screen. Common causes: " +
                "theme crash, splash overlay, or the activity detached " +
                "before composition finished.",
        )
    }
}
