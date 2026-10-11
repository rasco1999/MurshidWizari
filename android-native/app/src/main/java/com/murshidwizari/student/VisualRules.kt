package com.murshidwizari.student

/** Geometry shared with the iOS v3.1 SwiftUI reference. Values are logical dp/pt. */
object VisualRules {
    const val contentInset = 16
    const val cardCornerRadius = 22
    const val cardInset = 16
    const val sectionSpacing = 16
    const val gridSpacing = 12
    const val heroHeight = 220
    const val heroAutoAdvanceMs = 5500L
    const val heroPageTransitionMs = 350L
    const val heroDotTransitionMs = 200L
    const val minTouchTarget = 44
    // ThemeV34.swift PrimaryButtonStyle / SecondaryButtonStyle.
    const val primaryButtonHeight = 50
    const val secondaryButtonHeight = 48
    const val primaryButtonRadius = 17
    const val secondaryButtonRadius = 16

    // SwiftUI V3SectionHeader (30pt/9pt), metric (36pt/11pt), larger row (48pt/14pt).
    fun iconBubbleRadius(size: Int): Int = when {
        size <= 30 -> 9
        size <= 34 -> 10
        size <= 40 -> 11
        else -> 14
    }

    fun iconBubbleGlyphSize(size: Int): Int = when {
        size <= 30 -> 17
        size <= 40 -> 18
        else -> 24
    }

    // The SwiftUI Home/Tests use two .flexible GridItems at phone and tablet widths.
    fun homeColumns(): Int = 2

    // Larger Android tablets keep the reading column centered, like a compact iPad layout.
    fun contentWidthDp(widthDp: Int): Int? = if (widthDp >= 740) 700 else null

    fun tileWidthDp(widthDp: Int): Int {
        val total = contentWidthDp(widthDp) ?: widthDp
        return ((total - contentInset * 2 - gridSpacing) / homeColumns()).coerceAtLeast(0)
    }
}
