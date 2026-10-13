package com.murshidwizari.student

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolAdBadgeRulesTest {
    @Test fun existingAdsDoNotCreateAnUpgradeBadge() {
        val baseline = SchoolAdBadgeRules.decode(SchoolAdBadgeRules.encode(setOf(1, 2, 3)))
        assertEquals(0, SchoolAdBadgeRules.unreadCount(setOf(1, 2, 3), baseline))
        assertEquals(1, SchoolAdBadgeRules.unreadCount(setOf(1, 2, 3, 4), baseline))
    }

    @Test fun visitingAdsMarksPublishedAnnouncementsAsRead() {
        val old = setOf(2, 3)
        val newlyPublished = setOf(2, 3, 7, 9)
        assertEquals(2, SchoolAdBadgeRules.unreadCount(newlyPublished, old))
        val nowSeen = old + newlyPublished
        assertEquals(0, SchoolAdBadgeRules.unreadCount(newlyPublished, nowSeen))
    }

    @Test fun stateIsRobustAgainstMalformedStoredIds() {
        assertEquals(setOf(2, 11), SchoolAdBadgeRules.decode("2,-3,abc,11,2,,0"))
        assertEquals("2,11", SchoolAdBadgeRules.encode(setOf(-1, 11, 2, 0)))
        assertTrue(SchoolAdBadgeRules.decode("").isEmpty())
    }

    @Test fun badgeNeverOverflowsItsSmallNativeIcon() {
        assertEquals("", SchoolAdBadgeRules.badgeLabel(0))
        assertEquals("3", SchoolAdBadgeRules.badgeLabel(3))
        assertEquals("9+", SchoolAdBadgeRules.badgeLabel(10))
        assertEquals(99, SchoolAdBadgeRules.unreadCount((1..150).toSet(), emptySet()))
    }
}
