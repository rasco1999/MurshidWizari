package com.murshidwizari.student

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Private, account-scoped review notebook for the Android native coach.
 *
 * Only questions whose original answer the server has graded are saved.
 * Local recall NEVER sends progress, changes subscriptions, or consumes free
 * questions. The website's coach.php remains a separate, server-side schedule.
 */
class OfflineCoachNotebook(private val prefs: SharedPreferences) {
    private val secure = SecureCoachStorage(prefs)
    companion object {
        const val MAX_CARDS = 150
    }


    fun cards(userId: Int): List<JSONObject> {
        if (userId <= 0) return emptyList()
        val raw = secure.read(userId) ?: return emptyList()
        return runCatching {
            val source = JSONArray(raw)
            val all = (0 until source.length()).mapNotNull { source.optJSONObject(it) }
            val allowed = all.filter {
                it.optInt("id") > 0 && it.optString("question").isNotBlank() && !MatchingRules.generated(it.optString("question")) &&
                    !RetiredFrenchContent.isQuestion(it.optInt("id")) &&
                    !EquationStudyRules.isFrenchSubject(0, it.optString("subject"))
            }
            // Permanently purge previously graded French questions, not only
            // hide them from the coach UI.
            if (allowed.size != all.size) {
                secure.write(userId, JSONArray(allowed).toString())
            }
            allowed.sortedWith(compareBy<JSONObject> { it.optLong("due_at", 0L) }
                .thenByDescending { it.optLong("saved_at", 0L) })
        }.getOrDefault(emptyList())
    }

    fun remember(
        userId: Int, questionId: Int, question: String, correctAnswer: String,
        explanation: String, subject: String, wasCorrect: Boolean?,
        now: Long = System.currentTimeMillis(), matching: JSONObject? = null
    ): Boolean {
        if (userId <= 0 || questionId <= 0 || question.isBlank() || correctAnswer.isBlank() || MatchingRules.generated(question) ||
            RetiredFrenchContent.isQuestion(questionId) ||
            EquationStudyRules.isFrenchSubject(0, subject)) return false
        val saved = cards(userId).toMutableList()
        val existing = saved.firstOrNull { it.optInt("id") == questionId }
        val entry = JSONObject().apply {
            if (matching != null) put("english_match", matching)
            put("id", questionId)
            put("question", question.trim().take(4000))
            put("correct_answer", correctAnswer.trim().take(1800))
            put("explanation", explanation.trim().take(3000))
            put("subject", subject.trim().take(150))
            put("saved_at", existing?.optLong("saved_at", now) ?: now)
            put("level", existing?.optInt("level", 0) ?: 0)
            // Every newly graded question can be reviewed immediately.
            put("due_at", existing?.optLong("due_at", now) ?: now)
            if (wasCorrect != null) put("graded_correct", wasCorrect)
        }
        saved.removeAll { it.optInt("id") == questionId }
        saved.add(0, entry)
        // Keep the most recently introduced 150 records. Do not leak older
        // student/account content into the next user's notebook.
        val kept = saved.sortedByDescending { it.optLong("saved_at") }.take(MAX_CARDS)
        return secure.write(userId, JSONArray(kept).toString())
    }

    fun review(userId: Int, questionId: Int, mastered: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        val saved = cards(userId).toMutableList()
        val current = saved.firstOrNull { it.optInt("id") == questionId } ?: return false
        val level = current.optInt("level", 0)
        current.put("level", CoachSchedule.nextLevel(level, mastered))
        current.put("due_at", CoachSchedule.nextDueAt(now, level, mastered))
        current.put("last_reviewed_at", now)
        return secure.write(userId, JSONArray(saved).toString())
    }
}

object CoachSchedule {
    private val intervalsDays = longArrayOf(0, 1, 3, 7, 14, 30)
    private const val DAY_MILLIS = 86_400_000L
    private const val RETRY_MILLIS = 600_000L

    fun nextLevel(current: Int, mastered: Boolean): Int =
        if (mastered) (current.coerceIn(0, 5) + 1).coerceAtMost(5) else 0

    fun nextDueAt(now: Long, current: Int, mastered: Boolean): Long {
        val delay = if (mastered) intervalsDays[nextLevel(current, true)] * DAY_MILLIS else RETRY_MILLIS
        return if (now > Long.MAX_VALUE - delay) Long.MAX_VALUE else now + delay
    }
}
