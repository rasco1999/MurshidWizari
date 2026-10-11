package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StudyReminderRulesTest {
    private val baghdad = TimeZone.getTimeZone("Asia/Baghdad")

    private fun millis(day: Int, hour: Int, minute: Int) = Calendar.getInstance(baghdad).apply {
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun eveningReminderIsTodayIfNotPassed() {
        assertEquals(millis(11, 19, 0),
            StudyReminderRules.nextTrigger(millis(11, 7, 0), 19, 0, baghdad))
    }

    @Test fun reminderAfterTimePassesIsTomorrow() {
        assertEquals(millis(12, 19, 0),
            StudyReminderRules.nextTrigger(millis(11, 20, 30), 19, 0, baghdad))
    }

    @Test fun exactMinuteSchedulesFollowingDay() {
        assertEquals(millis(12, 19, 0),
            StudyReminderRules.nextTrigger(millis(11, 19, 0), 19, 0, baghdad))
    }

    @Test fun invalidTimeCannotBeScheduled() {
        assertFalse(StudyReminderRules.validTime(24, 0))
        assertFalse(StudyReminderRules.validTime(19, 60))
        assertTrue(StudyReminderRules.validTime(0, 0))
    }
}
