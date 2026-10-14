package com.murshidwizari.student

/**
 * Educational-only identifiers verified from the owner's 2026-10-14 SQL snapshot.
 * Guards against pre-deletion server responses and previously cached offline notes.
 * These ranges are not a substitute for deleting French in the production database.
 */
internal object RetiredFrenchContent {
    private val questionRanges = arrayOf(
        6278..6293,
        6439..6502,
        6675..6686,
        6747..6758,
        6771..6782,
        6949..7003,
        7449..7503,
        8677..8710,
        12788..12979,
        14864..14945,
        16976..17018,
        17185..17192,
        18218..18276,
        23341..23492,
        26747..27046,
        30532..30830
    )
    private val chapterRanges = arrayOf(22..29, 85..92, 145..152, 318..323)
    private val isolatedChapters = setOf(1461, 1507)
    fun isQuestion(id: Int): Boolean = id > 0 && questionRanges.any { id in it }
    fun isChapter(id: Int): Boolean = id > 0 &&
        (chapterRanges.any { id in it } || id in isolatedChapters)
}
