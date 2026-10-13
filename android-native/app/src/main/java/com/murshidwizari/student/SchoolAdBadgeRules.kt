package com.murshidwizari.student

/**
 * Account-scoped read state for school advertisements.
 * Existing advertisements are baselined the first time a student installs this feature,
 * so the badge announces NEW publications rather than historic advertisements.
 */
object SchoolAdBadgeRules {
    fun decode(value: String): Set<Int> = value.split(',')
        .mapNotNull { it.trim().toIntOrNull()?.takeIf { id -> id > 0 } }
        .toSet()

    fun encode(ids: Set<Int>): String = ids.asSequence()
        .filter { it > 0 }
        .sorted()
        .joinToString(",")

    fun unreadCount(published: Set<Int>, seen: Set<Int>): Int =
        (published.asSequence().filter { it > 0 && it !in seen }.count()).coerceAtMost(99)

    fun badgeLabel(count: Int): String = when {
        count <= 0 -> ""
        count > 9 -> "9+"
        else -> count.toString()
    }
}
