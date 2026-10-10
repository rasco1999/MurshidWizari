package com.murshidwizari.student

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class NetworkRulesTest {
    @Test fun timedOutPostWarnsAgainstDuplicateSubmission() {
        assertTrue(NetworkRules.message(SocketTimeoutException(), true).contains("قبل إعادة الإرسال"))
    }

    @Test fun failedGetCanBeRetried() {
        val result = NetworkRules.message(UnknownHostException(), false)
        assertTrue(result.contains("الإنترنت"))
        assertFalse(result.contains("ربما وصل الطلب"))
    }
}
