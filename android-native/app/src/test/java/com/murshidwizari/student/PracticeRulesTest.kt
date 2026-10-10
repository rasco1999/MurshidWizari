package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class PracticeRulesTest {
    @Test fun matchesIosPracticeFormBounds() {
        assertNull(PracticeRules.configurationError(1, 5, "all", "all", 0))
        assertNull(PracticeRules.configurationError(9, 50, "meaning", "favorite", 60))
        assertNotNull(PracticeRules.configurationError(0, 20, "all", "all", 0))
        assertNotNull(PracticeRules.configurationError(1, 21, "all", "all", 0))
        assertNotNull(PracticeRules.configurationError(1, 20, "unknown", "all", 0))
        assertNotNull(PracticeRules.configurationError(1, 20, "all", "unknown", 0))
        assertNotNull(PracticeRules.configurationError(1, 20, "all", "all", 25))
    }

    @Test fun validatesCustomPracticeQuestionsAndAnswers() {
        val q = PracticeRules.Question(10, "هل هذه إجابة صحيحة؟", listOf("صح", "خطأ"))
        assertNull(PracticeRules.questionsError(listOf(q, PracticeRules.Question(11, "اشرح", emptyList()))))
        assertNotNull(PracticeRules.questionsError(listOf(q, q)))
        assertNotNull(PracticeRules.questionsError(listOf(q.copy(options = listOf("صح", "صح")))))
        assertNotNull(PracticeRules.questionsError(listOf(q.copy(id = 0))))
        assertFalse(PracticeRules.answered("  "))
        assertTrue(PracticeRules.answered("الإجابة"))
    }
}
