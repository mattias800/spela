package com.spela.player.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ControllerStyleTest {
    @Test
    fun classifiesKnownVendors() {
        assertEquals(ControllerStyle.PlayStation, ControllerClassifier.fromVendorProduct(0x054C, 0x0CE6, ""))
        assertEquals(ControllerStyle.Xbox, ControllerClassifier.fromVendorProduct(0x045E, 0x02FD, ""))
        assertEquals(ControllerStyle.Nintendo, ControllerClassifier.fromVendorProduct(0x057E, 0x2009, ""))
    }

    @Test
    fun unknownVendorFallsBackToNameThenGeneric() {
        assertEquals(ControllerStyle.Nintendo, ControllerClassifier.fromVendorProduct(0x0000, 0x0000, "Pro Controller"))
        assertEquals(ControllerStyle.Generic, ControllerClassifier.fromVendorProduct(0x1234, 0x5678, "Some USB Gamepad"))
    }
}
