package com.murshidwizari.student

import org.junit.Assert.*
import org.junit.Test

class RecoveryRulesTest {
    @Test fun csrfIsReadInEitherAttributeOrder() {
        assertEquals("abc123", RecoveryRules.csrf("<input type='hidden' name='csrf' value='abc123'>"))
        assertEquals("def456", RecoveryRules.csrf("<input value=\"def456\" name=\"csrf\">"))
        assertNull(RecoveryRules.csrf("<input name='anything_else' value='x'>"))
    }

    @Test fun phoneTokenMustBeHexAndExactly64Characters() {
        val token = "a".repeat(64)
        assertEquals(token, RecoveryRules.phoneToken("/reset-password.php?phone_token=$token"))
        assertNull(RecoveryRules.phoneToken("/reset-password.php?phone_token=short"))
        assertNull(RecoveryRules.phoneToken("https://example.org/reset-password.php?x=1"))
    }

    @Test fun parsesVisibleHtmlStatusWithoutTags() {
        val error = RecoveryRules.formMessage("<div class='form-message error'><strong>رمز</strong> غير صحيح &amp; منتهي</div>")
        assertTrue(error.isError)
        assertEquals("رمز غير صحيح & منتهي", error.text)
        assertFalse(RecoveryRules.formMessage("<div class='form-message success'>تم الإرسال</div>").isError)
    }

    @Test fun arabicDigitsAndMatchingPasswordsAreRequired() {
        assertEquals("123456", RecoveryRules.normalizedDigits("١٢٣٤٥٦"))
        assertEquals("123456", RecoveryRules.normalizedDigits("۱۲۳۴۵۶"))
        assertTrue(RecoveryRules.validReset("١٢٣٤٥٦", "password123", "password123"))
        assertFalse(RecoveryRules.validReset("12345", "password123", "password123"))
        assertFalse(RecoveryRules.validReset("123456", "password123", "different"))
    }
}
