package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class MurshidTextScriptTest {
    @Test fun arabicIsDetectedAcrossDiacriticsAndMixedContent() {
        assertTrue(MurshidTextScript.containsArabic("السؤال 15 - Algebra"))
        assertTrue(MurshidTextScript.containsArabic("قُلْ هُوَ اللَّهُ أَحَدٌ"))
        assertFalse(MurshidTextScript.containsArabic("Build 49807"))
    }

    @Test fun latinWordsAndNumbersReceiveInterInMixedText() {
        val value = "درجتك 95% في MRI و Build49807"
        val fragments = MurshidTextScript.latinRuns(value).map { value.substring(it.first, it.last + 1) }
        assertEquals(listOf("95", "MRI", "Build49807"), fragments)
    }

    @Test fun noLatinSpansForPureArabic() {
        assertTrue(MurshidTextScript.latinRuns("المرشد الوزاري").isEmpty())
    }
}
