package com.murshidwizari.student

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.widget.TextView

/** Character-script rules are pure, so they can be regression tested on the JVM. */
internal object MurshidTextScript {
    private val westernRuns = Regex("[A-Za-z0-9]+(?:[._:/-][A-Za-z0-9]+)*")

    fun containsArabic(value: String): Boolean =
        value.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC }

    /** start inclusive, end exclusive */
    fun latinRuns(value: String): List<IntRange> =
        westernRuns.findAll(value).map { it.range.first until (it.range.last + 1) }.toList()
}

/** Different script shapes, consistent metrics and weights on every Android manufacturer.
 *
 * Fonts come from Google Fonts under SIL OFL-1.1. Licenses are embedded in
 * assets/licenses alongside the fonts by the reproducible GitHub build process.
 */
internal class MurshidTypography(context: Context) {
    private val arabic by lazy { context.resources.getFont(R.font.noto_sans_arabic) }
    private val latin by lazy { context.resources.getFont(R.font.inter) }
    private val arabicBold by lazy { Typeface.create(arabic, Typeface.BOLD) }
    private val latinBold by lazy { Typeface.create(latin, Typeface.BOLD) }

    fun typeface(value: String, bold: Boolean = false): Typeface {
        val arabicPrimary = MurshidTextScript.containsArabic(value)
        return if (arabicPrimary) { if (bold) arabicBold else arabic }
               else { if (bold) latinBold else latin }
    }

    /** For mixed Arabic/English sentences and amounts, Inter shapes Latin runs. */
    fun format(value: String, bold: Boolean = false): CharSequence {
        if (!MurshidTextScript.containsArabic(value)) return value
        val runs = MurshidTextScript.latinRuns(value)
        if (runs.isEmpty()) return value
        val result = SpannableString(value)
        val chosen = if (bold) latinBold else latin
        for (run in runs) {
            result.setSpan(MurshidFontSpan(chosen), run.first, run.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return result
    }

    fun apply(label: TextView, value: String, bold: Boolean = false) {
        label.typeface = typeface(value, bold)
        label.text = format(value, bold)
    }
}

private class MurshidFontSpan(private val typeface: Typeface): MetricAffectingSpan() {
    override fun updateMeasureState(p: TextPaint) { p.typeface = typeface }
    override fun updateDrawState(p: TextPaint) { p.typeface = typeface }
}
