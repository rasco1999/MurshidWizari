package com.murshidwizari.student

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class NetworkRulesTest {
    @Test fun ambiguousPostStatusesMustNotInviteImmediateResubmission() {
        for (status in listOf(302, 408, 409, 425, 429, 500, 502, 503, 504)) {
            assertTrue("HTTP $status", NetworkRules.uncertainHttpMutation(status, true))
        }
        assertTrue(NetworkRules.uncertainHttpMutation(200, false))
        assertTrue(NetworkRules.uncertainHttpMutation(400, false))
    }

    @Test fun definiteServerValidationAndAuthRejectionsRemainDistinguishable() {
        for (status in listOf(200, 201, 400, 401, 403, 422)) {
            assertFalse("HTTP $status", NetworkRules.uncertainHttpMutation(status, true))
        }
        assertFalse(NetworkRules.uncertainHttpMutation(401, false))
    }

    @Test fun networkFailureOrIndeterminateReceiptAlwaysRequiresReconciliation() {
        assertTrue(NetworkRules.requiresReconciliation(true, false))
        assertTrue(NetworkRules.requiresReconciliation(false, true))
        assertTrue(NetworkRules.requiresReconciliation(true, true))
        assertFalse(NetworkRules.requiresReconciliation(false, false))
    }

    @Test fun timedOutPostWarnsAgainstDuplicateSubmission() {
        assertTrue(NetworkRules.message(SocketTimeoutException(), true).contains("قبل إعادة الإرسال"))
    }

    @Test fun failedGetCanBeRetried() {
        val result = NetworkRules.message(UnknownHostException(), false)
        assertTrue(result.contains("الإنترنت"))
        assertFalse(result.contains("ربما وصل الطلب"))
    }
}
