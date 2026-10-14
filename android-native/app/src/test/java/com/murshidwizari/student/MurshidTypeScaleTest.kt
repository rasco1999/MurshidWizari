package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class MurshidTypeScaleTest {
    @Test fun navigationAndCaptionsRemainReadable() {
        assertEquals(11f, MurshidTypeScale.sizeSp(9f), 0f)
        assertEquals(11f, MurshidTypeScale.sizeSp(10f), 0f)
        assertEquals(12f, MurshidTypeScale.sizeSp(11f), 0f)
        assertEquals(12f, MurshidTypeScale.sizeSp(12f), 0f)
    }

    @Test fun textInputsAndQuestionsKeepTheirReadability() {
        assertEquals(15f, MurshidTypeScale.sizeSp(15f), 0f)
        assertEquals(16f, MurshidTypeScale.sizeSp(16f), 0f)
        assertEquals(19f, MurshidTypeScale.sizeSp(19f), 0f)
        assertEquals(20f, MurshidTypeScale.sizeSp(20f), 0f)
    }

    @Test fun titlesFitNarrowDisplaysAndLargeNumbersAreNotClamped() {
        assertEquals(24f, MurshidTypeScale.sizeSp(25f), 0f)
        assertEquals(40f, MurshidTypeScale.sizeSp(40f), 0f)
    }
}
