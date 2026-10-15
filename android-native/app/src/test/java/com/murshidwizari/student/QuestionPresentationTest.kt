package com.murshidwizari.student

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionPresentationTest {
    @Test fun oldCachedHintLinesDoNotLeakAnswers() {
        assertEquals("احسب 2 + 3.", QuestionPresentation.prompt("احسب 2 + 3.\nتلميح: الناتج خمسة"))
        assertEquals("Find x: x² = 4.", QuestionPresentation.prompt("Find x: x² = 4.\nHint: x = 2"))
        assertEquals("احسب 2 + 3.", QuestionPresentation.prompt("احسب 2 + 3. (تلميح: استخدم الجمع)"))
    }
    @Test fun educationalTermsAndScientificNotationArePreserved() {
        for (text in listOf("ما معنى كلمة تلميح؟", "P ≠ p؛ 10^2؛ 1,5؛ عِلْم", "قال تعالى: ﴿قُلْ هُوَ اللَّهُ أَحَدٌ﴾", "Match Hints to تلميحات."))
            assertEquals(text, QuestionPresentation.prompt(text))
    }
}
