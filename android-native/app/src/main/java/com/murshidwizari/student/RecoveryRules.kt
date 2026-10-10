package com.murshidwizari.student

import java.net.URI
import java.net.URLDecoder

/** HTML form contracts used by the existing iOS password recovery flow. */
object RecoveryRules {
    private val csrfPatterns = listOf(
        Regex("""name\s*=\s*["']csrf["'][^>]*value\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""value\s*=\s*["']([^"']+)["'][^>]*name\s*=\s*["']csrf["']""", RegexOption.IGNORE_CASE)
    )

    fun csrf(html: String): String? = csrfPatterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }

    fun phoneToken(redirect: String): String? {
        val uri = try { URI(redirect) } catch (_: Exception) { return null }
        val encoded = uri.rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == "phone_token" }
            ?.substringAfter('=', "") ?: return null
        val token = try { URLDecoder.decode(encoded, "UTF-8") } catch (_: Exception) { return null }
        return token.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
    }

    data class HtmlMessage(val text: String, val isError: Boolean)

    fun formMessage(html: String): HtmlMessage {
        val match = Regex("""<div[^>]*class\s*=\s*["'][^"']*form-message\s+([^"']+)["'][^>]*>(.*?)</div>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
            ?: return HtmlMessage("", false)
        val classes = match.groupValues[1].lowercase()
        val cleaned = match.groupValues[2].replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#039;", "'").trim()
        return HtmlMessage(cleaned, classes.contains("error") || classes.contains("bad"))
    }

    fun normalizedDigits(input: String): String = buildString {
        input.forEach { c ->
            append(when (c) {
                in '٠'..'٩' -> ('0'.code + c.code - '٠'.code).toChar()
                in '۰'..'۹' -> ('0'.code + c.code - '۰'.code).toChar()
                else -> c
            })
        }
    }

    fun validReset(code: String, password: String, confirmation: String): Boolean =
        normalizedDigits(code).matches(Regex("[0-9]{6}")) && password.length >= 8 && password == confirmation
}
