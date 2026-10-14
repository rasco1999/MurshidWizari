package com.muriq.murshid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Private, per-account offline study snapshots.
 * Results are provisional offline: only the server awards grades, quotas and XP.
 * Expiry and account matching reduce stale-data and cross-account disclosure.
 */
class OfflineStudyStore(context: Context) {
    private val prefs = context.getSharedPreferences("murshid_offline_study_v1", Context.MODE_PRIVATE)
    private val day = 86_400_000L
    private val lifetime = 7 * day

    private fun profileRaw(): JSONObject? = try {
        JSONObject(prefs.getString("profile", "") ?: "")
    } catch (_: Exception) { null }

    fun profile(): JSONObject? {
        val wrapper = profileRaw() ?: return null
        val whenSaved = wrapper.optLong("saved_at", 0L)
        if (whenSaved <= 0 || System.currentTimeMillis() - whenSaved !in 0L..lifetime) return null
        val data = wrapper.optJSONObject("data") ?: return null
        if ((data.optJSONObject("user")?.optInt("id", 0) ?: 0) <= 0) return null
        if (data.optBoolean("subscribed", false)) {
            // Subscription may expire during an offline interval.
            val days = data.optInt("subscription_days_remaining", 0)
            if (days <= 0 || System.currentTimeMillis() - whenSaved >= days.toLong() * day) return null
        }
        return data
    }

    fun saveProfile(data: JSONObject) {
        val userId = data.optJSONObject("user")?.optInt("id", 0) ?: 0
        if (userId <= 0) return
        val priorId = profileRaw()?.optJSONObject("data")?.optJSONObject("user")?.optInt("id", 0)
        if (priorId != null && priorId != userId) clear()
        prefs.edit().putString("profile", JSONObject()
            .put("saved_at", System.currentTimeMillis()).put("data", data).toString()).apply()
    }

    fun saveExam(chapterId: Int, data: JSONObject) {
        val owner = profile()?.optJSONObject("user")?.optInt("id", 0) ?: return
        if (chapterId <= 0 || data.optJSONArray("questions") == null || !data.optBoolean("ok", false)) return
        // A conservative size limit prevents runaway private storage.
        val raw = data.toString()
        if (raw.length > 1_500_000) return
        prefs.edit().putString("exam_${owner}_${chapterId}", JSONObject()
            .put("saved_at", System.currentTimeMillis())
            .put("data", data).toString()).apply()
    }

    fun exam(chapterId: Int): JSONObject? {
        val owner = profile()?.optJSONObject("user")?.optInt("id", 0) ?: return null
        return try {
            val wrapper = JSONObject(prefs.getString("exam_${owner}_${chapterId}", "") ?: "")
            val saved = wrapper.optLong("saved_at")
            if (System.currentTimeMillis() - saved !in 0L..lifetime) null
            else wrapper.optJSONObject("data")
        } catch (_: Exception) { null }
    }

    fun saveTopics(subjectId: Int, data: JSONObject) {
        val owner = profile()?.optJSONObject("user")?.optInt("id", 0) ?: return
        if (subjectId <= 0 || !data.optBoolean("ok", false)) return
        prefs.edit().putString("topics_${owner}_${subjectId}", JSONObject()
            .put("saved_at", System.currentTimeMillis()).put("data", data).toString()).apply()
    }

    fun topics(subjectId: Int): JSONObject? {
        val owner = profile()?.optJSONObject("user")?.optInt("id", 0) ?: return null
        return try {
            val wrapper = JSONObject(prefs.getString("topics_${owner}_${subjectId}", "") ?: "")
            val saved = wrapper.optLong("saved_at")
            if (System.currentTimeMillis() - saved !in 0L..lifetime) null
            else wrapper.optJSONObject("data")
        } catch (_: Exception) { null }
    }

    private fun pending(): JSONArray = try {
        JSONArray(prefs.getString("pending", "[]") ?: "[]")
    } catch (_: Exception) { JSONArray() }

    fun pendingCount(): Int = pending().length()

    fun isQueued(questionId: Int): Boolean {
        val list = pending()
        return (0 until list.length()).any { list.optJSONObject(it)?.optInt("question_id") == questionId }
    }

    fun enqueue(chapterId: Int, questionId: Int, index: Int, value: String): Boolean {
        val p = profile() ?: return false
        val userId = p.optJSONObject("user")?.optInt("id", 0) ?: return false
        if (chapterId <= 0 || questionId <= 0 || value.isBlank()) return false
        if (isQueued(questionId)) return true
        val q = pending()
        // Offline questions count against the last verified remaining free allowance.
        if (!p.optBoolean("subscribed", false) && q.length() >= p.optInt("free_remaining", 0)) return false
        if (q.length() >= 400) return false
        q.put(JSONObject().put("user_id", userId).put("chapter_id", chapterId)
            .put("question_id", questionId).put("question_index", index).put("answer", value))
        prefs.edit().putString("pending", q.toString()).apply()
        return true
    }

    suspend fun sync(api: MurshidApi, csrf: String, userId: Int): Int {
        if (csrf.isBlank() || userId <= 0) return 0
        val q = pending()
        val rest = JSONArray()
        var synced = 0
        var canSend = true
        for (i in 0 until q.length()) {
            val row = q.optJSONObject(i) ?: continue
            if (row.optInt("user_id") != userId) continue
            if (!canSend) { rest.put(row); continue }
            try {
                val answer = row.optString("answer")
                val question = row.optInt("question_id")
                val result = api.call("api/progress.php", data = payload(
                    "csrf" to csrf,
                    "chapter_id" to row.optInt("chapter_id"),
                    "question_index" to row.optInt("question_index"),
                    "completed" to false,
                    "answers" to payload(question.toString() to payload(
                        "text" to answer, "selected_text" to answer, "submitted" to true))
                ))
                if (result.obj("grades").obj(question.toString()).length() > 0) synced++
                else rest.put(row) // No grade or server acknowledgement; retain.
            } catch (_: Exception) {
                // Do not discard the answer if the network drops or quota is exhausted.
                canSend = false
                rest.put(row)
            }
        }
        prefs.edit().putString("pending", rest.toString()).apply()
        return synced
    }

    fun clear() = prefs.edit().clear().apply()
}
