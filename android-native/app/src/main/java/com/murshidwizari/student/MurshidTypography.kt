package com.murshidwizari.student

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.widget.TextView

/** Script detection remains JVM-testable, but Cairo shapes both Arabic and Latin.
 * Splitting the same line across fonts caused visibly inconsistent Arabic metrics.
 */
internal object MurshidTextScript {
    private val westernRuns = Regex("[A-Za-z0-9]+(?:[._:/-][A-Za-z0-9]+)*")

    fun containsArabic(value: String): Boolean =
        value.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC }

    fun latinRuns(value: String): List<IntRange> =
        westernRuns.findAll(value).map { it.range.first until (it.range.last + 1) }.toList()
}

/** Fixed typography scale (sp) designed around Cairo's tall Arabic ascenders.
 * No scaling of large figures or accessibility settings is suppressed.
 */
internal object MurshidTypeScale {
    fun sizeSp(requested: Float): Float = when {
        requested <= 10f -> 11f             // bottom navigation and small status captions
        requested < 12f -> 12f
        requested >= 24f && requested < 27f -> 24f // title width on narrow screens
        else -> requested
    }
}

/** Bundled Google Fonts Cairo, licensed under SIL Open Font License 1.1.
 * This SINGLE family is used for Arabic, English, numbers, static labels
 * and dynamic EditText content. No OEM/system font substitution is needed.
 */
internal class MurshidTypography(context: Context) {
    private val regular by lazy { context.resources.getFont(R.font.cairo) }
    private val semibold by lazy { weight(600) }
    private val bold by lazy { weight(700) }

    private fun weight(amount: Int): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(regular, amount, false)
        else Typeface.create(regular, if (amount >= 700) Typeface.BOLD else Typeface.NORMAL)

    fun typeface(value: String = "", bold: Boolean = false): Typeface =
        if (bold) this.bold else regular

    fun medium(): Typeface = semibold
    fun strong(): Typeface = bold

    fun apply(label: TextView, value: String, bold: Boolean = false) {
        label.typeface = if (bold) this.bold else regular
        label.text = value
        // Cairo's dotted glyphs and Quranic marks need the ascent/descent font padding.
        label.includeFontPadding = true
    }
}
