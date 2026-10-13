package com.muriq.murshid.iosparity.data

import org.junit.Assert.*
import org.junit.Test

class LatestIphoneParityTest {
    @Test fun fiveTabsAreVisibleForAllStudents() {
        assertEquals(listOf(0, 1, 2, 3, 4), IosPortRules.visibleTabs(true))
        assertEquals(listOf(0, 1, 2, 3, 4), IosPortRules.visibleTabs(false))
    }

    @Test fun frenchNeverReturnsThroughBootstrap() {
        assertEquals(listOf(Subject(12, "الرياضيات", 80)), Subject.visible(listOf(
            Subject(10, "اللغة الفرنسية", 100),
            Subject(11, "French", 100),
            Subject(12, "الرياضيات", 80)
        )))
    }
}
