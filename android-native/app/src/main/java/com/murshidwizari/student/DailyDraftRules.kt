package com.murshidwizari.student

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Identifies a server-provided challenge by account and content, not by midnight. */
internal object DailyDraftRules {
    /** Only authoritative completion or a *valid, comparable* new challenge may retire a draft. */
    fun mayReconcileDraft(available: Boolean, questions: List<CompetitionRules.Question>): Boolean =
        available && CompetitionRules.issue(questions) == null

    fun signature(accountId: Int, questions: List<CompetitionRules.Question>): String =
        hash(accountId, questions, "daily-v2", "")

    /** Accept the previous on-device format once to preserve existing answers. */
    fun legacySignature(accountId: Int, day: String, questions: List<CompetitionRules.Question>): String =
        if (day.isBlank()) "" else hash(accountId, questions, "daily-v1", day)

    private fun hash(accountId: Int, questions: List<CompetitionRules.Question>, version: String,
                     day: String): String {
        if (accountId <= 0 || CompetitionRules.issue(questions) != null) return ""
        val canonical = buildString {
            append(version).append('\n').append(accountId).append('\n')
            if (day.isNotBlank()) append(day).append('\n')
            for (question in questions) {
                append(question.id).append('\u001f').append(question.type).append('\u001f')
                append(question.text).append('\u001f')
                question.options.forEach { append(it.length).append(':').append(it).append('\u001e') }
                append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { ((it.toInt() and 255) + 256).toString(16).substring(1) }
    }

    /** Never restore a previous day's choice that no longer exists in the live question. */
    fun validAnswers(questions: List<CompetitionRules.Question>, saved: Map<Int, String>): Map<Int, String> =
        questions.mapNotNull { question ->
            val answer = saved[question.id]?.trim().orEmpty()
            if (answer.isBlank() || (question.choices().isNotEmpty() && answer !in question.choices()))
                null else question.id to answer
        }.toMap()
}
