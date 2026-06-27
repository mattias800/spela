package com.spela.player.android

import android.view.ViewGroup
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal Android-specific startup smoke.
 *
 * API contract smokes intentionally avoid launching MainActivity. Keep one
 * focused Activity launch in the emulator suite so CI still catches
 * Android-only startup regressions in MainActivity, platform Koin wiring, and
 * setContent attaching a view hierarchy.
 */
@RunWith(AndroidJUnit4::class)
class AndroidStartupSmokeTest {

    @get:Rule(order = 0)
    val koinResetRule = KoinResetRule()

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun mainActivityStartsAndAttachesContentView() {
        activityRule.scenario.onActivity { activity ->
            check(!activity.isFinishing) { "MainActivity is finishing immediately after launch" }
            check(!activity.isDestroyed) { "MainActivity is destroyed immediately after launch" }

            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            check(content.childCount > 0) {
                "MainActivity launched but did not attach a content view"
            }
        }
    }
}
