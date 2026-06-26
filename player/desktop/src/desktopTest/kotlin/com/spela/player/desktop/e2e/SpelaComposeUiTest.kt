package com.spela.player.desktop.e2e

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import kotlinx.coroutines.test.TestResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Compose Desktop's default runComposeUiTest timeout is 60 seconds. That is
 * tight for full-app E2E tests on GitHub's software-rendered Linux runners,
 * especially flows that drive text input and focus/idleness repeatedly.
 */
private val SpelaComposeUiTestTimeout: Duration = 2.minutes

@OptIn(ExperimentalTestApi::class)
fun runComposeUiTest(block: suspend ComposeUiTest.() -> Unit): TestResult =
    androidx.compose.ui.test.runComposeUiTest(
        testTimeout = SpelaComposeUiTestTimeout,
        block = block,
    )
