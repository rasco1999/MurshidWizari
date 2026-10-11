package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class ExamResultRulesTest {
    @Test fun fullyGradedTopicShowsReliableMetrics() {
        val result = ExamResultRules.Summary(answered = 20, graded = 20, correct = 17)
        assertEquals(85, result.percent)
        assertEquals(3, result.wrong)
        assertEquals(0, result.pending)
        assertEquals("أداء ممتاز", result.title)
    }

    @Test fun ungradedReceiptsAreNotMarkedIncorrect() {
        val result = ExamResultRules.Summary(answered = 5, graded = 3, correct = 2)
        assertEquals(67, result.percent)
        assertEquals(1, result.wrong)
        assertEquals(2, result.pending)
        assertEquals("تقدم جيد", result.title)
    }

    @Test fun zeroConfirmedGradesDoNotInventAccuracy() {
        val result = ExamResultRules.Summary(answered = 2, graded = 0, correct = 0)
        assertEquals(0, result.percent)
        assertEquals(0, result.wrong)
        assertEquals(2, result.pending)
        assertEquals("بانتظار التصحيح", result.title)
    }
}
