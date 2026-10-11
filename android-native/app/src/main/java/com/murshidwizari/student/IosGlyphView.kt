package com.murshidwizari.student

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Scalable, tintable line icons for the iOS-inspired native navigation and cards. */
class IosGlyphView(context: Context, private val symbol: String, private val tint: Int) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = 1.9f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 24f, size / 24f)
        fun path(vararg points: Float) {
            if (points.size < 4) return
            val p = Path().apply { moveTo(points[0], points[1]) }
            for (i in 2 until points.size step 2) p.lineTo(points[i], points[i + 1])
            canvas.drawPath(p, ink)
        }
        when (symbol) {
            "home" -> {
                path(3f, 10f, 12f, 3f, 21f, 10f, 21f, 21f, 3f, 21f, 3f, 10f)
                path(9f, 21f, 9f, 14f, 15f, 14f, 15f, 21f)
            }
            "tests", "list" -> {
                canvas.drawRoundRect(4f, 3f, 20f, 21f, 2f, 2f, ink)
                path(7f, 9f, 9f, 11f, 12f, 7f)
                path(14f, 9f, 17f, 9f)
                path(7f, 16f, 9f, 18f, 12f, 14f)
                path(14f, 16f, 17f, 16f)
            }
            "chart" -> {
                path(3f, 20f, 3f, 14f, 7f, 14f, 7f, 20f)
                path(10f, 20f, 10f, 10f, 14f, 10f, 14f, 20f)
                path(17f, 20f, 17f, 4f, 21f, 4f, 21f, 20f)
            }
            "person" -> {
                canvas.drawCircle(12f, 8f, 4f, ink)
                canvas.drawArc(3f, 12f, 21f, 25f, 195f, 150f, false, ink)
            }
            "bell" -> {
                path(5f, 18f, 7f, 16f, 7f, 10f)
                canvas.drawArc(7f, 4f, 17f, 14f, 180f, 180f, false, ink)
                path(17f, 10f, 17f, 16f, 19f, 18f, 5f, 18f)
                canvas.drawArc(10f, 16f, 14f, 22f, 0f, 180f, false, ink)
            }
            "moon" -> {
                val p = Path().apply {
                    moveTo(17.8f, 3.2f)
                    cubicTo(5f, 4f, 5.5f, 18f, 18.5f, 20.5f)
                    cubicTo(9f, 23.2f, 3f, 16f, 5.2f, 9f)
                    cubicTo(7f, 4.5f, 12.5f, 2f, 17.8f, 3.2f)
                    close()
                }
                canvas.drawPath(p, ink)
            }
            "sun" -> {
                canvas.drawCircle(12f, 12f, 4f, ink)
                for (i in 0 until 8) {
                    val angle = Math.PI * i / 4
                    val x = kotlin.math.cos(angle).toFloat()
                    val y = kotlin.math.sin(angle).toFloat()
                    path(12f + 7f * x, 12f + 7f * y, 12f + 10f * x, 12f + 10f * y)
                }
            }
            "graduation" -> {
                path(2f, 9f, 12f, 4f, 22f, 9f, 12f, 14f, 2f, 9f)
                path(6f, 11f, 6f, 17f, 12f, 20f, 18f, 17f, 18f, 11f)
                path(22f, 9f, 22f, 17f)
            }
            "book" -> {
                path(12f, 6f, 7f, 4f, 3f, 4f, 3f, 19f, 8f, 19f, 12f, 21f)
                path(12f, 6f, 17f, 4f, 21f, 4f, 21f, 19f, 16f, 19f, 12f, 21f, 12f, 6f)
            }
            "check" -> {
                canvas.drawCircle(12f, 12f, 9f, ink)
                path(7f, 12f, 10.5f, 15f, 17f, 9f)
            }
            "circle" -> canvas.drawCircle(12f, 12f, 9f, ink)
            "search" -> {
                canvas.drawCircle(10f, 10f, 6f, ink)
                path(14.5f, 14.5f, 21f, 21f)
            }
            "calendar" -> {
                canvas.drawRoundRect(3f, 5f, 21f, 21f, 2f, 2f, ink)
                path(3f, 10f, 21f, 10f)
                path(8f, 3f, 8f, 7f)
                path(16f, 3f, 16f, 7f)
            }
            "camera" -> {
                path(3f, 8f, 7f, 8f, 9f, 5f, 15f, 5f, 17f, 8f, 21f, 8f, 21f, 20f, 3f, 20f, 3f, 8f)
                canvas.drawCircle(12f, 13f, 4f, ink)
            }
            "heart" -> {
                val p = Path().apply {
                    moveTo(12f, 21f)
                    cubicTo(-1f, 13f, 2f, 3f, 8f, 4f)
                    cubicTo(10f, 4f, 12f, 6f, 12f, 7f)
                    cubicTo(17f, 0f, 25f, 5f, 21f, 13f)
                    cubicTo(19f, 17f, 14f, 20f, 12f, 21f)
                }
                canvas.drawPath(p, ink)
            }
            "trophy" -> {
                path(6f, 4f, 18f, 4f, 18f, 9f)
                canvas.drawArc(6f, 4f, 18f, 18f, 0f, 180f, false, ink)
                path(6f, 5f, 3f, 5f, 3f, 10f, 7f, 13f)
                path(18f, 5f, 21f, 5f, 21f, 10f, 17f, 13f)
                path(12f, 18f, 12f, 21f, 7f, 21f, 17f, 21f)
            }
            "chevron" -> path(15f, 5f, 8f, 12f, 15f, 19f)
            "arrow" -> path(5f, 12f, 19f, 12f, 13f, 6f)
            "sparkle" -> {
                path(12f, 2f, 14f, 10f, 22f, 12f, 14f, 14f, 12f, 22f, 10f, 14f, 2f, 12f, 10f, 10f, 12f, 2f)
            }
            "lock" -> {
                canvas.drawRoundRect(5f, 11f, 19f, 22f, 2f, 2f, ink)
                canvas.drawArc(8f, 2f, 16f, 16f, 180f, 180f, false, ink)
            }
            else -> {
                canvas.drawCircle(12f, 12f, 9f, ink)
                path(12f, 7f, 12f, 13f, 15f, 15f)
            }
        }
        canvas.restore()
    }
}
