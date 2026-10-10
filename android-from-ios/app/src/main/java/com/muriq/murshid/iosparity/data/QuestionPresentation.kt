package com.muriq.murshid.iosparity.data

/**
 * Android equivalent of murshidQuestionDisplayText / murshidQuestionClientValid
 * in the original iOS AuthViews.swift. Only display text is cleaned; the server
 * receives the original question ID and remains authoritative for correction.
 */
object QuestionPresentation {
    private val prefixes = listOf(
        """^\s*\[[^\]\n]{1,80}\]\s*[:：\-–—]?\s*""",
        """^\s*(?:استرجاع\s+)?وزاري(?:\s+[\p{N}]{1,4})?(?:\s*[/\-–—]\s*[^:：\n]{1,48})?\s*[:：\-–—]\s*""",
        """^\s*(?:سؤال\s+)?(?:تدريب|تدريبي|تدريبية)(?:\s+[\p{N}]{1,4})?\s*[:：\-–—]\s*""",
        """^\s*(?:سؤال\s+)?مراجعة(?:\s+[\p{N}]{1,4})?\s*[:：\-–—]\s*""",
        """^\s*اختيار\s+تحليلي(?:\s+[\p{N}]{1,4})?\s*[:：\-–—]\s*""",
        """^\s*إجابة\s+مركزة(?:\s+[\p{N}]{1,4})?\s*[:：\-–—]\s*""",
        """^\s*أكمل\s+المعنى\s+الوزاري(?:\s+[\p{N}]{1,4})?\s*[:：\-–—]\s*""",
        """^\s*سؤال\s+[\p{N}]{1,4}\s*[:：\-–—]\s*""",
        """^\s*[\p{N}]{1,4}\s*[:：.)\-–—]\s*""",
        """^\s*(?:تحقق|تدقيق|مراجعة)\s*(?:بصري|لغوي|علمي|نحوي|صرفي|إملائي|املائي|نهائي|يدوي|داخلي)?(?:\s+[\p{N}]{1,4})?\s*(?:[—–-]\s*)?(?:V\s*[\p{N}]{1,4})?\s*[:：\-–—]\s*""",
        """^\s*V\s*[\p{N}]{1,4}\s*[:：\-–—]\s*"""
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val answerLeaks = listOf(
        """\s*[—–-]\s*هل\s+(?:الإجابة|الاجابة|الجواب|الحل)\s+.+?\s+(?:صحيحة|صحيح|صائب|صائبة)\s*[؟?]?\s*$""",
        """\s*[—–-]\s*(?:الإجابة|الاجابة|الجواب|الحل)\s*(?:الصحيحة|الصحيح)?\s*[:：].*$""",
        """\s*\((?:الإجابة|الاجابة|الجواب|الحل)\s*[:：][^)]*\)\s*$"""
    ).map(::Regex)

    private val blocked = listOf(
        """^\s*هل\s+(?:الإجابة|الاجابة|الجواب|الحل)\b""",
        """^\s*(?:صح\s+أم\s+خطأ\s*:\s*)?(?:الإجابة|الاجابة)\s+عن\s+[«"“].+?[»"”]\s+هي\s+[«"“].+?[»"”]""",
        """تدريب\s+تحقق\s+وزاري\s*:""",
        """^(?:تحقق|تدقيق)\s+(?:بصري|لغوي|علمي)\b"""
    ).map(::Regex)

    private val choicePrompts = listOf(
        """^(?:اختر|اختاري|حدد|حدّد|عيّن)\b""",
        """^(?:أي|اي)\s+مما\s+(?:يأتي|ياتي|سبق)\b""",
        """^(?:أي|اي)\s+من\s+(?:الآتي|الاتي|التالي|التالية)\b""",
        """\b(?:الاختيار|الخيار)\s+الصحيح\b"""
    ).map(::Regex)

    fun display(raw: String): String {
        var value = raw.replace("\r\n", "\n").trim()
        if (value.isEmpty()) return ""
        value = value.replace(
            Regex("""^\s*عرّف\s+المصطلح\s+الوزاري(?:\s+[\p{N}]{1,4})?\s*[:：\-–—]\s*"""),
            "عرّف "
        )
        repeat(5) {
            val found = prefixes.firstOrNull { it.containsMatchIn(value) }
            if (found != null) value = found.replaceFirst(value, "").trim()
        }
        answerLeaks.forEach { value = it.replace(value, "") }
        value = value.replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""\s+[،,]\s*$"""), "").trim()
        if (value.isNotEmpty() &&
            Regex("""(?:^|[،,:؛]\s*)(?:ما|ماذا|هل|لماذا|كيف|كم|أي|أين|اين|متى|من)\s""")
                .containsMatchIn(value) &&
            listOf("؟", "?", ":", "؛").none(value::endsWith)
        ) value += "؟"
        return value
    }

    fun studentReady(q: QuizQuestion): Boolean {
        val text = display(q.text)
        if (text.length < 4 || blocked.any { it.containsMatchIn(text) }) return false
        val options = q.options.map { it.title.trim().lowercase() }.filter(String::isNotBlank)
        val choiceExpected = q.type == "mcq" || q.type == "true_false" ||
            choicePrompts.any { it.containsMatchIn(text) }
        if (choiceExpected && options.distinct().size < 2) return false
        if (q.type == "match" && (q.raw.optJSONArray("match_items")?.length() ?: 0) == 0) return false
        if (q.type == "calculation" && (q.raw.optJSONObject("calculation")
                ?.optJSONArray("stages")?.length() ?: 0) == 0) return false
        return q.correctAnswer.isNotBlank()
    }
}
