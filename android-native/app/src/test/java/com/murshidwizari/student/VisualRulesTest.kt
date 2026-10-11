package com.murshidwizari.student

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualRulesTest {
    @Test fun phoneGridStaysTwoColumnsLikeIos() {
        assertEquals(2, VisualRules.homeColumns())
        assertEquals(138, VisualRules.tileWidthDp(320))
        assertEquals(158, VisualRules.tileWidthDp(360))
    }

    @Test fun tabletContentCentersAndCapsAtSevenHundred() {
        assertNull(VisualRules.contentWidthDp(600))
        assertEquals(700, VisualRules.contentWidthDp(800))
        assertEquals(328, VisualRules.tileWidthDp(800))
    }

    @Test fun sharedGeometryTracksSwiftUiReference() {
        assertEquals(22, VisualRules.cardCornerRadius)
        assertEquals(16, VisualRules.cardInset)
        assertEquals(12, VisualRules.gridSpacing)
        assertEquals(220, VisualRules.heroHeight)
        assertTrue(VisualRules.minTouchTarget >= 44)
    }
}
