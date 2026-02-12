package com.spela.player.presentation.secondarydisplay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * A fake implementation of [PlatformSecondaryDisplay] for testing.
 * Tracks show/dismiss calls and allows controlling availability.
 */
class FakePlatformSecondaryDisplay : PlatformSecondaryDisplay {
    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    var showCallCount = 0
        private set
    var dismissCallCount = 0
        private set
    var isShowing = false
        private set

    fun setAvailable(available: Boolean) {
        _isAvailable.value = available
    }

    override fun show() {
        showCallCount++
        isShowing = true
    }

    override fun dismiss() {
        dismissCallCount++
        isShowing = false
    }
}

class FakePlatformSecondaryDisplayTest {

    @Test
    fun initiallyNotAvailable() {
        val display = FakePlatformSecondaryDisplay()
        assertFalse(display.isAvailable.value)
        assertFalse(display.isShowing)
    }

    @Test
    fun setAvailableUpdatesState() {
        val display = FakePlatformSecondaryDisplay()
        display.setAvailable(true)
        assertTrue(display.isAvailable.value)

        display.setAvailable(false)
        assertFalse(display.isAvailable.value)
    }

    @Test
    fun showTracksState() {
        val display = FakePlatformSecondaryDisplay()
        assertEquals(0, display.showCallCount)

        display.show()
        assertEquals(1, display.showCallCount)
        assertTrue(display.isShowing)
    }

    @Test
    fun dismissTracksState() {
        val display = FakePlatformSecondaryDisplay()
        display.show()
        assertTrue(display.isShowing)

        display.dismiss()
        assertEquals(1, display.dismissCallCount)
        assertFalse(display.isShowing)
    }

    @Test
    fun showAndDismissCycleTracksCorrectly() {
        val display = FakePlatformSecondaryDisplay()

        display.show()
        assertTrue(display.isShowing)
        assertEquals(1, display.showCallCount)

        display.dismiss()
        assertFalse(display.isShowing)
        assertEquals(1, display.dismissCallCount)

        display.show()
        assertTrue(display.isShowing)
        assertEquals(2, display.showCallCount)
    }

    @Test
    fun dismissWhenNotShowingIsSafe() {
        val display = FakePlatformSecondaryDisplay()
        assertFalse(display.isShowing)

        display.dismiss()
        assertFalse(display.isShowing)
        assertEquals(1, display.dismissCallCount)
    }
}
