package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class StudyRulesTest {
    @Test fun planAcceptsBothStepperExtremes() {
        assertNull(StudyRules.planError("الخطة الوزارية", 1, 5))
        assertNull(StudyRules.planError("الخطة الوزارية", 365, 500))
    }

    @Test fun planRejectsInvalidTitlesAndStepperValues() {
        assertNotNull(StudyRules.planError("  ", 30, 20))
        assertNotNull(StudyRules.planError("خطة", 0, 20))
        assertNotNull(StudyRules.planError("خطة", 366, 20))
        assertNotNull(StudyRules.planError("خطة", 30, 21))
        assertNotNull(StudyRules.planError("خطة", 30, 505))
    }

    @Test fun advertisementValidatesSchoolTextAndDuration() {
        assertNull(StudyRules.schoolAdError("مدارس النجاح", "إعلان التفاصيل الكاملة", 90))
        assertNotNull(StudyRules.schoolAdError("أ", "إعلان التفاصيل الكاملة", 1))
        assertNotNull(StudyRules.schoolAdError("مدارس", "قصير", 1))
        assertNotNull(StudyRules.schoolAdError("مدارس", "إعلان التفاصيل الكاملة", 91))
    }

    @Test fun advertisementUsesInternalSupportMessage() {
        val message = StudyRules.schoolAdMessage(" مدارسنا ", " التسجيل متاح الآن ", 12, " طالب ")
        assertTrue(message.contains("اسم المدرسة: مدارسنا"))
        assertTrue(message.contains("التسجيل متاح الآن"))
        assertTrue(message.contains("عدد الأيام: 12"))
        assertTrue(message.contains("الطالب المرسل: طالب"))
    }
}
