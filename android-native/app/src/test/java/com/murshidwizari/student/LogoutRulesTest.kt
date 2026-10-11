package com.murshidwizari.student

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoutRulesTest {
    @Test fun rejectionOrUnknownReceiptMustKeepSession() {
        assertFalse(LogoutRules.mayClearLocalSession("session-csrf", false))
    }

    @Test fun serverConfirmedLogoutClearsSession() {
        assertTrue(LogoutRules.mayClearLocalSession("session-csrf", true))
    }

    @Test fun noCsrfRequiresOnlyLocalLogoutAsOnIos() {
        assertTrue(LogoutRules.mayClearLocalSession("", false))
        assertTrue(LogoutRules.mayClearLocalSession("  ", false))
    }
}
