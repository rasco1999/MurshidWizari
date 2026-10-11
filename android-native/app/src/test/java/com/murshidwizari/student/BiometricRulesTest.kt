package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class BiometricRulesTest {
    private fun lock(enabled: Boolean = true, account: Boolean = true, authenticating: Boolean = false,
                     locked: Boolean = false, pending: Boolean = false, away: Long = 30_000,
                     sinceUnlock: Long = 30_000) = BiometricRules.shouldLock(
        enabled, account, authenticating, locked, pending, away, sinceUnlock)

    @Test fun activeAccountLocksAfterTwentySecondsRegardlessOfRememberSetting() {
        assertTrue(lock())
        assertFalse(lock(away = 20_000))
        assertTrue(lock(away = 20_001))
    }

    @Test fun freshSuccessfulAuthenticationDoesNotTriggerAnotherPrompt() {
        assertFalse(lock(sinceUnlock = 1_000))
        assertFalse(lock(authenticating = true))
        assertFalse(lock(locked = true))
    }

    @Test fun pendingUnlockSurvivesAnImmediateProcessRestartOrClockChange() {
        assertTrue(lock(pending = true, away = 0, sinceUnlock = 0))
        assertTrue(lock(pending = true, account = false, away = -10))
    }

    @Test fun disabledLockAndAnonymousSessionsStayUsable() {
        assertFalse(lock(enabled = false, pending = true))
        assertFalse(lock(account = false))
        assertFalse(lock(away = 0))
    }
}
