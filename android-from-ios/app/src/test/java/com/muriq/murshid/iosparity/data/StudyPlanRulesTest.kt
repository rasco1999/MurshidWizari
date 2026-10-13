package com.muriq.murshid.iosparity.data

import org.junit.Assert.*
import org.junit.Test

class StudyPlanRulesTest {
    @Test fun planCreationRejectsInvalidRanges() {
        assertNotNull(StudyPlanRules.error("خطة", 0, 20))
        assertNotNull(StudyPlanRules.error("خطتي الوزارية", 366, 20))
        assertNotNull(StudyPlanRules.error("خطتي الوزارية", 30, 501))
        assertNull(StudyPlanRules.error("خطتي الوزارية", 30, 20))
    }
}
