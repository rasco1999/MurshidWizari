package com.murshidwizari.student

/** Pure exam rules, shared by the Android UI and local JVM regression tests. */
internal object ExamRules {
    fun optionLetter(index: Int): String = listOf("أ", "ب", "ج", "د", "هـ", "و")
        .getOrNull(index) ?: (index + 1).toString()

    fun knownBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> when (value.toInt()) { 1 -> true; 0 -> false; else -> null }
        is String -> when (value.trim().lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
        else -> null
    }

    data class Shape(
        val id: Int,
        val text: String,
        val type: String,
        val correctAnswer: String,
        val options: List<String> = emptyList(),
        val matchIds: List<Int> = emptyList(),
        val matchWords: List<String> = emptyList(),
        val englishLeft: List<String> = emptyList(),
        val englishRight: List<String> = emptyList(),
        val stages: List<String> = emptyList(),
        val requiredStageKeys: List<String> = emptyList(),
        val invalidStageChoices: Boolean = false,
        val hasEnglishDefinition: Boolean = false
    )

    fun answerKey(value: String): String = value.trim().lowercase()
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
        .replace('ى', 'ي')
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    fun requiresOptions(text: String): Boolean {
        val prompt = text.trim()
        return Regex("^(اختر|اختاري|حدد|حدّد|عيّن|عين)(\\s|$)").containsMatchIn(prompt) ||
            Regex("^(أي|اي) (مما|من الآتي|من الاتي|من التالي|من التالية)").containsMatchIn(prompt) ||
            prompt.contains("الاختيار الصحيح") || prompt.contains("الخيار الصحيح")
    }

    /** Return a visible issue; never let a malformed question consume a free answer. */
    fun issue(q: Shape): String? {
        if (MatchingRules.generated(q.text)) return "صياغة السؤال تحتاج مراجعة؛ حدّث الموضوع أثناء الاتصال."
        if (q.id <= 0 || q.text.trim().length < 4) return "نص السؤال أو معرّفه غير مكتمل."
        if (q.correctAnswer.isBlank()) return "الإجابة النموذجية غير متاحة من الخادم."
        val type = q.type.trim().lowercase()
        val uniqueOptions = q.options.map(::answerKey).filter(String::isNotEmpty).distinct()
        if (q.hasEnglishDefinition) {
            if (q.englishLeft.isEmpty() || q.englishRight.size < q.englishLeft.size ||
                q.englishLeft.any(String::isBlank) || q.englishRight.any(String::isBlank))
                return "أطراف المطابقة الإنجليزية غير مكتملة."
        } else if (type == "match") {
            if (q.matchIds.isEmpty() || q.matchIds.any { it <= 0 } || q.matchIds.distinct().size != q.matchIds.size ||
                q.matchWords.size != q.matchIds.size || q.matchWords.any(String::isBlank))
                return "عناصر المطابقة غير مكتملة."
        } else if (type == "calculation" || type == "genetics") {
            if (q.stages.isEmpty() || q.requiredStageKeys.isEmpty() ||
                q.requiredStageKeys.any(String::isBlank) ||
                q.requiredStageKeys.distinct().size != q.requiredStageKeys.size || q.invalidStageChoices)
                return "مراحل المسألة أو خياراتها غير مكتملة."
        } else if (type == "mcq" || (requiresOptions(q.text) && type != "true_false")) {
            if (uniqueOptions.size < 2) return "هذا السؤال يتطلب اختيارين واضحين على الأقل."
        }
        if (q.options.isNotEmpty() && uniqueOptions.size < 2 && type != "true_false")
            return "خيارات السؤال مكررة أو غير مكتملة."
        return null
    }

    fun canAdvance(submitted: Boolean, sending: Boolean, uncertain: Boolean): Boolean =
        submitted && !sending && !uncertain

    fun nextIndex(index: Int, total: Int, submitted: Boolean, sending: Boolean, uncertain: Boolean): Int =
        if (canAdvance(submitted, sending, uncertain)) (index + 1).coerceAtMost(total) else index

    fun explanation(raw: String, correctAnswer: String): String {
        val cleaned = raw.trim().replace(Regex("^(الشرح|التوضيح|شرح الإجابة)\\s*[:：-]\\s*"), "")
        if (cleaned.isBlank()) return "لم يقدّم الخادم شرحاً تفصيلياً لهذا السؤال بعد."
        val normalized = answerKey(cleaned)
        val correct = answerKey(correctAnswer)
        if (correct.isNotEmpty() && (normalized == correct ||
                normalized == answerKey("الإجابة الصحيحة هي $correctAnswer") ||
                normalized == answerKey("الجواب الصحيح هو $correctAnswer")))
            return "لم يقدّم الخادم شرحاً إضافياً يوضح سبب الإجابة."
        if (listOf("تلميح:", "راجع الإجابة النموذجية", "هذا تدريب مشتق").any { cleaned.startsWith(it) })
            return "لم يقدّم الخادم شرحاً تفصيلياً لهذا السؤال بعد."
        return cleaned
    }
}
