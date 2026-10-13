package com.muriq.murshid.iosparity.net

import org.junit.Assert.*
import org.junit.Test

class VerificationFormRulesTest {
    @Test fun csrfCanBeBeforeOrAfterValue() {
        assertEquals("a1", VerificationFormRules.csrf("<input name=\"csrf\" value=\"a1\">"))
        assertEquals("b2", VerificationFormRules.csrf("<input value='b2' name='csrf'>"))
    }
    @Test fun ArabicAndFarsiDigitsBecomeSixAsciiDigits() {
        assertTrue(VerificationFormRules.validCode("١٢٣٤٥٦"))
        assertEquals("123456", VerificationFormRules.normalizedDigits("۱۲۳۴۵۶"))
        assertFalse(VerificationFormRules.validCode("12345"))
    }
    @Test fun serverFormErrorIsNotTreatedAsSuccess() {
        val failure = VerificationFormRules.result("<div class='form-message error'>رمز غير صحيح</div>")
        assertTrue(failure.error)
        assertEquals("رمز غير صحيح", failure.text)
    }
}
