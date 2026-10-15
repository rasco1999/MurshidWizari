package com.murshidwizari.student

/** One-to-one matching. Partial answers are drafts and are never submittable. */
internal object MatchingRules {
    data class Definition(val left: Map<String,String>, val right: Map<String,String>) {
        fun valid(): Boolean = left.size >= 2 && right.size >= left.size &&
            left.keys.all { it.toIntOrNull() != null } &&
            right.keys.all { it.matches(Regex("[a-z]")) } &&
            (left.values + right.values).all { it.isNotBlank() }
    }
    fun keys(values: Map<String,String>): List<String> = values.keys.sortedWith(
        compareBy<String>({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
    fun parse(answer: String): Map<String,String> {
        if (answer.isBlank()) return emptyMap()
        val pairs = linkedMapOf<String,String>()
        for (part in answer.split(',')) {
            val m = Regex("^\\s*([0-9]+)\\s*-\\s*([a-z])\\s*$").matchEntire(part) ?: return emptyMap()
            val key = m.groupValues[1]; val target = m.groupValues[2]
            if (pairs.containsKey(key) || pairs.containsValue(target)) return emptyMap()
            pairs[key] = target
        }
        return pairs
    }
    fun encode(pairs: Map<String,String>): String = keys(pairs).joinToString(", ") { "$it-${pairs[it]}" }
    fun compatible(def: Definition, pairs: Map<String,String>): Boolean = def.valid() &&
        pairs.keys.all { it in def.left } && pairs.values.all { it in def.right } &&
        pairs.values.distinct().size == pairs.size
    fun complete(def: Definition, answer: String): Boolean {
        val pairs = parse(answer)
        return compatible(def,pairs) && pairs.keys == def.left.keys
    }
    fun assign(pairs: Map<String,String>, left: String, right: String): Map<String,String> =
        pairs.filterValues { it != right } + (left to right)
    fun generated(text: String): Boolean = listOf("الإجابة المناسبة للسؤال", "— هل الإجابة", "الإجابة عن «",
        "عند الإجابة عن «", "راجع المعلومة: يرتبط السؤال", "تدريب تثبيت: الجواب",
        "وفق المفهوم الدراسي، هل إجابة", "قيّم العبارة: إذا سُئلت", "اختر صح أو خطأ للرابطة",
        "تأكد من المراجعة: يقابل السؤال").any { text.contains(it) } || Regex("^\\s*«.+»\\s+هي\\s+«.+»[.؟?]*\\s*$", RegexOption.DOT_MATCHES_ALL).matches(text)
}
