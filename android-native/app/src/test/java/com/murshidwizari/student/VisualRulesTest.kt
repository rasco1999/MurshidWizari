package com.murshidwizari.student

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualRulesTest {
    @Test fun examSummaryDoesNotRenderInactiveNavigationBar() {
        assertFalse(VisualRules.showExamNavigation(0, 0))
        assertTrue(VisualRules.showExamNavigation(0, 2))
        assertTrue(VisualRules.showExamNavigation(1, 2))
        assertFalse(VisualRules.showExamNavigation(2, 2))
        assertFalse(VisualRules.showExamNavigation(-1, 2))
    }

    @Test fun currentOwnerScreenshotsUseFiveRtlMainTabs() {
        assertEquals(listOf("home", "tests", "predictions", "ads", "account"),
            VisualRules.mainTabs.map { it.id })
        assertEquals(listOf("الرئيسية", "الاختبارات", "المرشحات", "الإعلانات", "حسابي"),
            VisualRules.mainTabs.map { it.title })
        assertEquals(VisualRules.mainTabs.size, VisualRules.mainTabs.map { it.icon }.distinct().size)
    }

    @Test fun darkAppearanceFollowsSystemOnlyUntilUserOverridesIt() {
        assertTrue(VisualRules.initialNight(null, true))
        assertEquals(false, VisualRules.initialNight(null, false))
        assertEquals(false, VisualRules.initialNight(false, true))
        assertTrue(VisualRules.initialNight(true, false))
    }

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
        assertEquals(5500L, VisualRules.heroAutoAdvanceMs)
        assertEquals(350L, VisualRules.heroPageTransitionMs)
        assertEquals(200L, VisualRules.heroDotTransitionMs)
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
