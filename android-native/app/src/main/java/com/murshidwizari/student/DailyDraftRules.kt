package com.murshidwizari.student

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Identifies a single server-provided daily challenge for one account and local day. */
internal object DailyDraftRules {
    fun signature(accountId: Int, day: String, questions: List<CompetitionRules.Question>): String {
        if (accountId <= 0 || day.isBlank() || CompetitionRules.issue(questions) != null) return ""
        val canonical = buildString {
            append("daily-v1\n").append(accountId).append('\n').append(day).append('\n')
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
