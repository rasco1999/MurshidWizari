package com.muriq.murshid.iosparity.data

import org.junit.Assert.*
import org.junit.Test

class RegistrationParityRulesTest {
    @Test fun personalPageRequiresThreeNamesAndGrade() {
        assertNotNull(RegistrationParityRules.personalError("محمد علي", 3))
        assertNotNull(RegistrationParityRules.personalError("محمد علي حسن", 0))
        assertNull(RegistrationParityRules.personalError("محمد علي حسن", 3))
    }

    @Test fun contactAcceptsIraqiNumberAndEmail() {
        assertNull(RegistrationParityRules.contactError("07712345678", "phone"))
        assertNull(RegistrationParityRules.contactError("+9647712345678", "phone"))
        assertNull(RegistrationParityRules.contactError("test@example.com", "email"))
        assertNotNull(RegistrationParityRules.contactError("test.invalid", "email"))
        assertNotNull(RegistrationParityRules.contactError("not-phone", "phone"))
    }

    @Test fun matchingPasswordIsRequired() {
        assertNotNull(RegistrationParityRules.passwordError("short", "short"))
        assertNotNull(RegistrationParityRules.passwordError("abcdefgh", "abcdefgi"))
        assertNull(RegistrationParityRules.passwordError("abcdefgh", "abcdefgh"))
    }
}
