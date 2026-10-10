package com.murshidwizari.student

import java.net.URI
import java.text.Normalizer

/** Contract shared by native profile, avatar and payment forms. */
object AccountRules {
    private val hiddenDirectionMarks = setOf(
        '\u200e', '\u200f', '\u061c', '\u202a', '\u202b', '\u202c', '\u202d', '\u202e',
        '\u2066', '\u2067', '\u2068', '\u2069', '\ufeff'
    )
    fun normalizeName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
        .filterNot { it in hiddenDirectionMarks }.trim().replace(Regex("\\s+"), " ")

    fun validFullName(value: String): Boolean {
        val words = normalizeName(value).split(' ').filter { it.isNotBlank() }
        return words.size >= 3 && words.all { word -> word.length >= 2 && word.none { it.isDigit() || Character.isISOControl(it) } }
    }

    fun normalizedIraqiPhone(value: String): String = value.filter { it in '0'..'9' }

    fun validIraqiPhone(value: String): Boolean = normalizedIraqiPhone(value).matches(Regex("07[0-9]{9}"))

    fun safePaymentRedirect(raw: String): Boolean {
        val uri = try { URI(raw) } catch (_: Exception) { return false }
        return uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.port in setOf(-1, 443) && raw.length < 2048
    }

    /** Only the platform's HTTPS image host is trusted. Revision defeats stale avatar caches. */
    fun avatarUrl(raw: String, revision: String): String? {
        val uri = try { URI(raw.trim()) } catch (_: Exception) { return null }
        if (uri.scheme != "https" || uri.host !in setOf("www.mur-iq.com", "mur-iq.com") ||
            uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
        val base = uri.toASCIIString()
        if (revision.isBlank()) return base
        val separator = if (uri.rawQuery.isNullOrBlank()) "?" else "&"
        val withoutFragment = base.substringBefore('#')
        val fragment = base.substringAfter('#', "")
        val result = withoutFragment + separator + "android_rev=" + java.net.URLEncoder.encode(revision, "UTF-8")
        return result + if (fragment.isEmpty()) "" else "#$fragment"
    }
}
