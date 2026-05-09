package com.spela.player.util

/**
 * Logs a diagnostic line that survives the Android E2E workflow's
 * logcat dump filter.
 *
 * The CI logcat dump on test failure runs:
 *   adb logcat -d -s E2E_SETUP:V E2E_NAV:V Spela:V SpelaTest:V ...
 *
 * which means messages from `kotlin.io.println` (tag "System.out") are
 * dropped — even when a wider grep pass runs, "System.out" doesn't
 * match the `com.spela|ktor|...` filter either. To get a shared-code
 * log line into the failure dump, route it through this primitive: on
 * Android it lands under tag "Spela" (which the dump explicitly
 * includes); on desktop it goes to stdout.
 *
 * Reach for this when temporarily diagnosing an integration bug whose
 * data flow crosses commonMain (auth, networking, repos). Remove the
 * call once the bug is fixed — these are intentionally unstructured
 * `println`-style lines, not a substitute for proper structured
 * logging.
 *
 * Don't go through `android.util.Log` directly from common code (it's
 * Android-only) or `println` (filtered out by the dump).
 */
expect fun spelaLog(tag: String, message: String)
