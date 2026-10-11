package com.murshidwizari.student

import java.util.Calendar
import java.util.TimeZone

/** Local daily reminder uses the phone's time zone, like iOS UNCalendarNotificationTrigger. */
object StudyReminderRules {
    fun validTime(hour: Int, minute: Int): Boolean = hour in 0..23 && minute in 0..59

    fun nextTrigger(nowMillis: Long, hour: Int, minute: Int,
                    zone: TimeZone = TimeZone.getDefault()): Long {
        require(validTime(hour, minute))
        val candidate = Calendar.getInstance(zone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (candidate.timeInMillis <= nowMillis) candidate.add(Calendar.DAY_OF_YEAR, 1)
        return candidate.timeInMillis
    }
}
