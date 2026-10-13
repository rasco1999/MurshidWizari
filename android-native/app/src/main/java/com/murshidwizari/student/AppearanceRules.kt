package com.murshidwizari.student

/** User-selected colors and appearance are device preferences, never account mutations. */
object AppearanceRules {
    const val ROYAL = "royal"
    const val VIOLET = "violet"
    const val AURORA = "aurora"
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    fun normalizeAccent(raw: String?): String =
        if (raw in setOf(ROYAL, VIOLET, AURORA)) raw!! else ROYAL

    fun normalizeMode(raw: String?, legacyNight: Boolean?): String = when (raw) {
        SYSTEM, LIGHT, DARK -> raw
        else -> when (legacyNight) { true -> DARK; false -> LIGHT; null -> SYSTEM }
    }

    fun isDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        DARK -> true
        LIGHT -> false
        else -> systemDark
    }
}
