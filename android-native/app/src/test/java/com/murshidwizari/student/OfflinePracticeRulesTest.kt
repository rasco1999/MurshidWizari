package com.murshidwizari.student

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflinePracticeRulesTest {
    @Test fun givesUsefulNumericAndChoiceFeedback() {
        assertEquals(OfflinePracticeRules.Result.CORRECT,
            OfflinePracticeRules.evaluate("text", "٨.٠", "8"))
        assertEquals(OfflinePracticeRules.Result.INCORRECT,
            OfflinePracticeRules.evaluate("mcq", "ستة", "خمسة", listOf("خمسة", "ستة")))
        assertEquals(OfflinePracticeRules.Result.CORRECT,
            OfflinePracticeRules.evaluate("text", "عَلِيّ.", "علي"))
    }
    @Test fun neverGuessesTextOrUnsupportedAlgebra() {
        for ((given, expected) in listOf("2+3" to "23", "10^2" to "102", "1,5" to "15")) {
            assertEquals(OfflinePracticeRules.Result.NEEDS_SERVER,
                OfflinePracticeRules.evaluate("text", given, expected))
        }
        assertEquals(OfflinePracticeRules.Result.NEEDS_SERVER,
            OfflinePracticeRules.evaluate("text", "إن الله", "ان الله"))
        assertEquals(OfflinePracticeRules.Result.NEEDS_SERVER,
            OfflinePracticeRules.evaluate("text", "x+x", "2*x"))
        assertEquals(OfflinePracticeRules.Result.NEEDS_SERVER,
            OfflinePracticeRules.evaluate("mcq", "جواب غير موجود", "خمسة", listOf("خمسة", "ستة")))
        assertEquals(OfflinePracticeRules.Result.NEEDS_SERVER,
            OfflinePracticeRules.evaluate("text", "", "8"))
    }
}
