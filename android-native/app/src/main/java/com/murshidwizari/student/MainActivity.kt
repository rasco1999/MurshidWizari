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
    private var topicsReturnScreen = "home"
    private var chapterId = 0
    private var chapterName = ""
    private var examFromSearch = false
    private var examSearchTarget = ""
    private var lastSearchQuery = ""
    private var lastSearchResults = JSONArray()
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
    private var homeInsights = JSONObject()
    private var homeWeekly = JSONObject()
    private var homeInsightsLoaded = false
    private var homeMetricHolder: LinearLayout? = null
    private var homeNextStepHolder: LinearLayout? = null
    private var homeHeroIndex = 0
    private var homeHeroTicker: Runnable? = null
    private var homeHeroView: ImageView? = null
    private var homeHeroContainer: FrameLayout? = null
    private var homeHeroFallback: TextView? = null
    private var homeHeroDots: LinearLayout? = null
    private val heroBitmaps = HashMap<Int, Bitmap>()
    private val heroPending = HashSet<Int>()
    private val heroJobs = Executors.newFixedThreadPool(2)
    private var lastValidatedInternet: Boolean? = null
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var sessionGeneration = 0
    private var screenGeneration = 0
    private var avatarGeneration = 0
    private var activeAvatar: java.lang.ref.WeakReference<ImageView>? = null
    private var paymentBusy = false
    private var paymentPlan = ""
    private var plansReturnScreen = "home"
    private var checkoutReturnPending = false
    private var recoveryBusy = false
    private var recoverReturnScreen = "login"
    private var pendingVerificationRemember = false
    private var focusMinutes = 25
    private var focusBusy = false
    private var focusDeadline = 0L
    private var focusTicker: Runnable? = null
    private var practiceSubjectId = 0
    private var practiceSubjectName = ""
    private var practiceTopics = JSONArray()
    private val practiceSelectedTopics = linkedSetOf<Int>()
    private var practiceCount = 20
    private var practiceType = "all"
    private var practiceState = "all"
    private var practiceMinutes = 0
    private var practiceSessionId = 0
    private var practiceQuestions = JSONArray()
    private val practiceAnswers = linkedMapOf<Int, String>()
    private var practiceCurrent = 0
    private var practiceDeadline = 0L
    private var practiceBusy = false
    private var practiceUncertain = false
    private var practiceTicker: Runnable? = null
    private val prefs by lazy { getSharedPreferences("native_preferences", MODE_PRIVATE) }
    // Identical brand palette to the SwiftUI ThemeV34 definitions.
    private val gold = Color.rgb(245,184,61)
    private val navy = Color.rgb(11,31,51)
    private val blue = Color.rgb(45,108,223)
    // UIKit's systemGroupedBackground / secondarySystemGroupedBackground light and dark tokens.
    private val appColor get() = if (night) Color.BLACK else Color.rgb(242,242,247)
    private val textColor get() = if (night) Color.WHITE else Color.rgb(28,28,30)
    private val surface get() = if (night) Color.rgb(28,28,30) else Color.WHITE
    private val accent get() = blue
    private val mutedText get() = if (night) Color.rgb(174,174,178) else Color.rgb(110,110,115)

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
    private fun contentWidth(): Int = VisualRules.contentWidthDp(resources.configuration.screenWidthDp)?.let(::dp) ?: -1
    private fun setupRoot(horizontalPadding: Int = 16, topPadding: Int = 16, tab: String? = null) {
        screenGeneration++
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(appColor); clipToPadding = false }
        if (Build.VERSION.SDK_INT >= 35 && tab == null) {
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
        if (tab == null) {
            setContentView(scroll)
        } else {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(appColor)
            }
            container.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            container.addView(mainTabBar(tab), LinearLayout.LayoutParams(-1, dp(58)))
            if (Build.VERSION.SDK_INT >= 35) {
                container.setOnApplyWindowInsetsListener { view, insets ->
                    val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                    insets
                }
            }
            setContentView(container)
        }
    }
    private fun base(title: String, showBack: Boolean = false, tab: String? = null) {
        stopHomeHeroTicker()
        window.statusBarColor = navy
        window.navigationBarColor = appColor
        setupRoot(topPadding = if (tab == null) 16 else 0, tab = tab)
        when {
            tab != null -> tabHeader(title, tab)
            showBack -> backHeader(title)
            else -> title(title)
        }
    }
    private fun backHeader(title: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            minimumHeight = dp(48)
        }
        val back = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            addView(IosGlyphView(this@MainActivity, "chevron", blue), LinearLayout.LayoutParams(dp(19), dp(19)))
            addView(uiText("رجوع", 15f, tint = blue))
        }
        row.addView(tappable(back, "رجوع") { navigateBack() }, LinearLayout.LayoutParams(dp(72), dp(44)))
        row.addView(uiText(title, 18f, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Space(this), LinearLayout.LayoutParams(dp(72), dp(1)))
        root.addView(row, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })
    }
    private fun tabHeader(title: String, tab: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(50)
        }
        fun headerIcon(symbol: String, label: String, action: () -> Unit): View =
            tappable(FrameLayout(this).apply {
                addView(IosGlyphView(this@MainActivity, symbol, blue),
                    FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            }, label, action)
        if (tab == "home") row.addView(headerIcon("bell", "الإشعارات") { notificationsScreen() },
            LinearLayout.LayoutParams(dp(42), dp(44)))
        else row.addView(Space(this), LinearLayout.LayoutParams(dp(42), dp(44)))
        row.addView(uiText(title, 18f, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        if (tab == "home") row.addView(headerIcon(if (night) "sun" else "moon", "تبديل المظهر") {
            night = !night
            prefs.edit().putBoolean("night", night).apply()
            showHome(homepage)
        }, LinearLayout.LayoutParams(dp(42), dp(44)))
        else row.addView(Space(this), LinearLayout.LayoutParams(dp(42), dp(44)))
        root.addView(row, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })
    }
    private fun mainTabBar(active: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setBackgroundColor(surface)
        elevation = dp(8).toFloat()
        val tabs = listOf(
            Triple("home", "الرئيسية", "home"),
            Triple("tests", "الاختبارات", "tests"),
            Triple("success", "النجاح", "chart"),
            Triple("account", "حسابي", "person")
        )
        for ((name, title, icon) in tabs) {
            val selected = active == name
            val color = if (selected) blue else mutedText
            val item = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(4), dp(2), dp(3))
                isClickable = true
                isFocusable = true
                contentDescription = title
                isSelected = selected
                setOnClickListener {
                    when (name) {
                        "home" -> showHome(homepage)
                        "tests" -> testsTabScreen()
                        "success" -> successTabScreen()
                        "account" -> accountScreen()
                    }
                }
            }
            item.addView(IosGlyphView(this@MainActivity, icon, color), LinearLayout.LayoutParams(dp(23), dp(23)))
            item.addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 10.5f
                setTextColor(color)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
                setPadding(0, dp(3), 0, 0)
            })
            addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
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
    private fun uiText(value: String, size: Float, bold: Boolean = false,
                       tint: Int = textColor): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(tint)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        setLineSpacing(dp(2).toFloat(), 1f)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    /** Mirrors SwiftUI MurshidCard: 16pt inset, 22pt continuous-looking corners. */
    private fun uiCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(VisualRules.cardInset), dp(VisualRules.cardInset),
            dp(VisualRules.cardInset), dp(VisualRules.cardInset))
        background = GradientDrawable().apply {
            setColor(surface)
            cornerRadius = dp(VisualRules.cardCornerRadius).toFloat()
            setStroke(dp(1).coerceAtLeast(1), if (night) Color.rgb(47,47,50) else Color.rgb(235,235,239))
        }
        elevation = dp(1).toFloat()
    }

    private fun addUiCard(card: View, parent: LinearLayout = root, bottom: Int = 12) {
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) })
    }

    private fun iconBubble(symbol: String, size: Int = 48, color: Int = blue): FrameLayout = FrameLayout(this).apply {
        background = shape(Color.argb(if (night) 42 else 24, Color.red(color), Color.green(color), Color.blue(color)), dp(14).toFloat())
        addView(IosGlyphView(this@MainActivity, symbol, color), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        minimumWidth = dp(size)
        minimumHeight = dp(size)
    }

    private fun sectionHeader(name: String, subtitle: String, symbol: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.TOP
        }
        row.addView(iconBubble(symbol, 30), LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(uiText(name, 20f, true))
        labels.addView(uiText(subtitle, 12f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7); bottomMargin = dp(12) })
    }

    private fun tappable(view: View, description: String, action: () -> Unit): View = view.apply {
        isClickable = true
        isFocusable = true
        contentDescription = description
        foreground = RippleDrawable(ColorStateList.valueOf(Color.argb(25, 45, 108, 223)), null, shape(Color.WHITE))
        setOnClickListener { action() }
    }

    private fun uiRow(name: String, description: String, symbol: String, action: () -> Unit): View {
        val card = uiCard()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        row.addView(iconBubble(symbol, 50), LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginEnd = dp(13) })
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        details.addView(uiText(name, 17f, true))
        details.addView(uiText(description, 12f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(IosGlyphView(this, "chevron", mutedText), LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(9) })
        card.addView(row)
        return tappable(card, name, action)
    }

    private fun uiTile(name: String, description: String, symbol: String, action: () -> Unit): View {
        val card = uiCard().apply { minimumHeight = dp(128) }
        card.addView(IosGlyphView(this, symbol, blue), LinearLayout.LayoutParams(dp(27), dp(27)).apply { bottomMargin = dp(8) })
        card.addView(uiText(name, 17f, true))
        card.addView(uiText(description, 12f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        return tappable(card, name, action)
    }

    /** Always two columns, including narrow iPhones; iOS uses two flexible grid columns. */
    private fun uiTwoColumnGrid(items: List<View>, parent: LinearLayout = root) {
        items.chunked(VisualRules.homeColumns()).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.TOP
            }
            group.forEachIndexed { index, view ->
                row.addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (index == 0) marginEnd = dp(VisualRules.gridSpacing / 2)
                    else marginStart = dp(VisualRules.gridSpacing / 2)
                })
            }
            if (group.size == 1) row.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f).apply {
                marginStart = dp(VisualRules.gridSpacing / 2)
            })
            parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
    }

    private fun subjectSymbol(name: String): String = when {
        name.contains("رياض") -> "chart"
        name.contains("فيزياء") -> "sparkle"
        name.contains("كيمي") -> "sparkle"
        name.contains("أحياء") -> "heart"
        name.contains("إنك") || name.contains("انك") -> "book"
        else -> "book"
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
            setSingleLine(false)
            maxLines = 3
            minHeight = dp(50)
            setPadding(dp(10), dp(8), dp(10), dp(8))
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
        root.addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        return b
    }

    /** Native stepper with the same ranges and increments as the SwiftUI controls. */
    private fun stepper(label: String, initial: Int, min: Int, max: Int, step: Int,
                        changed: (Int) -> Unit) {
        var value = initial
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = shape(surface)
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 16f; setTextColor(textColor)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        lateinit var valueText: TextView
        val subtract = makeButton("−", secondary = true) {
            value = (value - step).coerceAtLeast(min)
            valueText.text = value.toString()
            changed(value)
        }
        row.addView(subtract, LinearLayout.LayoutParams(dp(48), dp(44)))
        valueText = TextView(this).apply {
            text = value.toString(); textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setTextColor(textColor)
        }
        row.addView(valueText, LinearLayout.LayoutParams(dp(53), dp(46)))
        val add = makeButton("+", secondary = true) {
            value = (value + step).coerceAtMost(max)
            valueText.text = value.toString()
            changed(value)
        }
        row.addView(add, LinearLayout.LayoutParams(dp(48), dp(44)))
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
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
                row.addView(makeButton(label, action = action), LinearLayout.LayoutParams(0, -2, 1f).apply {
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
        homeInsightsLoaded = false; homeInsights = JSONObject(); homeWeekly = JSONObject()
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
        view.setImageResource(R.drawable.ic_avatar_placeholder)
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
            setImageResource(R.drawable.ic_avatar_placeholder)
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
                            pendingVerificationRemember = remember.isChecked
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
        val kind = if (response.optBoolean("phone_verification_required")) "phone" else "email"
        val destination = if (kind == "phone") response.optString("masked_phone") else response.optString("masked_email")
        verificationScreen(kind, destination, response.optString("message"))
    }

    private fun verificationScreen(kind: String, destination: String, note: String) {
        activeScreen = "verification"
        base(if (kind == "phone") "تأكيد رقم الهاتف" else "تأكيد البريد الإلكتروني", true)
        line("رمز التحقق", true)
        caption(note.ifBlank { "أدخل الرمز الذي أُرسل إلى $destination" })
        val code = input("رمز التحقق من 6 أرقام").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val verify = button("تأكيد الحساب") {}
        verify.setOnClickListener {
            val value = RecoveryRules.normalizedDigits(code.text.toString())
            if (!value.matches(Regex("[0-9]{6}"))) { message("أدخل الرمز كاملاً من 6 أرقام."); return@setOnClickListener }
            if (recoveryBusy) return@setOnClickListener
            recoveryBusy = true
            verify.isEnabled = false
            recoveryRequest(mayHaveSubmitted = true, action = { api.verifyPending(kind, value) }) { result ->
                recoveryBusy = false
                if (activeScreen != "verification") return@recoveryRequest
                verify.isEnabled = true
                if (result.optBoolean("ok")) {
                    api.setRemember(pendingVerificationRemember)
                    showHome(result)
                } else message(result.optString("message", "تعذر تأكيد الرمز. تحقق منه وأعد المحاولة."))
            }
        }
        val resend = button("إعادة إرسال الرمز", secondary = true) {}
        resend.setOnClickListener {
            if (recoveryBusy) return@setOnClickListener
            recoveryBusy = true
            resend.isEnabled = false
            recoveryRequest(mayHaveSubmitted = true, action = { api.resendPending(kind) }) { result ->
                recoveryBusy = false
                if (activeScreen == "verification") {
                    resend.isEnabled = true
                    message(result.optString("message", "تعذر إعادة إرسال الرمز."))
                }
            }
        }
        button("العودة لتسجيل الدخول", secondary = true) { loginScreen() }
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
        base("إنشاء حساب جديد", true)
        val name = input("الاسم الثلاثي الكامل")
        caption("طريقة التسجيل")
        val contact = Spinner(this)
        contact.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("البريد الإلكتروني", "رقم هاتف عراقي"))
        root.addView(contact)
        val identifier = input("البريد الإلكتروني أو 07XXXXXXXXX")
        contact.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                identifier.hint = if (position == 1) "07XXXXXXXXX" else "البريد الإلكتروني"
                identifier.inputType = if (position == 1) InputType.TYPE_CLASS_PHONE else
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
        }
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
        root.addView(gender)
        fetch("api/grades.php") { result ->
            if (activeScreen != "register") return@fetch
            val array = result.optJSONArray("grades") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                grades.add(item.optInt("id"))
                gradeNames.add(item.optString("name"))
            }
            gradeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, gradeNames)
        }
        gap()
        val create = button("إنشاء الحساب") {}
        create.setOnClickListener {
            val fullName = AccountRules.normalizeName(name.text.toString())
            if (!AccountRules.validFullName(fullName)) { message("أدخل الاسم الثلاثي الكامل بشكل صحيح."); return@setOnClickListener }
            if (gradeSpinner.selectedItemPosition !in grades.indices) { message("اختر صفك الدراسي."); return@setOnClickListener }
            val contactType = if (contact.selectedItemPosition == 1) "phone" else "email"
            val value = if (contactType == "phone") RecoveryRules.normalizedDigits(identifier.text.toString().trim())
                else identifier.text.toString().trim()
            if (contactType == "phone" && !AccountRules.validIraqiPhone(value)) {
                message("أدخل رقم هاتف عراقي صحيحاً من 11 رقماً يبدأ بـ 07.")
                return@setOnClickListener
            }
            if (contactType == "email" && !android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
                message("أدخل بريداً إلكترونياً صحيحاً.")
                return@setOnClickListener
            }
            if (pass.text.toString().length < 8) { message("كلمة المرور يجب أن تكون 8 أحرف على الأقل."); return@setOnClickListener }
            if (pass.text.toString() != confirm.text.toString()) { message("كلمتا المرور غير متطابقتين."); return@setOnClickListener }
            create.isEnabled = false
            create.text = "جارٍ إنشاء الحساب…"
            val data = JSONObject()
                .put("full_name", fullName).put("name", fullName).put("fullName", fullName)
                .put("identifier", value)
                .put("contact_type", contactType)
                .put("grade_id", grades[gradeSpinner.selectedItemPosition])
                .put("gender", if (gender.selectedItemPosition == 0) "male" else "female")
                .put("password", pass.text.toString())
                .put("password_confirmation", confirm.text.toString())
                .put("company_website", "")
            fetch("api/register.php", data) { result ->
                if (activeScreen != "register") return@fetch
                if (result.optBoolean("ok")) {
                    message(result.optString("message", "تم إنشاء الحساب."))
                    pendingVerificationRemember = false
                    verifyPrompt(result)
                } else if (result.optBoolean("network_error")) {
                    create.text = "حالة التسجيل غير مؤكدة"
                    message("قد يكون الحساب أُنشئ. حاول تسجيل الدخول أو استعادة كلمة المرور قبل إعادة التسجيل.")
                } else { create.isEnabled = true; create.text = "إنشاء الحساب" }
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
        base("المرشد الوزاري", tab = "home")

        val greeting = student.optString("name", "طالبنا").trim().split(' ').firstOrNull().orEmpty()
        val stats = response.optJSONObject("stats") ?: JSONObject()
        val total = stats.optInt("total_answers")
        val accuracy = if (total > 0) (100 * stats.optInt("correct_answers") / total) else 0
        val motivation = when {
            accuracy >= 85 && total >= 10 -> "مستواك ممتاز. حافظ على إيقاعك وراجع الأخطاء القليلة."
            total > 0 -> "كل إجابة اليوم تقرّبك أكثر من الدرجة التي تريدها."
            else -> "ابدأ بخطوة صغيرة اليوم، وسنبني تقدمك معك سؤالًا بعد سؤال."
        }
        val welcome = uiCard()
        val welcomeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        welcomeRow.addView(iconBubble("graduation", 48), LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(13) })
        val welcomeText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        welcomeText.addView(uiText("أهلًا، ${greeting.ifBlank { "طالبنا" }}", 20f, true))
        welcomeText.addView(uiText(student.optString("grade"), 15f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        welcomeText.addView(uiText(motivation, 12f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        welcomeRow.addView(welcomeText, LinearLayout.LayoutParams(0, -2, 1f))
        if (subscribed) welcomeRow.addView(IosGlyphView(this, "check", Color.rgb(42,172,91)),
            LinearLayout.LayoutParams(dp(22), dp(22)))
        welcome.addView(welcomeRow)
        addUiCard(welcome, bottom = 16)

        if (lastValidatedInternet == false) addUiCard(uiCard().apply {
            addView(uiText("لا يوجد اتصال بالإنترنت. ستُعرض البيانات المحفوظة متى كانت متاحة.", 13f, tint = gold))
        }, bottom = 16)
        addHomeHeroSlider()

        val subscription = uiCard()
        subscription.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val glyph = if (subscribed) "check" else "trophy"
            addView(iconBubble(glyph, 46, if (subscribed) Color.rgb(42,172,91) else gold),
                LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(12) })
            val labels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(uiText(if (subscribed) "كل الأسئلة مفتوحة" else "رصيد التجربة المجانية", 17f, true))
            labels.addView(uiText(if (subscribed) "اشتراكك فعّال — واصل التقدم بدون حدود الرصيد المجاني."
                else "متبقي ${response.optInt("free_remaining")} من ${response.optInt("free_limit")} سؤال",
                12f, tint = mutedText))
            addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            if (!subscribed) addView(IosGlyphView(this@MainActivity, "chevron", mutedText),
                LinearLayout.LayoutParams(dp(18), dp(18)))
        })
        if (subscribed) addUiCard(subscription, bottom = 16)
        else addUiCard(tappable(subscription, "الباقات والاشتراك") { plansScreen() }, bottom = 16)

        homeMetricHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(homeMetricHolder, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        populateHomeMetrics()

        sectionHeader("موادك", "اختر المادة ثم الموضوع", "book")
        val subjects = homepage.optJSONArray("subjects") ?: JSONArray()
        for (i in 0 until subjects.length()) {
            val subject = subjects.optJSONObject(i) ?: continue
            val id = subject.optInt("id")
            val name = subject.optString("name")
            addUiCard(uiRow(name, "المواضيع والاختبارات", subjectSymbol(name)) { topicsScreen(id, name) }, bottom = 12)
        }

        sectionHeader("خطواتك التالية", "اقتراحات مرتبة حسب تقدمك", "chart")
        homeNextStepHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(homeNextStepHolder)
        populateNextStep()

        sectionHeader("أدواتك السريعة", "اختبارات ومراجعة وبحث ومساهمات", "sparkle")
        val tiles = listOf(
            uiTile("راجع أخطاءك", "مراجعة ذكية", "check") { reviewScreen() },
            uiTile("اختبار مخصص", "عدد · نوع · مؤقت", "tests") { practiceBuilderScreen() },
            uiTile("تحدي اليوم", "خمسة أسئلة سريعة", "trophy") { openCompetition("daily_challenge") },
            uiTile("ابحث عن سؤال", "في بنك الأسئلة", "search") { questionSearchScreen() },
            uiTile("اقترح سؤالًا", "ساهم في البنك", "book") { proposeQuestionScreen() },
            uiTile("طلباتك", "تابع المراجعة", "list") { myQuestionSubmissionsScreen() },
            uiTile("سجل الاختبارات", "نتائجك السابقة", "calendar") { attemptHistoryScreen() },
            uiTile("خطة الدراسة", "هدف يومي", "calendar") { studyPlanScreen() },
            uiTile("الإنجازات", "XP والمستوى", "trophy") { openCompetition("achievements") },
            uiTile("جلسة تركيز", "25 · 45 · 60 دقيقة", "chart") { focusScreen() },
            uiTile("تحدي المليون", "ترتيب ونقاط", "trophy") { openCompetition("contest") },
            uiTile("غيّر جو", "استراحة قصيرة", "sparkle") { storiesScreen() },
            uiTile("المفضلة", "الأسئلة المحفوظة", "heart") { favoritesScreen() },
            uiTile("الدعم", "محادثة مع الإدارة", "person") { supportScreen() },
            uiTile("المرشحات", "للمشتركين", "book") {
                if (subscribed) subjectsScreen(true) else message("المرشحات للمشتركين فقط.")
            },
            uiTile("رمز الاشتراك", "تفعيل رمز", "check") { redeemScreen() }
        )
        uiTwoColumnGrid(tiles)
        val pendingPractice = prefs.getInt("practice_session_${student.optInt("id", 0)}", 0)
        if (pendingPractice > 0) button("متابعة الاختبار المخصص", secondary = true) { loadPracticeSession(pendingPractice) }
        button("تحديث البيانات", secondary = true) { refreshHome() }
        if (!homeInsightsLoaded) loadHomeInsights()
        fetch("mobile/notifications.php", showError = false) { /* notification count is read on demand */ }
    }

    private fun populateHomeMetrics() {
        val host = homeMetricHolder ?: return
        host.removeAllViews()
        val stats = homepage.optJSONObject("stats") ?: JSONObject()
        val metrics = listOf(
            Triple("الإجابات", stats.optInt("total_answers").toString(), "check"),
            Triple("اختبارات مكتملة", stats.optInt("exams_completed").toString(), "tests"),
            Triple("هذا الأسبوع", if (homeInsightsLoaded) homeWeekly.optInt("answers").toString() else "—", "calendar"),
            Triple("مؤشر النجاح", if (homeInsightsLoaded) "${homeInsights.optInt("success_index")}%" else "—", "sparkle")
        )
        uiTwoColumnGrid(metrics.map { (label, value, glyph) ->
            uiCard().apply {
                addView(iconBubble(glyph, 36), LinearLayout.LayoutParams(dp(36), dp(36)).apply { bottomMargin = dp(9) })
                addView(uiText(value, 23f, true))
                addView(uiText(label, 12f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            }
        }, host)
    }

    private fun populateNextStep() {
        val host = homeNextStepHolder ?: return
        host.removeAllViews()
        val suggestion = homeInsights.optJSONArray("daily_topics")?.optJSONObject(0)
        val subjects = homepage.optJSONArray("subjects") ?: JSONArray()
        val sid = suggestion?.optInt("subject_id") ?: subjects.optJSONObject(0)?.optInt("id") ?: 0
        val subject = (0 until subjects.length()).mapNotNull { subjects.optJSONObject(it) }
            .firstOrNull { it.optInt("id") == sid }
        val subjectName = subject?.optString("name") ?: suggestion?.optString("subject_name") ?: ""
        val card = if (suggestion != null && suggestion.optInt("id") > 0) {
            val title = suggestion.optString("name", suggestion.optString("display_name", "مراجعة مقترحة"))
            tappable(uiCard().apply {
                addView(uiText("مقترح لك · ${suggestion.optInt("coverage")}%", 13f, true, blue))
                addView(uiText(title, 20f, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
                addView(uiText(subjectName, 15f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }, title) {
                topicsReturnScreen = "home"
                topicsSubjectId = sid; topicsSubjectName = subjectName
                loadExam(suggestion.optInt("id"), title)
            }
        } else {
            uiRow("ابدأ أول مراجعة", "اختر موضوعك وسنحفظ تقدمك تلقائيًا.", "check") {
                if (sid > 0) topicsScreen(sid, subjectName) else subjectsScreen()
            }
        }
        addUiCard(card, host, bottom = 12)
    }

    private fun loadHomeInsights() {
        fetch("mobile/insights.php", showError = false) { result ->
            if (!result.optBoolean("ok")) return@fetch
            homeInsights = result.optJSONObject("insights") ?: JSONObject()
            homeWeekly = result.optJSONObject("weekly") ?: JSONObject()
            homeInsightsLoaded = true
            if (activeScreen == "home") { populateHomeMetrics(); populateNextStep() }
        }
    }

    private val heroNames = listOf("تحدي المليون", "أعلن مدرستك", "الاختبارات", "النجاح")
    private val heroSlugs = listOf("million", "advertise-here", "tests", "study")

    private fun addHomeHeroSlider() {
        val hero = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TR_BL, intArrayOf(navy, blue))
                .apply { cornerRadius = dp(24).toFloat() }
            clipToOutline = true
            isClickable = true
            isFocusable = true
        }
        homeHeroContainer = hero
        homeHeroView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        hero.addView(homeHeroView, FrameLayout.LayoutParams(-1, -1))
        homeHeroFallback = uiText(heroNames[homeHeroIndex], 23f, true, Color.WHITE).apply {
            gravity = Gravity.CENTER
        }
        hero.addView(homeHeroFallback, FrameLayout.LayoutParams(-1, -1))
        var touchX = 0f
        var swiped = false
        hero.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { touchX = event.x; swiped = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.x - touchX) > dp(35)) swiped = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (swiped) showHeroSlide(homeHeroIndex + if (event.x < touchX) 1 else -1)
                    else view.performClick()
                    true
                }
                else -> true
            }
        }
        hero.setOnClickListener {
            when (homeHeroIndex) {
                0 -> openCompetition("contest")
                1 -> schoolAdScreen()
                2 -> testsTabScreen()
                else -> successTabScreen()
            }
        }
        root.addView(hero, LinearLayout.LayoutParams(-1, dp(VisualRules.heroHeight)).apply { bottomMargin = dp(8) })
        homeHeroDots = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        root.addView(homeHeroDots, LinearLayout.LayoutParams(-1, dp(16)).apply { bottomMargin = dp(16) })
        showHeroSlide(homeHeroIndex)
        startHomeHeroTicker()
    }

    private fun showHeroSlide(index: Int) {
        homeHeroIndex = ((index % heroNames.size) + heroNames.size) % heroNames.size
        val bitmap = heroBitmaps[homeHeroIndex]
        homeHeroView?.setImageBitmap(bitmap)
        homeHeroContainer?.contentDescription = heroNames[homeHeroIndex]
        homeHeroFallback?.apply {
            text = heroNames[homeHeroIndex]
            visibility = if (bitmap == null) View.VISIBLE else View.GONE
        }
        homeHeroDots?.apply {
            removeAllViews()
            for (i in heroNames.indices) {
                addView(View(this@MainActivity).apply {
                    background = shape(if (i == homeHeroIndex) blue else mutedText, dp(4).toFloat())
                }, LinearLayout.LayoutParams(dp(if (i == homeHeroIndex) 22 else 7), dp(7)).apply {
                    marginStart = dp(3); marginEnd = dp(3)
                })
            }
        }
        if (bitmap == null) loadHeroImage(homeHeroIndex)
    }

    private fun loadHeroImage(index: Int) {
        if (!heroPending.add(index)) return
        val url = "https://www.mur-iq.com/assets/img/hero/${heroSlugs[index]}.webp?v=20260928-v56"
        heroJobs.execute {
            val bitmap = try {
                val connection = URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 7000
                connection.readTimeout = 10000
                connection.instanceFollowRedirects = false
                try {
                    if (connection.responseCode !in 200..299 || connection.contentLength > 3_000_000) null
                    else connection.inputStream.use { input ->
                        val bytes = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var used = 0
                        while (used <= 3_000_000) {
                            val n = input.read(buf)
                            if (n < 0) break
                            used += n
                            if (used > 3_000_000) break
                            bytes.write(buf, 0, n)
                        }
                        if (used > 3_000_000) null else BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, used)
                    }
                } finally { connection.disconnect() }
            } catch (_: Exception) { null }
            main.post {
                heroPending.remove(index)
                if (bitmap != null) {
                    heroBitmaps[index] = bitmap
                    if (activeScreen == "home" && homeHeroIndex == index) showHeroSlide(index)
                }
            }
        }
    }

    private fun stopHomeHeroTicker() {
        homeHeroTicker?.let(main::removeCallbacks)
        homeHeroTicker = null
    }

    private fun startHomeHeroTicker() {
        stopHomeHeroTicker()
        val ticker = object : Runnable {
            override fun run() {
                if (activeScreen != "home" || homeHeroTicker !== this) return
                showHeroSlide(homeHeroIndex + 1)
                main.postDelayed(this, 5500)
            }
        }
        homeHeroTicker = ticker
        main.postDelayed(ticker, 5500)
    }

    private fun testsTabScreen() {
        activeScreen = "tests"
        base("الاختبارات", tab = "tests")
        val intro = uiCard()
        intro.addView(uiText("اختباراتك", 12f, true, blue))
        intro.addView(uiText("اختر المادة وابدأ بثقة", 23f, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        intro.addView(uiText("المواضيع مرتبة حسب المنهج، وتقدمك محفوظ داخل حسابك.", 15f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        addUiCard(intro, bottom = 14)
        val search = EditText(this).apply {
            hint = "ابحث عن مادة"
            textSize = 16f
            setSingleLine(true)
            background = shape(surface, dp(16).toFloat())
            setPadding(dp(13), dp(12), dp(13), dp(12))
            setTextColor(textColor)
            setHintTextColor(mutedText)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rows)
        fun filterSubjects(text: String) {
            rows.removeAllViews()
            val subjects = homepage.optJSONArray("subjects") ?: JSONArray()
            var shown = 0
            for (i in 0 until subjects.length()) {
                val subject = subjects.optJSONObject(i) ?: continue
                val name = subject.optString("name")
                if (!name.contains(text.trim(), ignoreCase = true)) continue
                shown++
                addUiCard(uiRow(name, "المواضيع والاختبارات", subjectSymbol(name)) {
                    topicsScreen(subject.optInt("id"), name)
                }, rows)
            }
            if (shown == 0) rows.addView(uiText("لا توجد نتيجة. جرّب كتابة اسم المادة بطريقة مختلفة.", 15f,
                tint = mutedText))
        }
        filterSubjects("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSubjects(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun successTabScreen() {
        activeScreen = "success"
        base("النجاح", tab = "success")
        if (!homeInsightsLoaded) {
            addUiCard(uiCard().apply { addView(uiText("جارٍ تحميل مؤشر النجاح…", 16f, tint = mutedText)) })
            fetch("mobile/insights.php", showError = false) { response ->
                if (response.optBoolean("ok")) {
                    homeInsights = response.optJSONObject("insights") ?: JSONObject()
                    homeWeekly = response.optJSONObject("weekly") ?: JSONObject()
                    homeInsightsLoaded = true
                    if (activeScreen == "success") successTabScreen()
                } else if (activeScreen == "success") {
                    button("إعادة المحاولة") { successTabScreen() }
                }
            }
            return
        }
        val hero = uiCard()
        hero.addView(uiText("مؤشر النجاح", 13f, true, blue))
        hero.addView(uiText("${homeInsights.optInt("success_index")}%", 39f, true, blue),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        hero.addView(uiText("صورتك الدراسية الآن", 20f, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        hero.addView(uiText("الدقة ${homeInsights.optInt("accuracy")}% · التغطية ${homeInsights.optInt("coverage")}% · " +
            "${homeInsights.optInt("distinct_answered")} سؤال مختلف", 15f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        addUiCard(hero, bottom = 16)
        sectionHeader("آخر 7 أيام", "نشاطك الحديث", "calendar")
        uiTwoColumnGrid(listOf(
            uiTile("${homeWeekly.optInt("answers")}", "إجابة", "check") { reviewScreen() },
            uiTile("${homeWeekly.optInt("accuracy")}%", "دقة", "chart") { reviewScreen() }
        ))
        sectionHeader("خطوات للتحسين", "واصل حل الاختبارات وراجع نقاط ضعفك", "chart")
        addUiCard(uiRow("مراجعة الأخطاء", "تعرّف على الأسئلة التي تحتاج إلى إتقانها.", "check") { reviewScreen() })
        addUiCard(uiRow("خطة الدراسة", "نظّم هدفك اليومي.", "calendar") { studyPlanScreen() })
        addUiCard(uiRow("تحدي المليون", "قارن ترتيبك مع الطلاب.", "trophy") { openCompetition("contest") })
        button("تحديث الإحصاءات", secondary = true) {
            homeInsightsLoaded = false
            successTabScreen()
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
        if (activeScreen != "exam" && activeScreen != "topics") topicsReturnScreen = activeScreen
        topicsSubjectId = subjectId; topicsSubjectName = subjectName
        val expectedScreenGeneration = screenGeneration
        fetch("mobile/topics.php?subject_id=" + subjectId) { response ->
            if (!response.optBoolean("ok") || expectedScreenGeneration != screenGeneration ||
                topicsSubjectId != subjectId) return@fetch
            activeScreen = "topics"
            base(subjectName, true)
            val topics = response.optJSONArray("topics") ?: JSONArray()
            val intro = uiCard()
            intro.addView(uiText(subjectName, 12f, true, blue))
            intro.addView(uiText("اختر الموضوع", 23f, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            intro.addView(uiText("شاهد تقدمك وعدد الأسئلة قبل البدء.", 15f, tint = mutedText),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            addUiCard(intro, bottom = 14)
            for (i in 0 until topics.length()) {
                val topic = topics.optJSONObject(i) ?: continue
                val count = topic.optInt("count")
                val name = topic.optString("name")
                val metric = homeInsights.optJSONArray("chapters")?.let { chapters ->
                    (0 until chapters.length()).mapNotNull { chapters.optJSONObject(it) }
                        .firstOrNull { it.optInt("id") == topic.optInt("id") }
                }
                val coverage = metric?.optInt("coverage") ?: 0
                val progress = when {
                    coverage >= 100 -> "مكتمل"
                    coverage > 0 -> "قيد التقدم · $coverage%"
                    else -> "لم يبدأ"
                }
                addUiCard(uiRow(name, "$count سؤال · $progress", "book") {
                    if (count > 0) loadExam(topic.optInt("id"), name) else message("لم تتم إضافة أسئلة لهذا الموضوع بعد.")
                }, bottom = 12)
            }
            if (topics.length() == 0) addUiCard(uiCard().apply {
                addView(uiText("لم تُضف مواضيع بعد.", 15f, tint = mutedText))
            })
        }
    }

    private fun loadExam(id: Int, name: String, fromSearch: Boolean = false, targetText: String = "") {
        examFromSearch = fromSearch
        examSearchTarget = if (fromSearch) targetText else ""
        chapterId = id; chapterName = name
        val expectedScreenGeneration = screenGeneration
        fetch("mobile/exam.php?chapter_id=" + id) { result ->
            if (!result.optBoolean("ok") || chapterId != id ||
                expectedScreenGeneration != screenGeneration) return@fetch
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
            val match = if (examFromSearch && examSearchTarget.isNotBlank())
                SearchRules.questionIndex((0 until examQuestions.length()).map { index ->
                    examQuestions.optJSONObject(index)?.optString("text").orEmpty()
                }, examSearchTarget)
            else null
            questionIndex = match ?: 0
            if (match == null) while (questionIndex < examQuestions.length() &&
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
            button(if (examFromSearch) "العودة إلى نتائج البحث" else "عودة إلى المواضيع") { leaveExam() }
            return
        }
        val question = examQuestions.optJSONObject(questionIndex)
        if (question == null) {
            line("تعذر قراءة بيانات السؤال. حدّث الاختبار ثم أبلغ الإدارة إن استمر الخلل.")
            button("تحديث الأسئلة") { loadExam(chapterId, chapterName, examFromSearch, examSearchTarget) }
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
                button("تحديث بيانات السؤال") { loadExam(chapterId, chapterName, examFromSearch, examSearchTarget) }
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
        button(if (examFromSearch) "العودة إلى نتائج البحث" else "العودة للمواضيع", secondary = true) { leaveExam() }
    }

    private fun leaveExam() {
        if (examFromSearch) renderQuestionSearchResults(lastSearchQuery, lastSearchResults)
        else topicsScreen(topicsSubjectId, topicsSubjectName)
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
                            loadExam(chapterId, chapterName, examFromSearch, examSearchTarget)
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
                    .setPositiveButton("تحديث الاختبار") { _, _ ->
                        loadExam(chapterId, chapterName, examFromSearch, examSearchTarget)
                    }
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
        val photoChange = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "اختيار صورة شخصية جديدة"
            setPadding(dp(2), dp(7), dp(2), dp(3))
            addView(IosGlyphView(this@MainActivity, "camera", Color.WHITE),
                LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginEnd = dp(3) })
            addView(uiText("تغيير الصورة", 12f, true, Color.WHITE))
            setOnClickListener { chooseAvatar() }
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

    private fun accountSetting(card: LinearLayout, title: String, subtitle: String,
                               symbol: String, action: () -> Unit) {
        if (card.childCount > 0) card.addView(View(this).apply {
            setBackgroundColor(if (night) Color.rgb(52,52,55) else Color.rgb(235,235,239))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(3); bottomMargin = dp(3) })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            minimumHeight = dp(52)
        }
        row.addView(iconBubble(symbol, 34), LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        details.addView(uiText(title, 16f, true))
        details.addView(uiText(subtitle, 12f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(IosGlyphView(this, "chevron", mutedText), LinearLayout.LayoutParams(dp(15), dp(15)))
        card.addView(tappable(row, title, action), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(5); bottomMargin = dp(5)
        })
    }

    private fun accountScreen() {
        activeScreen = "account"
        base("حسابي", tab = "account")
        addUiCard(uiCard().apply { addView(uiText("جارٍ تحديث بيانات الحساب…", 15f, tint = mutedText)) })
        fetch("mobile/account.php", showError = false) { response ->
            if (activeScreen != "account") return@fetch
            if (!response.optBoolean("ok")) {
                addUiCard(uiCard().apply {
                    addView(uiText(response.optString("message", "تعذر تحميل الحساب."), 15f, tint = mutedText))
                })
                button("إعادة المحاولة", secondary = true) { accountScreen() }
                return@fetch
            }
            base("حسابي", tab = "account")
            val profile = response.optJSONObject("user") ?: JSONObject()
            accountProfileCard(profile, response)

            val subscription = uiRow(if (subscribed) "الاشتراك" else "افتح كامل المنصة",
                if (subscribed) "اشتراكك مفعّل حاليًا."
                else "الدفع خارجي ولا نطلب رقمًا إضافيًا من هنا.", "check") { plansScreen() }
            addUiCard(subscription, bottom = 16)
            val days = response.optInt("subscription_days_remaining", -1)
            if (subscribed && days >= 0) addUiCard(uiCard().apply {
                addView(uiText("متبقٍ من الاشتراك: $days يوم", 13f, tint = mutedText))
            }, bottom = 16)

            sectionHeader("الحساب والخدمات", "إدارة كل ما يخص حسابك", "person")
            val services = uiCard()
            if (response.optBoolean("can_change_name")) accountSetting(services, "تغيير الاسم",
                "متاح مرة واحدة", "person") { promptRename(profile.optString("name")) }
            accountSetting(services, "طلب إعلان مدرسة", "أرسل الطلب مباشرة إلى الإدارة", "bell") { schoolAdScreen() }
            accountSetting(services, "الإشعارات", "الرسائل الواردة من المنصة", "bell") { notificationsScreen() }
            accountSetting(services, "خدمة العملاء", "محادثة مباشرة مع الدعم", "person") { supportScreen() }
            accountSetting(services, "غيّر جو", "رسائل وقصص قصيرة", "sparkle") { storiesScreen() }
            addUiCard(services, bottom = 16)

            sectionHeader("الأمان والتذكير", "خيارات خاصة بجهازك", "lock")
            val security = uiCard()
            accountSetting(security, "استعادة / تغيير كلمة المرور", "تأكيد الهوية لحماية حسابك", "lock") {
                recoverScreen()
            }
            accountSetting(security, "تحديث بيانات الحساب", "الاسم والصورة والاشتراك", "check") {
                refreshAccountAfterMutation()
            }
            addUiCard(security, bottom = 16)

            sectionHeader("المظهر", "اختر الشكل المريح لك", "moon")
            addUiCard(uiRow(if (night) "الوضع الداكن" else "الوضع الفاتح", "التبديل بين المظهرين", if (night) "sun" else "moon") {
                night = !night
                prefs.edit().putBoolean("night", night).apply()
                accountScreen()
            }, bottom = 16)

            sectionHeader("إدارة الحساب", "خيارات حساسة", "lock")
            val management = uiCard()
            accountSetting(management, "إزالة الصورة الشخصية", "تأكيد قبل الحذف", "camera") {
                AlertDialog.Builder(this).setTitle("حذف الصورة")
                    .setMessage("هل تريد إزالة صورتك الشخصية؟")
                    .setPositiveButton("نعم") { _, _ ->
                        fetchForm("mobile/avatar.php", mapOf("csrf" to csrf, "remove" to "1")) { result ->
                            if (result.optBoolean("ok")) { avatarGeneration++; refreshAccountAfterMutation() }
                        }
                    }.setNegativeButton("إلغاء", null).show()
            }
            accountSetting(management, "إدارة خصوصية الحساب", "طلب الحذف وإدارة البيانات", "lock") { deletionScreen(response) }
            accountSetting(management, "تسجيل الخروج", "إنهاء الجلسة على هذا الجهاز", "person") {
                AlertDialog.Builder(this).setTitle("تسجيل الخروج؟")
                    .setMessage("سيتم إنهاء جلسة حسابك على هذا الجهاز.")
                    .setPositiveButton("تسجيل الخروج") { _, _ ->
                        fetch("mobile/logout.php", JSONObject().put("csrf", csrf), showError = false) {
                            sessionGeneration++
                            api.clear(); csrf = ""; student = JSONObject()
                            homeInsightsLoaded = false; homeInsights = JSONObject(); homeWeekly = JSONObject()
                            loginScreen()
                        }
                    }.setNegativeButton("إلغاء", null).show()
            }
            addUiCard(management, bottom = 16)

            val info = uiCard()
            info.addView(uiText("منصة المرشد الوزاري", 17f, true))
            info.addView(uiText("تطبيق Android أصلي مرتبط مباشرة بحسابك في المنصة.", 13f, tint = mutedText),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addUiCard(info, bottom = 8)
            button("سياسة الخصوصية", secondary = true) { external("https://www.mur-iq.com/privacy.php") }
            button("شروط الاستخدام", secondary = true) { external("https://www.mur-iq.com/terms.php") }
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
            activeScreen = "notifications"
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
                val read = item.optBoolean("is_read") || item.optInt("is_read") == 1
                line((if (read) "" else "● ") + item.optString("title") + "\n" + item.optString("body"))
                if (item.optString("created_at").isNotBlank()) caption(item.optString("created_at"))
            }
            if (items.length() == 0) caption("لا توجد إشعارات.")
        }
    }

    private fun favoritesScreen() {
        fetch("mobile/favorites.php") { response ->
            if (!response.optBoolean("ok")) return@fetch
            activeScreen = "favorites"
            base("أسئلتي المفضلة", true)
            val items = response.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                line(item.optString("question_text"), true)
                if (item.optString("correct_answer").isNotBlank())
                    caption("الإجابة الصحيحة: " + item.optString("correct_answer"))
                if (item.optString("explanation").isNotBlank())
                    caption("الشرح: " + item.optString("explanation"))
                caption(item.optString("subject_name") + " • " + item.optString("chapter_name"))
                val id = item.optInt("id", item.optInt("question_id"))
                if (id > 0) {
                    val remove = button("إزالة من المفضلة", secondary = true) {}
                    remove.setOnClickListener {
                        remove.isEnabled = false
                        fetch("api/question-tools.php", JSONObject().put("csrf", csrf)
                            .put("question_id", id).put("action", "unfavorite")) { result ->
                            if (result.optBoolean("ok")) favoritesScreen() else remove.isEnabled = true
                        }
                    }
                }
            }
            if (items.length() == 0) caption("لا توجد أسئلة محفوظة.")
        }
    }

    private fun studyPlanScreen() {
        activeScreen = "study_plan"
        base("خطة الدراسة", true)
        line("جارٍ تحميل خططك الدراسية…")
        fetch("mobile/study-plan.php") { response ->
            if (activeScreen != "study_plan") return@fetch
            if (response.optBoolean("ok")) renderStudyPlans(response.optJSONArray("plans") ?: JSONArray())
            else {
                base("خطة الدراسة", true)
                line("تعذر عرض خططك حالياً.")
                button("إعادة المحاولة") { studyPlanScreen() }
            }
        }
    }

    private fun renderStudyPlans(plans: JSONArray, notice: String = "") {
        activeScreen = "study_plan"
        base("خطة الدراسة", true)
        line("خطة جديدة", true)
        val planName = input("اسم الخطة").apply { setText("خطتي الوزارية") }
        var days = 30
        var target = 20
        stepper("المدة (يوم)", days, 1, 365, 1) { days = it }
        stepper("الهدف اليومي (سؤال)", target, 5, 500, 5) { target = it }
        val create = button("إنشاء الخطة") {}
        create.setOnClickListener {
            val title = planName.text.toString().trim()
            val issue = StudyRules.planError(title, days, target)
            if (issue != null) { message(issue); return@setOnClickListener }
            create.isEnabled = false
            create.text = "جارٍ إنشاء الخطة…"
            fetch("mobile/study-plan.php", JSONObject().put("csrf", csrf).put("title", title)
                .put("days", days).put("target", target)) { result ->
                if (activeScreen != "study_plan") return@fetch
                if (result.optBoolean("ok")) {
                    val updated = result.optJSONArray("plans")
                    if (updated != null) renderStudyPlans(updated, "تم إنشاء الخطة.")
                    else studyPlanScreen()
                } else if (result.optBoolean("network_error")) {
                    create.text = "حالة الإنشاء غير مؤكدة"
                    message("قد يكون الخادم أنشأ الخطة. افحص خططك قبل محاولة إنشائها مجدداً.")
                } else {
                    create.isEnabled = true
                    create.text = "إنشاء الخطة"
                }
            }
        }
        if (notice.isNotBlank()) line(notice)
        gap(12)
        line("خططي", true)
        if (plans.length() == 0) caption("لم تُنشأ أي خطة دراسية بعد.")
        for (i in 0 until plans.length()) {
            val plan = plans.optJSONObject(i) ?: continue
            line(plan.optString("title"), true)
            caption(plan.optString("start_date") + " ← " + plan.optString("end_date") +
                " • " + plan.optInt("daily_target") + " سؤال/يوم")
        }
        button("تحديث الخطط", secondary = true) { studyPlanScreen() }
    }

    private fun schoolAdScreen() {
        activeScreen = "school_ad"
        base("طلب إعلان مدرسة", true)
        caption("يرسل طلبك إلى إدارة المنصة، ويشمل اسم المدرسة والإعلان ومدة عرضه.")
        val school = input("اسم المدرسة")
        val advertisement = input("الإعلان الكامل", multiline = true)
        var days = 1
        stepper("مدة الإعلان (يوم)", days, 1, 90, 1) { days = it }
        val send = button("إرسال الطلب إلى الإدارة") {}
        send.setOnClickListener {
            val schoolName = school.text.toString().trim()
            val text = advertisement.text.toString().trim()
            val issue = StudyRules.schoolAdError(schoolName, text, days)
            if (issue != null) { message(issue); return@setOnClickListener }
            val content = StudyRules.schoolAdMessage(schoolName, text, days, student.optString("name"))
            AlertDialog.Builder(this).setTitle("تأكيد طلب الإعلان")
                .setMessage("إرسال طلب إعلان مدرسة $schoolName لمدة $days يوم إلى الإدارة؟")
                .setPositiveButton("إرسال") { _, _ ->
                    send.isEnabled = false
                    send.text = "جارٍ إرسال الطلب…"
                    fetch("mobile/support.php", JSONObject().put("csrf", csrf).put("message", content)) { result ->
                        if (activeScreen != "school_ad") return@fetch
                        if (result.optBoolean("ok")) {
                            advertisement.setText("")
                            send.text = "تم إرسال الطلب"
                            message("تم إرسال طلب الإعلان إلى الإدارة بنجاح.")
                        } else if (result.optBoolean("network_error")) {
                            send.text = "حالة الإرسال غير مؤكدة"
                            message("قد يكون الطلب وصل. تحقق من مراسلات الدعم قبل إرساله ثانية.")
                        } else {
                            send.isEnabled = true
                            send.text = "إرسال الطلب إلى الإدارة"
                        }
                    }
                }.setNegativeButton("إلغاء", null).show()
        }
        button("مراجعة مراسلات الدعم", secondary = true) { supportScreen() }
    }

    private fun reviewScreen() {
        fetch("mobile/review.php") { response ->
            if (!response.optBoolean("ok")) return@fetch
            activeScreen = "review"
            base("مراجعة الأخطاء", true)
            val weak = response.optJSONArray("weak") ?: JSONArray()
            if (weak.length() > 0) {
                line("المواضيع التي تحتاج تركيزاً", true)
                for (i in 0 until weak.length()) {
                    val item = weak.optJSONObject(i) ?: continue
                    line(item.optString("chapter") + " • " + item.optInt("rate") + "%", true)
                    caption(item.optString("subject") + " • أخطاء: " + item.optInt("wrong") +
                        " من " + item.optInt("total"))
                }
                line("آخر الأخطاء", true)
            }
            val items = response.optJSONArray("mistakes") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                line(item.optString("question_text"))
                caption("الإجابة الصحيحة: " + item.optString("correct_answer"))
                caption("التوضيح: " + item.optString("explanation"))
                caption(item.optString("subject_name") + " • " + item.optString("chapter_name"))
            }
            if (items.length() == 0) caption("لا توجد أخطاء مسجلة.")
            button("تحديث المراجعة", secondary = true) { reviewScreen() }
        }
    }

    private fun questionSearchScreen() {
        activeScreen = "question_search"
        base("البحث في الأسئلة", true)
        caption("اكتب حرفين على الأقل للبحث في الأسئلة والمواضيع.")
        val query = input("ابحث في الأسئلة والمواضيع")
        val search = button("بحث") {}
        search.setOnClickListener {
            val value = query.text.toString().trim()
            if (value.length < 2) { message("اكتب حرفين على الأقل."); return@setOnClickListener }
            search.isEnabled = false
            val path = "api/search.php?q=" + URLEncoder.encode(value, "UTF-8")
            fetch(path, showError = false) { response ->
                if (activeScreen != "question_search") return@fetch
                search.isEnabled = true
                if (!SearchRules.accepts(response.has("ok"), response.optBoolean("ok"),
                        response.optJSONArray("results") != null)) {
                    message(response.optString("message", "تعذر تحميل نتائج البحث."))
                    return@fetch
                }
                // Replace only earlier search results, keeping the query field editable.
                val results = response.optJSONArray("results") ?: JSONArray()
                val savedQuery = query.text.toString()
                renderQuestionSearchResults(savedQuery, results)
            }
        }
    }

    private fun renderQuestionSearchResults(queryText: String, results: JSONArray) {
        lastSearchQuery = queryText
        lastSearchResults = results
        activeScreen = "question_search"
        base("البحث في الأسئلة", true)
        val query = input("ابحث في الأسئلة والمواضيع").apply { setText(queryText) }
        button("بحث") {
            val next = query.text.toString().trim()
            if (next.length < 2) { message("اكتب حرفين على الأقل."); return@button }
            val path = "api/search.php?q=" + URLEncoder.encode(next, "UTF-8")
            fetch(path, showError = false) { response ->
                if (activeScreen != "question_search") return@fetch
                if (SearchRules.accepts(response.has("ok"), response.optBoolean("ok"),
                        response.optJSONArray("results") != null)) {
                    renderQuestionSearchResults(next, response.optJSONArray("results") ?: JSONArray())
                } else message(response.optString("message", "تعذر تحميل نتائج البحث."))
            }
        }
        line("نتائج البحث", true)
        if (results.length() == 0) caption("لا توجد نتائج مطابقة.")
        for (i in 0 until results.length()) {
            val result = results.optJSONObject(i) ?: continue
            caption(result.optString("type"))
            val title = result.optString("title").ifBlank { "نتيجة بحث" }
            val resultChapterId = SearchRules.chapterId(result.optString("url"))
            if (resultChapterId == null) line(title, true) else button(title, secondary = true) {
                loadExam(resultChapterId, result.optString("meta").ifBlank { title },
                    fromSearch = true, targetText = title)
            }
            if (result.optString("meta").isNotBlank()) caption(result.optString("meta"))
        }
    }

    private fun attemptHistoryScreen() {
        activeScreen = "attempt_history"
        base("سجل الاختبارات", true)
        line("جارٍ تحميل المحاولات السابقة…")
        fetch("mobile/practice.php?history=1") { response ->
            if (activeScreen != "attempt_history") return@fetch
            base("سجل الاختبارات", true)
            if (!response.optBoolean("ok")) {
                line("تعذر تحميل سجل الاختبارات.")
                button("إعادة المحاولة") { attemptHistoryScreen() }
                return@fetch
            }
            val rows = response.optJSONArray("history") ?: JSONArray()
            if (rows.length() == 0) caption("لا توجد محاولات. ستظهر اختباراتك المخصصة المكتملة هنا.")
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                line(row.optString("title"), true)
                caption(row.optString("subject_name") + " • " + row.optString("completed_at"))
                val score = row.optDouble("score", 0.0).toInt()
                caption("النتيجة: $score% • " + row.optInt("correct") + " / " + row.optInt("total_questions"))
            }
            button("تحديث المحاولات", secondary = true) { attemptHistoryScreen() }
        }
    }

    private fun focusKey(suffix: String): String = "study_focus_${student.optInt("id", 0)}_$suffix"

    private fun stopFocusTicker() {
        focusTicker?.let(main::removeCallbacks)
        focusTicker = null
    }

    private fun confirmFocusRetry(action: String, proceed: () -> Unit) {
        if (prefs.getString(focusKey("uncertain"), "") != action) { proceed(); return }
        AlertDialog.Builder(this).setTitle("تحقق قبل إعادة الإرسال")
            .setMessage("حالة الطلب السابق غير مؤكدة بسبب الاتصال. تحقق أولاً من سجلك أو من فريق الدعم؛ فقد يكون الخادم نفّذه. هل تأكدت وتريد إعادة الإرسال يدوياً؟")
            .setPositiveButton("أعد الإرسال بعد التحقق") { _, _ -> proceed() }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun focusScreen(status: String = "") {
        stopFocusTicker()
        activeScreen = "focus"
        focusMinutes = prefs.getInt(focusKey("minutes"), focusMinutes)
        focusDeadline = prefs.getLong(focusKey("deadline"), 0L)
        base("جلسة التركيز", true)
        line("وقت هادئ لدراستك", true)
        caption("اختر 25 أو 45 أو 60 دقيقة، وسنسجل نشاطك على الخادم بعد إنهاء الجلسة.")
        val clock = TextView(this).apply {
            textSize = 40f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            setPadding(dp(8), dp(24), dp(8), dp(24))
            background = shape(surface)
        }
        root.addView(clock, LinearLayout.LayoutParams(-1, -2))
        gap()
        fun tick() {
            val seconds = if (focusDeadline > 0L)
                StudyRules.remainingFocusSeconds(focusDeadline, System.currentTimeMillis())
            else focusMinutes * 60
            clock.text = "%02d:%02d".format(java.util.Locale.US, seconds / 60, seconds % 60)
        }
        tick()
        if (focusDeadline > 0L) {
            caption("الجلسة نشطة. يمكنك الخروج والعودة دون فقدان وقتها.")
            if (StudyRules.remainingFocusSeconds(focusDeadline, System.currentTimeMillis()) == 0)
                line("اكتمل الوقت! احفظ الجلسة لتسجيل دقائقك.")
            val finish = button("إنهاء الجلسة وحفظها") {}
            finish.isEnabled = !focusBusy
            finish.setOnClickListener {
                if (focusBusy) return@setOnClickListener
                confirmFocusRetry("finish") {
                focusBusy = true
                finish.isEnabled = false
                fetch("mobile/study-session.php", JSONObject().put("csrf", csrf)
                    .put("action", "finish")) { result ->
                    focusBusy = false
                    if (result.optBoolean("ok")) {
                        prefs.edit().remove(focusKey("deadline")).remove(focusKey("minutes"))
                            .remove(focusKey("uncertain")).apply()
                        focusDeadline = 0L
                        if (activeScreen == "focus")
                            focusScreen("أحسنت! تم تسجيل " + result.optInt("minutes") + " دقيقة في نشاطك الدراسي.")
                        refreshFocusStats()
                    } else if (result.optBoolean("network_error")) {
                        prefs.edit().putString(focusKey("uncertain"), "finish").apply()
                        if (activeScreen == "focus")
                            focusScreen("حالة الحفظ غير مؤكدة. تحقق من نشاطك أو تواصل مع الدعم قبل إرسال الطلب ثانية.")
                    } else if (activeScreen == "focus") focusScreen(result.optString("message", "لم تُحفظ الجلسة."))
                }
                }
            }
        } else {
            caption("المدة")
            val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (minutes in listOf(25, 45, 60)) {
                val button = makeButton("$minutes دقيقة", secondary = minutes != focusMinutes) {
                    if (!focusBusy) {
                        focusMinutes = minutes
                        prefs.edit().putInt(focusKey("minutes"), minutes).apply()
                        focusScreen()
                    }
                }
                choices.addView(button, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    marginStart = dp(2); marginEnd = dp(2)
                })
            }
            root.addView(choices)
            gap(14)
            val start = button("ابدأ جلسة التركيز") {}
            start.isEnabled = !focusBusy
            start.setOnClickListener {
                if (focusBusy || !StudyRules.validFocusMinutes(focusMinutes)) return@setOnClickListener
                confirmFocusRetry("start") {
                focusBusy = true
                start.isEnabled = false
                fetch("mobile/study-session.php", JSONObject().put("csrf", csrf)
                    .put("action", "start").put("planned", focusMinutes)) { result ->
                    focusBusy = false
                    if (result.optBoolean("ok")) {
                        focusDeadline = System.currentTimeMillis() + focusMinutes * 60_000L
                        prefs.edit().putLong(focusKey("deadline"), focusDeadline)
                            .putInt(focusKey("minutes"), focusMinutes)
                            .remove(focusKey("uncertain")).apply()
                        if (activeScreen == "focus") focusScreen("بدأت الجلسة. ركّز على هدف واحد.")
                    } else if (result.optBoolean("network_error")) {
                        prefs.edit().putString(focusKey("uncertain"), "start").apply()
                        if (activeScreen == "focus")
                            focusScreen("حالة بدء الجلسة غير مؤكدة. لا تكرر الطلب قبل التحقق من الدعم.")
                    } else if (activeScreen == "focus") focusScreen(result.optString("message", "تعذر بدء جلسة التركيز."))
                }
                }
            }
        }
        if (status.isNotBlank()) line(status)
        else if (prefs.getString(focusKey("uncertain"), "").orEmpty().isNotEmpty())
            line("هناك طلب جلسة غير مؤكد. تحقق من النشاط أو مع الدعم قبل إعادة الإرسال.")
        button("مراسلات الدعم", secondary = true) { supportScreen() }
        if (focusDeadline > 0L) {
            val ticker = object : Runnable {
                override fun run() {
                    if (activeScreen != "focus" || isFinishing || isDestroyed) return
                    tick()
                    main.postDelayed(this, 1000L)
                }
            }
            focusTicker = ticker
            main.postDelayed(ticker, 1000L)
        }
    }

    private fun refreshFocusStats() {
        fetch("mobile/bootstrap.php", showError = false) { response ->
            if (response.optBoolean("ok")) applyBootstrap(response)
        }
    }

    private fun practiceKey(suffix: String): String = "practice_${suffix}_${student.optInt("id", 0)}"

    private fun practiceBuilderScreen() {
        activeScreen = "practice_builder"
        val subjects = homepage.optJSONArray("subjects") ?: JSONArray()
        if (practiceSubjectId <= 0 && subjects.length() > 0) {
            val first = subjects.optJSONObject(0)
            practiceSubjectId = first?.optInt("id") ?: 0
            practiceSubjectName = first?.optString("name").orEmpty()
        }
        if (practiceSubjectId <= 0) {
            base("اختبار مخصص", true)
            line("لم تصل المواد الدراسية من الخادم.")
            button("تحديث المواد") { refreshHome() }
            return
        }
        base("اختبار مخصص", true)
        caption("جارٍ جلب مواضيع المادة…")
        val selectedSubject = practiceSubjectId
        fetch("mobile/topics.php?subject_id=$selectedSubject", showError = false) { response ->
            if (activeScreen != "practice_builder" || selectedSubject != practiceSubjectId) return@fetch
            practiceTopics = if (response.optBoolean("ok")) response.optJSONArray("topics") ?: JSONArray() else JSONArray()
            renderPracticeBuilder(if (response.optBoolean("ok")) "" else "تعذر تحميل المواضيع؛ يمكنك اختيار كل المادة.")
        }
    }

    private fun practicePicker(title: String, values: List<Pair<String, String>>,
                               selected: String, changed: (String) -> Unit) {
        val label = values.firstOrNull { it.first == selected }?.second ?: selected
        button("$title: $label", secondary = true) {
            AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(
                values.map { it.second }.toTypedArray(), values.indexOfFirst { it.first == selected }) { dialog, index ->
                dialog.dismiss()
                changed(values[index].first)
                renderPracticeBuilder()
            }.setNegativeButton("إلغاء", null).show()
        }
    }

    private fun renderPracticeBuilder(status: String = "") {
        activeScreen = "practice_builder"
        base("اختبار مخصص", true)
        line("أنشئ اختباراً مخصصاً", true)
        caption("حدد المادة والمواضيع وعدد الأسئلة والنوع والحالة والمؤقت. التصحيح بعد إنهاء الاختبار.")
        val subjects = homepage.optJSONArray("subjects") ?: JSONArray()
        button("المادة: $practiceSubjectName", secondary = true) {
            val names = Array(subjects.length()) { index -> subjects.optJSONObject(index)?.optString("name").orEmpty() }
            AlertDialog.Builder(this).setTitle("اختر المادة").setItems(names) { _, index ->
                val selected = subjects.optJSONObject(index) ?: return@setItems
                practiceSubjectId = selected.optInt("id")
                practiceSubjectName = selected.optString("name")
                practiceSelectedTopics.clear()
                practiceBuilderScreen()
            }.show()
        }
        caption("المواضيع: اتركها دون تحديد لاستخدام جميع أسئلة المادة.")
        for (index in 0 until practiceTopics.length()) {
            val topic = practiceTopics.optJSONObject(index) ?: continue
            val id = topic.optInt("id")
            if (id <= 0) continue
            val name = topic.optString("name") + " • " + topic.optInt("count") + " سؤال"
            lateinit var choice: Button
            choice = button((if (id in practiceSelectedTopics) "☑ " else "☐ ") + name, secondary = true) {
                if (!practiceSelectedTopics.add(id)) practiceSelectedTopics.remove(id)
                choice.text = (if (id in practiceSelectedTopics) "☑ " else "☐ ") + name
            }
        }
        stepper("عدد الأسئلة", practiceCount, 5, 50, 5) { practiceCount = it }
        practicePicker("نوع السؤال", listOf("all" to "الكل", "mcq" to "اختيارات",
            "true_false" to "صح / خطأ", "text" to "نصي", "fill" to "أكمل", "meaning" to "معاني"),
            practiceType) { practiceType = it }
        practicePicker("حالة السؤال", listOf("all" to "الكل", "unanswered" to "لم أجب",
            "wrong" to "أخطأت سابقاً", "correct" to "أجبت صحيحاً", "favorite" to "المفضلة"),
            practiceState) { practiceState = it }
        practicePicker("المؤقت", listOf("0" to "بدون مؤقت", "15" to "15 دقيقة",
            "30" to "30 دقيقة", "45" to "45 دقيقة", "60" to "60 دقيقة"),
            practiceMinutes.toString()) { practiceMinutes = it.toInt() }
        if (status.isNotBlank()) line(status)
        val pending = prefs.getInt(practiceKey("session"), 0)
        if (pending > 0) button("متابعة الاختبار الحالي", secondary = true) { loadPracticeSession(pending) }
        val create = button("ابدأ الاختبار") {}
        create.isEnabled = !practiceBusy
        create.setOnClickListener {
            val issue = PracticeRules.configurationError(practiceSubjectId, practiceCount,
                practiceType, practiceState, practiceMinutes)
            if (issue != null) { message(issue); return@setOnClickListener }
            val proceed = {
                practiceBusy = true
                create.isEnabled = false
                create.text = "جارٍ إنشاء الاختبار…"
                val topicIds = JSONArray()
                practiceSelectedTopics.forEach { topicIds.put(it) }
                val payload = JSONObject().put("csrf", csrf).put("action", "create")
                    .put("subject_id", practiceSubjectId).put("chapter_ids", topicIds)
                    .put("count", practiceCount).put("type", practiceType)
                    .put("state", practiceState).put("minutes", practiceMinutes)
                fetch("mobile/practice.php", payload) { result ->
                    practiceBusy = false
                    if (result.optBoolean("ok") && result.optInt("session_id") > 0) {
                        val id = result.optInt("session_id")
                        prefs.edit().putInt(practiceKey("session"), id)
                            .remove(practiceKey("uncertain_create"))
                            .remove(practiceKey("answers")).remove(practiceKey("index"))
                            .remove(practiceKey("deadline")).remove(practiceKey("uncertain_finish")).apply()
                        practiceSessionId = 0
                        if (activeScreen == "practice_builder") loadPracticeSession(id)
                    } else if (activeScreen == "practice_builder") {
                        val ambiguous = result.optBoolean("network_error") ||
                            (result.optBoolean("ok") && result.optInt("session_id") <= 0)
                        if (ambiguous) prefs.edit().putBoolean(practiceKey("uncertain_create"), true).apply()
                        renderPracticeBuilder(if (ambiguous)
                            "حالة الإنشاء غير مؤكدة. تحقق من المحاولات أو الدعم قبل تكراره." else
                            result.optString("message", "تعذر إنشاء الاختبار."))
                    }
                }
            }
            if (prefs.getBoolean(practiceKey("uncertain_create"), false)) {
                AlertDialog.Builder(this).setTitle("إعادة إنشاء اختبار؟")
                    .setMessage("طلب الإنشاء السابق غير مؤكد. تأكد من المحاولات أو مع الإدارة أولاً، فقد يكون الخادم أنشأ جلسة.")
                    .setPositiveButton("تحققت، إنشاء جديد") { _, _ -> proceed() }
                    .setNegativeButton("إلغاء", null).show()
            } else if (pending > 0) {
                AlertDialog.Builder(this).setTitle("اختبار لم يكتمل")
                    .setMessage("يوجد اختبار مخصص سابق. هل تريد إنشاء آخر دون حذف إجابات السابق؟")
                    .setPositiveButton("إنشاء آخر") { _, _ -> proceed() }
                    .setNegativeButton("إلغاء", null).show()
            } else proceed()
        }
    }

    private fun stopPracticeTicker() {
        practiceTicker?.let(main::removeCallbacks)
        practiceTicker = null
    }

    private fun persistPracticeAnswers() {
        val values = JSONObject()
        practiceAnswers.forEach { (id, value) -> values.put(id.toString(), value) }
        prefs.edit().putString(practiceKey("answers"), values.toString())
            .putInt(practiceKey("index"), practiceCurrent).apply()
    }

    private fun loadPracticeSession(id: Int) {
        if (id <= 0) { message("معرّف الاختبار غير صالح."); return }
        stopPracticeTicker()
        activeScreen = "practice_exam"
        base("اختبار مخصص", true)
        caption("جارٍ تحميل أسئلة الاختبار…")
        fetch("mobile/practice.php?session_id=$id") { response ->
            if (activeScreen != "practice_exam") return@fetch
            if (!response.optBoolean("ok")) {
                base("اختبار مخصص", true)
                line("تعذر استعادة جلسة الاختبار.")
                button("إعادة تحميل الجلسة") { loadPracticeSession(id) }
                return@fetch
            }
            val questions = response.optJSONArray("questions") ?: JSONArray()
            val shapes = (0 until questions.length()).mapNotNull { i ->
                val q = questions.optJSONObject(i) ?: return@mapNotNull null
                val options = q.optJSONArray("options") ?: JSONArray()
                PracticeRules.Question(q.optInt("id"), q.optString("text"),
                    (0 until options.length()).map { j ->
                        options.optJSONObject(j)?.optString("text") ?: options.optString(j)
                    })
            }
            val issue = if (shapes.size != questions.length()) "بعض الأسئلة غير قابلة للقراءة."
                else PracticeRules.questionsError(shapes)
            if (issue != null) {
                base("اختبار مخصص", true)
                line(issue)
                button("إعداد اختبار آخر", secondary = true) { practiceBuilderScreen() }
                return@fetch
            }
            if (practiceSessionId != id) {
                practiceAnswers.clear()
                val stored = try { JSONObject(prefs.getString(practiceKey("answers"), "{}") ?: "{}") }
                    catch (_: Exception) { JSONObject() }
                for (key in stored.keys()) {
                    val parsed = key.toIntOrNull() ?: continue
                    practiceAnswers[parsed] = stored.optString(key)
                }
                practiceCurrent = prefs.getInt(practiceKey("index"), 0)
            }
            practiceSessionId = id
            practiceQuestions = questions
            practiceCurrent = practiceCurrent.coerceIn(0, questions.length() - 1)
            prefs.edit().putInt(practiceKey("session"), id).apply()
            val duration = response.optJSONObject("session")?.optInt("duration_seconds") ?: 0
            practiceDeadline = prefs.getLong(practiceKey("deadline"), 0L)
            if (duration > 0 && practiceDeadline <= 0L) {
                practiceDeadline = System.currentTimeMillis() + duration * 1000L
                prefs.edit().putLong(practiceKey("deadline"), practiceDeadline).apply()
            }
            practiceUncertain = prefs.getBoolean(practiceKey("uncertain_finish"), false)
            renderPracticeQuestion()
        }
    }

    private fun renderPracticeQuestion() {
        stopPracticeTicker()
        activeScreen = "practice_exam"
        base("اختبار مخصص", true)
        val question = practiceQuestions.optJSONObject(practiceCurrent)
        if (question == null) {
            line("السؤال غير متاح. حدّث جلسة الاختبار.")
            button("تحديث") { loadPracticeSession(practiceSessionId) }
            return
        }
        val id = question.optInt("id")
        caption("السؤال ${practiceCurrent + 1} من ${practiceQuestions.length()}")
        val timerLabel = TextView(this).apply {
            textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setTextColor(accent)
        }
        if (practiceDeadline > 0L) {
            root.addView(timerLabel)
            fun updateTime() {
                val seconds = StudyRules.remainingFocusSeconds(practiceDeadline, System.currentTimeMillis())
                timerLabel.text = "⏱ %02d:%02d".format(java.util.Locale.US, seconds / 60, seconds % 60)
            }
            updateTime()
            val timer = object : Runnable {
                override fun run() {
                    if (activeScreen != "practice_exam" || isFinishing || isDestroyed) return
                    updateTime()
                    if (StudyRules.remainingFocusSeconds(practiceDeadline, System.currentTimeMillis()) == 0 &&
                        !practiceBusy && !practiceUncertain) {
                        stopPracticeTicker()
                        finishPracticeSession()
                    } else if (!practiceUncertain) main.postDelayed(this, 1000L)
                }
            }
            practiceTicker = timer
            main.postDelayed(timer, 1000L)
        }
        line(question.optString("text"), true)
        val details = listOf(question.optString("exam_year"), question.optString("exam_round"))
            .filter(String::isNotBlank)
        if (details.isNotEmpty()) caption(details.joinToString(" • "))
        caption("إجابتك")
        val options = question.optJSONArray("options") ?: JSONArray()
        if (options.length() > 0) {
            for (i in 0 until options.length()) {
                val option = options.optJSONObject(i)?.optString("text") ?: options.optString(i)
                val selected = practiceAnswers[id] == option
                button((if (selected) "✓ " else "○ ") + option, secondary = !selected) {
                    if (practiceBusy || practiceUncertain) return@button
                    practiceAnswers[id] = option
                    persistPracticeAnswers()
                    renderPracticeQuestion()
                }
            }
        } else {
            val answer = input("اكتب إجابتك هنا", multiline = true).apply {
                setText(practiceAnswers[id].orEmpty())
            }
            answer.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    practiceAnswers[id] = s?.toString().orEmpty()
                    persistPracticeAnswers()
                }
            })
        }
        if (practiceUncertain) line("حالة إنهاء الاختبار غير مؤكدة. راجع سجل الاختبارات أولاً.")
        gap(8)
        if (practiceCurrent > 0) button("السابق", secondary = true) {
            if (!practiceBusy) { practiceCurrent--; persistPracticeAnswers(); renderPracticeQuestion() }
        }
        if (practiceCurrent < practiceQuestions.length() - 1) button("التالي") {
            if (!PracticeRules.answered(practiceAnswers[id])) message("أجب عن السؤال الحالي أولاً.")
            else if (!practiceBusy) {
                practiceCurrent++; persistPracticeAnswers(); renderPracticeQuestion()
            }
        } else button("إنهاء الاختبار والتصحيح") {
            if (!PracticeRules.answered(practiceAnswers[id])) { message("أجب عن السؤال الأخير أولاً."); return@button }
            AlertDialog.Builder(this).setTitle("إنهاء الاختبار")
                .setMessage("هل تريد إرسال جميع إجاباتك للتصحيح وعرض النتيجة؟")
                .setPositiveButton("إنهاء") { _, _ -> finishPracticeSession() }
                .setNegativeButton("إلغاء", null).show()
        }
        button("العودة لإعدادات الاختبار", secondary = true) { practiceBuilderScreen() }
    }

    private fun finishPracticeSession() {
        if (practiceBusy || practiceSessionId <= 0 || practiceQuestions.length() == 0) return
        if (practiceUncertain) {
            AlertDialog.Builder(this).setTitle("حالة التصحيح غير مؤكدة")
                .setMessage("قد يكون الخادم استلم الإجابات بالفعل. افحص سجل الاختبارات أولاً. هل تأكدت وتريد إعادة الإرسال يدوياً؟")
                .setPositiveButton("تحققت، أعد الإرسال") { _, _ ->
                    practiceUncertain = false
                    sendPracticeFinish()
                }.setNegativeButton("إلغاء", null).show()
            return
        }
        sendPracticeFinish()
    }

    private fun sendPracticeFinish() {
        if (practiceBusy || practiceSessionId <= 0) return
        stopPracticeTicker()
        practiceBusy = true
        val session = practiceSessionId
        val answers = JSONObject()
        practiceAnswers.forEach { (id, answer) ->
            if (id > 0 && answer.isNotBlank()) answers.put(id.toString(), answer.trim())
        }
        if (activeScreen == "practice_exam") {
            base("تصحيح الاختبار", true)
            line("جارٍ إرسال إجاباتك للتصحيح…")
        }
        fetch("mobile/practice.php", JSONObject().put("csrf", csrf).put("action", "finish")
            .put("session_id", session).put("answers", answers)) { response ->
            practiceBusy = false
            if (response.optBoolean("ok") && response.has("score") && response.has("total")) {
                prefs.edit().remove(practiceKey("session")).remove(practiceKey("answers"))
                    .remove(practiceKey("index")).remove(practiceKey("deadline"))
                    .remove(practiceKey("uncertain_finish")).apply()
                practiceSessionId = 0
                practiceDeadline = 0L
                practiceUncertain = false
                practiceAnswers.clear()
                refreshFocusStats()
                if (activeScreen == "practice_exam") renderPracticeResult(response)
            } else {
                val ambiguous = response.optBoolean("network_error") ||
                    (response.optBoolean("ok") && (!response.has("score") || !response.has("total")))
                practiceUncertain = ambiguous
                if (ambiguous) prefs.edit().putBoolean(practiceKey("uncertain_finish"), true).apply()
                if (activeScreen == "practice_exam") {
                    renderPracticeQuestion()
                    message(if (ambiguous) "التصحيح غير مؤكد؛ تحقق من السجل قبل إعادة الإرسال." else
                        response.optString("message", "تعذر تصحيح الاختبار."))
                }
            }
        }
    }

    private fun renderPracticeResult(result: JSONObject) {
        stopPracticeTicker()
        activeScreen = "practice_result"
        base("نتيجة الاختبار المخصص", true)
        val score = result.optDouble("score", 0.0).toInt()
        line("النتيجة: $score%", true)
        caption("الإجابات الصحيحة: " + result.optInt("correct") + " من " + result.optInt("total"))
        line("مراجعة الإجابات", true)
        val review = result.optJSONArray("review") ?: JSONArray()
        var incorrect = 0
        for (i in 0 until review.length()) {
            val row = review.optJSONObject(i) ?: continue
            if (ExamRules.knownBoolean(row.opt("is_correct")) == true) continue
            incorrect++
            line(row.optString("question_text"), true)
            caption("إجابتك: " + row.optString("submitted_answer", "—"))
            caption("الإجابة الصحيحة: " + row.optString("correct_answer"))
            if (row.optString("explanation").isNotBlank()) caption("الشرح: " + row.optString("explanation"))
        }
        if (incorrect == 0) caption("لا توجد إجابات خاطئة ضمن التفاصيل التي أعادها الخادم.")
        button("سجل الاختبارات") { attemptHistoryScreen() }
        button("إنشاء اختبار آخر", secondary = true) { practiceBuilderScreen() }
    }

    private fun proposeQuestionScreen() {
        activeScreen = "question_proposal"
        base("اقترح سؤالاً", true)
        line("ساهم في بنك الأسئلة", true)
        caption("اكتب السؤال وإجابته ومصدره ليصل إلى فريق المحتوى للمراجعة قبل النشر.")
        val subjects = homepage.optJSONArray("subjects") ?: JSONArray()
        var subjectId = subjects.optJSONObject(0)?.optInt("id") ?: 0
        var chapterId = 0
        var topics = JSONArray()
        var type = "text"
        val subject = button("المادة: " + (subjects.optJSONObject(0)?.optString("name") ?: "اختر مادة"), secondary = true) {}
        val topic = button("الموضوع: جارٍ التحميل…", secondary = true) {}
        fun loadTopicsForSubject() {
            val id = subjectId
            chapterId = 0
            topic.text = "الموضوع: جارٍ التحميل…"
            if (id <= 0) { topic.text = "لا توجد مادة"; return }
            fetch("mobile/topics.php?subject_id=$id", showError = false) { response ->
                if (activeScreen != "question_proposal" || subjectId != id) return@fetch
                topics = if (response.optBoolean("ok")) response.optJSONArray("topics") ?: JSONArray() else JSONArray()
                chapterId = topics.optJSONObject(0)?.optInt("id") ?: 0
                topic.text = "الموضوع: " + (topics.optJSONObject(0)?.optString("name") ?: "اختر موضوعاً")
                if (!response.optBoolean("ok")) message("تعذر تحميل المواضيع. حاول مجدداً.")
            }
        }
        subject.setOnClickListener {
            val names = Array(subjects.length()) { i -> subjects.optJSONObject(i)?.optString("name").orEmpty() }
            AlertDialog.Builder(this).setTitle("المادة").setItems(names) { _, i ->
                val selected = subjects.optJSONObject(i) ?: return@setItems
                subjectId = selected.optInt("id")
                subject.text = "المادة: " + selected.optString("name")
                loadTopicsForSubject()
            }.show()
        }
        topic.setOnClickListener {
            val names = Array(topics.length()) { i -> topics.optJSONObject(i)?.optString("name").orEmpty() }
            AlertDialog.Builder(this).setTitle("الموضوع").setItems(names) { _, i ->
                val selected = topics.optJSONObject(i) ?: return@setItems
                chapterId = selected.optInt("id")
                topic.text = "الموضوع: " + selected.optString("name")
            }.show()
        }
        loadTopicsForSubject()
        val typeButton = button("نوع السؤال: نصي", secondary = true) {}
        val questionText = input("نص السؤال", multiline = true)
        val optionFields = (1..4).map { index -> input("الخيار $index") }
        val correctAnswer = input("الإجابة الصحيحة")
        val trueFalse = button("اختر الإجابة: صح / خطأ", secondary = true) {}
        trueFalse.setOnClickListener {
            AlertDialog.Builder(this).setTitle("الإجابة الصحيحة").setItems(arrayOf("صح", "خطأ")) { _, index ->
                correctAnswer.setText(if (index == 0) "صح" else "خطأ")
            }.show()
        }
        fun updateType() {
            optionFields.forEach { it.visibility = if (type == "mcq") View.VISIBLE else View.GONE }
            trueFalse.visibility = if (type == "true_false") View.VISIBLE else View.GONE
        }
        typeButton.setOnClickListener {
            val types = listOf("text" to "نصي", "mcq" to "اختيارات", "true_false" to "صح / خطأ",
                "fill" to "أكمل", "meaning" to "معاني")
            AlertDialog.Builder(this).setTitle("نوع السؤال")
                .setSingleChoiceItems(types.map { it.second }.toTypedArray(), types.indexOfFirst { it.first == type }) { dialog, index ->
                    dialog.dismiss()
                    type = types[index].first
                    typeButton.text = "نوع السؤال: " + types[index].second
                    updateType()
                }.setNegativeButton("إلغاء", null).show()
        }
        updateType()
        val explanation = input("شرح مختصر (اختياري)", multiline = true)
        val sourceText = input("المصدر / الملاحظة")
        val year = input("السنة (اختياري)").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val round = input("الدور (اختياري)")
        val submit = button("إرسال للمراجعة") {}
        submit.setOnClickListener {
            val options = optionFields.map { it.text.toString().trim() }.filter(String::isNotEmpty)
            val issue = ProposalRules.error(chapterId, type, questionText.text.toString(),
                correctAnswer.text.toString(), options)
            if (issue != null) { message(issue); return@setOnClickListener }
            submit.isEnabled = false
            submit.text = "جارٍ إرسال الاقتراح…"
            val payload = JSONObject().put("csrf", csrf).put("chapter_id", chapterId)
                .put("question_type", type).put("question_text", questionText.text.toString().trim())
                .put("correct_answer", correctAnswer.text.toString().trim())
                .put("explanation", explanation.text.toString().trim())
                .put("source_text", sourceText.text.toString().trim())
                .put("exam_year", year.text.toString().trim())
                .put("exam_round", round.text.toString().trim())
                .put("options", JSONArray(options))
            fetch("mobile/question-submit.php", payload) { response ->
                if (activeScreen != "question_proposal") return@fetch
                if (response.optBoolean("ok")) {
                    questionText.setText(""); correctAnswer.setText("")
                    explanation.setText(""); sourceText.setText("")
                    year.setText(""); round.setText("")
                    optionFields.forEach { it.setText("") }
                    submit.isEnabled = true
                    submit.text = "إرسال اقتراح آخر"
                    message("وصل اقتراحك إلى فريق المحتوى. تابعه في طلباتك.")
                } else if (response.optBoolean("network_error")) {
                    submit.text = "حالة الإرسال غير مؤكدة"
                    message("تحقق من طلباتك قبل إرسال السؤال ثانية؛ فقد يكون وصل بالفعل.")
                } else {
                    submit.isEnabled = true
                    submit.text = "إرسال للمراجعة"
                }
            }
        }
        button("عرض طلباتي", secondary = true) { myQuestionSubmissionsScreen() }
    }

    private fun myQuestionSubmissionsScreen() {
        activeScreen = "question_submissions"
        base("طلباتي", true)
        line("جارٍ تحميل اقتراحات الأسئلة…")
        fetch("mobile/question-submit.php") { response ->
            if (activeScreen != "question_submissions") return@fetch
            base("طلباتي", true)
            if (!response.optBoolean("ok")) {
                line("تعذر عرض طلبات الأسئلة.")
                button("إعادة المحاولة") { myQuestionSubmissionsScreen() }
                return@fetch
            }
            val rows = response.optJSONArray("submissions") ?: JSONArray()
            if (rows.length() == 0) caption("لم ترسل أسئلة بعد. ستظهر هنا مع حالة مراجعتها.")
            val status = mapOf("new" to "جديد", "reviewing" to "قيد المراجعة",
                "accepted" to "مقبول", "published" to "تم النشر", "rejected" to "مرفوض")
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i) ?: continue
                caption(status[item.optString("status")] ?: item.optString("status"))
                line(item.optString("question_text"), true)
                caption(item.optString("subject_name") + " • " + item.optString("chapter_name"))
                caption(item.optString("created_at"))
                if (item.optString("admin_note").isNotBlank())
                    caption("ملاحظة المراجعة: " + item.optString("admin_note"))
            }
            button("اقترح سؤالاً آخر") { proposeQuestionScreen() }
            button("تحديث الطلبات", secondary = true) { myQuestionSubmissionsScreen() }
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
            "practice_exam" -> { stopPracticeTicker(); practiceBuilderScreen() }
            "practice_result" -> attemptHistoryScreen()
            "daily_challenge", "contest", "achievements" -> {
                val previous = if (competitionHistory.isEmpty()) "home" else competitionHistory.removeLast()
                when (previous) {
                    "daily_challenge" -> loadDailyChallenge()
                    "contest" -> loadContest()
                    "achievements" -> loadAchievements()
                    else -> showHome(homepage)
                }
            }
            "exam" -> leaveExam()
            "topics" -> when (topicsReturnScreen) {
                "home" -> showHome(homepage)
                "tests" -> testsTabScreen()
                else -> subjectsScreen()
            }
            "subjects" -> showHome(homepage)
            "tests", "success" -> showHome(homepage)
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
        stopHomeHeroTicker()
        stopFocusTicker()
        stopPracticeTicker()
        heroJobs.shutdownNow()
        jobs.shutdownNow()
        super.onDestroy()
    }
}
