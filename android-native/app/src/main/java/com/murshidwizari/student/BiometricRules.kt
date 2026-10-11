package com.murshidwizari.student

/** Lifecycle policy only; the platform biometric prompt remains the authenticator. */
internal object BiometricRules {
    fun shouldLock(enabled: Boolean, hasAccount: Boolean, authenticating: Boolean,
                   alreadyLocked: Boolean, pendingUnlock: Boolean,
                   awayMs: Long, sinceUnlockMs: Long): Boolean =
        enabled && !authenticating && !alreadyLocked && (hasAccount || pendingUnlock) &&
            (pendingUnlock || (awayMs > 20_000L && sinceUnlockMs > 2_000L))
}
