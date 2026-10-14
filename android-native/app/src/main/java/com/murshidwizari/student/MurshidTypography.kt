package com.murshidwizari.student

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import java.util.WeakHashMap

/** Character-script rules are pure, so they can be regression tested on the JVM. */
internal object MurshidTextScript {
    private val westernRuns = Regex("[A-Za-z0-9]+(?:[._:/-][A-Za-z0-9]+)*")

    fun containsArabic(value: String): Boolean =
        value.any {
            Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC ||
                it in '\u0660'..'\u0669' || it in '\u06F0'..'\u06F9'
        }

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
    private data class Snapshot(
        val textHash: Int, val textLength: Int, val hintHash: Int,
        val bold: Boolean, val requestedFace: Typeface
    )

    // Weak refs avoid retaining screens and account views across navigation.
    private val styled = WeakHashMap<TextView, Snapshot>()
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
        styled[label] = Snapshot(value.hashCode(), value.length,
            label.hint?.toString().orEmpty().hashCode(), bold, label.typeface)
    }

    /**
     * Covers legacy direct TextView/EditText/Button/RadioButton/Switch creation,
     * including controls injected after the screen starts rendering.
     * Call from a screen's OnGlobalLayoutListener: snapshots make subsequent
     * layout passes read-only and do not disturb the editor's caret or IME.
     */
    fun styleTree(view: View) {
        if (view is TextView) {
            val value = view.text?.toString().orEmpty()
            val hint = view.hint?.toString().orEmpty()
            val primary = value.ifBlank { hint }
            val previous = styled[view]
            val bold = previous?.bold ?: (view.typeface?.isBold == true)
            val wanted = typeface(primary, bold)
            if (previous == null || previous.textHash != value.hashCode() ||
                previous.textLength != value.length || previous.hintHash != hint.hashCode() ||
                previous.bold != bold || view.typeface !== wanted) {
                // Editable text must not be replaced: it would reset input methods
                // and can interfere with selecting/copying answers.
                view.typeface = wanted
                if (view !is EditText && value.isNotEmpty() &&
                    MurshidTextScript.containsArabic(value) &&
                    MurshidTextScript.latinRuns(value).isNotEmpty()) {
                    // Keep hyperlinks, colors, clickable ranges and existing spans.
                    val formatted = android.text.SpannableString(view.text)
                    val latinFace = if (bold) latinBold else latin
                    for (run in MurshidTextScript.latinRuns(value))
                        formatted.setSpan(MurshidFontSpan(latinFace), run.first, run.last + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    view.text = formatted
                }
                // Keep signatures rather than copies of potentially sensitive edit text.
                styled[view] = Snapshot(value.hashCode(), value.length, hint.hashCode(), bold, wanted)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) styleTree(view.getChildAt(i))
        }
    }
}

private class MurshidFontSpan(private val typeface: Typeface): MetricAffectingSpan() {
    override fun updateMeasureState(p: TextPaint) { p.typeface = typeface }
    override fun updateDrawState(p: TextPaint) { p.typeface = typeface }
}
