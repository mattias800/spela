package com.spela.player.presentation.ui.components.social

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatRelativeTimeTest {

    @Test
    fun invalidTimestampReturnsEmpty() {
        assertEquals("", formatRelativeTime("not-a-date"))
    }

    @Test
    fun emptyStringReturnsEmpty() {
        assertEquals("", formatRelativeTime(""))
    }

    @Test
    fun veryOldTimestampReturnsWeeks() {
        // A date far in the past should produce "Xw ago"
        val result = formatRelativeTime("2020-01-01T00:00:00Z")
        assertTrue(result.endsWith("w ago"), "Expected weeks ago, got: $result")
    }

    private fun assertTrue(condition: Boolean, message: String) {
        kotlin.test.assertTrue(condition, message)
    }
}
