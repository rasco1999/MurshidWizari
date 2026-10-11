package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class ExamRulesTest {
    @Test fun optionLabelsFollowTheIPhoneArabicOrder() {
        assertEquals(listOf("أ", "ب", "ج", "د", "هـ", "و", "7"),
            (0..6).map(ExamRules::optionLetter))
    }

    private fun question(type: String = "text") = ExamRules.Shape(
        id = 25, text = "اذكر أهمية هذا الدرس", type = type, correctAnswer = "الإجابة العلمية"
    )

    @Test fun textFillAndMeaningAcceptARealModelAnswer() {
        for (type in listOf("text", "fill", "meaning"))
            assertNull(ExamRules.issue(question(type)))
    }

    @Test fun missingOrInvalidQuestionCannotBeCharged() {
        assertNotNull(ExamRules.issue(question().copy(correctAnswer = "  ")))
        assertNotNull(ExamRules.issue(question().copy(id = 0)))
        assertNotNull(ExamRules.issue(question().copy(text = "")))
    }

    @Test fun choiceAndSemanticChoiceRequireTwoDistinctOptions() {
        assertNotNull(ExamRules.issue(question("mcq")))
        assertNull(ExamRules.issue(question("mcq").copy(options = listOf("نعم", "لا"))))
        assertNotNull(ExamRules.issue(question("mcq").copy(options = listOf("أحمد", "احمد"))))
        assertNotNull(ExamRules.issue(question().copy(text = "اختر الإجابة الصحيحة", options = emptyList())))
    }

    @Test fun trueFalseCanUseBuiltInChoicesWhenServerSendsNoOptions() {
        assertNull(ExamRules.issue(question("true_false")))
    }

    @Test fun matchRejectsMissingDuplicateAndInvalidItemIds() {
        assertNotNull(ExamRules.issue(question("match")))
        assertNotNull(ExamRules.issue(question("match").copy(matchIds = listOf(1, 1), matchWords = listOf("أ", "ب"))))
        assertNotNull(ExamRules.issue(question("match").copy(matchIds = listOf(0), matchWords = listOf("أ"))))
        assertNotNull(ExamRules.issue(question("match").copy(matchIds = listOf(1), matchWords = listOf(""))))
        assertNull(ExamRules.issue(question("match").copy(matchIds = listOf(1, 2), matchWords = listOf("أ", "ب"))))
    }

    @Test fun englishMatchingRequiresEnoughNonBlankTargets() {
        val sample = question().copy(hasEnglishDefinition = true, englishLeft = listOf("1", "2"))
        assertNotNull(ExamRules.issue(sample.copy(englishRight = listOf("A"))))
        assertNotNull(ExamRules.issue(sample.copy(englishRight = listOf("A", ""))))
        assertNull(ExamRules.issue(sample.copy(englishRight = listOf("A", "B"))))
    }

    @Test fun calculationAndGeneticsMustHaveEditableStages() {
        for (type in listOf("calculation", "genetics")) {
            val item = question(type)
            assertNotNull(ExamRules.issue(item))
            assertNotNull(ExamRules.issue(item.copy(stages = listOf("info"))))
            assertNotNull(ExamRules.issue(item.copy(stages = listOf("x", "x"), requiredStageKeys = listOf("x", "x"))))
            assertNotNull(ExamRules.issue(item.copy(stages = listOf("x"), requiredStageKeys = listOf("x"), invalidStageChoices = true)))
            assertNull(ExamRules.issue(item.copy(stages = listOf("x"), requiredStageKeys = listOf("x"))))
        }
    }

    @Test fun explanationsDoNotRepeatTheAnswerOrInventJustifications() {
        assertTrue(ExamRules.explanation("", "بغداد").contains("لم يقدّم"))
        assertTrue(ExamRules.explanation("الإجابة الصحيحة هي بغداد", "بغداد").contains("لم يقدّم"))
        assertEquals("لأن الماء يتمدد بالتجمّد.", ExamRules.explanation("الشرح: لأن الماء يتمدد بالتجمّد.", "الماء"))
    }

    @Test fun previousAndNextNavigationNeverSkipUnsubmittedQuestions() {
        assertEquals(2, ExamRules.nextIndex(2, 5, submitted = false, sending = false, uncertain = false))
        assertEquals(2, ExamRules.nextIndex(2, 5, submitted = true, sending = true, uncertain = false))
        assertEquals(2, ExamRules.nextIndex(2, 5, submitted = true, sending = false, uncertain = true))
        assertEquals(3, ExamRules.nextIndex(2, 5, submitted = true, sending = false, uncertain = false))
        assertEquals(5, ExamRules.nextIndex(5, 5, submitted = true, sending = false, uncertain = false))
    }

    @Test fun arabicNormalizationOnlyUsedForQualityAudit() {
        assertEquals(ExamRules.answerKey("إجابة"), ExamRules.answerKey("اجابة"))
        assertEquals(ExamRules.answerKey("عَلِم"), ExamRules.answerKey("علم"))
    }

    @Test fun serverBooleanFlagsMayBeJsonBooleansNumbersOrStrings() {
        for (yes in listOf(true, 1, "1", "true")) assertEquals(true, ExamRules.knownBoolean(yes))
        for (no in listOf(false, 0, "0", "false")) assertEquals(false, ExamRules.knownBoolean(no))
        assertNull(ExamRules.knownBoolean(""))
        assertNull(ExamRules.knownBoolean(null))
    }
}
