package com.murshidwizari.student

import android.content.SharedPreferences

/**
 * Account-scoped local study access after one successful online authentication.
 * Stores ONLY an account id, never credentials, entitlement, or a cookie.
 * An explicit user sign-out revokes this local shortcut too.
 */
class OfflineCoachAccess(private val prefs: SharedPreferences) {
    private val userKey = "offline_coach_access_user_v1"

    fun remember(userId: Int) {
        if (userId <= 0) {
            clear()
            return
        }
        prefs.edit().putInt(userKey, userId).apply()
    }

    /** Last server-verified account on this device, independent of saved cookies. */
    fun studentId(): Int = prefs.getInt(userKey, 0).coerceAtLeast(0)

    fun clear() {
        prefs.edit().remove(userKey).apply()
    }
}
