package com.murshidwizari.student

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.min

/** Canvas counterpart of Build 498's native three-second welcome artwork. */
class CinematicWelcomeView(context: Context, private val greetingText: String = "أهلًا بك") : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emblem = BitmapFactory.decodeResource(resources, R.drawable.welcome_emblem)
    private val started = SystemClock.elapsedRealtime()
    private val gold = Color.rgb(250, 204, 107)
    private val ivory = Color.rgb(250, 247, 235)
    private val emerald = Color.rgb(15, 122, 89)
    private val density = resources.displayMetrics.density
    private val motion = ValueAnimator.areAnimatorsEnabled()
    init { contentDescription = "منصة المرشد الوزاري. $greetingText. تعلّم بثقة، واصنع مستقبلك"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    private fun dp(v: Float) = v * density
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2
        if (w <= 0 || h <= 0) return
        val elapsed = (SystemClock.elapsedRealtime() - started).toFloat()
        val reveal = if (motion) ((elapsed - 620f) / 650f).coerceIn(0f, 1f) else 1f
        val initial = if (motion) (elapsed / 460f).coerceIn(0f, 1f) else 1f
        val greeting = if (motion) ((elapsed - 1870f) / 400f).coerceIn(0f, 1f) else 1f
        paint.shader = LinearGradient(w, 0f, 0f, h, intArrayOf(Color.rgb(5,16,34), Color.rgb(13,37,66), Color.rgb(6,26,46), Color.rgb(5,16,34)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,paint)
        paint.shader = RadialGradient(w*.78f,h*.32f,dp(320f),Color.argb(95,15,122,89), Color.TRANSPARENT,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,paint)
        paint.shader = RadialGradient(w*.23f,h*.6f,dp(280f),Color.argb(48,250,204,107), Color.TRANSPARENT,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,paint); paint.shader = null
        val inset = maxOf(dp(22f), (w-dp(552f))/2)
        fun label(text: String, x: Float, y: Float, size: Float, color: Int, alpha: Float = 1f, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) {
            paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color; paint.alpha = (alpha*255).toInt()
            paint.textAlign = align; paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            paint.textSize = dp(size)
            if (align == Paint.Align.CENTER && paint.measureText(text) > w-2*inset) paint.textSize *= (w-2*inset)/paint.measureText(text)
            canvas.drawText(text,x,y,paint)
        }
        label("منصة تعليمية عراقية", w-inset, dp(45f), 12f, ivory, initial, true, Paint.Align.RIGHT)
        for ((i,color) in listOf(emerald,ivory,Color.rgb(204,59,69)).withIndex()) {
            paint.color=color; paint.alpha=(initial*255).toInt()
            val x=inset+dp(i*14f); canvas.drawRoundRect(x,dp(35f),x+dp(12f),dp(38f),dp(2f),dp(2f),paint)
        }
        val skyline = Path()
        val sh = h*.38f; val sy = h-sh
        fun point(x: Float,y: Float) { skyline.lineTo(x*w,sy+y*sh) }
        skyline.moveTo(0f,sy+sh*.67f)
        point(.07f,.67f);point(.07f,.5f);point(.15f,.5f);point(.15f,.62f);point(.24f,.62f);point(.24f,.38f);point(.28f,.38f);point(.28f,.28f);point(.31f,.28f);point(.31f,.62f);point(.41f,.62f);point(.41f,.53f)
        skyline.quadTo(w*.495f,sy+sh*.29f,w*.58f,sy+sh*.53f)
        point(.58f,.66f);point(.69f,.66f);point(.69f,.40f);point(.74f,.40f);point(.74f,.30f);point(.77f,.30f);point(.77f,.63f);point(.88f,.63f);point(.88f,.51f);point(1f,.51f);point(1f,1f);point(0f,1f);skyline.close()
        paint.shader=LinearGradient(0f,sy,0f,h,Color.rgb(36,79,84),Color.BLACK,Shader.TileMode.CLAMP);paint.alpha=(148*reveal).toInt()
        canvas.drawPath(skyline,paint);paint.shader=null
        val cy = h*.37f; val r = min(dp(108f),w*.28f)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(1f);paint.color=ivory;paint.alpha=(20*initial).toInt()
        canvas.drawCircle(cx,cy,r,paint)
        paint.color=gold;paint.alpha=(230*initial).toInt();paint.strokeWidth=dp(2f)
        canvas.drawArc(cx-r*.935f,cy-r*.935f,cx+r*.935f,cy+r*.935f,-70+285*reveal,335*initial,false,paint)
        paint.color=emerald;paint.alpha=(165*reveal).toInt();paint.strokeWidth=dp(1.6f)
        canvas.drawArc(cx-r*1.037f,cy-r*1.037f,cx+r*1.037f,cy+r*1.037f,45-225*reveal,281*reveal,false,paint)
        paint.style=Paint.Style.FILL;paint.alpha=(initial*255).toInt()
        val er=r*.741f*(.36f+.64f*initial)
        canvas.drawBitmap(emblem,null,RectF(cx-er,cy-er,cx+er,cy+er),paint)
        val titleY=cy+r+dp(49f)
        label("منصة المرشد الوزاري",cx,titleY,30f,ivory,reveal,true)
        paint.color=gold;paint.alpha=(170*reveal).toInt();paint.strokeWidth=dp(1f)
        canvas.drawLine(cx-dp(47f),titleY+dp(25f),cx-dp(11f),titleY+dp(25f),paint)
        canvas.drawLine(cx+dp(11f),titleY+dp(25f),cx+dp(47f),titleY+dp(25f),paint)
        label("تعلّم بثقة، واصنع مستقبلك",cx,titleY+dp(58f),15f,ivory,reveal)
        label(greetingText,cx,titleY+dp(103f),20f,gold,greeting,true)
        label("المنهج العراقي • طريقك نحو النجاح",cx,h-dp(50f),12f,ivory,reveal)
        paint.color=ivory;paint.alpha=(48*reveal).toInt(); val trackL=inset+dp(20f);val trackR=w-trackL
        canvas.drawRoundRect(trackL,h-dp(29f),trackR,h-dp(25f),dp(2f),dp(2f),paint)
        paint.shader=LinearGradient(trackL,0f,trackR,0f,intArrayOf(emerald,Color.rgb(102,222,166),gold),null,Shader.TileMode.CLAMP);paint.alpha=(255*reveal).toInt()
        val progress=((elapsed-620f)/1750f).coerceIn(0f,1f)
        canvas.drawRoundRect(trackL,h-dp(29f),trackL+(trackR-trackL)*progress,h-dp(25f),dp(2f),dp(2f),paint)
        paint.shader=null
        if (elapsed < 3000f) postInvalidateOnAnimation()
    }
}
