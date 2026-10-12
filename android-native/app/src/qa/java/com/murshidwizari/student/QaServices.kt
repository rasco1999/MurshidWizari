package com.murshidwizari.student

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Compiled only into the disposable loopback QA APK, never debug/candidate/release. */
class QaPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        check(BuildConfig.BUILD_TYPE == "qa" && context.packageName.endsWith(".qa"))
        StudentPush.receive(context, mapOf("binding_id" to intent.getStringExtra("binding_id").orEmpty(),
            "notification_id" to intent.getStringExtra("notification_id").orEmpty()))
    }
}

class QaCheckoutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.BUILD_TYPE == "qa" && intent.data?.host == "checkout.example.test")
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32,100,32,32)
            addView(TextView(this@QaCheckoutActivity).apply { text = "بوابة دفع محلية للاختبار"; textSize = 22f })
            addView(Button(this@QaCheckoutActivity).apply {
                text = "العودة إلى المرشد"
                setOnClickListener { finish() }
            })
        })
    }
}
