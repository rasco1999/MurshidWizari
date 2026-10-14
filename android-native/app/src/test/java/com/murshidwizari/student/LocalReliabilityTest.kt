package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class LocalReliabilityTest {
    @Test fun scrubEndpointsAndQueryParameters() {
        assertEquals("exam", LocalReliability.category("mobile/exam.php?chapter_id=999"))
        assertEquals("answers", LocalReliability.category("api/progress.php"))
        assertEquals("mobile_other", LocalReliability.category("mobile/secret-account?email=a@example.com"))
        assertEquals("other", LocalReliability.category("https://example.com/token=secret"))
    }
}
