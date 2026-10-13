package com.murshidwizari.student

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachScheduleTest {
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    @Test fun correctReviewIntervalsFollowWebsiteLeitnerBoxes() {
        assertEquals(now + day, CoachSchedule.nextDueAt(now, 0, true))
        assertEquals(now + 3 * day, CoachSchedule.nextDueAt(now, 1, true))
        assertEquals(now + 7 * day, CoachSchedule.nextDueAt(now, 2, true))
        assertEquals(now + 14 * day, CoachSchedule.nextDueAt(now, 3, true))
        assertEquals(now + 30 * day, CoachSchedule.nextDueAt(now, 4, true))
        assertEquals(now + 30 * day, CoachSchedule.nextDueAt(now, 5, true))
        assertEquals(5, CoachSchedule.nextLevel(5, true))
    }

    @Test fun forgottenReviewReturnsInTenMinutesWithoutServerMutation() {
        assertEquals(now + 10 * 60_000L, CoachSchedule.nextDueAt(now, 4, false))
        assertEquals(0, CoachSchedule.nextLevel(4, false))
    }

    @Test fun corruptedLevelsAndFutureOverflowRemainBounded() {
        assertEquals(1, CoachSchedule.nextLevel(-100, true))
        assertEquals(5, CoachSchedule.nextLevel(100, true))
        assertEquals(Long.MAX_VALUE, CoachSchedule.nextDueAt(Long.MAX_VALUE, 0, true))
        assertTrue(CoachSchedule.nextDueAt(now, 0, true) > now)
    }
}
