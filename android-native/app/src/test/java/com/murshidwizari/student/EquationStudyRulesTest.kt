package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class EquationStudyRulesTest {
    @Test fun acceptsAllQuantitativeSubjects() {
        val examples = listOf(
            "رياضيات" to "أوجد معادلة المستقيم y=2x+3",
            "الفيزياء" to "احسب السرعة 20m/s",
            "الكيمياء" to "وازن المعادلة H2+O2→H2O",
            "الأحياء" to "احسب احتمال ظهور الصفة بنسبة 25%"
        )
        examples.forEach { (subject, prompt) ->
            assertTrue(subject, EquationStudyRules.applies(prompt, subject))
            assertTrue(EquationStudyRules.steps(prompt, subject).size >= 4)
        }
    }
    @Test fun unrelatedProseDoesNotTrigger() {
        assertFalse(EquationStudyRules.applies("متى ولد الجواهري عام 1899؟", "اللغة العربية"))
        assertFalse(EquationStudyRules.applies("How did the canary sing? Because it was hungry.", "اللغة الإنكليزية"))
    }
    @Test fun archivedFrenchIdentifiersBlockedIncludingOfflineNotebooks() {
        assertTrue(RetiredFrenchContent.isQuestion(6278))
        assertTrue(RetiredFrenchContent.isQuestion(30830))
        assertTrue(RetiredFrenchContent.isQuestion(26747))
        assertFalse(RetiredFrenchContent.isQuestion(26746))
        assertFalse(RetiredFrenchContent.isQuestion(0))
        assertTrue(RetiredFrenchContent.isChapter(22))
        assertTrue(RetiredFrenchContent.isChapter(1507))
        assertFalse(RetiredFrenchContent.isChapter(21))
    }

    @Test fun frenchSubjectsHiddenAcrossGrades() {
        for(id in listOf(33,36,40)) assertTrue(EquationStudyRules.isFrenchSubject(id,"اللغة الفرنسية"))
        assertTrue(EquationStudyRules.isFrenchSubject(0,"French"))
        assertFalse(EquationStudyRules.isFrenchSubject(14,"الكيمياء"))
    }
    @Test fun isolatedCoordinateGuide() {
        assertTrue(EquationStudyRules.coordinateQuestion("أوجد معادلة المستقيم المار بالنقطتين"))
        assertFalse(EquationStudyRules.coordinateQuestion("أوجد مساحة الشكل"))
    }
}
