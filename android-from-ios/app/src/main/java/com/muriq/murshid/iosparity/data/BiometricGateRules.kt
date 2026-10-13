package com.muriq.murshid.iosparity.data

/** Keep encrypted remembered cookies protected until the OS confirms the user. */
object BiometricGateRules {
    fun requiresUnlock(savedSession: Boolean, biometricEnabled: Boolean): Boolean =
        savedSession && biometricEnabled
}
