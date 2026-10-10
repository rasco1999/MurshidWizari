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
    private val api by lazy { ApiClient(this) }
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
            val result = try { api.call(path, payload) }
                catch (e: Exception) { JSONObject().put("ok",false).put("message","تعذر الاتصال بالخادم: " + (e.localizedMessage ?: "")) }
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
            api.setRemember(remember.isChecked)
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
            button("تسجيل الخروج") { request("mobile/logout.php") { api.clear(); csrf=""; loginScreen() } }
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
            val selected = arrayOf("")
            if (q.optString("answer_mode") == "choice" || q.optString("type") == "mcq" || q.optString("type") == "true_false") {
                val group = RadioGroup(this)
                for (i in 0 until options.length()) {
                    val option = options.optJSONObject(i)
                    val value = option?.optString("text") ?: options.optString(i)
                    group.addView(RadioButton(this).apply { text = value; setOnClickListener { selected[0] = value } })
                }
                root.addView(group)
            } else {
                val answer = field("اكتب إجابتك")
                answer.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { selected[0] = s?.toString() ?: "" }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
            }
            button("إرسال الإجابة") {
                if (selected[0].isBlank()) Toast.makeText(this,"اختر إجابة أولاً",Toast.LENGTH_SHORT).show()
                else {
                    val id = q.optInt("id")
                    val answers = JSONObject().put(id.toString(),JSONObject().put("submitted",true).put("text",selected[0]))
                    val payload = JSONObject().put("csrf",csrf).put("chapter_id",currentChapter)
                        .put("question_index",questionIndex).put("answers",answers)
                        .put("completed",questionIndex == questions.length()-1)
                    request("api/progress.php",payload) { response ->
                        if (!failed(response)) {
                            val grade = response.optJSONObject("grades")?.optJSONObject(id.toString())
                            q.put("submitted",true)
                            q.put("explanation",grade?.optString("explanation",q.optString("explanation")) ?: q.optString("explanation"))
                            renderQuestion()
                        }
                    }
                }
            }
        }
        if (q.optBoolean("submitted")) button("السؤال التالي") { questionIndex++; renderQuestion() }
        button("رجوع للمواد") { bootstrap() }
    }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
