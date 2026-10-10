package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class CompetitionRulesTest {
    private val questions = listOf(
        CompetitionRules.Question(1, "mcq", "اختر الإجابة الصحيحة", listOf("أ", "ب")),
        CompetitionRules.Question(2, "true_false", "الأرض كروية أم مسطحة؟", emptyList()),
        CompetitionRules.Question(3, "text", "اذكر سبب الظاهرة", emptyList())
    )

    @Test fun challengeSupportsChoiceTrueFalseAndText() {
        assertNull(CompetitionRules.issue(questions))
        assertEquals(listOf("صح", "خطأ"), questions[1].choices())
        assertEquals(3, CompetitionRules.unansweredCount(questions, emptyMap()))
    }

    @Test fun rejectsMalformedOrDuplicateQuestionsWithoutConsumingQuota() {
        assertNotNull(CompetitionRules.issue(emptyList()))
        assertNotNull(CompetitionRules.issue(questions + questions.first()))
        assertNotNull(CompetitionRules.issue(listOf(questions[0].copy(options = listOf("أ")))))
        assertNotNull(CompetitionRules.issue(listOf(questions[2].copy(text = ""))))
    }

    @Test fun waitsUntilEveryValidAnswerIsPresent() {
        val incomplete = mapOf(1 to "أ", 2 to "صح")
        assertEquals(1, CompetitionRules.unansweredCount(questions, incomplete))
        assertFalse(CompetitionRules.canSubmit(questions, incomplete, true, false, false, false))
        val complete = incomplete + (3 to "شرح مفصل")
        assertTrue(CompetitionRules.canSubmit(questions, complete, true, false, false, false))
        assertFalse(CompetitionRules.canSubmit(questions, complete, true, true, false, false))
        assertFalse(CompetitionRules.canSubmit(questions, complete, true, false, true, false))
        assertFalse(CompetitionRules.canSubmit(questions, complete, true, false, false, true))
        assertFalse(CompetitionRules.canSubmit(questions, complete, false, false, false, false))
    }

    @Test fun invalidChoiceIsNotACompleteAnswer() {
        val answers = mapOf(1 to "خيار آخر", 2 to "خطأ", 3 to "نص")
        assertEquals(1, CompetitionRules.unansweredCount(questions, answers))
    }

    @Test fun payloadContainsOnlyServerQuestionIdsAndTrimmedValues() {
        val answers = mapOf(1 to "أ", 2 to "صح", 3 to " لأن السبب... ", 99 to "غير مطلوب")
        val sent = CompetitionRules.submission(questions, answers)
        assertEquals(setOf("1", "2", "3"), sent.keys)
        assertEquals("لأن السبب...", sent["3"])
    }
}
