package com.murshidwizari.student

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Android spacing only: physical density, user font scale and text metrics stay intact. */
internal object CompactLayoutRules {
    fun spacing(dp: Int): Int = if (dp < 8) dp else (dp * 0.65f).roundToInt().coerceAtLeast(6)
    fun heading(sp: Float): Float = when {
        sp >= 30f -> sp * 0.8f
        sp >= 20f -> sp - 2f
        else -> sp
    }
    fun columns(widthDp: Int, fontScale: Float): Int =
        if (widthDp < 360 && fontScale > 1.25f || fontScale >= 1.7f) 1 else 2
    fun navigationHeight(fontScale: Float): Int = maxOf(61, (40 + 22 * fontScale).roundToInt())
}

/** Apply the same vertical rhythm to every content screen, including async additions.
 * Each view is visited once, before measurement, so repeated layout never shrinks it again.
 * Only whitespace changes; fields, text, illustrations and touch bounds retain their size.
 */
internal class CompactColumn(context: Context) : LinearLayout(context) {
    private val visited = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private fun compact(view: View) {
        if (visited.add(view)) {
            val density = resources.displayMetrics.density
            fun spacing(px: Int) = (CompactLayoutRules.spacing((px / density).roundToInt()) * density).roundToInt()
            if (!view.isClickable && !view.isFocusable && view is ViewGroup) {
                view.setPadding(view.paddingLeft, spacing(view.paddingTop), view.paddingRight, spacing(view.paddingBottom))
            }
            val params = view.layoutParams
            if (params is ViewGroup.MarginLayoutParams) {
                params.topMargin = spacing(params.topMargin)
                params.bottomMargin = spacing(params.bottomMargin)
            }
            // Font enlargement must grow the label/control rather than crop Arabic dots.
            if (view is TextView && params != null && params.height > 0) {
                view.minimumHeight = maxOf(view.minimumHeight, params.height)
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            if (view is Space) {
                if (params != null && params.height > 0) params.height = spacing(params.height)
                view.minimumHeight = spacing(view.minimumHeight)
            }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) compact(view.getChildAt(index))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        compact(this)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
