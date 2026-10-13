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
    @Test fun productionPhoneAndEmailDangerResponsesAreErrors() {
        val incorrectCode = VerificationFormRules.result(
            "<div class='form-message danger' role='status'>رمز التحقق غير صحيح</div>"
        )
        val blockedResend = VerificationFormRules.result(
            "<div class=\"form-message danger\" role=\"status\">تعذّر إرسال رمز جديد.</div>"
        )
        val sentSuccessfully = VerificationFormRules.result(
            "<div class='form-message success' role='status'>تم إرسال الرمز.</div>"
        )
        assertTrue(incorrectCode.error)
        assertTrue(blockedResend.error)
        assertFalse(sentSuccessfully.error)
        assertEquals("تعذّر إرسال رمز جديد.", blockedResend.text)
    }
    @Test fun serverFormErrorIsNotTreatedAsSuccess() {
        val failure = VerificationFormRules.result("<div class='form-message error'>رمز غير صحيح</div>")
        assertTrue(failure.error)
        assertEquals("رمز غير صحيح", failure.text)
    }
}
