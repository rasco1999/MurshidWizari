package com.murshidwizari.student

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineStartupRulesTest {
    @Test fun offlineWithoutRemember() {
        assertEquals(OfflineStartupRules.Destination.OFFLINE_COACH,
            OfflineStartupRules.destination(false, false, 42))
    }
    @Test fun firstLaunchWithoutInternet() {
        assertEquals(OfflineStartupRules.Destination.GUEST,
            OfflineStartupRules.destination(false, false, 0))
    }
    @Test fun noLocalAccountCannotAuthenticate() {
        assertEquals(OfflineStartupRules.Destination.GUEST,
            OfflineStartupRules.destination(false, true, 0))
    }
    @Test fun onlineRememberRestoresServer() {
        assertEquals(OfflineStartupRules.Destination.RESTORE_SERVER_SESSION,
            OfflineStartupRules.destination(true, true, 42))
    }
    @Test fun onlineWithoutRememberDoesNotAutoLogin() {
        assertEquals(OfflineStartupRules.Destination.GUEST,
            OfflineStartupRules.destination(true, false, 42))
    }
}
