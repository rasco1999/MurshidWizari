package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class AccountRulesTest {
    @Test fun normalizeAndAcceptThreePartNames() {
        assertEquals("علي محمد حسن", AccountRules.normalizeName("  علي  محمد\nحسن "))
        assertEquals("علي محمد حسن", AccountRules.normalizeName("علي\u202e محمد حسن"))
        assertTrue(AccountRules.validFullName("علي محمد حسن"))
        assertFalse(AccountRules.validFullName("علي حسن"))
        assertFalse(AccountRules.validFullName("علي محمد 3"))
    }

    @Test fun paymentPhoneMustBeIraqiMobile() {
        assertTrue(AccountRules.validIraqiPhone("0770 123 4567"))
        assertFalse(AccountRules.validIraqiPhone("078123456"))
        assertFalse(AccountRules.validIraqiPhone("+9647701234567"))
    }

    @Test fun avatarRevisionHandlesExistingQueryAndFragment() {
        assertEquals("https://www.mur-iq.com/a.jpg?android_rev=44", AccountRules.avatarUrl("https://www.mur-iq.com/a.jpg", "44"))
        assertEquals("https://mur-iq.com/a.jpg?size=1&android_rev=5#avatar", AccountRules.avatarUrl("https://mur-iq.com/a.jpg?size=1#avatar", "5"))
        assertNull(AccountRules.avatarUrl("http://mur-iq.com/a.jpg", "2"))
        assertNull(AccountRules.avatarUrl("https://mur-iq.com.evil.net/a.jpg", "2"))
    }

    @Test fun checkoutUrlMustBeHttpsAndHaveNoEmbeddedCredentials() {
        assertTrue(AccountRules.safePaymentRedirect("https://gateway.example/checkout?order=1"))
        assertFalse(AccountRules.safePaymentRedirect("javascript:alert(1)"))
        assertFalse(AccountRules.safePaymentRedirect("http://gateway.example/checkout"))
        assertFalse(AccountRules.safePaymentRedirect("https://user:pass@gateway.example/pay"))
    }
}
