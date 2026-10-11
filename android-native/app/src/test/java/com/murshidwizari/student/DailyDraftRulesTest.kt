package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class DailyDraftRulesTest {
    private val questions = listOf(
        CompetitionRules.Question(10, "mcq", "ما الإجابة الصحيحة؟", listOf("أ", "ب")),
        CompetitionRules.Question(11, "text", "أكمل العبارة التالية", emptyList())
    )

    @Test fun signatureIsStableButScopedToAccountDayAndLiveQuestionContent() {
        val signature = DailyDraftRules.signature(4, "2026-10-11", questions)
        assertEquals(64, signature.length)
        assertEquals(signature, DailyDraftRules.signature(4, "2026-10-11", questions))
        assertNotEquals(signature, DailyDraftRules.signature(5, "2026-10-11", questions))
        assertNotEquals(signature, DailyDraftRules.signature(4, "2026-10-12", questions))
        assertNotEquals(signature, DailyDraftRules.signature(4, "2026-10-11", questions.reversed()))
        assertNotEquals(signature, DailyDraftRules.signature(4, "2026-10-11",
            questions.map { if (it.id == 10) it.copy(text = "سؤال آخر مختلف") else it }))
    }

    @Test fun invalidOrAnonymousChallengeCannotCreatePersistentDraftSignature() {
        assertEquals("", DailyDraftRules.signature(0, "2026-10-11", questions))
        assertEquals("", DailyDraftRules.signature(4, "", questions))
        assertEquals("", DailyDraftRules.signature(4, "2026-10-11", emptyList()))
        assertEquals("", DailyDraftRules.signature(4, "2026-10-11", questions + questions.first()))
    }

    @Test fun restoringDraftFiltersUnknownAndNoLongerAvailableAnswers() {
        assertEquals(mapOf(10 to "أ", 11 to "إجابة مكتوبة"), DailyDraftRules.validAnswers(questions,
            mapOf(10 to "أ", 11 to "  إجابة مكتوبة ", 99 to "قديم")))
        assertEquals(mapOf(11 to "نص"), DailyDraftRules.validAnswers(questions,
            mapOf(10 to "خيار محذوف", 11 to "نص", 99 to "قديم")))
    }
}
