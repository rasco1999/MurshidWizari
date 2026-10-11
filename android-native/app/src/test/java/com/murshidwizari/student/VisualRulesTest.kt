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

    @Test fun buttonsAndSmallIconBubblesMatchSwiftUiTokens() {
        assertEquals(50, VisualRules.primaryButtonHeight)
        assertEquals(48, VisualRules.secondaryButtonHeight)
        assertEquals(17, VisualRules.primaryButtonRadius)
        assertEquals(16, VisualRules.secondaryButtonRadius)
        assertEquals(9, VisualRules.iconBubbleRadius(30))
        assertEquals(10, VisualRules.iconBubbleRadius(34))
        assertEquals(11, VisualRules.iconBubbleRadius(36))
        assertEquals(14, VisualRules.iconBubbleRadius(48))
        assertEquals(17, VisualRules.iconBubbleGlyphSize(30))
        assertEquals(18, VisualRules.iconBubbleGlyphSize(36))
        assertEquals(24, VisualRules.iconBubbleGlyphSize(48))
    }
}
