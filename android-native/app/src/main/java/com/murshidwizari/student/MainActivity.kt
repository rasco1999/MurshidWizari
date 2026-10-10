package com.murshidwizari.student

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

/** 100% Android native UI: no WebView, no embedded website, no hardcoded secrets. */
class MainActivity : Activity() {
    private val jobs = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var api: ApiClient
    private lateinit var root: LinearLayout
    private var csrf = ""
    private var student = JSONObject()
    private var homepage = JSONObject()
    private var topicsSubjectId = 0
    private var topicsSubjectName = ""
    private var chapterId = 0
    private var chapterName = ""
    private var examQuestions = JSONArray()
    private var questionIndex = 0
    private var busyAnswer = false
    private var submissionUncertain = false
    private var activeScreen = "login"
    private var subscribed = false
    private var night = false
    private val prefs by lazy { getSharedPreferences("native_preferences", MODE_PRIVATE) }
    private val gold = Color.rgb(205,151,40)
    private val navy = Color.rgb(24,49,92)
    private val appColor get() = if (night) Color.rgb(28,35,48) else Color.WHITE
    private val textColor get() = if (night) Color.rgb(241,244,249) else Color.rgb(30,40,55)
    private val surface get() = if (night) Color.rgb(45,55,70) else Color.rgb(246,248,252)
    private val accent get() = if (night) gold else navy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = ApiClient(this)
        night = prefs.getBoolean("night", false)
        loginScreen()
        if (api.hasRememberedSession()) {
            fetch("mobile/bootstrap.php", showError = false) { response ->
                if (response.optBoolean("ok")) showHome(response)
            }
        }
    }
    private fun shape(color: Int, radius: Float = 20f): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(size: Int): Int = (size * resources.displayMetrics.density).toInt()
    private fun base(title: String, showBack: Boolean = false) {
        window.statusBarColor = if (night) Color.rgb(28,35,48) else navy
        window.navigationBarColor = appColor
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(appColor) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(35))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(root)
        setContentView(scroll)
        if (showBack) button("‹ الرجوع", secondary = true) { navigateBack() }
        title(title)
    }
    private fun title(text: String) {
        root.addView(TextView(this).apply {
            this.text = text; textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(accent)
            setPadding(dp(4), dp(10), dp(4), dp(20))
        })
    }
    private fun caption(text: String) {
        root.addView(TextView(this).apply {
            this.text = text; textSize = 15f; setTextColor(textColor)
            setPadding(dp(4), dp(8), dp(4), dp(10))
        })
    }
    private fun line(text: String, prominent: Boolean = false) {
        root.addView(TextView(this).apply {
            this.text = text
            textSize = if (prominent) 19f else 16f
            setTextColor(textColor)
            if (prominent) typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(13), dp(12), dp(13))
            background = shape(surface)
        })
        gap()
    }
    private fun gap(n: Int = 10) { root.addView(Space(this).apply { minimumHeight = dp(n) }) }
    private fun input(hint: String, password: Boolean = false, multiline: Boolean = false): EditText {
        val field = EditText(this).apply {
            this.hint = hint
            textSize = 16f
            setTextColor(textColor)
            setHintTextColor(if (night) Color.LTGRAY else Color.GRAY)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setSingleLine(!multiline)
            if (password) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                transformationMethod = PasswordTransformationMethod.getInstance()
            } else if (multiline) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 3
            }
            background = shape(surface)
        }
        root.addView(field, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        return field
    }
    private fun button(text: String, secondary: Boolean = false, action: () -> Unit): Button {
        val b = Button(this).apply {
            this.text = text; textSize = 15f
            isAllCaps = false
            setTextColor(if (secondary) textColor else Color.WHITE)
            background = shape(if (secondary) surface else navy, dp(12).toFloat())
            setOnClickListener { action() }
        }
        root.addView(b, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
        return b
    }
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun fetch(path: String, body: JSONObject? = null, showError: Boolean = true, done: (JSONObject) -> Unit) {
        jobs.execute {
            val result = try { api.call(path, body) } catch (e: Exception) {
                JSONObject().put("ok", false).put("message", "الاتصال بالخادم غير متاح. تحقق من الإنترنت وأعد المحاولة.")
            }
            main.post {
                if (showError && !result.optBoolean("ok")) {
                    if (result.optBoolean("session_expired")) {
                        api.clear(); csrf = ""; loginScreen()
                    }
                    message(result.optString("message", "تعذر تنفيذ الطلب."))
                }
                done(result)
            }
        }
    }
    private fun external(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun fetchForm(path: String, data: Map<String, String>, done: (JSONObject) -> Unit) {
        jobs.execute {
            val result = try { api.postForm(path, data) } catch (_: Exception) {
                JSONObject().put("ok", false).put("message", "تعذر الاتصال بالخادم.")
            }
            main.post {
                if (!result.optBoolean("ok")) message(result.optString("message", "تعذر تنفيذ الطلب."))
                done(result)
            }
        }
    }

    private fun loadAvatar(url: String) {
        if (url.isBlank()) return
        val uri = try { java.net.URI(url) } catch (_: Exception) { return }
        if (uri.scheme != "https" || uri.host !in setOf("www.mur-iq.com", "mur-iq.com")) return
        val view = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "الصورة الشخصية"
        }
        root.addView(view, LinearLayout.LayoutParams(dp(88), dp(88)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(12)
        })
        jobs.execute {
            val bitmap = try {
                val connection = URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 12000
                connection.instanceFollowRedirects = false
                val length = connection.contentLength
                if (length > 3_000_000) null else connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > 3_000_000) break
                        output.write(buffer, 0, n)
                    }
                    if (total > 3_000_000) null else {
                        val bytes = output.toByteArray()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }.also { connection.disconnect() }
            } catch (_: Exception) { null }
            main.post { if (bitmap != null && view.parent != null) view.setImageBitmap(bitmap) }
        }
    }

    private fun loginScreen() {
        activeScreen = "login"
        base("منصة المرشد الوزاري")
        line("رحلتك نحو التفوق تبدأ هنا")
        caption("تسجيل الدخول إلى حساب الطالب")
        val identifier = input("البريد الإلكتروني أو رقم الهاتف")
        val password = input("كلمة المرور", password = true)
        val preview = CheckBox(this).apply { text = "إظهار كلمة المرور"; setTextColor(textColor) }
        preview.setOnCheckedChangeListener { _, checked ->
            password.transformationMethod = if (checked) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            password.setSelection(password.text.length)
        }
        root.addView(preview)
        val remember = CheckBox(this).apply { text = "تذكرني على هذا الجهاز"; setTextColor(textColor) }
        root.addView(remember)
        button("تسجيل الدخول") {
            val id = identifier.text.toString().trim()
            val pass = password.text.toString()
            if (id.isEmpty() || pass.isEmpty()) { message("أدخل البريد أو الهاتف وكلمة المرور."); return@button }
            val data = JSONObject().put("identifier", id).put("password", pass).put("remember_me", remember.isChecked)
            fetch("api/login.php", data) { response ->
                if (response.optBoolean("ok")) {
                    if (response.optBoolean("email_verification_required") || response.optBoolean("phone_verification_required")) {
                        verifyPrompt(response)
                    } else {
                        api.setRemember(remember.isChecked)
                        fetch("mobile/bootstrap.php") { b -> if (b.optBoolean("ok")) showHome(b) }
                    }
                }
            }
        }
        button("إنشاء حساب طالب", secondary = true) { registerScreen() }
        button("نسيت كلمة المرور؟", secondary = true) { recoverScreen() }
        button("سياسة الخصوصية", secondary = true) { external("https://mur-iq.com/privacy.php") }
    }

    private fun verifyPrompt(response: JSONObject) {
        val email = response.optBoolean("email_verification_required")
        AlertDialog.Builder(this).setTitle("تأكيد الحساب")
            .setMessage(response.optString("message", "أكمل تأكيد الحساب."))
            .setPositiveButton("فتح صفحة التحقق") { _, _ ->
                external(if (email) "https://www.mur-iq.com/verify-email.php" else "https://www.mur-iq.com/verify-phone.php")
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun recoverScreen() {
        activeScreen = "recover"
        base("استعادة الحساب", showBack = false)
        line("استعادة الحساب تتم عبر إجراءات التحقق الرسمية الخاصة بالموقع.")
        button("فتح استعادة الحساب في المتصفح") { external("https://www.mur-iq.com/auth.php") }
        button("عودة لتسجيل الدخول", secondary = true) { loginScreen() }
    }

    private fun registerScreen() {
        activeScreen = "register"
        base("إنشاء حساب جديد")
        val name = input("الاسم الثلاثي الكامل")
        val email = input("البريد الإلكتروني")
        val pass = input("كلمة المرور (8 أحرف على الأقل)", true)
        val confirm = input("تأكيد كلمة المرور", true)
        caption("الصف الدراسي")
        val grades = ArrayList<Int>()
        val gradeNames = ArrayList<String>()
        val gradeSpinner = Spinner(this)
        root.addView(gradeSpinner)
        caption("الجنس")
        val gender = Spinner(this)
        gender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("ذكر", "أنثى"))
        fetch("api/grades.php") { result ->
            val array = result.optJSONArray("grades") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                grades.add(item.optInt("id"))
                gradeNames.add(item.optString("name"))
            }
            gradeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, gradeNames)
        }
        gap()
        button("إنشاء الحساب") {
            if (gradeSpinner.selectedItemPosition !in grades.indices) { message("اختر صفك الدراسي."); return@button }
            if (pass.text.toString() != confirm.text.toString()) { message("كلمتا المرور غير متطابقتين."); return@button }
            val data = JSONObject()
                .put("full_name", name.text.toString().trim())
                .put("identifier", email.text.toString().trim())
                .put("contact_type", "email")
                .put("grade_id", grades[gradeSpinner.selectedItemPosition])
                .put("gender", if (gender.selectedItemPosition == 0) "male" else "female")
                .put("password", pass.text.toString())
                .put("password_confirmation", confirm.text.toString())
            fetch("api/register.php", data) { result ->
                if (result.optBoolean("ok")) {
                    message(result.optString("message", "تم إنشاء الحساب."))
                    if (result.optBoolean("email_verification_required")) verifyPrompt(result)
                    loginScreen()
                }
            }
        }
        button("لديك حساب بالفعل؟", secondary = true) { loginScreen() }
    }

    private fun showHome(response: JSONObject) {
        if (!response.optBoolean("ok")) { loginScreen(); return }
        homepage = response; csrf = response.optString("csrf", csrf)
        student = response.optJSONObject("user") ?: JSONObject()
        subscribed = response.optBoolean("subscribed")
        activeScreen = "home"
        base("المرشد الوزاري")
        loadAvatar(student.optString("avatar_url"))
        line("مرحباً، " + student.optString("name", "الطالب"), true)
        caption("الصف الدراسي: " + student.optString("grade"))
        val remaining = response.optInt("free_remaining", 0)
        line(if (subscribed) "اشتراك فعّال" +
            (if (response.isNull("subscription_days_remaining")) "" else " • المتبقي " + response.optInt("subscription_days_remaining") + " يوم")
            else "الإجابات المجانية المتبقية: " + remaining)
        val stats = response.optJSONObject("stats") ?: JSONObject()
        caption("المستوى " + stats.optInt("level",1) + " • نقاط XP " + stats.optInt("xp",0) +
            " • الإجابات الصحيحة " + stats.optInt("correct_answers",0))
        button("المواد الدراسية والاختبارات") { subjectsScreen() }
        button("حسابي") { accountScreen() }
        button("الإشعارات") { notificationsScreen() }
        button("المفضلة") { favoritesScreen() }
        button("راجع أخطاءك") { reviewScreen() }
        button("غيّر جو • قصص") { storiesScreen() }
        button("الدعم والمراسلات") { supportScreen() }
        button("تفعيل رمز الاشتراك") { redeemScreen() }
        button("الباقات والأسعار") { plansScreen() }
        button("المرشحات", secondary = !subscribed) {
            if (subscribed) subjectsScreen(true) else message("المرشحات للمشتركين فقط.")
        }
        button("الوضع " + (if (night) "الفاتح" else "الليلي"), secondary = true) {
            night = !night
            prefs.edit().putBoolean("night", night).apply()
            showHome(homepage)
        }
        button("تحديث البيانات", secondary = true) { refreshHome() }
        button("تسجيل الخروج", secondary = true) {
            fetch("mobile/logout.php", JSONObject().put("csrf", csrf), showError = false) {
                api.clear(); csrf = ""; student = JSONObject(); loginScreen()
            }
        }
    }

    private fun plansScreen() {
        base("الاشتراكات", true)
        caption("الدفع يتم عبر قنوات المنصة الرسمية.")
        val plans = homepage.optJSONArray("plans") ?: JSONArray()
        for (i in 0 until plans.length()) {
            val plan = plans.optJSONObject(i) ?: continue
            line(plan.optString("label") + " — " + plan.optInt("amount") + " دينار عراقي")
        }
        button("تفعيل رمز اشتراك") { redeemScreen() }
    }

    private fun refreshHome() {
        fetch("mobile/bootstrap.php") { result -> if (result.optBoolean("ok")) showHome(result) }
    }

    private fun subjectsScreen(predictions: Boolean = false) {
        activeScreen = "subjects"
        base(if (predictions) "المرشحات الوزارية" else "المواد الدراسية", true)
        val subjects = homepage.optJSONArray("subjects") ?: JSONArray()
        for (i in 0 until subjects.length()) {
            val obj = subjects.optJSONObject(i) ?: continue
            val subjectId = obj.optInt("id")
            val name = obj.optString("name")
            button(name) { if (predictions) predictionsScreen(subjectId, name) else topicsScreen(subjectId, name) }
        }
        if (subjects.length() == 0) line("لا توجد مواد متاحة حالياً.")
    }

    private fun topicsScreen(subjectId: Int, subjectName: String) {
        topicsSubjectId = subjectId; topicsSubjectName = subjectName
        fetch("mobile/topics.php?subject_id=" + subjectId) { response ->
            if (!response.optBoolean("ok")) return@fetch
            activeScreen = "topics"
        base(subjectName, true)
            val topics = response.optJSONArray("topics") ?: JSONArray()
            for (i in 0 until topics.length()) {
                val topic = topics.optJSONObject(i) ?: continue
                val count = topic.optInt("count")
                val name = topic.optString("name")
                button(name + " • " + count + " سؤال") {
                    if (count > 0) loadExam(topic.optInt("id"), name) else message("لم تتم إضافة أسئلة لهذا الموضوع بعد.")
                }
            }
            if (topics.length() == 0) line("لم تُضف مواضيع بعد.")
        }
    }

    private fun loadExam(id: Int, name: String) {
        chapterId = id; chapterName = name
        fetch("mobile/exam.php?chapter_id=" + id) { result ->
            if (!result.optBoolean("ok")) return@fetch
            csrf = result.optString("csrf", csrf)
            examQuestions = result.optJSONArray("questions") ?: JSONArray()
            submissionUncertain = false
            questionIndex = 0
            while (questionIndex < examQuestions.length() && examQuestions.optJSONObject(questionIndex)?.optBoolean("submitted") == true) questionIndex++
            renderQuestion()
        }
    }

    private fun renderQuestion() {
        activeScreen = "exam"
        base(chapterName, true)
        if (examQuestions.length() == 0) {
            line("لا توجد أسئلة في هذا الموضوع.")
            return
        }
        if (questionIndex >= examQuestions.length()) {
            line("أكملت عرض أسئلة هذا الموضوع. أحسنت!", true)
            button("طلب أسئلة جديدة") { questionRequest() }
            button("عودة إلى المواضيع") { topicsScreen(topicsSubjectId, topicsSubjectName) }
            return
        }
        val question = examQuestions.optJSONObject(questionIndex) ?: return
        val id = question.optInt("id")
        val submitted = question.optBoolean("submitted")
        caption("السؤال " + (questionIndex+1) + " من " + examQuestions.length() + " • وزاري")
        line(question.optString("text"), true)
        if (!question.isNull("meaning_word") && question.optString("meaning_word").isNotBlank())
            caption("المفردة: " + question.optString("meaning_word"))
        val previous = question.optJSONObject("answer")
        if (submitted) {
            val correct = previous?.optBoolean("correct") == true
            line(if (correct) "✓ إجابتك صحيحة" else "إجابتك مسجلة")
            caption("الإجابة المسجلة: " + (previous?.optString("text") ?: ""))
            caption("الإجابة النموذجية: " + question.optString("correct_answer"))
            caption("الشرح: " + question.optString("explanation"))
            button("السؤال التالي") { questionIndex++; renderQuestion() }
        } else {
            when {
                question.optJSONObject("english_match") != null ->
                    englishMatchQuestion(question)
                question.optString("type") == "calculation" ->
                    stagesQuestion(question, "calculation")
                question.optString("type") == "genetics" ->
                    stagesQuestion(question, "genetics")
                question.optString("type") == "match" ->
                    tajweedQuestion(question)
                (question.optJSONArray("options")?.length() ?: 0) > 0 -> {
                    val options = question.optJSONArray("options") ?: JSONArray()
                    for (i in 0 until options.length()) {
                        val option = options.optJSONObject(i)
                        val chosen = option?.optString("text") ?: options.optString(i)
                        val optionId = option?.optInt("id") ?: 0
                        button(chosen, secondary = true) {
                            AlertDialog.Builder(this).setMessage("اعتماد الإجابة: " + chosen + "؟")
                                .setPositiveButton("إرسال الإجابة") { _, _ -> sendAnswer(question, chosen, optionId) }
                                .setNegativeButton("إلغاء", null).show()
                        }
                    }
                }
                else -> {
                    val field = input("اكتب إجابتك هنا", multiline = true)
                    button("تصحيح الإجابة") {
                        sendAnswer(question, field.text.toString().trim(), 0)
                    }
                }
            }
        }
        gap(15)
        button(if (question.optBoolean("favorite")) "إزالة من المفضلة" else "إضافة للمفضلة", secondary = true) {
            val action = if (question.optBoolean("favorite")) "unfavorite" else "favorite"
            val body = JSONObject().put("csrf", csrf).put("question_id", id).put("action", action)
            fetch("api/question-tools.php", body) { result ->
                if (result.optBoolean("ok")) { question.put("favorite", result.optBoolean("favorite")); renderQuestion() }
            }
        }
        button("الإبلاغ عن مشكلة في السؤال", secondary = true) { reportQuestion(id) }
        button("العودة للمواضيع", secondary = true) { topicsScreen(topicsSubjectId, topicsSubjectName) }
    }

    private fun sendAnswer(question: JSONObject, answer: String, optionId: Int) {
        if (busyAnswer) return
        if (question.optBoolean("submitted")) { message("تم تسجيل إجابة هذا السؤال بالفعل."); return }
        if (answer.isBlank()) { message("اكتب الإجابة أولاً."); return }
        if (csrf.isEmpty()) { message("انتهت الجلسة، حدّث الصفحة."); return }
        if (submissionUncertain) {
            message("تحقق من حالة الإجابة السابقة قبل إرسال إجابة جديدة.")
            return
        }
        busyAnswer = true
        val id = question.optInt("id")
        val stored = JSONObject().put("text", answer).put("selected_text", answer).put("submitted", true)
        if (optionId > 0) stored.put("selected", optionId)
        val all = JSONObject().put(id.toString(), stored)
        val data = JSONObject().put("csrf", csrf).put("chapter_id", chapterId)
            .put("question_index", questionIndex + 1).put("answers", all).put("completed", false)
        fetch("api/progress.php", data, showError = true) { result ->
            busyAnswer = false
            if (!result.optBoolean("ok")) {
                if (!result.has("payment_required") && !result.has("session_expired")) {
                    submissionUncertain = true
                    AlertDialog.Builder(this).setTitle("التحقق من حفظ الإجابة")
                        .setMessage("قد تكون الإجابة قد حُفظت رغم انقطاع الاتصال. حدّث الاختبار للتحقق قبل المحاولة مجدداً.")
                        .setPositiveButton("التحقق الآن") { _, _ ->
                            loadExam(chapterId, chapterName)
                        }.setNegativeButton("لاحقاً", null).show()
                }
                if (result.optBoolean("payment_required")) {
                    AlertDialog.Builder(this).setTitle("انتهت الأسئلة المجانية")
                        .setMessage(result.optString("message", "اشترك لمتابعة الاختبارات."))
                        .setPositiveButton("تفعيل كود") { _, _ -> redeemScreen() }
                        .setNegativeButton("إلغاء", null).show()
                }
                return@fetch
            }
            val grade = result.optJSONObject("grades")?.optJSONObject(id.toString())
            // Server is the only source of truth; record a submitted answer after its ack.
            val confirmed = grade != null
            if (!confirmed) {
                submissionUncertain = true
                AlertDialog.Builder(this).setTitle("لم يصل تأكيد الحفظ")
                    .setMessage("سنقرأ الإجابة المحفوظة من الخادم قبل السماح بمحاولة أخرى.")
                    .setPositiveButton("تحديث الاختبار") { _, _ -> loadExam(chapterId, chapterName) }
                    .setCancelable(false).show()
                return@fetch
            }
            question.put("submitted", true)
            question.put("answer", JSONObject().put("text",answer).put("correct",grade.optBoolean("is_correct")))
            val explanation = grade.optString("explanation", question.optString("explanation"))
            val correctAnswer = grade.optString("correct_answer", question.optString("correct_answer"))
            question.put("explanation", explanation)
            question.put("correct_answer", correctAnswer)
            renderQuestion()
            AlertDialog.Builder(this).setTitle(if (grade.optBoolean("is_correct")) "إجابة صحيحة ✓" else "راجع إجابتك")
                .setMessage("الإجابة: " + correctAnswer + "\n\n" + explanation)
                .setPositiveButton("حسناً", null).show()
        }
    }

    private fun stagesQuestion(question: JSONObject, field: String) {
        val stages = question.optJSONObject(field)?.optJSONArray("stages") ?: JSONArray()
        if (stages.length() == 0) { line("هذا النوع من الأسئلة يحتاج إلى بيانات مراحل غير متاحة حالياً."); return }
        val values = LinkedHashMap<String, EditText>()
        val selections = LinkedHashMap<String, Spinner>()
        for (i in 0 until stages.length()) {
            val stage = stages.optJSONObject(i) ?: continue
            val key = stage.optString("key", "step" + i)
            caption(stage.optString("label", key))
            if (stage.optString("kind") == "info") {
                line(stage.optString("value")); continue
            }
            if (stage.optString("kind") == "punnett") {
                val cells = stage.optJSONArray("cells") ?: JSONArray()
                for (j in 0 until cells.length()) {
                    values[key + "_" + j] = input("المربع " + (j+1))
                }
            } else if (stage.optString("kind") == "choice") {
                val options = stage.optJSONArray("options") ?: JSONArray()
                val keys = ArrayList<String>()
                val labels = ArrayList<String>()
                keys.add(""); labels.add("اختر الإجابة")
                for (j in 0 until options.length()) {
                    val opt = options.optJSONObject(j)
                    keys.add(opt?.optString("value") ?: options.optString(j))
                    labels.add(opt?.optString("label") ?: options.optString(j))
                }
                val spinner = Spinner(this)
                spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
                root.addView(spinner)
                selections[key] = spinner
                spinner.tag = keys
            } else values[key] = input(stage.optString("placeholder", "اكتب جواب هذه المرحلة"))
        }
        button("إرسال الحل للتصحيح") {
            val payload = JSONObject()
            values.forEach { (key, v) ->
                val value = v.text.toString().trim()
                if (value.isNotBlank()) payload.put(key, value)
            }
            selections.forEach { (key, spinner) ->
                @Suppress("UNCHECKED_CAST") val keys = spinner.tag as ArrayList<String>
                val v = keys.getOrNull(spinner.selectedItemPosition).orEmpty()
                if (v.isNotBlank()) payload.put(key, v)
            }
            val needed = values.size + selections.size
            if (payload.length() != needed) { message("أكمل جميع مراحل الحل أولاً."); return@button }
            sendAnswer(question, JSONObject().put("stages", payload).toString(), 0)
        }
    }

    private fun tajweedQuestion(question: JSONObject) {
        val items = question.optJSONArray("match_items") ?: JSONArray()
        if (items.length() == 0) { line("لم ترد عناصر المطابقة من الخادم."); return }
        val types = listOf("اختر نوع المد", "مد طبيعي", "مد متصل", "مد منفصل", "مد بدل", "مد لازم", "مد عارض للسكون", "مد لين", "مد صلة صغرى", "مد صلة كبرى", "مد تمكين", "مد عوض")
        val fields = ArrayList<Triple<Int, Spinner, EditText>>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            caption(item.optString("mad_word"))
            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
            root.addView(spinner)
            val reason = input("سبب نوع المد")
            fields.add(Triple(item.optInt("id"), spinner, reason))
        }
        button("إرسال الإجابة") {
            val responses = JSONObject()
            for ((id, spinner, field) in fields) {
                if (spinner.selectedItemPosition == 0 || field.text.isBlank()) {
                    message("حدد نوع المد واكتب السبب لكل كلمة."); return@button
                }
                responses.put(id.toString(), JSONObject().put("type",types[spinner.selectedItemPosition]).put("reason", field.text.toString().trim()))
            }
            sendAnswer(question, JSONObject().put("items", responses).toString(), 0)
        }
    }

    private fun englishMatchQuestion(question: JSONObject) {
        val definition = question.optJSONObject("english_match") ?: return
        val left = definition.optJSONObject("left") ?: JSONObject()
        val right = definition.optJSONObject("right") ?: JSONObject()
        val keys = ArrayList<String>()
        val choices = ArrayList<String>()
        val rightKeys = right.keys()
        choices.add("اختر الوصل"); keys.add("")
        while (rightKeys.hasNext()) {
            val key = rightKeys.next()
            keys.add(key); choices.add(key + " : " + right.optString(key))
        }
        val pairFields = ArrayList<Pair<String, Spinner>>()
        val leftKeys = left.keys()
        while (leftKeys.hasNext()) {
            val key = leftKeys.next()
            caption(key + " : " + left.optString(key))
            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices)
            root.addView(spinner)
            pairFields.add(key to spinner)
        }
        button("تسليم التوصيلات") {
            val lines = ArrayList<String>()
            for ((key, spinner) in pairFields) {
                val selected = keys.getOrNull(spinner.selectedItemPosition).orEmpty()
                if (selected.isBlank()) { message("أكمل جميع التوصيلات أولاً."); return@button }
                lines.add(key + "-" + selected)
            }
            sendAnswer(question, lines.joinToString(", "), 0)
        }
    }

    private fun reportQuestion(id: Int) {
        val labels = arrayOf("إجابة غير صحيحة", "سؤال غير واضح", "سؤال مكرر", "المصدر", "مشكلة أخرى")
        val codes = arrayOf("wrong_answer", "unclear", "duplicate", "source", "other")
        AlertDialog.Builder(this).setTitle("سبب البلاغ")
            .setItems(labels) { _, index ->
                val field = EditText(this).apply { hint = "ملاحظة إضافية (اختياري)" }
                AlertDialog.Builder(this).setTitle(labels[index]).setView(field)
                    .setPositiveButton("إرسال") { _, _ ->
                        val data = JSONObject().put("csrf",csrf).put("question_id",id)
                            .put("action","report").put("reason",codes[index])
                            .put("message",field.text.toString())
                        fetch("api/question-tools.php",data) { response ->
                            if (response.optBoolean("ok")) message(response.optString("message","تم إرسال البلاغ."))
                        }
                    }.setNegativeButton("إلغاء",null).show()
            }.show()
    }

    private fun questionRequest() {
        val id = chapterId
        fetch("mobile/question-request.php?chapter_id=" + id) { status ->
            if (!status.optBoolean("ok")) return@fetch
            if (!status.optBoolean("complete")) { message("أكمل جميع أسئلة الموضوع أولاً."); return@fetch }
            if (status.optBoolean("pending")) { message("طلبك قيد المراجعة."); return@fetch }
            AlertDialog.Builder(this).setMessage("إرسال طلب إضافة أسئلة جديدة لهذا الموضوع؟")
                .setPositiveButton("إرسال") { _, _ ->
                    fetch("mobile/question-request.php", JSONObject().put("csrf", csrf).put("chapter_id",id)) { done ->
                        if (done.optBoolean("ok")) message(done.optString("message", "تم إرسال الطلب."))
                    }
                }.setNegativeButton("إلغاء",null).show()
        }
    }

    private fun accountScreen() {
        fetch("mobile/account.php") { response ->
            if (!response.optBoolean("ok")) return@fetch
            base("حسابي", true)
            val profile = response.optJSONObject("user") ?: JSONObject()
            loadAvatar(response.optString("avatar_url"))
            line("الاسم: " + profile.optString("name"), true)
            line("معرّف الطالب: " + profile.optInt("id"))
            caption("حالة الصورة: " + response.optString("avatar_status", "غير محددة"))
            button("حذف الصورة الشخصية", secondary = true) {
                AlertDialog.Builder(this).setTitle("حذف الصورة")
                    .setMessage("هل تريد إزالة صورتك الشخصية؟")
                    .setPositiveButton("نعم") { _, _ ->
                        fetchForm("mobile/avatar.php", mapOf("csrf" to csrf, "remove" to "1")) { result ->
                            if (result.optBoolean("ok")) accountScreen()
                        }
                    }.setNegativeButton("إلغاء", null).show()
            }
            val days = response.optInt("subscription_days_remaining", -1)
            if (days >= 0) line("الأيام المتبقية من الاشتراك: " + days)
            if (response.optBoolean("can_change_name")) {
                val name = input("تغيير الاسم الثلاثي")
                button("حفظ الاسم") {
                    val newName = name.text.toString().trim()
                    fetch("mobile/account.php",JSONObject().put("csrf",csrf).put("action","change_name").put("name",newName)) {
                        if (it.optBoolean("ok")) { message("تم تحديث الاسم."); refreshHome() }
                    }
                }
            }
            button("سياسة الخصوصية", secondary = true) { external("https://mur-iq.com/privacy.php") }
        }
    }

    private fun notificationsScreen() {
        fetch("mobile/notifications.php") { response ->
            if (!response.optBoolean("ok")) return@fetch
            base("الإشعارات", true)
            val items = response.optJSONArray("items") ?: JSONArray()
            caption("غير المقروءة: " + response.optInt("unread", 0))
            if (items.length() > 0) button("تحديد الكل مقروءاً", secondary = true) {
                fetch("mobile/notifications.php",JSONObject().put("csrf",csrf).put("read_all",true)) {
                    if (it.optBoolean("ok")) notificationsScreen()
                }
            }
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                line(item.optString("title") + "\n" + item.optString("body"))
            }
            if (items.length() == 0) caption("لا توجد إشعارات.")
        }
    }

    private fun favoritesScreen() {
        fetch("mobile/favorites.php") { response ->
            if (!response.optBoolean("ok")) return@fetch
            base("أسئلتي المفضلة", true)
            val items = response.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                line(item.optString("question_text"))
                caption(item.optString("subject_name") + " • " + item.optString("chapter_name"))
            }
            if (items.length() == 0) caption("لا توجد أسئلة محفوظة.")
        }
    }

    private fun reviewScreen() {
        fetch("mobile/review.php") { response ->
            if (!response.optBoolean("ok")) return@fetch
            base("مراجعة الأخطاء", true)
            val items = response.optJSONArray("mistakes") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                line(item.optString("question_text"))
                caption("الإجابة الصحيحة: " + item.optString("correct_answer"))
                caption("التوضيح: " + item.optString("explanation"))
            }
            if (items.length() == 0) caption("لا توجد أخطاء مسجلة.")
        }
    }

    private fun storiesScreen() {
        fetch("mobile/stories.php") { response ->
            if (!response.optBoolean("ok")) return@fetch
            base("غيّر جو", true)
            val stories = response.optJSONArray("stories") ?: JSONArray()
            for (i in 0 until stories.length()) {
                val item = stories.optJSONObject(i) ?: continue
                line(item.optString("title"), true)
                caption(item.optString("body", item.optString("content")))
            }
            if (stories.length() == 0) caption("لا توجد قصص متاحة.")
        }
    }

    private fun supportScreen() {
        fetch("mobile/support.php") { response ->
            if (!response.optBoolean("ok")) return@fetch
            base("الدعم والمراسلات", true)
            val messages = response.optJSONArray("messages") ?: JSONArray()
            for (i in 0 until messages.length()) {
                val obj = messages.optJSONObject(i) ?: continue
                line(obj.optString("body", obj.optString("message")))
            }
            val msg = input("اكتب رسالة لفريق الدعم", multiline = true)
            button("إرسال الرسالة") {
                if (msg.text.isBlank()) { message("اكتب رسالتك."); return@button }
                fetch("mobile/support.php",JSONObject().put("csrf",csrf).put("message",msg.text.toString())) { result ->
                    if (result.optBoolean("ok")) supportScreen()
                }
            }
        }
    }

    private fun redeemScreen() {
        base("تفعيل الاشتراك", true)
        line("أدخل رمز الاشتراك الرسمي الذي حصلت عليه من إدارة المنصة.")
        val code = input("رمز الاشتراك")
        button("تفعيل الكود") {
            if (code.text.isBlank()) { message("أدخل رمز الاشتراك."); return@button }
            AlertDialog.Builder(this).setMessage("هل تريد تفعيل الرمز الآن؟")
                .setPositiveButton("تفعيل") { _, _ ->
                    fetch("mobile/redeem-subscription-code.php", JSONObject().put("csrf",csrf).put("code",code.text.toString().trim())) { result ->
                        if (result.optBoolean("ok")) { message(result.optString("message","تم تفعيل الاشتراك.")); refreshHome() }
                    }
                }.setNegativeButton("إلغاء",null).show()
        }
    }

    private fun predictionsScreen(subjectId: Int, subject: String) {
        if (!subscribed) { message("المرشحات متاحة للمشتركين فقط."); return }
        fetch("mobile/predictions.php?subject_id=" + subjectId) { response ->
            if (!response.optBoolean("ok")) return@fetch
            base("مرشحات " + subject, true)
            val predictions = response.optJSONArray("predictions") ?: JSONArray()
            for (i in 0 until predictions.length()) {
                val p = predictions.optJSONObject(i) ?: continue
                line(p.optString("question_text", p.optString("title", "مرشح " + (i+1))))
                caption(p.optString("explanation"))
            }
            if (predictions.length() == 0) caption("لم تُنشَر مرشحات لهذه المادة بعد.")
        }
    }

    private fun navigateBack() {
        when (activeScreen) {
            "exam" -> topicsScreen(topicsSubjectId, topicsSubjectName)
            "topics" -> subjectsScreen()
            "subjects" -> showHome(homepage)
            "login" -> finish()
            else -> if (homepage.optBoolean("ok")) showHome(homepage) else loginScreen()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { navigateBack() }

    override fun onDestroy() {
        jobs.shutdownNow()
        super.onDestroy()
    }
}
