package com.murshidwizari.student

import android.content.SharedPreferences
import org.json.JSONObject

/** Private device-only stability counters.
 * No email, name, question, answer, URL query, cookie, token or user ID is recorded.
 */
internal class LocalReliability(private val prefs: SharedPreferences) {
    private val key = "diagnostics_anonymous_v1"

    fun record(path: String, millis: Long, ok: Boolean, networkFailure: Boolean) {
        val kind = category(path)
        val existing = runCatching { JSONObject(prefs.getString(key, "{}")) }.getOrDefault(JSONObject())
        val entry = existing.optJSONObject(kind) ?: JSONObject()
        entry.put("requests", (entry.optInt("requests") + 1).coerceAtMost(100_000))
        if (!ok) entry.put("errors", (entry.optInt("errors") + 1).coerceAtMost(100_000))
        if (networkFailure) entry.put("network_errors", (entry.optInt("network_errors") + 1).coerceAtMost(100_000))
        if (millis >= 3_000L) entry.put("slow", (entry.optInt("slow") + 1).coerceAtMost(100_000))
        existing.put(kind, entry)
        prefs.edit().putString(key, existing.toString()).apply()
    }

    fun counters(): JSONObject = runCatching { JSONObject(prefs.getString(key, "{}")) }
        .getOrDefault(JSONObject())

    fun clear() { prefs.edit().remove(key).apply() }

    companion object {
        /** Recognized, fixed functional categories rather than endpoint or query strings. */
        fun category(path: String): String = when {
            path.startsWith("mobile/bootstrap.php") -> "bootstrap"
            path.startsWith("mobile/exam.php") -> "exam"
            path.startsWith("api/progress.php") -> "answers"
            path.startsWith("mobile/study-plan.php") -> "study_plan"
            path.startsWith("mobile/review.php") -> "review"
            path.startsWith("mobile/notifications.php") -> "notifications"
            path.startsWith("mobile/") -> "mobile_other"
            path.startsWith("api/") -> "api_other"
            else -> "other"
        }
    }
}
