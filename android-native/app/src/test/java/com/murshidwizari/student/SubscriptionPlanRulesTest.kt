package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class SubscriptionPlanRulesTest {
    @Test fun acceptsBootstrapPlanKeyAndLegacyId() {
        assertEquals("month1", SubscriptionPlanRules.identifier(null, "month1"))
        assertEquals("month3", SubscriptionPlanRules.identifier("month3", null))
        assertEquals("month1", SubscriptionPlanRules.identifier("", " month1 "))
        assertEquals("month3", SubscriptionPlanRules.identifier("month1", "month3"))
    }

    @Test fun rejectsBlankAndZeroPricePlans() {
        assertFalse(SubscriptionPlanRules.selectable("", 8000))
        assertFalse(SubscriptionPlanRules.selectable("month1", 0))
        assertTrue(SubscriptionPlanRules.selectable("month1", 8000))
        assertTrue(SubscriptionPlanRules.selectable("month3", 15000))
    }

    @Test fun choosesExistingOrFirstServerProvidedPlan() {
        assertEquals("month3", SubscriptionPlanRules.selected(listOf("month1","month3"), "month3"))
        assertEquals("month1", SubscriptionPlanRules.selected(listOf("month1","month3"), "old"))
        assertEquals("", SubscriptionPlanRules.selected(emptyList(), "month1"))
    }
}
