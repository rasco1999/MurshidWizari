package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class OfflineExamRulesTest {
    @Test fun supportsSimpleAnswersForOfflineServerGrading() {
        for (kind in listOf("mcq", "true_false", "text", "fill", "meaning")) {
            assertTrue(OfflineExamRules.supported(kind, 2))
        }
    }

    @Test fun refusesMalformedMcqOptions() {
        assertFalse(OfflineExamRules.supported("mcq", 0))
        assertFalse(OfflineExamRules.supported("mcq", 1))
        assertTrue(OfflineExamRules.supported("mcq", 2))
    }

    @Test fun refusesMultistageQuestionsRatherThanSilentlyMisgrading() {
        for (kind in listOf("calculation", "genetics", "match", "unknown")) {
            assertFalse(OfflineExamRules.supported(kind, 0))
        }
    }

    @Test fun offlineRevisionSignatureBlocksChangedQuestions() {
        fun sig(text: String = "ما ناتج 2+2؟", correct: String = "4",
                options: List<Pair<Int,String>> = listOf(11 to "4", 12 to "5")) =
            OfflineExamRules.signature(251, "mcq", text, "", correct, options)
        val originallyDownloaded = sig()
        assertEquals(originallyDownloaded, sig())
        assertNotEquals(originallyDownloaded, sig(text = "ما ناتج 2+3؟"))
        assertNotEquals(originallyDownloaded, sig(correct = "5"))
        assertNotEquals(originallyDownloaded, sig(options = listOf(11 to "5", 12 to "4")))
        assertNotEquals(originallyDownloaded, sig(options = listOf(13 to "4", 12 to "5")))
    }

    @Test fun signatureIncludesQuestionIdentityAndArabicText() {
        val original = OfflineExamRules.signature(10, "text", "أكمل الآية", "", "الإخلاص", emptyList())
        assertEquals(original,
            OfflineExamRules.signature(10, "text", "أكمل الآية", "", "الإخلاص", emptyList()))
        assertNotEquals(original,
            OfflineExamRules.signature(11, "text", "أكمل الآية", "", "الإخلاص", emptyList()))
        assertNotEquals(original,
            OfflineExamRules.signature(10, "text", "اكمل الآية", "", "الإخلاص", emptyList()))
    }
}
