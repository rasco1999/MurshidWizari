package com.murshidwizari.student

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/** Local graph scratchpad: drawing never bypasses authoritative answer grading. */
class NativeCoordinateGrid(context: Context) : View(context) {
    private val points = mutableListOf<Pair<Int,Int>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(80,100,140);strokeWidth=2.5f }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(22,97,207) }
    override fun onDraw(canvas: Canvas) {
        val width=width.toFloat();val height=height.toFloat()
        canvas.drawColor(Color.rgb(248,250,253))
        for(i in 0..10) {
            val x=i*width/10f;val y=i*height/10f
            paint.color=if(i==5)Color.rgb(75,103,145) else Color.rgb(214,225,237)
            paint.strokeWidth=if(i==5)2f else 1f
            canvas.drawLine(x,0f,x,height,paint)
            canvas.drawLine(0f,y,width,y,paint)
        }
        points.forEach { (x,y) ->
            canvas.drawCircle((x+5)*width/10f,(5-y)*height/10f,7f,dot)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.action==MotionEvent.ACTION_UP){
            if(width<=0||height<=0)return true
            val x=(event.x/width*10-5).roundToInt().coerceIn(-5,5)
            val y=(5-event.y/height*10).roundToInt().coerceIn(-5,5)
            if(points.size>=6)points.removeAt(0)
            points.add(x to y)
            invalidate()
            performClick()
            return true
        }
        return true
    }
    override fun performClick():Boolean { super.performClick();return true }
    fun coordinates():String=points.joinToString("، "){"(\${it.first},\${it.second})"}
    fun reset(){points.clear();invalidate()}
}
