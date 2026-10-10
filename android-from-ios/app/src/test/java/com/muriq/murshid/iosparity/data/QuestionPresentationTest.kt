package com.muriq.murshid.iosparity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionPresentationTest {
    @Test fun removesEditorialPrefixesWithoutChangingEducationalVerb() {
        assertEquals("عرّف البناء الضوئي", QuestionPresentation.display("وزاري 2024: عرّف البناء الضوئي"))
        assertEquals("عرّف الخلية", QuestionPresentation.display("عرّف المصطلح الوزاري 12: الخلية"))
        assertEquals("ما وظيفة القلب؟", QuestionPresentation.display("[فحص] سؤال 3: ما وظيفة القلب"))
    }

    @Test fun removesAnswerLeakAndKeepsQuestion() {
        assertEquals("ما عاصمة العراق؟",
            QuestionPresentation.display("ما عاصمة العراق؟ — الإجابة: بغداد"))
        assertEquals("", QuestionPresentation.display(" "))
    }

    @Test fun rejectsBrokenMultipleChoiceButPreservesOpenText() {
        val broken = QuizQuestion(1, "اختر الإجابة الصحيحة", "mcq", "",
            "أ", "", listOf(AnswerOption("1", "أ")), false, null, org.json.JSONObject())
        assertFalse(QuestionPresentation.studentReady(broken))
        val text = broken.copy(text = "عرّف البناء الضوئي", type = "text",
            options = emptyList(), correctAnswer = "عملية صنع الغذاء")
        assertTrue(QuestionPresentation.studentReady(text))
    }
}
