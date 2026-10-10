package com.murshidwizari.student

/** Server-authoritative custom practice: client only validates form and navigation. */
internal object PracticeRules {
    val types = setOf("all", "mcq", "true_false", "text", "fill", "meaning")
    val states = setOf("all", "unanswered", "wrong", "correct", "favorite")
    val durations = setOf(0, 15, 30, 45, 60)

    fun configurationError(subjectId: Int, count: Int, type: String, state: String, minutes: Int): String? = when {
        subjectId <= 0 -> "اختر مادة دراسية."
        count !in 5..50 || count % 5 != 0 -> "عدد الأسئلة من 5 إلى 50 بفواصل 5."
        type !in types -> "نوع السؤال غير صالح."
        state !in states -> "حالة الأسئلة غير صالحة."
        minutes !in durations -> "مدة المؤقت غير صالحة."
        else -> null
    }

    data class Question(val id: Int, val text: String, val options: List<String>)

    fun questionsError(questions: List<Question>): String? {
        if (questions.isEmpty()) return "لا توجد أسئلة مناسبة لهذه الإعدادات."
        if (questions.map { it.id }.distinct().size != questions.size) return "تكررت معرّفات الأسئلة."
        for (question in questions) {
            if (question.id <= 0 || question.text.isBlank()) return "وصل سؤال غير مكتمل من الخادم."
            if (question.options.isNotEmpty() && (question.options.any { it.isBlank() } ||
                question.options.distinct().size != question.options.size))
                return "اختيارات السؤال ناقصة أو مكررة."
        }
        return null
    }

    fun answered(answer: String?): Boolean = !answer.isNullOrBlank()
}
