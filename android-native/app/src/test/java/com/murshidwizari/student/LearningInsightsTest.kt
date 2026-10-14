package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class LearningInsightsTest {
    private val day = 86_400_000L
    private fun q(subject: String, level: Int, correct: Boolean?, due: Long) =
        LearningInsights.Review(subject, level, correct, due, 0L)

    @Test fun groupsChaptersIntoSubjectsAndDetectsWeakness() {
        val v = listOf(q("رياضيات · الفصل الأول", 4, true, 0),
            q("رياضيات · الفصل الثاني", 0, false, 3 * day),
            q("اللغة العربية · البلاغة", 3, true, day))
        val result = LearningInsights.mastery(v, 0)
        assertEquals(listOf("رياضيات", "اللغة العربية"), result.map { it.subject })
        assertEquals(50, result[0].percent)
        assertEquals(1, result[0].due)
        assertEquals(1, result[0].needsPractice)
        assertEquals(100, result[1].percent)
    }

    @Test fun dailyGoalIsBoundedAndRoundsToFive() {
        assertEquals(10, LearningInsights.suggestedDailyTarget(emptyList(), 0))
        assertEquals(10, LearningInsights.suggestedDailyTarget(listOf(q("رياضيات", 0, false, 1)), 0))
        val many = (0..75).map { q("فيزياء", 0, false, 0L) }
        assertEquals(30, LearningInsights.suggestedDailyTarget(many, 0))
    }

    @Test fun remindersOnlyUseDueCardsAndNeverInventGrades() {
        assertTrue(LearningInsights.nextReview(emptyList(), 0).contains("ابدأ"))
        assertTrue(LearningInsights.nextReview(listOf(q("أحياء", 0, false, 0)), 0).contains("1 سؤال"))
        assertTrue(LearningInsights.nextReview(listOf(q("أحياء", 3, true, 200)), 0)
            .contains("لا توجد"))
    }
}
