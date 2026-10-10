package com.murshidwizari.student

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.CookieManager
import java.util.concurrent.Executors

/**
 * Android-native screens (NO WebView).
 * Uses existing PHP session cookies and published JSON endpoints.
 * No credentials or session cookies are persisted to disk.
 */
class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val cookies = CookieManager()
    private lateinit var root: LinearLayout
    private val burgundy = Color.rgb(84,21,28)
    private var csrf = ""
    private var currentChapter = 0
    private var questions = org.json.JSONArray()
    private var questionIndex = 0
    private var userName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = burgundy
        loginScreen()
    }
    private fun layout(title: String) {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26,32,26,36)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(root)
        setContentView(scroll)
        val head = TextView(this).apply {
            text = title; textSize = 25f; setTextColor(burgundy)
            gravity = Gravity.CENTER; setPadding(0,8,0,26)
        }
        root.addView(head)
    }
    private fun field(hint: String, secret: Boolean = false): EditText {
        val f = EditText(this).apply {
            this.hint = hint; textSize = 17f
            if (secret) inputType = 129
            setSingleLine(true)
        }
        root.addView(f)
        return f
    }
    private fun label(message: String) {
        root.addView(TextView(this).apply { text = message; textSize = 16f; setPadding(0,12,0,12) })
    }
    private fun button(name: String, action: () -> Unit) {
        val b = Button(this).apply { text = name; setTextColor(Color.WHITE); setBackgroundColor(burgundy); setOnClickListener { action() } }
        root.addView(b)
        root.addView(Space(this).apply { minimumHeight = 12 })
    }
    private fun request(path: String, payload: JSONObject? = null, done: (JSONObject) -> Unit) {
        executor.execute {
            val result = try {
                val connection = (URL("https://www.mur-iq.com/" + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = if (payload == null) "GET" else "POST"
                    connectTimeout = 15000; readTimeout = 20000
                    setRequestProperty("Accept","application/json")
                    setRequestProperty("X-Murshid-Client","AndroidNative")
                    cookies.getCookieStore().cookies.joinToString("; ") { it.name + "=" + it.value }.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie",it) }
                    if (payload != null) {
                        doOutput = true
                        setRequestProperty("Content-Type","application/json; charset=utf-8")
                        outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    }
                }
                connection.headerFields["Set-Cookie"]?.forEach { header ->
                    try { cookies.put(java.net.URI("https://www.mur-iq.com"),mapOf("Set-Cookie" to listOf(header))) } catch (_: Exception) {}
                }
                val body = (if (connection.responseCode < 400) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: "{}"
                connection.disconnect()
                JSONObject(body)
            } catch (e: Exception) {
                JSONObject().put("ok",false).put("message","تعذر الاتصال بالخادم: " + (e.localizedMessage ?: ""))
            }
            ui.post { done(result) }
        }
    }
    private fun failed(o: JSONObject): Boolean {
        if (o.optBoolean("ok",false)) return false
        Toast.makeText(this,o.optString("message","تعذر تنفيذ الطلب"),Toast.LENGTH_LONG).show()
        return true
    }
    private fun loginScreen() {
        layout("منصة المرشد الوزاري")
        label("تسجيل دخول الطالب")
        val identifier = field("البريد الإلكتروني أو رقم الهاتف")
        val password = field("كلمة المرور",true)
        val remember = CheckBox(this).apply { text = "تذكرني على هذا الجهاز" }
        root.addView(remember)
        button("تسجيل الدخول") {
            val data = JSONObject().put("identifier",identifier.text.toString().trim())
                .put("password",password.text.toString()).put("remember_me",remember.isChecked)
            request("api/login.php",data) { response ->
                if (!failed(response)) {
                    if (response.optBoolean("email_verification_required") || response.optBoolean("phone_verification_required")) {
                        label("يجب إكمال التحقق من الحساب قبل الدخول.")
                    } else bootstrap()
                }
            }
        }
    }
    private fun bootstrap() {
        request("mobile/bootstrap.php") { response ->
            if (failed(response)) { loginScreen(); return@request }
            csrf = response.optString("csrf")
            val user = response.optJSONObject("user") ?: JSONObject()
            userName = user.optString("name","الطالب")
            layout("مرحباً، " + userName)
            label("الصف: " + user.optString("grade"))
            label(if (response.optBoolean("subscribed")) "اشتراك فعّال" else "الإجابات المجانية المتبقية: " + response.optInt("free_remaining"))
            val subjects = response.optJSONArray("subjects") ?: org.json.JSONArray()
            label("المواد الدراسية")
            for (i in 0 until subjects.length()) {
                val s = subjects.optJSONObject(i) ?: continue
                button(s.optString("name")) { topics(s.optInt("id"),s.optString("name")) }
            }
            button("تحديث") { bootstrap() }
            button("تسجيل الخروج") { request("mobile/logout.php") { cookies.cookieStore.removeAll(); csrf=""; loginScreen() } }
        }
    }
    private fun topics(id: Int, subject: String) {
        request("mobile/topics.php?subject_id=" + id) { response ->
            if (failed(response)) return@request
            layout(subject)
            val topics = response.optJSONArray("topics") ?: org.json.JSONArray()
            for (i in 0 until topics.length()) {
                val t = topics.optJSONObject(i) ?: continue
                button(t.optString("name") + " (" + t.optInt("count") + ")") { exam(t.optInt("id")) }
            }
            button("رجوع للمواد") { bootstrap() }
        }
    }
    private fun exam(chapter: Int) {
        currentChapter = chapter
        request("mobile/exam.php?chapter_id=" + chapter) { response ->
            if (failed(response)) return@request
            csrf = response.optString("csrf",csrf)
            questions = response.optJSONArray("questions") ?: org.json.JSONArray()
            questionIndex = 0
            renderQuestion()
        }
    }
    private fun renderQuestion() {
        layout("الاختبار • " + (questionIndex+1) + " / " + questions.length())
        if (questionIndex >= questions.length()) {
            label("انتهت الأسئلة المعروضة.")
            button("المواد الدراسية") { bootstrap() }
            return
        }
        val q = questions.optJSONObject(questionIndex) ?: return
        label(q.optString("text"))
        val options = q.optJSONArray("options") ?: org.json.JSONArray()
        if (options.length() > 0) {
            for (i in 0 until options.length()) {
                val o = options.optJSONObject(i)
                label(if (o != null) o.optString("text") else options.optString(i))
            }
        }
        if (q.optBoolean("submitted")) {
            label("سبق الإجابة عن هذا السؤال.")
            label(q.optString("explanation"))
        } else {
            label("إرسال الإجابات غير مفعّل في هذه النسخة الأولية حتى نتحقق من واجهة التصحيح ونضمن عدم خصم إجابات الطالب بالخطأ.")
        }
        button("السؤال التالي") { questionIndex++; renderQuestion() }
        button("رجوع للمواد") { bootstrap() }
    }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
