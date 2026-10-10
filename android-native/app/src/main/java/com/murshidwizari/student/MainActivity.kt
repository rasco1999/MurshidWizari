package com.murshidwizari.student

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Native Android screen; no WebView. Authentication/API integration pending endpoint review. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
        }
        root.addView(TextView(this).apply {
            text = "منصة المرشد الوزاري"
            textSize = 26f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "نسخة Android أصلية قيد التطوير — لم يتم ربط تسجيل الدخول والأسئلة بعد."
            textSize = 16f
            gravity = Gravity.CENTER
        })
        setContentView(root)
    }
}
