package com.muriq.murshid.iosparity.ui

import org.junit.Assert.*
import org.junit.Test

class MurshidPaletteParityTest {
    @Test fun matchingThreeSourcePalettes() {
        assertEquals(listOf("royal", "aurora", "orchid"), MurshidPaletteOptions.names)
        assertNotEquals(MurshidPaletteOptions.forName("royal"),
            MurshidPaletteOptions.forName("orchid"))
    }
}
