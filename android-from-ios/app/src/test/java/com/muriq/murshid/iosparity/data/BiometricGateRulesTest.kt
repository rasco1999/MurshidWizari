package com.muriq.murshid.iosparity.data

import org.junit.Assert.*
import org.junit.Test

class BiometricGateRulesTest {
    @Test fun rememberedBiometricSessionStartsLocked() {
        assertTrue(BiometricGateRules.requiresUnlock(true, true))
        assertFalse(BiometricGateRules.requiresUnlock(false, true))
        assertFalse(BiometricGateRules.requiresUnlock(true, false))
    }
}
