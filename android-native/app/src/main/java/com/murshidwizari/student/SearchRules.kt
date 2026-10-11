package com.murshidwizari.student

import java.net.URI

/** Search currently returns {"results": [...]} without an "ok" field. */
object SearchRules {
    fun accepts(hasOk: Boolean, ok: Boolean, hasResultsArray: Boolean): Boolean =
        hasResultsArray && (!hasOk || ok)

    /** Only turn known same-site exam links into native navigation targets. */
    fun chapterId(link: String): Int? {
        val uri = try { URI(link.trim()) } catch (_: Exception) { return null }
        if (uri.isAbsolute || uri.rawAuthority != null || uri.rawFragment != null) return null
        if (uri.path !in setOf("exam.php", "student/exam.php", "/student/exam.php")) return null
        val parts = uri.rawQuery?.split('&')?.filter { it.startsWith("chapter_id=") } ?: return null
        if (parts.size != 1) return null
        val rawId = parts.single().removePrefix("chapter_id=")
        if (rawId.isEmpty() || !rawId.all { it in '0'..'9' }) return null
        return rawId.toIntOrNull()?.takeIf { it > 0 }
    }
}
