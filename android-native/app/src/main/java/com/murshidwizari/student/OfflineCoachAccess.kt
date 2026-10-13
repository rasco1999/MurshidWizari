package com.murshidwizari.student

import android.content.SharedPreferences

/**
 * An offline-only doorway for a previously authenticated student.
 *
 * Storing the numeric user id is sufficient to select the private local coach
 * notebook; never cache a password, session cookie, CSRF token, subscription
 * entitlement, or a full bootstrap response in these preferences.
 *
 * The caller must additionally require ApiClient.hasRememberedSession().
 */
class OfflineCoachAccess(private val prefs: SharedPreferences) {
    private val userKey = "offline_coach_access_user_v1"

    fun remember(userId: Int, rememberedSession: Boolean) {
        if (userId <= 0 || !rememberedSession) {
            clear()
            return
        }
        prefs.edit().putInt(userKey, userId).apply()
    }

    fun studentId(hasRememberedSession: Boolean): Int =
        if (hasRememberedSession) prefs.getInt(userKey, 0).coerceAtLeast(0) else 0

    fun clear() {
        prefs.edit().remove(userKey).apply()
    }
}
