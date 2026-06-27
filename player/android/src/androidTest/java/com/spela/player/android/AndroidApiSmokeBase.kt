package com.spela.player.android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Base class for Android instrumentation smokes that need the app process and
 * production Android Koin graph, but do not need to launch MainActivity.
 *
 * This keeps the live Android/OkHttp/SQLDelight bindings used by the app while
 * avoiding ComposeRule + Activity startup for pure repository/API contract
 * checks.
 */
abstract class AndroidApiSmokeBase {

    @get:Rule(order = 0)
    val androidApiSmokeRule = AndroidApiSmokeRule()

    @Before
    open fun baseSetUp() {
        resetServerState()
    }
}

class AndroidApiSmokeRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val ffArg = InstrumentationRegistry
                    .getArguments()
                    .getString("failFast")
                if (ffArg != "off" && FailureDiagnosticsListener.anyTestFailed) {
                    throw org.junit.AssumptionViolatedException(
                        "Skipping ${description.methodName} - earlier failure in this run, fail-fast active",
                    )
                }

                MainActivity.isTestMode = true
                // Touch the target application context so SpelaApplication has
                // started Koin before API smoke tests resolve production singletons.
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

                base.evaluate()
            }
        }
    }
}
