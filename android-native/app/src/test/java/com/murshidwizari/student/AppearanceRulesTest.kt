package com.murshidwizari.student

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceRulesTest {
    @Test fun displayDefaultsToSystemUntilExplicitChoice() {
        assertEquals(AppearanceRules.SYSTEM, AppearanceRules.normalizeMode(null, null))
        assertTrue(AppearanceRules.isDark(AppearanceRules.SYSTEM, true))
        assertFalse(AppearanceRules.isDark(AppearanceRules.SYSTEM, false))
    }

    @Test fun existingManualNightPreferenceIsPreserved() {
        assertEquals(AppearanceRules.DARK, AppearanceRules.normalizeMode(null, true))
        assertEquals(AppearanceRules.LIGHT, AppearanceRules.normalizeMode(null, false))
        assertFalse(AppearanceRules.isDark(AppearanceRules.LIGHT, true))
        assertTrue(AppearanceRules.isDark(AppearanceRules.DARK, false))
    }

    @Test fun selectedSystemModeSupersedesLegacyBoolean() {
        assertEquals(AppearanceRules.SYSTEM, AppearanceRules.normalizeMode("system", true))
        assertEquals(AppearanceRules.LIGHT, AppearanceRules.normalizeMode("light", true))
        assertEquals(AppearanceRules.DARK, AppearanceRules.normalizeMode("dark", false))
    }

    @Test fun onlyOwnerVisiblePaletteIsAccepted() {
        assertEquals(AppearanceRules.ROYAL, AppearanceRules.normalizeAccent(null))
        assertEquals(AppearanceRules.ROYAL, AppearanceRules.normalizeAccent("random"))
        assertEquals(AppearanceRules.VIOLET, AppearanceRules.normalizeAccent("violet"))
        assertEquals(AppearanceRules.AURORA, AppearanceRules.normalizeAccent("aurora"))
    }
}
