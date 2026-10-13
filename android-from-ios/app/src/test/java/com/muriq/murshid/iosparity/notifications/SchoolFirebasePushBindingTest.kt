package com.muriq.murshid.iosparity.notifications

import org.junit.Assert.*
import org.junit.Test

class SchoolFirebasePushBindingTest {
    private val binding = "18e765bf-3f4b-48cc-9188-c197e7da6f64"
    @Test fun validAccountBindingAndMonotonicNotificationPasses() {
        assertTrue(SchoolFirebasePush.acceptedBinding(21, binding, binding, 70, 71))
    }
    @Test fun anotherAccountOrOldSignalNeverDisplays() {
        assertFalse(SchoolFirebasePush.acceptedBinding(0, binding, binding, 70, 71))
        assertFalse(SchoolFirebasePush.acceptedBinding(21, binding, "other", 70, 71))
        assertFalse(SchoolFirebasePush.acceptedBinding(21, binding, binding, 71, 71))
        assertFalse(SchoolFirebasePush.acceptedBinding(21, binding, binding, 72, 71))
    }
}
