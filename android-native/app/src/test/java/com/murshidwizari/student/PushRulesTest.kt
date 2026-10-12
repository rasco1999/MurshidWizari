package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class PushRulesTest {
    private val accountA = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    private val accountB = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    @Test fun accountChangesAndLogoutCannotDisplayStalePush() {
        assertTrue(PushRules.accepts(5, accountA, accountA, 9, 8, true))
        assertFalse(PushRules.accepts(6, accountB, accountA, 9, 8, true))
        assertFalse(PushRules.accepts(0, accountA, accountA, 9, 8, true))
        assertFalse(PushRules.accepts(5, "", "", 9, 8, true))
    }
    @Test fun duplicatesMalformedIdsAndPermissionDenialAreIgnored() {
        assertFalse(PushRules.accepts(5, accountA, accountA, 9, 9, true))
        assertFalse(PushRules.accepts(5, accountA, accountA, 7, 9, true))
        assertFalse(PushRules.accepts(5, accountA, accountA, 0, 0, true))
        assertFalse(PushRules.accepts(5, accountA, accountA, 9, 8, false))
        assertFalse(PushRules.validBinding("https://example.test"))
    }
}
