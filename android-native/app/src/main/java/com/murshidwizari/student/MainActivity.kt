package com.murshidwizari.student

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.net.Uri
import android.media.ExifInterface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.content.Context
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.InputFilter
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
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
    private val examIssues = HashMap<Int, String>()
    private var questionIndex = 0
    private var busyAnswer = false
    private var submissionUncertain = false
    private var activeScreen = "login"
    private var subscribed = false
    private val competitionHistory = ArrayDeque<String>()
    private var dailyQuestions = emptyList<CompetitionRules.Question>()
    private val dailyAnswers = LinkedHashMap<Int, String>()
    private var dailyAvailable = true
    private var dailyDone = false
    private var dailyBusy = false
    private var dailyUncertain = false
    private var dailyQuotaBlocked = false
    private var dailyScore = 0
    private var dailyTotal = 0
    private var dailyReward = 0
    private var dailyStatusMessage = ""
    private var contest = JSONObject()
    private var season = JSONObject()
    private var contestMode = "contest"
    private var contestShowAll = false
    private var achievements = JSONArray()
    private var achievementStats = JSONObject()
    private var achievementFilter = "all"
    private var night = false
    private var screenFrame: FrameLayout? = null
    private var lastValidatedInternet: Boolean? = null
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var sessionGeneration = 0
    private var avatarGeneration = 0
    private var activeAvatar: java.lang.ref.WeakReference<ImageView>? = null
    private var paymentBusy = false
    private var paymentPlan = ""
    private var plansReturnScreen = "home"
    private var checkoutReturnPending = false
    private var recoveryBusy = false
    private var recoverReturnScreen = "login"
    private val prefs by lazy { getSharedPreferences("native_preferences", MODE_PRIVATE) }
    // Identical brand palette to the SwiftUI ThemeV34 definitions.
    private val gold = Color.rgb(245,184,61)
    private val navy = Color.rgb(11,31,51)
    private val blue = Color.rgb(45,108,223)
    private val appColor get() = if (night) Color.rgb(22,27,36) else Color.rgb(243,244,249)
    private val textColor get() = if (night) Color.rgb(241,244,249) else Color.rgb(30,40,55)
    private val surface get() = if (night) Color.rgb(40,47,61) else Color.WHITE
    private val accent get() = if (night) gold else blue

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
    private fun shape(color: Int, radius: Float = dp(22).toFloat()): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(size: Int): Int = (size * resources.displayMetrics.density).toInt()
    private fun contentWidth(): Int = if (resources.configuration.screenWidthDp >= 740) dp(700) else -1
    private fun setupRoot(horizontalPadding: Int = 16, topPadding: Int = 16) {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(appColor); clipToPadding = false }
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15 enforces edge-to-edge: keep text and actions out of status/nav cutouts.
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                insets
            }
        }
        screenFrame = FrameLayout(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(horizontalPadding), dp(topPadding), dp(horizontalPadding), dp(32))
        }
        screenFrame!!.addView(root, FrameLayout.LayoutParams(contentWidth(), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        scroll.addView(screenFrame)
        setContentView(scroll)
    }
    private fun base(title: String, showBack: Boolean = false) {
        window.statusBarColor = navy
        window.navigationBarColor = appColor
        setupRoot()
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
    private fun makeButton(text: String, secondary: Boolean = false, action: () -> Unit): Button =
        Button(this).apply {
            this.text = text; textSize = 15f
            isAllCaps = false
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (secondary) accent else Color.WHITE)
            val fill = if (secondary) shape(if (night) surface else Color.rgb(227,235,250), dp(17).toFloat())
                else GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(blue, Color.rgb(23,71,173)))
                    .apply { cornerRadius = dp(17).toFloat() }
            background = RippleDrawable(ColorStateList.valueOf(Color.argb(60, 120, 160, 230)), fill, shape(Color.WHITE, dp(17).toFloat()))
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> if (view.isEnabled) view.animate().scaleX(0.985f).scaleY(0.985f).setDuration(120).start()
                    MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
                false
            }
            setOnClickListener { action() }
        }

    private fun button(text: String, secondary: Boolean = false, action: () -> Unit): Button {
        val b = makeButton(text, secondary, action)
        root.addView(b, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
        return b
    }

    private fun homeActionGrid(vararg items: Pair<String, () -> Unit>) {
        val columns = if (resources.configuration.screenWidthDp >= 600) 2 else 1
        if (columns == 1) {
            items.forEach { (label, action) -> button(label, action = action) }
            return
        }
        items.toList().chunked(columns).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { (label, action) ->
                row.addView(makeButton(label, action = action), LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                })
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun expireSession() {
        sessionGeneration++
        api.clear()
        csrf = ""; student = JSONObject(); homepage = JSONObject(); subscribed = false
        busyAnswer = false; submissionUncertain = false; dailyBusy = false; dailyUncertain = false
        prefs.edit().remove("last_payment_order").apply()
        loginScreen()
        message("انتهت جلسة الدخول. سجّل الدخول مجدداً.")
    }
    private fun fetch(path: String, body: JSONObject? = null, showError: Boolean = true, done: (JSONObject) -> Unit) {
        val generation = sessionGeneration
        jobs.execute {
            val result = try { api.call(path, body) } catch (e: Exception) {
                JSONObject().put("ok", false).put("network_error", true)
                    .put("message", NetworkRules.message(e, body != null))
            }
            main.post {
                if (generation != sessionGeneration || isFinishing || isDestroyed) return@post
                if (result.optBoolean("session_expired")) { expireSession(); return@post }
                if (showError && !result.optBoolean("ok")) {
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
        val generation = sessionGeneration
        jobs.execute {
            val result = try { api.postForm(path, data) } catch (e: Exception) {
                JSONObject().put("ok", false).put("message", NetworkRules.message(e, true))
            }
            main.post {
                if (generation != sessionGeneration || isFinishing || isDestroyed) return@post
                if (result.optBoolean("session_expired")) { expireSession(); return@post }
                if (!result.optBoolean("ok")) message(result.optString("message", "تعذر تنفيذ الطلب."))
                done(result)
            }
        }
    }

    private fun loadAvatar(url: String, revision: String = "") {
        val view = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "الصورة الشخصية"
        }
        view.setImageResource(R.drawable.ic_murshid)
        root.addView(view, LinearLayout.LayoutParams(dp(88), dp(88)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(12)
        })
        fetchAvatarInto(view, url, revision)
    }

    private fun fetchAvatarInto(view: ImageView, raw: String, revision: String) {
        val url = AccountRules.avatarUrl(raw, revision + "_" + avatarGeneration) ?: return
        jobs.execute {
            val bitmap = try {
                val connection = URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 12000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
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
            main.post { if (bitmap != null && view.parent != null && !isDestroyed) view.setImageBitmap(bitmap) }
        }
    }

    private fun loginScreen() {
        activeScreen = "login"
        window.statusBarColor = if (night) Color.rgb(13, 29, 54) else navy
        window.navigationBarColor = appColor
        setupRoot(horizontalPadding = 20, topPadding = 18)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(27), dp(16), dp(27))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(15, 40, 79), Color.rgb(39, 91, 165))
            ).apply { cornerRadius = dp(28).toFloat() }
        }
        val emblem = ImageView(this).apply {
            setImageResource(R.drawable.ic_murshid)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
            }
            contentDescription = "شعار المرشد الوزاري"
        }
        header.addView(emblem, LinearLayout.LayoutParams(dp(76), dp(76)).apply {
            bottomMargin = dp(14)
        })
        header.addView(TextView(this).apply {
            text = "منصة المرشد الوزاري"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })
        header.addView(TextView(this).apply {
            text = "خطوتك الواثقة نحو التفوق الوزاري"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(218, 230, 249))
            setPadding(0, dp(9), 0, 0)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(24)
        })

        val welcome = TextView(this).apply {
            text = "أهلاً بعودتك!"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(welcome)
        caption("سجّل دخولك وأكمل رحلتك التعليمية.")
        gap(10)
        val identifier = input("البريد الإلكتروني أو رقم الهاتف")
        identifier.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val password = input("كلمة المرور", password = true)
        val settingsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val preview = CheckBox(this).apply {
            text = "إظهار كلمة المرور"
            textSize = 13f
            setTextColor(textColor)
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        preview.setOnCheckedChangeListener { _, checked ->
            password.transformationMethod = if (checked)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            password.setSelection(password.text.length)
        }
        val remember = CheckBox(this).apply {
            text = "تذكرني"
            textSize = 13f
            setTextColor(textColor)
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            isChecked = api.hasRememberedSession()
        }
        settingsRow.addView(preview, LinearLayout.LayoutParams(0, -2, 1f))
        settingsRow.addView(remember, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(settingsRow)
        gap(14)

        val submit = button("تسجيل الدخول") {}
        submit.setOnClickListener {
            val id = identifier.text.toString().trim()
            val pass = password.text.toString()
            if (id.isEmpty() || pass.isEmpty()) {
                message("أدخل البريد أو الهاتف وكلمة المرور.")
            } else {
                submit.isEnabled = false
                submit.text = "جارٍ تسجيل الدخول..."
                val data = JSONObject().put("identifier", id).put("password", pass)
                    .put("remember_me", remember.isChecked)
                fetch("api/login.php", data) { response ->
                    submit.isEnabled = true
                    submit.text = "تسجيل الدخول"
                    if (response.optBoolean("ok")) {
                        if (response.optBoolean("email_verification_required") ||
                            response.optBoolean("phone_verification_required")) {
                            verifyPrompt(response)
                        } else {
                            api.setRemember(remember.isChecked)
                            fetch("mobile/bootstrap.php") { b ->
                                if (b.optBoolean("ok")) showHome(b)
                            }
                        }
                    }
                }
            }
        }
        if (api.hasRememberedSession()) {
            button("إعادة الاتصال واستعادة جلستي", secondary = true) {
                fetch("mobile/bootstrap.php") { result ->
                    if (result.optBoolean("ok")) showHome(result)
                }
            }
        }
        gap(6)
        button("إنشاء حساب طالب جديد", secondary = true) { registerScreen() }
        button("نسيت كلمة المرور؟", secondary = true) { recoverScreen() }
        gap(12)
        root.addView(TextView(this).apply {
            text = "mur-iq.com  •  منصة تعليمية عراقية"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (night) Color.LTGRAY else Color.GRAY)
            setPadding(0, dp(10), 0, dp(8))
        })
        val privacy = TextView(this).apply {
            text = "سياسة الخصوصية"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { external("https://mur-iq.com/privacy.php") }
        }
        root.addView(privacy)
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
        if (activeScreen != "recover" && activeScreen != "phone_reset")
            recoverReturnScreen = if (activeScreen == "account") "account" else "login"
        activeScreen = "recover"
        base("استعادة كلمة المرور", showBack = true)
        line("استعد حسابك دون مغادرة التطبيق", true)
        caption("أدخل البريد أو رقم الهاتف المسجل. سيصل رمز إلى WhatsApp للهاتف، أو رابط آمن إلى البريد الإلكتروني.")
        val identifier = input("07XXXXXXXXX أو البريد الإلكتروني")
        identifier.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val submit = button("متابعة الاستعادة") {
            val value = RecoveryRules.normalizedDigits(identifier.text.toString().trim())
            if (value.isBlank()) { identifier.error = "أدخل الهاتف أو البريد."; return@button }
            if (recoveryBusy) return@button
            recoveryBusy = true
            recoveryRequest(mayHaveSubmitted = true, action = { api.beginPasswordReset(value) }) { response ->
                recoveryBusy = false
                if (activeScreen != "recover") return@recoveryRequest
                if (response.optBoolean("ok")) {
                    if (response.optString("channel") == "phone") phoneResetScreen(response.optString("token"))
                    else line(response.optString("message", "تحقق من بريدك الإلكتروني."))
                } else message(response.optString("message", "تعذر استعادة الحساب الآن."))
            }
        }
        submit.isEnabled = !recoveryBusy
        button("العودة", secondary = true) { navigateBack() }
    }

    private fun recoveryRequest(mayHaveSubmitted: Boolean, action: () -> JSONObject, done: (JSONObject) -> Unit) {
        val generation = sessionGeneration
        jobs.execute {
            val response = try { action() } catch (e: Exception) {
                JSONObject().put("ok", false).put("message", NetworkRules.message(e, mayHaveSubmitted))
            }
            main.post {
                if (generation == sessionGeneration && !isFinishing && !isDestroyed) done(response)
            }
        }
    }

    private fun phoneResetScreen(token: String) {
        if (!token.matches(Regex("[a-f0-9]{64}"))) { message("رابط استعادة الهاتف غير صالح."); recoverScreen(); return }
        activeScreen = "phone_reset"
        base("تأكيد استعادة الهاتف", true)
        line("أدخل رمز WhatsApp واختر كلمة مرور جديدة.", true)
        caption("ستحتاج إلى تسجيل الدخول مجدداً بعد نجاح التغيير.")
        val code = input("رمز WhatsApp من 6 أرقام").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val password = input("كلمة مرور جديدة (8 أحرف على الأقل)", password = true)
        val confirm = input("تأكيد كلمة المرور", password = true)
        val save = button("حفظ كلمة المرور الجديدة") {
            val otp = RecoveryRules.normalizedDigits(code.text.toString().trim())
            val pass = password.text.toString()
            val confirmation = confirm.text.toString()
            if (!RecoveryRules.validReset(otp, pass, confirmation)) {
                message("تحقق من الرمز المكوّن من 6 أرقام ومن تطابق كلمتي المرور (8 أحرف على الأقل).")
                return@button
            }
            if (recoveryBusy) return@button
            recoveryBusy = true
            recoveryRequest(mayHaveSubmitted = true,
                action = { api.resetPhonePassword(token, otp, pass, confirmation) }) { result ->
                recoveryBusy = false
                if (activeScreen != "phone_reset") return@recoveryRequest
                if (result.optBoolean("ok")) {
                    sessionGeneration++
                    csrf = ""; student = JSONObject(); homepage = JSONObject(); subscribed = false
                    message(result.optString("message", "تم تغيير كلمة المرور. سجّل الدخول من جديد."))
                    loginScreen()
                } else message(result.optString("message", "تعذر تغيير كلمة المرور."))
            }
        }
        save.isEnabled = !recoveryBusy
        button("إعادة إرسال رمز WhatsApp", secondary = true) {
            if (recoveryBusy) return@button
            recoveryBusy = true
            recoveryRequest(mayHaveSubmitted = true, action = { api.resendPhonePasswordCode(token) }) { result ->
                recoveryBusy = false
                if (activeScreen == "phone_reset")
                    message(result.optString("message", "تعذر إعادة إرسال الرمز."))
            }
        }
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

    private fun applyBootstrap(response: JSONObject) {
        homepage = response; csrf = response.optString("csrf", csrf)
        student = response.optJSONObject("user") ?: JSONObject()
        subscribed = response.optBoolean("subscribed")
    }

    private fun showHome(response: JSONObject) {
        if (!response.optBoolean("ok")) { loginScreen(); return }
        competitionHistory.clear()
        applyBootstrap(response)
        activeScreen = "home"
        base("المرشد الوزاري")
        loadAvatar(student.optString("avatar_url"), student.optString("avatar_revision"))
        line("مرحباً، " + student.optString("name", "الطالب"), true)
        caption("الصف الدراسي: " + student.optString("grade"))
        val remaining = response.optInt("free_remaining", 0)
        line(if (subscribed) "اشتراك فعّال" +
            (if (response.isNull("subscription_days_remaining")) "" else " • المتبقي " + response.optInt("subscription_days_remaining") + " يوم")
            else "الإجابات المجانية المتبقية: " + remaining)
        val stats = response.optJSONObject("stats") ?: JSONObject()
        caption("المستوى " + stats.optInt("level",1) + " • نقاط XP " + stats.optInt("xp",0) +
            " • الإجابات الصحيحة " + stats.optInt("correct_answers",0))
        homeActionGrid(
            "المواد الدراسية والاختبارات" to { subjectsScreen() },
            "تحدي اليوم • اختبار سريع" to { openCompetition("daily_challenge") },
            "تحدي المليون • المنافسة" to { openCompetition("contest") },
            "إنجازاتي • الشارات" to { openCompetition("achievements") }
        )
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
                sessionGeneration++
                api.clear(); csrf = ""; student = JSONObject(); loginScreen()
            }
        }
    }

    private fun lastPaymentKey(): String = "last_payment_order_" + student.optInt("id", 0)

    /** Mirrors the latest iOS V31 flow: plan selection, external HTTPS checkout and status polling. */
    private fun plansScreen(statusText: String = "") {
        if (activeScreen != "plans") plansReturnScreen = if (activeScreen == "account") "account" else "home"
        activeScreen = "plans"
        base("الاشتراك", true)
        line(if (subscribed) "✓ اشتراكك مفعّل" else "افتح كامل الأسئلة", true)
        caption(if (subscribed) "يمكنك الوصول إلى كامل المحتوى المتاح في صفك." else
            "اختر الباقة، ثم انتقل إلى صفحة الدفع الخارجية الآمنة.")
        if (statusText.isNotBlank()) line(statusText)
        if (subscribed) {
            line("لا تحتاج إلى إجراء أي عملية دفع الآن.")
            button("تحديث حالة الاشتراك", secondary = true) { refreshSubscriptionStatus() }
            return
        }
        val plans = homepage.optJSONArray("plans") ?: JSONArray()
        if (plans.length() == 0) {
            line("لم تصل الباقات من الخادم.")
            button("إعادة التحميل") { refreshSubscriptionStatus() }
            return
        }
        caption("اختر الباقة المناسبة:")
        val validIds = ArrayList<String>()
        for (i in 0 until plans.length()) {
            val plan = plans.optJSONObject(i) ?: continue
            val id = plan.optString("id")
            if (id.isBlank()) continue
            validIds.add(id)
        }
        if (paymentPlan !in validIds) paymentPlan = validIds.firstOrNull() ?: ""
        for (i in 0 until plans.length()) {
            val plan = plans.optJSONObject(i) ?: continue
            val id = plan.optString("id")
            if (id.isBlank()) continue
            val selected = id == paymentPlan
            val price = java.text.NumberFormat.getNumberInstance(java.util.Locale("ar", "IQ"))
                .format(plan.optLong("amount", 0))
            button((if (selected) "◉ " else "◯ ") + plan.optString("label") + " — " + price + " د.ع",
                secondary = !selected) {
                if (!paymentBusy) { paymentPlan = id; plansScreen(statusText) }
            }
        }
        val checkout = button("الانتقال إلى الدفع الخارجي") { beginCheckout() }
        checkout.isEnabled = !paymentBusy && paymentPlan.isNotBlank()
        val lastOrder = prefs.getInt(lastPaymentKey(), 0)
        if (lastOrder > 0) button("التحقق من حالة آخر عملية #$lastOrder", secondary = true) {
            checkPaymentStatus(lastOrder)
        }
        caption("بيانات الدفع الحساسة لا تمر عبر التطبيق، وسيُؤكَّد الاشتراك بعد تحقق الخادم من العملية.")
        button("لديك رمز اشتراك؟", secondary = true) { redeemScreen() }
    }

    private fun beginCheckout() {
        if (paymentBusy || subscribed || paymentPlan.isBlank()) return
        paymentBusy = true
        plansScreen("جارٍ تجهيز رابط الدفع…")
        val body = JSONObject().put("csrf", csrf).put("action", "checkout")
            .put("plan", paymentPlan)
            .put("customer_name", student.optString("name", "طالب مشترك"))
            // The live V31 iOS contract uses this server placeholder; no phone is collected in-app.
            .put("customer_phone", "07700000000")
        fetch("api/payment-checkout.php", body, showError = false) { result ->
            paymentBusy = false
            if (!result.optBoolean("ok")) {
                plansScreen(result.optString("message", "تعذر تجهيز الدفع؛ تحقق من حالة أي طلب سابق."))
                return@fetch
            }
            if (result.optBoolean("subscribed")) { refreshSubscriptionStatus(); return@fetch }
            val url = result.optString("redirect")
            val order = result.optInt("order_id")
            if (order <= 0 || !AccountRules.safePaymentRedirect(url)) {
                plansScreen("لم يُرجع الخادم رابط دفع آمن ورقم طلب صالحاً. تحقق من الطلب قبل المحاولة مجدداً.")
                return@fetch
            }
            prefs.edit().putInt(lastPaymentKey(), order).apply()
            plansScreen("تم إنشاء الطلب #$order. أكمل الدفع في المتصفح ثم تحقق من حالته.")
            try {
                checkoutReturnPending = true
                external(url)
            } catch (_: Exception) {
                checkoutReturnPending = false
                message("تعذر فتح متصفح الدفع. يمكنك التحقق من حالة الطلب لاحقاً.")
            }
        }
    }

    private fun checkPaymentStatus(order: Int) {
        if (paymentBusy || order <= 0) return
        paymentBusy = true
        plansScreen("جارٍ التحقق من العملية #$order…")
        fetch("api/payment-checkout.php", JSONObject().put("csrf", csrf)
            .put("action", "status").put("order_id", order), showError = false) { result ->
            paymentBusy = false
            if (!result.optBoolean("ok")) {
                plansScreen(result.optString("message", "تعذر التحقق من حالة الدفع حالياً."))
            } else if (result.optBoolean("subscribed")) {
                // The checkout response alone does not unlock subscriber-only features.
                refreshSubscriptionStatus(clearOrderOnSuccess = true)
            } else {
                plansScreen(result.optString("message", "الدفع قيد التحقق."))
            }
        }
    }

    private fun refreshSubscriptionStatus(clearOrderOnSuccess: Boolean = false) {
        fetch("mobile/bootstrap.php", showError = false) { result ->
            if (!result.optBoolean("ok")) {
                plansScreen("تعذر جلب حالة الاشتراك من الخادم. أعد التحديث عند عودة الاتصال.")
                return@fetch
            }
            applyBootstrap(result)
            if (subscribed && clearOrderOnSuccess) prefs.edit().remove(lastPaymentKey()).apply()
            plansScreen(if (subscribed) "أكد الخادم تفعيل اشتراكك." else "لم يؤكد الخادم التفعيل بعد.")
        }
    }

    private fun refreshHome() {
        fetch("mobile/bootstrap.php") { result -> if (result.optBoolean("ok")) showHome(result) }
    }

    private fun refreshAccountAfterMutation() {
        fetch("mobile/bootstrap.php", showError = false) { result ->
            if (result.optBoolean("ok")) applyBootstrap(result)
            else message("تم حفظ التغيير لكن تعذر تحديث حالة الحساب الآن. اسحب/حدّث عند عودة الاتصال.")
            accountScreen()
        }
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
            if (!result.optBoolean("ok") || chapterId != id) return@fetch
            csrf = result.optString("csrf", csrf)
            examQuestions = result.optJSONArray("questions") ?: JSONArray()
            submissionUncertain = false
            examIssues.clear()
            val seen = HashSet<String>()
            val seenIds = HashSet<Int>()
            for (i in 0 until examQuestions.length()) {
                val q = examQuestions.optJSONObject(i)
                if (q == null) { examIssues[i] = "بيانات السؤال غير قابلة للقراءة."; continue }
                val issue = auditQuestion(q)
                val duplicateKey = ExamRules.answerKey(q.optString("text"))
                examIssues[i] = when {
                    issue != null -> issue
                    !seenIds.add(q.optInt("id")) -> "معرّف هذا السؤال مكرر في الاختبار."
                    duplicateKey.isNotBlank() && !seen.add(duplicateKey) -> "هذا السؤال مكرر ضمن الاختبار."
                    else -> continue
                }
            }
            questionIndex = 0
            while (questionIndex < examQuestions.length() &&
                examQuestions.optJSONObject(questionIndex)?.let(::isSubmitted) == true) questionIndex++
            renderQuestion()
        }
    }

    private fun isSubmitted(question: JSONObject): Boolean =
        ExamRules.knownBoolean(question.opt("submitted")) == true

    private fun auditQuestion(q: JSONObject): String? {
        val options = q.optJSONArray("options") ?: JSONArray()
        val optionTexts = (0 until options.length()).map { i ->
            options.optJSONObject(i)?.optString("text") ?: options.optString(i)
        }
        val items = q.optJSONArray("match_items") ?: JSONArray()
        val type = q.optString("type")
        val structure = q.optJSONObject(type)
        val stages = structure?.optJSONArray("stages") ?: JSONArray()
        val stageNames = ArrayList<String>()
        val required = ArrayList<String>()
        var invalidChoice = false
        for (i in 0 until stages.length()) {
            val stage = stages.optJSONObject(i)
            if (stage == null) { invalidChoice = true; continue }
            val key = stage.optString("key")
            stageNames.add(key)
            if (stage.optString("kind") == "info" || stage.optBoolean("ungraded")) continue
            if (stage.optString("kind") == "punnett") {
                val cells = stage.optJSONArray("cells")
                if (cells == null || cells.length() == 0) invalidChoice = true
                for (j in 0 until (cells?.length() ?: 0)) required.add(key + "_" + j)
            } else {
                required.add(key)
                if (stage.optString("kind") == "choice" && (stage.optJSONArray("options")?.length() ?: 0) < 2)
                    invalidChoice = true
            }
        }
        val english = q.optJSONObject("english_match")
        val left = english?.optJSONObject("left")
        val right = english?.optJSONObject("right")
        val shape = ExamRules.Shape(
            id = q.optInt("id"), text = q.optString("text"), type = type,
            correctAnswer = q.optString("correct_answer"), options = optionTexts,
            matchIds = (0 until items.length()).map { items.optJSONObject(it)?.optInt("id") ?: 0 },
            matchWords = (0 until items.length()).map { items.optJSONObject(it)?.optString("mad_word") ?: "" },
            englishLeft = left?.keys()?.asSequence()?.map { key ->
                if (left?.optString(key).isNullOrBlank()) "" else key
            }?.toList() ?: emptyList(),
            englishRight = right?.keys()?.asSequence()?.map { key ->
                if (right?.optString(key).isNullOrBlank()) "" else key
            }?.toList() ?: emptyList(),
            stages = stageNames, requiredStageKeys = required,
            invalidStageChoices = invalidChoice,
            hasEnglishDefinition = q.has("english_match") && !q.isNull("english_match")
        )
        return ExamRules.issue(shape)
    }

    private fun renderQuestion() {
        activeScreen = "exam"
        base(chapterName, true)
        if (examQuestions.length() == 0) {
            line("لا توجد أسئلة في هذا الموضوع.")
            return
        }
        if (questionIndex >= examQuestions.length()) {
            val total = examQuestions.length()
            val submitted = (0 until total).count { index ->
                examQuestions.optJSONObject(index)?.let(::isSubmitted) == true
            }
            val correct = (0 until total).count { index ->
                val answer = examQuestions.optJSONObject(index)?.optJSONObject("answer")
                answer != null && ExamRules.knownBoolean(answer.opt("correct")) == true
            }
            val graded = (0 until total).count { index ->
                val answer = examQuestions.optJSONObject(index)?.optJSONObject("answer")
                answer != null && ExamRules.knownBoolean(answer.opt("correct")) != null
            }
            line("نتيجة الموضوع: $submitted من $total سؤالاً محلولاً، والصحيح $correct من $graded إجابات مؤكدة التصحيح.", true)
            if (submitted < total) caption("بعض الأسئلة لم تُحلّ بعد. راجعها قبل إنهاء الموضوع.")
            button("مراجعة جميع الأسئلة") { questionIndex = 0; renderQuestion() }
            button("طلب أسئلة جديدة") { questionRequest() }
            button("عودة إلى المواضيع") { topicsScreen(topicsSubjectId, topicsSubjectName) }
            return
        }
        val question = examQuestions.optJSONObject(questionIndex)
        if (question == null) {
            line("تعذر قراءة بيانات السؤال. حدّث الاختبار ثم أبلغ الإدارة إن استمر الخلل.")
            button("تحديث الأسئلة") { loadExam(chapterId, chapterName) }
            if (questionIndex > 0) button("السؤال السابق", secondary = true) { questionIndex--; renderQuestion() }
            return
        }
        val id = question.optInt("id")
        val submitted = isSubmitted(question)
        caption("السؤال " + (questionIndex+1) + " من " + examQuestions.length() + " • وزاري")
        line(question.optString("text"), true)
        if (!question.isNull("meaning_word") && question.optString("meaning_word").isNotBlank())
            caption("المفردة: " + question.optString("meaning_word"))
        val previous = question.optJSONObject("answer")
        if (submitted) {
            val correct = ExamRules.knownBoolean(previous?.opt("correct"))
            line(when (correct) { true -> "✓ إجابتك صحيحة"; false -> "إجابتك غير صحيحة"; null -> "تم حفظ إجابتك" })
            val text = previous?.optString("text")?.takeIf { it.isNotBlank() }
                ?: previous?.optString("selected_text").orEmpty()
            if (text.isNotBlank()) caption("الإجابة المسجلة: " + text)
            val correctAnswer = question.optString("correct_answer")
            caption("الإجابة النموذجية: " + correctAnswer.ifBlank { "غير متاحة من الخادم" })
            caption("الشرح: " + ExamRules.explanation(question.optString("explanation"), correctAnswer))
        } else {
            val issue = examIssues[questionIndex]
            if (issue != null) {
                line("هذا السؤال يحتاج إلى تصحيح في محتوى الخادم: $issue")
                caption("لن تُخصم محاولة مجانية لأن إرسال الإجابة معطّل لهذا السؤال.")
                button("تحديث بيانات السؤال") { loadExam(chapterId, chapterName) }
            } else when {
                question.optJSONObject("english_match") != null ->
                    englishMatchQuestion(question)
                question.optString("type") == "calculation" ->
                    stagesQuestion(question, "calculation")
                question.optString("type") == "genetics" ->
                    stagesQuestion(question, "genetics")
                question.optString("type") == "match" ->
                    tajweedQuestion(question)
                (question.optJSONArray("options")?.length() ?: 0) > 0 || question.optString("type") == "true_false" -> {
                    val serverOptions = question.optJSONArray("options") ?: JSONArray()
                    val options = if (serverOptions.length() > 0) serverOptions else JSONArray().put("صح").put("خطأ")
                    for (i in 0 until options.length()) {
                        val option = options.optJSONObject(i)
                        val chosen = option?.optString("text") ?: options.optString(i)
                        val optionId = option?.optInt("id") ?: 0
                        button(chosen, secondary = true) {
                            sendAnswer(question, chosen, optionId)
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
        if (questionIndex > 0) button("السؤال السابق", secondary = true) {
            if (!busyAnswer) { questionIndex--; renderQuestion() }
        }
        if (submitted) button(if (questionIndex == examQuestions.length() - 1) "عرض النتيجة" else "السؤال التالي") {
            val next = ExamRules.nextIndex(questionIndex, examQuestions.length(), submitted, busyAnswer, submissionUncertain)
            if (next != questionIndex) { questionIndex = next; renderQuestion() }
        }
        lateinit var favoriteButton: Button
        favoriteButton = button(if (question.optBoolean("favorite")) "إزالة من المفضلة" else "إضافة للمفضلة", secondary = true) {
            val action = if (question.optBoolean("favorite")) "unfavorite" else "favorite"
            val body = JSONObject().put("csrf", csrf).put("question_id", id).put("action", action)
            fetch("api/question-tools.php", body) { result ->
                if (result.optBoolean("ok")) {
                    question.put("favorite", result.optBoolean("favorite"))
                    favoriteButton.text = if (question.optBoolean("favorite")) "إزالة من المفضلة" else "إضافة للمفضلة"
                }
            }
        }
        button("الإبلاغ عن مشكلة في السؤال", secondary = true) { reportQuestion(id) }
        button("العودة للمواضيع", secondary = true) { topicsScreen(topicsSubjectId, topicsSubjectName) }
    }

    private fun sendAnswer(question: JSONObject, answer: String, optionId: Int) {
        if (busyAnswer) return
        if (isSubmitted(question)) { message("تم تسجيل إجابة هذا السؤال بالفعل."); return }
        if (examIssues[questionIndex] != null) { message("يجب إصلاح بيانات السؤال قبل إرسال إجابته."); return }
        if (answer.isBlank()) { message("اكتب الإجابة أولاً."); return }
        if (csrf.isEmpty()) { message("انتهت الجلسة، حدّث الصفحة."); return }
        if (submissionUncertain) {
            message("تحقق من حالة الإجابة السابقة قبل إرسال إجابة جديدة.")
            return
        }
        busyAnswer = true
        val id = question.optInt("id")
        val sentIndex = questionIndex
        val sentChapterId = chapterId
        val stored = JSONObject().put("text", answer).put("selected_text", answer).put("submitted", true)
        if (optionId > 0) stored.put("selected", optionId)
        val all = JSONObject().put(id.toString(), stored)
        val data = JSONObject().put("csrf", csrf).put("chapter_id", chapterId)
            .put("question_index", sentIndex + 1).put("answers", all)
            .put("completed", sentIndex == examQuestions.length() - 1)
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
            val savedAnswer = JSONObject().put("text", answer).put("selected_text", answer)
            ExamRules.knownBoolean(grade.opt("is_correct"))?.let { savedAnswer.put("correct", it) }
            question.put("answer", savedAnswer)
            val explanation = grade.optString("explanation").ifBlank { question.optString("explanation") }
            val correctAnswer = grade.optString("correct_answer").ifBlank { question.optString("correct_answer") }
            question.put("explanation", explanation)
            question.put("correct_answer", correctAnswer)
            if (activeScreen == "exam" && sentChapterId == chapterId && questionIndex == sentIndex) {
                renderQuestion()
                val feedback = when (ExamRules.knownBoolean(grade.opt("is_correct"))) {
                    true -> "إجابة صحيحة ✓"
                    false -> "راجع إجابتك"
                    null -> "تم حفظ الإجابة"
                }
                AlertDialog.Builder(this).setTitle(feedback)
                    .setMessage("الإجابة النموذجية: " + correctAnswer.ifBlank { "غير متاحة" } +
                        "\n\n" + ExamRules.explanation(explanation, correctAnswer))
                    .setPositiveButton("حسناً", null).show()
            }
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
            if (stage.optString("kind") == "info" || stage.optBoolean("ungraded")) {
                val info = stage.optString("value")
                if (info.isNotBlank()) line(info)
                continue
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
        val rightKeys = right.keys().asSequence().toList().sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        choices.add("اختر الوصل"); keys.add("")
        for (key in rightKeys) {
            keys.add(key); choices.add(key + " : " + right.optString(key))
        }
        val pairFields = ArrayList<Pair<String, Spinner>>()
        val leftKeys = left.keys().asSequence().toList().sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        for (key in leftKeys) {
            caption(key + " : " + left.optString(key))
            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices)
            root.addView(spinner)
            pairFields.add(key to spinner)
        }
        button("تسليم التوصيلات") {
            val lines = ArrayList<String>()
            val used = HashSet<String>()
            for ((key, spinner) in pairFields) {
                val selected = keys.getOrNull(spinner.selectedItemPosition).orEmpty()
                if (selected.isBlank()) { message("أكمل جميع التوصيلات أولاً."); return@button }
                if (!used.add(selected)) { message("لا يمكن وصل خيار واحد بأكثر من عبارة."); return@button }
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 301 || resultCode != RESULT_OK) return
        val selected = data?.data ?: return
        val mime = contentResolver.getType(selected)
        if (mime?.startsWith("image/") != true) {
            message("اختر ملف صورة صالحاً.")
            return
        }
        message("جارٍ تجهيز الصورة ورفعها…")
        val generation = sessionGeneration
        jobs.execute {
            val bytes = prepareAvatarJpeg(selected)
            val preview = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            val response = if (bytes == null)
                JSONObject().put("ok", false).put("message", "تعذر معالجة الصورة. اختر صورة أخرى بجودة مناسبة.")
            else try { api.uploadAvatar(csrf, "avatar.jpg", "image/jpeg", bytes) }
            catch (e: Exception) { JSONObject().put("ok", false).put("network_error", true)
                .put("message", NetworkRules.message(e, true)) }
            main.post {
                if (generation != sessionGeneration || isFinishing || isDestroyed) return@post
                if (response.optBoolean("session_expired")) { expireSession(); return@post }
                if (response.optBoolean("ok")) {
                    avatarGeneration++
                    preview?.let { activeAvatar?.get()?.setImageBitmap(it) }
                    message(response.optString("message", "تم تحديث الصورة الشخصية."))
                    refreshAccountAfterMutation()
                } else {
                    message(response.optString("message", "تعذر رفع الصورة."))
                    if (response.optBoolean("network_error")) accountScreen()
                }
            }
        }
    }

    /** iOS converts PhotosPicker images to bounded JPEG; Android handles JPEG, PNG and decodable HEIC. */
    private fun prepareAvatarJpeg(uri: Uri): ByteArray? {
        return try {
        val original = contentResolver.openInputStream(uri)?.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var size = 0
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                size += n
                if (size > 16_000_000) return null
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: return null
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, dimensions)
        if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0) return null
        var sample = 1
        while (maxOf(dimensions.outWidth, dimensions.outHeight) / sample > 1440) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val orientation = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { preScale(-1f, 1f); postRotate(90f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { preScale(-1f, 1f); postRotate(270f) }
            }
        }
        val corrected = if (matrix.isIdentity) bitmap else
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val maxDimension = maxOf(corrected.width, corrected.height)
        val scaled = if (maxDimension <= 1440) corrected else {
            val ratio = 1440f / maxDimension
            Bitmap.createScaledBitmap(corrected, (corrected.width * ratio).toInt().coerceAtLeast(1),
                (corrected.height * ratio).toInt().coerceAtLeast(1), true)
        }
        val output = java.io.ByteArrayOutputStream()
        for (quality in intArrayOf(88, 76, 64)) {
            output.reset()
            if (scaled.compress(Bitmap.CompressFormat.JPEG, quality, output) && output.size() <= 3_000_000)
                return output.toByteArray()
        }
        null
        } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
    }

    private fun accountProfileCard(profile: JSONObject, account: JSONObject) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TR_BL,
                intArrayOf(navy, blue)).apply { cornerRadius = dp(28).toFloat() }
            elevation = dp(4).toFloat()
        }
        val avatarColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }
        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_murshid)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(2), Color.argb(140,255,255,255))
            }
            clipToOutline = true
            contentDescription = "الصورة الشخصية الحالية"
        }
        avatarColumn.addView(avatar, LinearLayout.LayoutParams(dp(78), dp(78)))
        activeAvatar = java.lang.ref.WeakReference(avatar)
        fetchAvatarInto(avatar, account.optString("avatar_url"), account.optString("avatar_revision"))
        val photoChange = TextView(this).apply {
            text = "📷 تغيير الصورة"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(7), dp(2), dp(3))
            setOnClickListener { chooseAvatar() }
            contentDescription = "اختيار صورة شخصية جديدة"
        }
        avatarColumn.addView(photoChange)
        card.addView(avatarColumn)
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(4), 0)
        }
        fun detail(value: String, size: Float, strong: Boolean, opacity: Int = 255) {
            details.addView(TextView(this).apply {
                text = value; textSize = size
                setTextColor(Color.argb(opacity, 255,255,255))
                if (strong) typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(3), 0, dp(3))
            })
        }
        detail(profile.optString("name", student.optString("name", "طالب")), 20f, true)
        detail(profile.optString("grade", student.optString("grade")), 14f, false, 210)
        detail(if (subscribed) "✓ اشتراك فعّال" else "🎁 ${homepage.optInt("free_remaining", 0)} سؤال مجاني متبقٍ", 13f, true)
        card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
    }

    private fun chooseAvatar() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "اختر صورة شخصية"), 301)
    }

    private fun promptRename(current: String) {
        val field = EditText(this).apply {
            setSingleLine(true); setText(current); setSelection(text.length)
            hint = "الاسم الثلاثي"
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this).setTitle("تغيير الاسم")
            .setMessage("يمكن تغيير الاسم مرة واحدة فقط حسب إعدادات المنصة.")
            .setView(field).setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val normalized = AccountRules.normalizeName(field.text.toString())
                if (!AccountRules.validFullName(normalized)) {
                    field.error = "اكتب الاسم الثلاثي على الأقل، دون أرقام."; return@setOnClickListener
                }
                val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                save.isEnabled = false
                fetch("mobile/account.php", JSONObject().put("csrf", csrf).put("action", "change_name")
                    .put("name", normalized)) { result ->
                    save.isEnabled = true
                    if (result.optBoolean("ok")) {
                        student.put("name", normalized)
                        dialog.dismiss()
                        message("تم تحديث الاسم.")
                        refreshAccountAfterMutation()
                    } else if (result.optBoolean("network_error")) {
                        dialog.dismiss()
                        accountScreen() // Re-read the one-time rename state before allowing another attempt.
                    }
                }
            }
        }
        dialog.show()
    }

    private fun accountScreen() {
        activeScreen = "account"
        base("حسابي", true)
        line("جارٍ تحديث بيانات الحساب…")
        button("إعادة التحميل", secondary = true) { accountScreen() }
        fetch("mobile/account.php") { response ->
            if (!response.optBoolean("ok")) return@fetch
            base("حسابي", true)
            val profile = response.optJSONObject("user") ?: JSONObject()
            accountProfileCard(profile, response)
            caption("معرّف الطالب: " + profile.optInt("id"))
            caption(if (response.optString("avatar_status") == "approved") "الصورة مفعّلة" else "يمكن تغيير الصورة مباشرة")
            button("تغيير الصورة الشخصية") { chooseAvatar() }
            button("حذف الصورة الشخصية", secondary = true) {
                AlertDialog.Builder(this).setTitle("حذف الصورة")
                    .setMessage("هل تريد إزالة صورتك الشخصية؟")
                    .setPositiveButton("نعم") { _, _ ->
                        fetchForm("mobile/avatar.php", mapOf("csrf" to csrf, "remove" to "1")) { result ->
                            if (result.optBoolean("ok")) { avatarGeneration++; refreshAccountAfterMutation() }
                        }
                    }.setNegativeButton("إلغاء", null).show()
            }
            val days = response.optInt("subscription_days_remaining", -1)
            if (days >= 0) line("الأيام المتبقية من الاشتراك: " + days)
            button(if (subscribed) "إدارة الاشتراك الفعّال" else "فتح الاشتراك الكامل") { plansScreen() }
            button("تحديث حالة الاشتراك", secondary = true) { refreshAccountAfterMutation() }
            if (response.optBoolean("can_change_name")) {
                button("تغيير الاسم الثلاثي (مرة واحدة)", secondary = true) { promptRename(profile.optString("name")) }
            }
            button("استعادة / تغيير كلمة المرور", secondary = true) { recoverScreen() }
            button("إدارة خصوصية الحساب", secondary = true) { deletionScreen(response) }
            button("سياسة الخصوصية", secondary = true) { external("https://mur-iq.com/privacy.php") }
        }
    }

    private fun deletionScreen(response: JSONObject) {
        base("خصوصية الحساب", true)
        caption("يمكنك تقديم طلب حذف الحساب أو إلغاء طلب سابق.")
        val deletion = response.optJSONObject("deletion") ?: JSONObject()
        val status = deletion.optString("status")
        if (status == "pending" || status == "requested" || deletion.optBoolean("pending")) {
            line("يوجد طلب حذف حساب قيد المعالجة.", true)
            button("إلغاء طلب حذف الحساب", secondary = true) {
                AlertDialog.Builder(this).setTitle("إلغاء طلب الحذف")
                    .setMessage("هل تريد إلغاء طلب حذف الحساب الحالي؟")
                    .setPositiveButton("تأكيد") { _, _ ->
                        fetch("mobile/account.php", JSONObject().put("csrf", csrf)
                            .put("action", "cancel_delete")) { result ->
                            if (result.optBoolean("ok")) accountScreen()
                        }
                    }.setNegativeButton("رجوع", null).show()
            }
        } else {
            line("حذف الحساب قد يؤدي إلى فقدان بياناتك بشكل دائم.")
            val confirmation = input("اكتب: حذف حسابي")
            button("تقديم طلب حذف الحساب", secondary = true) {
                if (confirmation.text.toString().trim() != "حذف حسابي") {
                    message("اكتب عبارة التأكيد كما هي.")
                } else {
                    AlertDialog.Builder(this).setTitle("التأكيد النهائي")
                        .setMessage("هل تريد فعلاً تقديم طلب حذف الحساب؟")
                        .setPositiveButton("تأكيد الطلب") { _, _ ->
                            fetch("mobile/account.php", JSONObject().put("csrf", csrf)
                                .put("action", "request_delete")
                                .put("confirmation", "حذف حسابي")) { result ->
                                if (result.optBoolean("ok")) accountScreen()
                            }
                        }.setNegativeButton("إلغاء", null).show()
                }
            }
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

    // The native equivalents of iOS Daily Challenge, Million Contest and Achievements.
    // The server alone grades challenge answers and awards XP/badges.
    private fun openCompetition(screen: String) {
        if (activeScreen != screen) competitionHistory.addLast(activeScreen)
        when (screen) {
            "daily_challenge" -> loadDailyChallenge()
            "contest" -> loadContest()
            "achievements" -> loadAchievements()
        }
    }

    private fun parseDailyQuestions(rows: JSONArray): List<CompetitionRules.Question> =
        (0 until rows.length()).map { i ->
            // Never silently skip a malformed row: it could change the submitted set.
            val row = rows.optJSONObject(i)
                ?: return@map CompetitionRules.Question(0, "invalid", "", emptyList())
            val options = row.optJSONArray("options") ?: JSONArray()
            CompetitionRules.Question(
                id = row.optInt("id"),
                type = row.optString("type", "text"),
                text = row.optString("question_text").ifBlank { row.optString("text") },
                options = (0 until options.length()).map { j ->
                    val option = options.optJSONObject(j)
                    if (option != null) option.optString("answer_text").ifBlank { option.optString("text") }
                    else options.optString(j)
                }
            )
        }

    private fun loadDailyChallenge() {
        activeScreen = "daily_challenge"
        base("تحدي اليوم", true)
        caption("جارٍ تحميل أسئلة التحدي ونتيجتك من الخادم…")
        fetch("mobile/challenge.php") { response ->
            if (activeScreen != "daily_challenge" || !response.optBoolean("ok")) return@fetch
            csrf = response.optString("csrf", csrf)
            dailyAvailable = ExamRules.knownBoolean(response.opt("available")) ?: true
            dailyDone = ExamRules.knownBoolean(response.opt("done")) == true
            dailyQuotaBlocked = response.optBoolean("quota_blocked") || response.optBoolean("payment_required")
            dailyScore = response.optInt("score")
            dailyTotal = response.optInt("total")
            dailyReward = response.optInt("reward")
            dailyQuestions = parseDailyQuestions(response.optJSONArray("questions") ?: JSONArray())
            dailyAnswers.keys.retainAll(dailyQuestions.map { it.id }.toSet())
            dailyUncertain = false // A successful GET reconciles any uncertain POST.
            dailyStatusMessage = ""
            renderDailyChallenge()
        }
    }

    private fun renderDailyChallenge() {
        activeScreen = "daily_challenge"
        base("تحدي اليوم", true)
        line(if (dailyDone) "أكملت تحدي اليوم!" else "خمس دقائق تصنع فرقاً", true)
        caption("أجب عن جميع أسئلة صفك ثم سلّم التحدي مرة واحدة. الخادم يحسب النتيجة والنقاط.")
        if (!dailyAvailable) {
            line("لا يوجد تحدٍ متاح الآن.")
            button("تحديث التحدي", secondary = true) { loadDailyChallenge() }
            return
        }
        if (dailyDone) {
            line("نتيجتك: $dailyScore / $dailyTotal", true)
            if (dailyReward > 0) line("المكافأة الممنوحة: +$dailyReward XP")
            caption("تظهر مكافأتك وإنجازاتك بعد تحديث البيانات من الخادم.")
            button("عرض ترتيبي في تحدي المليون") { openCompetition("contest") }
            button("متابعة إنجازاتي") { openCompetition("achievements") }
            button("تحديث النتيجة", secondary = true) { loadDailyChallenge() }
            return
        }
        if (dailyQuotaBlocked) {
            line("انتهى رصيدك المجاني للتحدي. لن يُعاد إرسال أي إجابة قبل تفعيل الاشتراك.")
            button("تفعيل رمز اشتراك") { redeemScreen() }
            button("إعادة التحقق من الرصيد", secondary = true) { loadDailyChallenge() }
            return
        }
        val issue = CompetitionRules.issue(dailyQuestions)
        if (issue != null) {
            line("لا يمكن تقديم تحدٍ غير مكتمل: $issue")
            button("إعادة تحميل الأسئلة") { loadDailyChallenge() }
            button("مراسلة الدعم", secondary = true) { supportScreen() }
            return
        }
        if (dailyUncertain) {
            line("تعذر التأكد من حفظ التحدي. قد تكون الإجابات وصلت رغم انقطاع الاتصال.")
            caption("تحقق من الخادم أولاً؛ لن تُرسل إجاباتك مرة ثانية تلقائياً.")
            button("التحقق من حالة التحدي") { loadDailyChallenge() }
            return
        }
        for ((index, question) in dailyQuestions.withIndex()) {
            caption("السؤال ${index + 1} من ${dailyQuestions.size}")
            line(question.text, true)
            val choices = question.choices()
            if (choices.isNotEmpty()) {
                val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
                val ids = HashMap<Int, String>()
                for (choice in choices) {
                    val radio = RadioButton(this).apply {
                        id = View.generateViewId()
                        text = choice
                        textSize = 16f
                        setTextColor(textColor)
                        isChecked = dailyAnswers[question.id] == choice
                        setPadding(dp(4), dp(5), dp(4), dp(5))
                    }
                    ids[radio.id] = choice
                    group.addView(radio)
                }
                group.setOnCheckedChangeListener { _, selected ->
                    ids[selected]?.let { dailyAnswers[question.id] = it }
                }
                root.addView(group)
            } else {
                val field = input("اكتب إجابتك هنا", multiline = true)
                field.setText(dailyAnswers[question.id].orEmpty())
                field.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        dailyAnswers[question.id] = s?.toString().orEmpty()
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
            }
            gap(8)
        }
        val submit = button(if (dailyBusy) "جارٍ التصحيح…" else "إنهاء تحدي اليوم") {
            if (!CompetitionRules.canSubmit(dailyQuestions, dailyAnswers, dailyAvailable, dailyDone,
                    dailyBusy, dailyUncertain)) {
                message("أكمل كل أسئلة التحدي قبل التسليم.")
            } else {
                AlertDialog.Builder(this).setTitle("تسليم تحدي اليوم")
                    .setMessage("سيتم إرسال إجابات جميع الأسئلة دفعة واحدة للتصحيح. هل أنت مستعد؟")
                    .setPositiveButton("إرسال وتصحيح") { _, _ -> submitDailyChallenge() }
                    .setNegativeButton("إلغاء", null).show()
            }
        }
        submit.isEnabled = !dailyBusy
        if (dailyStatusMessage.isNotBlank()) caption(dailyStatusMessage)
        button("تحديث التحدي", secondary = true) { if (!dailyBusy) loadDailyChallenge() }
    }

    private fun submitDailyChallenge() {
        if (dailyQuotaBlocked) return
        if (!CompetitionRules.canSubmit(dailyQuestions, dailyAnswers, dailyAvailable, dailyDone,
                dailyBusy, dailyUncertain)) return
        if (csrf.isBlank()) { message("انتهت الجلسة. حدّث التحدي أولاً."); return }
        dailyBusy = true
        val answerObject = JSONObject()
        CompetitionRules.submission(dailyQuestions, dailyAnswers).forEach { (id, answer) ->
            answerObject.put(id, answer)
        }
        val payload = JSONObject().put("csrf", csrf).put("answers", answerObject)
        renderDailyChallenge()
        fetch("mobile/challenge.php", payload, showError = true) { response ->
            dailyBusy = false
            if (response.optBoolean("quota_blocked") || response.optBoolean("payment_required")) {
                dailyStatusMessage = "انتهى رصيدك المجاني أثناء التحدي. يمكنك تفعيل الاشتراك والمتابعة."
                dailyUncertain = false
                dailyQuotaBlocked = true
                if (activeScreen == "daily_challenge") renderDailyChallenge()
                return@fetch
            }
            if (!response.optBoolean("ok")) {
                dailyUncertain = true
                if (activeScreen == "daily_challenge") renderDailyChallenge()
                return@fetch
            }
            dailyDone = ExamRules.knownBoolean(response.opt("done")) == true
            dailyScore = response.optInt("score", dailyScore)
            dailyTotal = response.optInt("total", dailyTotal)
            dailyReward = response.optInt("reward", dailyReward)
            if (!dailyDone) {
                // An accepted POST without a completion receipt must be reconciled.
                dailyUncertain = true
                dailyStatusMessage = "استجاب الخادم دون تأكيد إنهاء التحدي. تحقق من حالته."
            } else {
                dailyStatusMessage = ""
                fetch("mobile/challenge.php", showError = false) { latest ->
                    if (latest.optBoolean("ok") && ExamRules.knownBoolean(latest.opt("done")) == true) {
                        dailyScore = latest.optInt("score", dailyScore)
                        dailyTotal = latest.optInt("total", dailyTotal)
                        dailyReward = latest.optInt("reward", dailyReward)
                        if (activeScreen == "daily_challenge") renderDailyChallenge()
                    }
                }
                fetch("mobile/bootstrap.php", showError = false) { updated ->
                    if (updated.optBoolean("ok")) {
                        homepage = updated
                        student = updated.optJSONObject("user") ?: student
                        csrf = updated.optString("csrf", csrf)
                        subscribed = updated.optBoolean("subscribed", subscribed)
                    }
                }
            }
            if (activeScreen == "daily_challenge") renderDailyChallenge()
        }
    }

    private fun loadContest() {
        activeScreen = "contest"
        base("تحدي المليون", true)
        caption("جارٍ تحميل ترتيبك والمتصدرين…")
        fetch("mobile/contest.php") { first ->
            if (activeScreen != "contest" || !first.optBoolean("ok")) return@fetch
            contest = first
            fetch("mobile/season.php", showError = false) { second ->
                if (activeScreen != "contest") return@fetch
                season = if (second.optBoolean("ok")) second else JSONObject()
                renderContest()
            }
        }
    }

    private fun renderContest() {
        activeScreen = "contest"
        base("تحدي المليون", true)
        caption("الترتيب يُحتسب في الخادم من نقاط XP والإجابات الصحيحة ضمن فترة التحدي.")
        val mine = contest.optJSONObject("mine")
        val active = ExamRules.knownBoolean(contest.optJSONObject("config")?.opt("active"))
        line(when (active) { true -> "حالة المنافسة: نشطة"; false -> "حالة المنافسة: غير نشطة حالياً"; null -> "حالة المنافسة: غير محددة" })
        if (mine == null) line("أجب عن أسئلة المنصة لتدخل الترتيب.") else {
            line("مركزك: #${mine.optInt("rank")}", true)
            caption("${mine.optInt("xp")} XP • ${mine.optInt("correct")} إجابة صحيحة")
        }
        button(if (contestMode == "contest") "● تحدي المليون" else "تحدي المليون") {
            contestMode = "contest"; contestShowAll = false; renderContest()
        }
        button(if (contestMode == "season") "● ترتيب الموسم" else "ترتيب الموسم", secondary = true) {
            contestMode = "season"; contestShowAll = false; renderContest()
        }
        val data = if (contestMode == "contest") contest else season
        val ranking = data.optJSONArray("leaderboard") ?: JSONArray()
        line(if (contestMode == "contest") "متصدرو تحدي المليون" else "متصدرو الموسم", true)
        if (contestMode == "season" && season.length() == 0)
            caption("تعذر تحميل ترتيب الموسم الآن. حدّث الصفحة لاحقاً.")
        if (ranking.length() == 0) caption("لم تُنشر مراكز لهذا الترتيب حتى الآن.")
        val limit = if (contestShowAll) ranking.length() else minOf(ranking.length(), 10)
        for (i in 0 until limit) {
            val row = ranking.optJSONObject(i) ?: continue
            val rank = row.optInt("rank")
            val name = row.optString("name", "طالب")
            val xp = row.optInt("xp")
            button("#$rank • $name • $xp XP", secondary = true) {
                AlertDialog.Builder(this).setTitle("المركز #$rank — $name")
                    .setMessage(buildString {
                        if (row.optString("grade").isNotBlank()) append("الصف: ${row.optString("grade")}\n")
                        append("النقاط: $xp XP")
                        if (row.has("correct")) append("\nالإجابات الصحيحة: ${row.optInt("correct")}")
                    })
                    .setPositiveButton("حسناً", null).show()
            }
        }
        if (ranking.length() > 10) button(if (contestShowAll) "عرض أول 10 مراكز" else "عرض جميع المراكز", secondary = true) {
            contestShowAll = !contestShowAll; renderContest()
        }
        button("شارك في تحدي اليوم") { openCompetition("daily_challenge") }
        button("اجمع نقاطاً بحل الأسئلة") { subjectsScreen() }
        button("تابع إنجازاتك") { openCompetition("achievements") }
        button("تحديث الترتيب", secondary = true) { loadContest() }
    }

    private fun loadAchievements() {
        activeScreen = "achievements"
        base("الإنجازات", true)
        caption("جارٍ تحميل المستوى والشارات من حسابك…")
        fetch("mobile/achievements.php") { response ->
            if (activeScreen != "achievements" || !response.optBoolean("ok")) return@fetch
            achievements = response.optJSONArray("achievements") ?: JSONArray()
            achievementStats = response.optJSONObject("stats") ?: JSONObject()
            renderAchievements()
        }
    }

    private fun isEarned(item: JSONObject): Boolean =
        !item.isNull("earned_at") && item.optString("earned_at").isNotBlank()

    private fun renderAchievements() {
        activeScreen = "achievements"
        base("إنجازاتي", true)
        val earned = (0 until achievements.length()).count { i ->
            achievements.optJSONObject(i)?.let(::isEarned) == true
        }
        line("المستوى ${achievementStats.optInt("level", 1)} • ${achievementStats.optInt("xp")} XP", true)
        caption("الشارات المكتسبة: $earned من ${achievements.length()}")
        button(if (achievementFilter == "all") "● جميع الشارات" else "جميع الشارات", secondary = true) {
            achievementFilter = "all"; renderAchievements()
        }
        button(if (achievementFilter == "earned") "● المكتسبة" else "المكتسبة", secondary = true) {
            achievementFilter = "earned"; renderAchievements()
        }
        button(if (achievementFilter == "locked") "● غير المكتسبة" else "غير المكتسبة", secondary = true) {
            achievementFilter = "locked"; renderAchievements()
        }
        var shown = 0
        for (i in 0 until achievements.length()) {
            val item = achievements.optJSONObject(i) ?: continue
            val unlocked = isEarned(item)
            if (achievementFilter == "earned" && !unlocked || achievementFilter == "locked" && unlocked) continue
            shown++
            val name = item.optString("title", "إنجاز")
            val description = item.optString("description")
            button((if (unlocked) "🏆 " else "🔒 ") + name, secondary = !unlocked) {
                AlertDialog.Builder(this).setTitle(name)
                    .setMessage(buildString {
                        append(description.ifBlank { "لم تُضف تفاصيل لهذا الإنجاز." })
                        append("\n\n")
                        append(if (unlocked) "مكتسب في: ${item.optString("earned_at")}" else "لم تحصل عليه بعد.")
                        if (item.has("target") && item.optInt("target") > 0 && item.has("progress"))
                            append("\nالتقدم: ${item.optInt("progress")} / ${item.optInt("target")}")
                    })
                    .setPositiveButton("إغلاق", null)
                    .setNeutralButton(if (unlocked) "عرض الترتيب" else "ابدأ التحدي") { _, _ ->
                        openCompetition(if (unlocked) "contest" else "daily_challenge")
                    }.show()
            }
            if (description.isNotBlank()) caption(description)
        }
        if (shown == 0) caption(if (achievements.length() == 0) "لم يرسل الخادم إنجازات بعد." else "لا توجد شارات ضمن هذا التصنيف.")
        button("تحدي اليوم • حقق إنجازاً جديداً") { openCompetition("daily_challenge") }
        button("أكمل اختباراتك") { subjectsScreen() }
        button("تحديث الإنجازات", secondary = true) { loadAchievements() }
    }

    private fun navigateBack() {
        if (activeScreen == "plans" && paymentBusy) {
            message("انتظر اكتمال التحقق من طلب الدفع حتى لا تضيع حالته.")
            return
        }
        when (activeScreen) {
            "phone_reset" -> recoverScreen()
            "recover" -> if (recoverReturnScreen == "account") accountScreen() else loginScreen()
            "daily_challenge", "contest", "achievements" -> {
                val previous = if (competitionHistory.isEmpty()) "home" else competitionHistory.removeLast()
                when (previous) {
                    "daily_challenge" -> loadDailyChallenge()
                    "contest" -> loadContest()
                    "achievements" -> loadAchievements()
                    else -> showHome(homepage)
                }
            }
            "exam" -> topicsScreen(topicsSubjectId, topicsSubjectName)
            "topics" -> subjectsScreen()
            "subjects" -> showHome(homepage)
            "account" -> showHome(homepage)
            "plans" -> if (plansReturnScreen == "account") accountScreen() else showHome(homepage)
            "login" -> finish()
            else -> if (homepage.optBoolean("ok")) showHome(homepage) else loginScreen()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { navigateBack() }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Keep an active exam and its unsent answers intact on phone/tablet rotation.
        screenFrame?.getChildAt(0)?.let { content ->
            content.layoutParams = (content.layoutParams as FrameLayout.LayoutParams).apply {
                width = contentWidth()
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        }
        // Only idle screens reflow their action grid; active exams/forms keep entered state.
        if (activeScreen == "home" && homepage.optBoolean("ok")) showHome(homepage)
        else if (activeScreen == "plans") plansScreen()
    }

    private fun inspectInternet() {
        val manager = connectivity ?: return
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        main.post {
            val old = lastValidatedInternet
            lastValidatedInternet = validated
            if (old == true && !validated) message("انقطع اتصال الإنترنت. ستُحفظ الإجابات غير المرسلة في هذه الشاشة.")
            if (old == false && validated) message("عاد اتصال الإنترنت. يمكنك إعادة تحميل البيانات؛ لن تُعاد طلبات الحفظ تلقائياً.")
        }
    }

    override fun onStart() {
        super.onStart()
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivity = manager
        if (networkCallback == null) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { inspectInternet() }
                override fun onLost(network: Network) { inspectInternet() }
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { inspectInternet() }
            }
            manager.registerDefaultNetworkCallback(networkCallback!!)
        }
        inspectInternet()
    }

    override fun onResume() {
        super.onResume()
        if (checkoutReturnPending && activeScreen == "plans") {
            checkoutReturnPending = false
            val order = prefs.getInt(lastPaymentKey(), 0)
            if (order > 0) checkPaymentStatus(order)
        }
    }

    override fun onStop() {
        networkCallback?.let { callback ->
            try { connectivity?.unregisterNetworkCallback(callback) } catch (_: Exception) { }
        }
        networkCallback = null
        super.onStop()
    }

    override fun onDestroy() {
        jobs.shutdownNow()
        super.onDestroy()
    }
}
