package com.muriq.murshid.iosparity.net

/** Pure validation and form parsing for official native verification. */
object VerificationFormRules {
    private val tokens = listOf(
        Regex("""name\s*=\s*["']csrf["'][^>]*value\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""value\s*=\s*["']([^"']+)["'][^>]*name\s*=\s*["']csrf["']""", RegexOption.IGNORE_CASE)
    )
    fun csrf(html: String): String? = tokens.firstNotNullOfOrNull {
        it.find(html)?.groupValues?.getOrNull(1)
    }
    fun normalizedDigits(raw: String): String = buildString {
        raw.forEach { c -> append(when(c) {
            in '٠'..'٩' -> ('0'.code + c.code - '٠'.code).toChar()
            in '۰'..'۹' -> ('0'.code + c.code - '۰'.code).toChar()
            else -> c
        }) }
    }
    fun validCode(text: String): Boolean = normalizedDigits(text).matches(Regex("[0-9]{6}"))
    data class FormMessage(val text: String, val error: Boolean)
    fun result(html: String): FormMessage {
        val pattern = Regex("""<div[^>]*class\s*=\s*["'][^"']*form-message\s+([^"']+)["'][^>]*>(.*?)</div>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val matched = pattern.find(html) ?: return FormMessage("", false)
        val cleaned = matched.groupValues[2].replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#039;", "'").trim()
        val cls = matched.groupValues[1].lowercase()
        return FormMessage(cleaned, cls.contains("error") || cls.contains("bad"))
    }
}
