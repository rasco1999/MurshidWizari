package com.murshidwizari.student

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRulesTest {
    @Test fun liveSearchEnvelopeWithoutOkIsAccepted() {
        assertTrue(SearchRules.accepts(hasOk = false, ok = false, hasResultsArray = true))
        assertTrue(SearchRules.accepts(hasOk = true, ok = true, hasResultsArray = true))
        assertFalse(SearchRules.accepts(hasOk = true, ok = false, hasResultsArray = true))
        assertFalse(SearchRules.accepts(hasOk = false, ok = false, hasResultsArray = false))
        assertFalse(SearchRules.accepts(hasOk = true, ok = true, hasResultsArray = false))
    }

    @Test fun observedSearchLinksOpenOnlyNativeExams() {
        assertEquals(253, SearchRules.chapterId("exam.php?chapter_id=253"))
        assertEquals(67, SearchRules.chapterId("/student/exam.php?chapter_id=67"))
        assertEquals(228, SearchRules.chapterId("student/exam.php?source=search&chapter_id=228"))
    }

    @Test fun externalMalformedAndAmbiguousLinksAreIgnored() {
        for (url in listOf("https://evil.invalid/exam.php?chapter_id=253", "//evil.invalid/exam.php?chapter_id=253",
            "account.php?chapter_id=253", "exam.php?chapter_id=0", "exam.php?chapter_id=-1",
            "exam.php?chapter_id=a", "exam.php?chapter_id=99999999999999999",
            "exam.php?chapter_id=1&chapter_id=2", "exam.php?chapter_id=4#fragment", "")) {
            assertNull(url, SearchRules.chapterId(url))
        }
    }
}
