package com.muriq.murshid.iosparity.data

import org.junit.Assert.*
import org.junit.Test

class DisplayRefreshRulesTest {
    @Test fun highRefreshModeIsPreferredWhenSupported() {
        assertEquals(120f, listOf(60f, 90f, 120f).maxOf(DisplayRefreshRules::priority), 0.01f)
    }
    @Test fun unsupportedHighModesDoNotForceAnInvalidRate() {
        assertTrue(DisplayRefreshRules.priority(144f) < 0)
        assertEquals(90f, listOf(60f, 90f, 144f).maxOf(DisplayRefreshRules::priority), 0.01f)
    }
}
