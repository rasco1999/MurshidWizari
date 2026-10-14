package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class OfflineExamRulesTest {
    @Test fun supportsSimpleAnswersForOfflineServerGrading() {
        for (kind in listOf("mcq", "true_false", "text", "fill", "meaning")) {
            assertTrue(OfflineExamRules.supported(kind, 2))
        }
    }

    @Test fun refusesMultistageQuestionsRatherThanSilentlyMisgrading() {
        for (kind in listOf("calculation", "genetics", "match", "unknown")) {
            assertFalse(OfflineExamRules.supported(kind, 0))
        }
    }
}
