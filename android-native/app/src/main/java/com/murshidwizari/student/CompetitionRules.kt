package com.murshidwizari.student

/** Server-compatible daily challenge form rules; no local score or invented rewards. */
internal object CompetitionRules {
    /** A contest opened from an account or study tab returns to that tab, not home. */
    fun backDestination(origin: String): String = when (origin) {
        "daily_challenge", "contest", "achievements", "account", "tests", "success",
        "subjects", "topics" -> origin
        else -> "home"
    }

    data class Question(val id: Int, val type: String, val text: String, val options: List<String>, val matching: MatchingRules.Definition? = null) {
        fun choices(): List<String> = if (matching != null) emptyList() else if (type == "true_false" && options.isEmpty())
            listOf("صح", "خطأ") else options.map(String::trim).filter(String::isNotEmpty).distinct()
    }

    fun issue(questions: List<Question>): String? {
        if (questions.isEmpty()) return "لم يرسل الخادم أسئلة التحدي."
        if (questions.map { it.id }.distinct().size != questions.size) return "تكررت معرّفات أسئلة التحدي."
        for (q in questions) {
            if (q.id <= 0 || q.text.trim().length < 4) return "يوجد سؤال ناقص في التحدي."
            if (MatchingRules.generated(q.text)) return "وصلت صياغة تحقق تحتاج مراجعة. حدّث التحدي."
            if (q.matching != null && !q.matching.valid()) return "عناصر التوصيل غير مكتملة."
            val choices = q.choices()
            if (q.matching == null && (q.type == "mcq" || q.type == "true_false" || ExamRules.requiresOptions(q.text)) &&
                choices.size < 2) return "توجد اختيارات ناقصة في التحدي."
            if (q.options.isNotEmpty() && choices.size < 2) return "توجد اختيارات مكررة في التحدي."
        }
        return null
    }

    fun unansweredCount(questions: List<Question>, answers: Map<Int, String>): Int = questions.count { q ->
        val answer = answers[q.id]?.trim().orEmpty()
        answer.isEmpty() || (q.matching != null && !MatchingRules.complete(q.matching, answer)) || (q.choices().isNotEmpty() && answer !in q.choices())
    }

    fun canSubmit(questions: List<Question>, answers: Map<Int, String>,
                  available: Boolean, done: Boolean, busy: Boolean, uncertain: Boolean): Boolean =
        available && !done && !busy && !uncertain && issue(questions) == null &&
            unansweredCount(questions, answers) == 0

    fun submission(questions: List<Question>, answers: Map<Int, String>): Map<String, String> =
        questions.associate { it.id.toString() to answers[it.id].orEmpty().trim() }
}
