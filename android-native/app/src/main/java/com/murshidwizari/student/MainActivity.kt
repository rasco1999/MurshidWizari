package com.murshidwizari.student

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.biometrics.BiometricManager
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
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
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import android.text.InputType
import android.text.InputFilter
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Executor

/** 100% Android native UI: no WebView, no embedded website, no hardcoded secrets. */
class MainActivity : Activity() {
    private val typography by lazy { MurshidTypography(this) }
    private val offlineExams by lazy { OfflineExamBank(this) }
    private var offlineChapterId = 0
    private var offlineSyncInFlight = false
    private val jobs = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var api: ApiClient
    private lateinit var root: LinearLayout
    private lateinit var headerHost: LinearLayout
    private var contentScroll: ScrollView? = null
    private var selectedPredictionSubject = 0
    private var predictionGeneration = 0
    private var serviceTicker: Runnable? = null
    private var appForeground = false
    private var backgroundEnteredAt = 0L
    private var resumeWelcomeOverlay: View? = null
    private var resumeWelcomeAfterUnlock = false
    private var lastPopupPoll = 0L
    private var lastSchoolAdsBadgePoll = 0L
    private var lastHeartbeatPoll = 0L
    private var schoolAdsUnread = 0
    private var schoolAdsBadgeView: TextView? = null
    private var popupChecking = false
    private var currentAdDialog: AlertDialog? = null
    private var examSubmitButton: Button? = null
    private var csrf = ""
    private var student = JSONObject()
    private var homepage = JSONObject()
    private var homeFeatureFlags = JSONObject()
    private var homeMockExamHost: LinearLayout? = null
    private var homeQuickToolsHost: LinearLayout? = null
    private var topicsSubjectId = 0
    private var topicsSubjectName = ""
    private var topicsReturnScreen = "home"
    private var predictionsReturnScreen = "predictions_tab"
    private var schoolAdReturnScreen = "home"
    private var chapterId = 0
    private var chapterName = ""
    private var examFromSearch = false
    private var examSearchTarget = ""
    private var lastSearchQuery = ""
    private var lastSearchResults = JSONArray()
    private var examQuestions = JSONArray()
    private var examStartedAt = 0L
    private val examIssues = HashMap<Int, String>()
    private var questionIndex = 0
    private var busyAnswer = false
    private var submissionUncertain = false
    private var activeScreen = "welcome"
    private var offlineCoachMode = false
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
    private var dailyGivenAnswer = ""
    private var dailyCorrectAnswer = ""
    private var dailyExplanation = ""
    private var dailyNextResetAt = ""
    private var dailyStatusMessage = ""
    private var contest = JSONObject()
    private var season = JSONObject()
    private var contestMode = "contest"
    private var contestShowAll = false
    private var achievements = JSONArray()
    private var achievementStats = JSONObject()
    private var achievementFilter = "all"
    private var night = false
    private var instructionsReturnScreen = "login"
    private var biometricAuthInProgress = false
    private var biometricOverlay: FrameLayout? = null
    // Remove private account views from the window while the biometric shield is active.
    private var protectedContentWhileLocked: View? = null
    private var biometricLastSuccessAt = 0L
    private var biometricBackgroundAt = 0L
    private var reminderPermissionPending = false
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
    private var homeHeroOutgoing: View? = null
    private val heroBitmaps = HashMap<Int, Bitmap>()
    private val heroPending = HashSet<Int>()
    private val heroJobs = Executors.newFixedThreadPool(2)
    private var lastValidatedInternet: Boolean? = null
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var sessionGeneration = 0
    private var screenGeneration = 0
    private val avatarCache = android.util.LruCache<String, Bitmap>(8)
    private var avatarGeneration = 0
    private var activeAvatar: java.lang.ref.WeakReference<ImageView>? = null
    private var paymentBusy = false
    private var paymentPlan = ""
    private var plansReturnScreen = "home"
    private var checkoutReturnPending = false
    private var recoveryBusy = false
    private var recoverReturnScreen = "login"
    private var registerReturnScreen = "guest"
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
    // These three palettes mirror the iPhone's account personalization choices.
    private var accentStyle = AppearanceRules.ROYAL
    private var appearanceMode = AppearanceRules.SYSTEM
    private val gold get() = when (accentStyle) {
        AppearanceRules.VIOLET -> Color.rgb(229, 130, 207)
        AppearanceRules.AURORA -> Color.rgb(74, 213, 195)
        else -> Color.rgb(245, 184, 61)
    }
    private val navy get() = when (accentStyle) {
        AppearanceRules.VIOLET -> Color.rgb(42, 25, 76)
        AppearanceRules.AURORA -> Color.rgb(7, 60, 72)
        else -> Color.rgb(11, 31, 51)
    }
    private val blue get() = when (accentStyle) {
        AppearanceRules.VIOLET -> Color.rgb(185, 88, 220)
        AppearanceRules.AURORA -> Color.rgb(29, 154, 215)
        else -> Color.rgb(45, 108, 223)
    }
    // UIKit's systemGroupedBackground / secondarySystemGroupedBackground light and dark tokens.
    private val appColor get() = if (night) Color.BLACK else Color.rgb(242,242,247)
    private val textColor get() = if (night) Color.WHITE else Color.rgb(28,28,30)
    private val surface get() = if (night) Color.rgb(28,28,30) else Color.WHITE
    private val accent get() = blue
    private val mutedText get() = if (night) Color.rgb(174,174,178) else Color.rgb(110,110,115)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = ApiClient(this)
        val systemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        accentStyle = AppearanceRules.normalizeAccent(prefs.getString("accent_style", null))
        appearanceMode = AppearanceRules.normalizeMode(prefs.getString("appearance_mode", null),
            if (prefs.contains("night")) prefs.getBoolean("night", false) else null)
        night = AppearanceRules.isDark(appearanceMode, systemNight)
        setTheme(if (night) R.style.MurshidDark else R.style.MurshidLight)
        showAcademicWelcome()
        val minimumWelcomeMs = 3000L // v4.8.18 Build 498 cinematic welcome.
        val readyAt = SystemClock.elapsedRealtime() + minimumWelcomeMs
        val launchDestination = OfflineStartupRules.destination(
            hasAnyInternetNetwork(), api.hasRememberedSession(),
            OfflineCoachAccess(prefs).studentId()
        )
        if (launchDestination == OfflineStartupRules.Destination.OFFLINE_COACH) {
            main.postDelayed({
                if (activeScreen == "welcome") showOfflineCoachHome()
            }, minimumWelcomeMs)
        } else if (launchDestination == OfflineStartupRules.Destination.RESTORE_SERVER_SESSION) {
            main.postDelayed({
                if (activeScreen == "welcome") {
                    activeScreen = "restoring"
                    base("المرشد الوزاري")
                    caption("جارٍ استعادة حسابك…")
                }
            }, minimumWelcomeMs)
            if (!hasAnyInternetNetwork() && canOpenOfflineCoach()) {
                main.postDelayed({
                    if (activeScreen == "welcome" || activeScreen == "restoring")
                        showOfflineCoachHome()
                }, minimumWelcomeMs)
            } else {
                fetch("mobile/bootstrap.php", showError = false) { response ->
                    main.postDelayed({
                        if (activeScreen == "welcome" || activeScreen == "restoring") {
                            if (response.optBoolean("ok")) showHome(response)
                            else if (response.optBoolean("network_error") && canOpenOfflineCoach())
                                showOfflineCoachHome()
                            else {
                                activeScreen = "restore_error"
                                base("استعادة الاتصال")
                                caption(response.optString("message", "تعذر الاتصال الآن. بيانات جلستك محفوظة."))
                                if (canOpenOfflineCoach()) button("مدربي الذكي دون إنترنت", secondary = true) {
                                    showOfflineCoachHome()
                                }
                                button("إعادة المحاولة") { refreshHome() }
                                button("العودة لتسجيل الدخول", secondary = true) { loginScreen() }
                            }
                        }
                    }, (readyAt - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                }
            }
        } else {
            main.postDelayed({ if (activeScreen == "welcome") guestLandingScreen() }, minimumWelcomeMs)
        }
    }

    /** iOS V31 uses actual Face ID, not a cosmetic switch; Android verifies a real device biometric. */
    private fun deviceBiometricAvailable(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val manager = getSystemService(BiometricManager::class.java) ?: return false
            val status = if (Build.VERSION.SDK_INT >= 30)
                manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            else manager.canAuthenticate()
            return status == BiometricManager.BIOMETRIC_SUCCESS
        }
        if (Build.VERSION.SDK_INT == 28) return true // API 28's prompt verifies enrollment.
        val fingerprint = getSystemService(Context.FINGERPRINT_SERVICE) as? FingerprintManager
        return fingerprint?.isHardwareDetected == true && fingerprint.hasEnrolledFingerprints()
    }

    private fun authenticateBiometric(reason: String, onResult: (Boolean) -> Unit) {
        if (biometricAuthInProgress) return
        if (!deviceBiometricAvailable()) {
            message("لا توجد بصمة وجه أو إصبع مهيأة في إعدادات الجهاز.")
            onResult(false)
            return
        }
        biometricAuthInProgress = true
        var finished = false
        fun finish(success: Boolean) {
            if (finished) return
            finished = true
            biometricAuthInProgress = false
            if (success) {
                biometricLastSuccessAt = SystemClock.elapsedRealtime()
                biometricBackgroundAt = 0L
                prefs.edit().remove("biometric_background_at").remove("biometric_unlock_required").apply()
            }
            onResult(success)
        }
        try {
            val cancellation = CancellationSignal()
            if (Build.VERSION.SDK_INT >= 28) {
                val executor = Executor { command -> runOnUiThread(command) }
                val prompt = BiometricPrompt.Builder(this)
                    .setTitle("المرشد الوزاري")
                    .setSubtitle(reason)
                    .setNegativeButton("إلغاء", executor) { _, _ -> cancellation.cancel() }
                    .build()
                prompt.authenticate(cancellation, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        finish(true)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        finish(false)
                    }
                })
            } else {
                val fingerprint = getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager
                val guide = AlertDialog.Builder(this).setTitle("تأكيد بصمة الإصبع")
                    .setMessage("ضع إصبعك على مستشعر البصمة لفتح المرشد الوزاري.")
                    .setNegativeButton("إلغاء") { _, _ -> cancellation.cancel(); finish(false) }
                    .create()
                guide.setOnCancelListener { cancellation.cancel(); finish(false) }
                guide.show()
                fingerprint.authenticate(null, cancellation, 0, object : FingerprintManager.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult) {
                        guide.dismiss()
                        finish(true)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        guide.dismiss()
                        finish(false)
                    }
                }, main)
            }
        } catch (_: Exception) {
            finish(false)
            message("تعذر استخدام البصمة. تحقق من إعدادات الأمان في جهازك.")
        }
    }

    private fun showBiometricLock() {
        if (biometricOverlay != null || biometricAuthInProgress) return
        // A cancelled/failed unlock must not become an unlocked session if
        // Android kills this Activity before the next twenty-second interval.
        prefs.edit().putBoolean("biometric_unlock_required", true).commit()
        val parent = findViewById<FrameLayout>(android.R.id.content)
        protectedContentWhileLocked = if (parent.childCount > 0) parent.getChildAt(parent.childCount - 1) else null
        // Keeping a covered account in the hierarchy can still expose it to
        // accessibility services on some Android versions. Detach it completely.
        parent.removeAllViews()
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            contentDescription = "المرشد الوزاري مقفل إلى حين نجاح التحقق بالبصمة"
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(IosGlyphView(this@MainActivity, "lock", blue),
                LinearLayout.LayoutParams(dp(58), dp(58)).apply { bottomMargin = dp(20) })
            addView(uiText("التطبيق مقفل", 24f, true, Color.WHITE).apply { gravity = Gravity.CENTER })
            addView(uiText("استخدم بصمة الوجه أو الإصبع للعودة إلى حسابك.",
                15f, tint = Color.LTGRAY).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            addView(makeButton("فتح باستخدام البصمة") { unlock() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        }
        overlay.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        parent.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        biometricOverlay = overlay
        unlock()
    }

    private fun unlock() {
        if (biometricOverlay == null) return
        authenticateBiometric("افتح المرشد الوزاري باستخدام البصمة") { success ->
            if (success) {
                val parent = biometricOverlay?.parent as? FrameLayout
                parent?.removeView(biometricOverlay)
                biometricOverlay = null
                protectedContentWhileLocked?.let { content ->
                    (content.parent as? ViewGroup)?.removeView(content)
                    if (parent != null) parent.addView(content, FrameLayout.LayoutParams(-1, -1))
                    else setContentView(content)
                }
                protectedContentWhileLocked = null
                main.post { openPushInboxIfReady() }
                if (resumeWelcomeAfterUnlock) {
                    resumeWelcomeAfterUnlock = false
                    replayWelcomeAfterBackground()
                }
            } else message("لم يتم فتح التطبيق. استخدم زر فتح بالبصمة للمحاولة مجددًا.")
        }
    }

    private fun setStudyReminder(enabled: Boolean) {
        if (!enabled) {
            DailyStudyReminder.disable(this)
            accountScreen()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            reminderPermissionPending = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), DailyStudyReminder.requestCode)
            return
        }
        val hour = prefs.getInt("study_reminder_hour", 19)
        val minute = prefs.getInt("study_reminder_minute", 0)
        if (DailyStudyReminder.enable(this, hour, minute)) {
            message("تم تفعيل تذكير الدراسة اليومي.")
        } else message("اسمح بإشعارات التطبيق من إعدادات الجهاز لتفعيل التذكير.")
        accountScreen()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                             grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == StudentPush.requestCode) {
            StudentPush.refresh(this)
            if (activeScreen == "notifications") notificationsScreen()
            return
        }
        if (requestCode != DailyStudyReminder.requestCode || !reminderPermissionPending) return
        reminderPermissionPending = false
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) setStudyReminder(true)
        else {
            message("لم يتم تفعيل التذكير دون إذن الإشعارات.")
            if (activeScreen == "account") accountScreen()
        }
    }
    private fun shape(color: Int, radius: Float = dp(22).toFloat()): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(size: Int): Int = (size * resources.displayMetrics.density).toInt()
    private fun contentWidth(): Int = VisualRules.contentWidthDp(resources.configuration.screenWidthDp)?.let(::dp) ?: -1
    private fun styleSystemBars() {
        // Android 15 draws transparent system bars over the activity. A light
        // screen must request dark status/navigation glyphs or they disappear
        // against the iOS-style grouped background.
        window.statusBarColor = appColor
        window.navigationBarColor = appColor
        window.decorView.systemUiVisibility = if (night) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
    }
    private fun setupRoot(horizontalPadding: Int = 16, topPadding: Int = 16, tab: String? = null) {
        screenGeneration++
        styleSystemBars()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(appColor)
        }
        headerHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(horizontalPadding), 0, dp(horizontalPadding), 0)
        }
        container.addView(headerHost, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(appColor)
            clipToPadding = false
        }
        contentScroll = scroll
        screenFrame = FrameLayout(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(horizontalPadding), dp(topPadding), dp(horizontalPadding), dp(28))
        }
        screenFrame!!.addView(root, FrameLayout.LayoutParams(contentWidth(), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        scroll.addView(screenFrame)
        container.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val examNavigation = tab == null && activeScreen == "exam" &&
            VisualRules.showExamNavigation(questionIndex, examQuestions.length())
        if (tab != null || examNavigation) {
            val footer = if (tab != null) mainTabBar(tab) else examNavigationFooter()
            container.addView(footer, LinearLayout.LayoutParams(-1, dp(if (tab != null) 61 else 82)))
            container.viewTreeObserver.addOnGlobalLayoutListener {
                val visible = Rect()
                container.getWindowVisibleDisplayFrame(visible)
                val keyboard = container.rootView.height - visible.bottom > dp(150)
                footer.visibility = if (keyboard) View.GONE else View.VISIBLE
            }
        }
        if (Build.VERSION.SDK_INT >= 35) container.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, maxOf(safe.bottom, keyboard.bottom))
            insets
        }
        setProtectedContent(container)
    }

    /** Never attach private account views while the device awaits biometric unlock. */
    private fun setProtectedContent(content: View) {
        if (biometricOverlay != null) {
            // Async bootstrap may redraw the app while locked. Keep only the
            // black shield attached; display the latest content after unlock.
            protectedContentWhileLocked = content
            return
        }
        protectedContentWhileLocked = null
        setContentView(content)
    }
    private fun base(title: String, showBack: Boolean = false, tab: String? = null) {
        stopHomeHeroTicker()
        setupRoot(topPadding = if (tab == null) 16 else 0, tab = tab)
        when {
            tab != null -> tabHeader(title, tab)
            showBack -> backHeader(title)
            else -> title(title)
        }
        if (tab != null || showBack) {
            val header = root.getChildAt(0)
            root.removeView(header)
            headerHost.addView(header)
        }
    }
    private fun backHeader(title: String) {
        val backLabel = if (title == "الاشتراك" && plansReturnScreen == "account") "حسابي" else "رجوع"
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
            addView(uiText(backLabel, 15f, tint = blue))
        }
        row.addView(tappable(back, backLabel) { navigateBack() }, LinearLayout.LayoutParams(dp(72), dp(44)))
        row.addView(uiText(title, 18f, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, -2, 1f))
        if (activeScreen == "exam" && questionIndex < examQuestions.length()) {
            val question = examQuestions.optJSONObject(questionIndex)
            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (question != null) {
                val favoriteButton = TextView(this).apply {
                    text = if (question.optBoolean("favorite")) "♥" else "♡"
                    textSize = 28f
                    gravity = Gravity.CENTER
                    setTextColor(blue)
                    contentDescription = "المفضلة"
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val id = question.optInt("id")
                        val action = if (question.optBoolean("favorite")) "unfavorite" else "favorite"
                        val body = JSONObject().put("csrf", csrf).put("question_id", id).put("action", action)
                        fetch("api/question-tools.php", body) { result ->
                            if (result.optBoolean("ok")) {
                                question.put("favorite", result.optBoolean("favorite"))
                                text = if (question.optBoolean("favorite")) "♥" else "♡"
                            }
                        }
                    }
                }
                actionRow.addView(favoriteButton, LinearLayout.LayoutParams(dp(42), dp(42)))
                val more = TextView(this).apply {
                    text = "•••"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(blue)
                    contentDescription = "خيارات السؤال"
                    background = shape(if (night) Color.rgb(43, 43, 46) else Color.rgb(242, 243, 246), dp(24).toFloat())
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { anchor ->
                        val menu = PopupMenu(this@MainActivity, anchor).apply {
                            this.menu.add(0, 1, 0, "ملاحظتي الخاصة")
                            this.menu.add(0, 2, 1, "الإبلاغ عن السؤال")
                            this.menu.add(0, 3, 2, "طلب أسئلة جديدة")
                            this.menu.add(0, 4, 3, "مشاركة السؤال")
                            setOnMenuItemClickListener { item ->
                                when (item.itemId) {
                                    1 -> questionNote(question.optInt("id"))
                                    2 -> reportQuestion(question.optInt("id"))
                                    3 -> questionRequest()
                                    4 -> {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT,
                                                question.optString("text") + "\nhttps://www.mur-iq.com/")
                                        }
                                        startActivity(Intent.createChooser(share, "مشاركة السؤال"))
                                    }
                                }
                                true
                            }
                        }
                        menu.show()
                    }
                }
                actionRow.addView(more, LinearLayout.LayoutParams(dp(42), dp(42)))
            }
            row.addView(actionRow, LinearLayout.LayoutParams(dp(88), dp(44)))
        } else row.addView(Space(this), LinearLayout.LayoutParams(dp(72), dp(1)))
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
            appearanceMode = if (night) AppearanceRules.LIGHT else AppearanceRules.DARK
            night = !night
            prefs.edit().putString("appearance_mode", appearanceMode).remove("night").apply()
            setTheme(if (night) R.style.MurshidDark else R.style.MurshidLight)
            showHome(homepage)
        }, LinearLayout.LayoutParams(dp(42), dp(44)))
        else row.addView(Space(this), LinearLayout.LayoutParams(dp(42), dp(44)))
        root.addView(row, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })
    }
    private fun mainTabBar(active: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setBackgroundColor(if (night) Color.rgb(37,37,39) else Color.rgb(250,250,252))
        elevation = dp(1).toFloat()
        for ((name, title, icon) in VisualRules.mainTabs) {
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
                        "predictions" -> predictionsTabScreen()
                        "ads" -> advertisementsTabScreen()
                        "account" -> accountScreen()
                    }
                }
            }
            if (name == "account" && !selected) {
                // A neutral profile silhouette is the appropriate fallback.
                // Keep the exact Build498 golden icon for the launcher only.
                val tabAvatar = ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_account_placeholder)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.WHITE)
                    }
                    clipToOutline = true
                }
                item.addView(tabAvatar, LinearLayout.LayoutParams(dp(27), dp(27)))
                fetchAvatarInto(tabAvatar, student.optString("avatar_url").ifBlank { homepage.optString("avatar_url") },
                    student.optString("avatar_revision").ifBlank { homepage.optString("avatar_revision") })
            } else if (name == "ads") {
                val iconHolder = FrameLayout(this@MainActivity)
                iconHolder.addView(IosGlyphView(this@MainActivity, icon, color),
                    FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
                val badge = TextView(this@MainActivity).apply {
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    typeface = typography.strong()
                    includeFontPadding = false
                    background = shape(Color.rgb(226, 43, 61), dp(10).toFloat())
                }
                iconHolder.addView(badge, FrameLayout.LayoutParams(dp(19), dp(19),
                    Gravity.TOP or Gravity.RIGHT))
                schoolAdsBadgeView = badge
                refreshSchoolAdsBadge()
                item.addView(iconHolder, LinearLayout.LayoutParams(dp(36), dp(29)))
            } else {
                item.addView(IosGlyphView(this@MainActivity, icon, color),
                    LinearLayout.LayoutParams(dp(23), dp(23)))
            }
            item.addView(TextView(this@MainActivity).apply {
                typography.apply(this, title, true)
                textSize = MurshidTypeScale.sizeSp(10f)
                setTextColor(color)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, dp(3), 0, 0)
            })
            addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun examNavigationFooter(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setBackgroundColor(if (night) Color.rgb(37,37,39) else Color.rgb(249,249,251))
        elevation = dp(8).toFloat()
        val hasQuestion = questionIndex < examQuestions.length()
        val submitted = examQuestions.optJSONObject(questionIndex)?.let(::isSubmitted) == true
        val canNext = hasQuestion && ExamRules.canAdvance(submitted, busyAnswer, submissionUncertain)
        val canPrevious = questionIndex > 0 && !busyAnswer
        val prev = uiText("‹ السابق", 16f, true, if (canPrevious) blue else mutedText).apply {
            gravity = Gravity.CENTER
            background = shape(if (night) Color.BLACK else Color.WHITE, dp(14).toFloat())
            alpha = if (canPrevious) 1f else 0.55f
            isEnabled = canPrevious
            if (canPrevious) setOnClickListener {
                if (!busyAnswer) { questionIndex--; renderQuestion() }
            }
            contentDescription = "السؤال السابق"
        }
        addView(prev, LinearLayout.LayoutParams(dp(104), -1))
        addView(Space(this@MainActivity), LinearLayout.LayoutParams(0, dp(1), 1f))
        val next = uiText(if (questionIndex == examQuestions.length() - 1) "عرض النتيجة ›"
            else "التالي ›", 16f, true, if (canNext) blue else mutedText).apply {
            gravity = Gravity.CENTER
            background = shape(if (night) Color.BLACK else Color.WHITE, dp(14).toFloat())
            alpha = if (canNext) 1f else 0.55f
            isEnabled = canNext
            if (canNext) setOnClickListener {
                val index = ExamRules.nextIndex(questionIndex, examQuestions.length(), submitted, busyAnswer, submissionUncertain)
                if (index != questionIndex) { questionIndex = index; renderQuestion() }
            }
            contentDescription = "السؤال التالي"
        }
        addView(next, LinearLayout.LayoutParams(dp(104), -1))
    }
    private fun title(text: String) {
        root.addView(TextView(this).apply {
            typography.apply(this, text, true); textSize = MurshidTypeScale.sizeSp(25f)
            gravity = Gravity.CENTER
            setTextColor(accent)
            setPadding(dp(4), dp(10), dp(4), dp(20))
        })
    }
    private fun caption(text: String) {
        root.addView(TextView(this).apply {
            typography.apply(this, text); textSize = 15f; setTextColor(textColor)
            setPadding(dp(4), dp(8), dp(4), dp(10))
        })
    }
    private fun uiText(value: String, size: Float, bold: Boolean = false,
                       tint: Int = textColor): TextView = TextView(this).apply {
        typography.apply(this, value, bold)
        textSize = MurshidTypeScale.sizeSp(size)
        setTextColor(tint)
        includeFontPadding = true
        setLineSpacing(dp(1).toFloat(), 1.03f)
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
        background = shape(Color.argb(26, Color.red(color), Color.green(color), Color.blue(color)),
            dp(VisualRules.iconBubbleRadius(size)).toFloat())
        val glyphSize = VisualRules.iconBubbleGlyphSize(size)
        addView(IosGlyphView(this@MainActivity, symbol, color),
            FrameLayout.LayoutParams(dp(glyphSize), dp(glyphSize), Gravity.CENTER))
        minimumWidth = dp(size)
        minimumHeight = dp(size)
    }

    /** Both iPhone lists put a magnifier on the right side of their dark search field. */
    private fun searchControl(hintText: String, cornerDp: Int = 16): Pair<FrameLayout, EditText> {
        val frame = FrameLayout(this).apply {
            background = shape(surface, dp(cornerDp).toFloat())
        }
        val input = EditText(this).apply {
            hint = hintText
            typeface = typography.typeface(hintText)
            textSize = 16f
            setSingleLine(true)
            setTextColor(textColor)
            setHintTextColor(mutedText)
            background = null
            setPadding(dp(13), dp(12), dp(45), dp(12))
        }
        frame.addView(input, FrameLayout.LayoutParams(-1, -2))
        frame.addView(IosGlyphView(this, "search", mutedText),
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                .apply { rightMargin = dp(14) })
        frame.setOnClickListener { input.requestFocus() }
        return frame to input
    }

    private fun sectionHeader(name: String, subtitle: String, symbol: String,
                              parent: LinearLayout = root) {
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
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7); bottomMargin = dp(12) })
    }

    private fun tappable(view: View, description: String, action: () -> Unit): View = view.apply {
        isClickable = true
        isFocusable = true
        contentDescription = description
        foreground = RippleDrawable(ColorStateList.valueOf(Color.argb(25, Color.red(blue),
            Color.green(blue), Color.blue(blue))), null, shape(Color.WHITE))
        setOnClickListener { action() }
    }

    private fun uiRow(name: String, description: String, symbol: String, action: () -> Unit): View {
        val card = uiCard()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val rowIcon = if (symbol == "subscription-active") ImageView(this).apply {
            setImageResource(R.drawable.ic_subscription_active)
            scaleType = ImageView.ScaleType.FIT_CENTER
        } else iconBubble(symbol, 50)
        row.addView(rowIcon, LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginEnd = dp(13) })
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
        name.contains("فيزياء") -> "atom"
        name.contains("كيمي") -> "flask"
        name.contains("أحياء") -> "heart"
        name.contains("إنك") || name.contains("انك") -> "book"
        else -> "book"
    }
    private fun line(text: String, prominent: Boolean = false) {
        root.addView(TextView(this).apply {
            typography.apply(this, text, prominent)
            textSize = if (prominent) 19f else 16f
            setTextColor(textColor)
            setPadding(dp(12), dp(13), dp(12), dp(13))
            background = shape(surface)
        })
        gap()
    }
    private fun gap(n: Int = 10) { root.addView(Space(this).apply { minimumHeight = dp(n) }) }
    private fun input(hint: String, password: Boolean = false, multiline: Boolean = false,
                      parent: LinearLayout = root): EditText {
        val field = EditText(this).apply {
            this.hint = hint
            typeface = typography.typeface(hint)
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
        parent.addView(field, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        return field
    }
    private fun makeButton(text: String, secondary: Boolean = false, action: () -> Unit): Button =
        object : Button(this) {
            override fun drawableStateChanged() {
                super.drawableStateChanged()
                // SwiftUI ButtonStyles reduce the whole control to 48% opacity when disabled.
                alpha = if (isEnabled) 1f else 0.48f
            }
        }.apply {
            typography.apply(this, text, true); textSize = 15f
            isAllCaps = false
            setSingleLine(false)
            maxLines = 3
            minHeight = dp(if (secondary) VisualRules.secondaryButtonHeight else VisualRules.primaryButtonHeight)
            setPadding(dp(if (secondary) 14 else 16), dp(8), dp(if (secondary) 14 else 16), dp(8))
            setTextColor(if (secondary) accent else Color.WHITE)
            val radius = dp(if (secondary) VisualRules.secondaryButtonRadius else VisualRules.primaryButtonRadius).toFloat()
            val fill = if (secondary) GradientDrawable().apply {
                // SwiftUI: blue at 8% opacity, 22%-opacity 1pt blue border.
                setColor(Color.argb(20, Color.red(blue), Color.green(blue), Color.blue(blue)))
                cornerRadius = radius
                setStroke(dp(1).coerceAtLeast(1),
                    Color.argb(56, Color.red(blue), Color.green(blue), Color.blue(blue)))
            }
                else GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(blue, Color.rgb(23,71,173)))
                    .apply { cornerRadius = radius }
            background = RippleDrawable(ColorStateList.valueOf(
                if (secondary) Color.argb(16, Color.red(blue), Color.green(blue), Color.blue(blue))
                else Color.argb(60, 120, 160, 230)), fill, shape(Color.WHITE, radius))
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> if (view.isEnabled) {
                        if (!secondary) view.alpha = 0.82f
                        view.animate().cancel()
                        view.animate().scaleX(0.985f).scaleY(0.985f)
                            .setDuration(85).withLayer().start()
                    }
                    MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                        view.alpha = if (view.isEnabled) 1f else 0.48f
                        view.animate().cancel()
                        view.animate().scaleX(1f).scaleY(1f)
                            .setDuration(85).withLayer().start()
                    }
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

    /** Native equivalent of the iPhone's segmented registration pickers. */
    private fun segmentedChoices(labels: List<String>, changed: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            minimumHeight = dp(48)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = shape(if (night) Color.rgb(37, 38, 42) else Color.rgb(238, 239, 243),
                dp(14).toFloat())
        }
        val options = labels.map { label ->
            uiText(label, 14f, true).apply {
                gravity = Gravity.CENTER
                minimumHeight = dp(40)
                isClickable = true
                isFocusable = true
                contentDescription = label
            }.also { row.addView(it, LinearLayout.LayoutParams(0, -1, 1f)) }
        }
        fun select(index: Int) {
            options.forEachIndexed { at, text ->
                val selected = index == at
                text.background = shape(if (selected) blue else Color.TRANSPARENT, dp(11).toFloat())
                text.setTextColor(if (selected) Color.WHITE else textColor)
                text.isSelected = selected
            }
        }
        options.forEachIndexed { index, option ->
            option.setOnClickListener {
                select(index)
                changed(index)
            }
        }
        select(0)
        return row
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
            typography.apply(this, label); textSize = 16f; setTextColor(textColor)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        lateinit var valueText: TextView
        val subtract = makeButton("−", secondary = true) {
            value = (value - step).coerceAtLeast(min)
            valueText.text = value.toString()
            changed(value)
        }
        row.addView(subtract, LinearLayout.LayoutParams(dp(48), dp(44)))
        valueText = TextView(this).apply {
            text = value.toString(); textSize = 18f; typeface = typography.strong()
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
    private fun needsMutationReconciliation(result: JSONObject): Boolean =
        NetworkRules.requiresReconciliation(result.optBoolean("network_error"),
            result.optBoolean("transport_uncertain"))
    private fun expireSession() {
        OfflineCoachAccess(prefs).clear()
        offlineCoachMode = false
        sessionGeneration++
        StudentPush.detach(this)
        intent.removeExtra(StudentPush.inboxExtra)
        serviceTicker?.let { main.removeCallbacks(it) }; serviceTicker = null
        popupChecking = false; lastPopupPoll = 0L
        currentAdDialog?.dismiss(); currentAdDialog = null
        api.clear()
        // Session expiry preserves encrypted pending answers for later re-authentication.
        csrf = ""; student = JSONObject(); homepage = JSONObject(); subscribed = false
        homeFeatureFlags = JSONObject()
        homeInsightsLoaded = false; homeInsights = JSONObject(); homeWeekly = JSONObject()
        busyAnswer = false; submissionUncertain = false; dailyBusy = false; dailyUncertain = false
        prefs.edit().remove("last_payment_order").apply()
        guestLandingScreen()
        message("انتهت جلسة الدخول. سجّل الدخول مجدداً.")
    }
    private fun fetch(path: String, body: JSONObject? = null, showError: Boolean = true, done: (JSONObject) -> Unit) {
        val generation = sessionGeneration
        jobs.execute {
            val startedAt = SystemClock.elapsedRealtime()
            val result = try { api.call(path, body) } catch (e: Exception) {
                JSONObject().put("ok", false).put("network_error", true)
                    .put("message", NetworkRules.message(e, body != null))
            }
            // Local aggregates only: never store account or question contents.
            LocalReliability(prefs).record(path, SystemClock.elapsedRealtime() - startedAt,
                result.optBoolean("ok"), result.optBoolean("network_error"))
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
                JSONObject().put("ok", false).put("network_error", true)
                    .put("message", NetworkRules.message(e, true))
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
        view.setImageResource(R.drawable.ic_account_placeholder)
        root.addView(view, LinearLayout.LayoutParams(dp(88), dp(88)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(12)
        })
        fetchAvatarInto(view, url, revision)
    }

    private fun fetchAvatarInto(view: ImageView, raw: String, revision: String) {
        val url = AccountRules.avatarUrl(raw, revision + "_" + avatarGeneration) ?: return
        avatarCache.get(url)?.let { view.setImageBitmap(it); return }
        heroJobs.execute {
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
            if (bitmap != null) avatarCache.put(url, bitmap)
            main.post { if (bitmap != null && view.parent != null && !isDestroyed) view.setImageBitmap(bitmap) }
        }
    }

    /** Native rendering of the Build 498 three-second welcome. */
    private fun showAcademicWelcome() {
        activeScreen = "welcome"
        window.statusBarColor = Color.rgb(5,16,34)
        window.navigationBarColor = Color.rgb(5,16,34)
        window.decorView.systemUiVisibility = 0
        setProtectedContent(CinematicWelcomeView(this))
    }

    /** Replay the iOS Build 498 welcome after at least three seconds away. */
    private fun replayWelcomeAfterBackground() {
        if (activeScreen == "welcome" || activeScreen == "restoring" || isFinishing || isDestroyed) return
        if (resumeWelcomeOverlay != null) return
        val parent = findViewById<FrameLayout>(android.R.id.content)
        val firstName = student.optString("name").trim().split(' ').firstOrNull().orEmpty()
        val greeting = if (firstName.isEmpty()) "أهلًا بك" else "أهلًا، $firstName"
        val overlay = CinematicWelcomeView(this, greeting).apply {
            isClickable = true
            isFocusable = true
        }
        parent.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        resumeWelcomeOverlay = overlay
        Log.i("MurshidWelcome", "background-replay-started")
        main.postDelayed({
            if (resumeWelcomeOverlay === overlay && overlay.parent != null) {
                overlay.animate().alpha(0f).setDuration(300L).withEndAction {
                    (overlay.parent as? FrameLayout)?.removeView(overlay)
                    if (resumeWelcomeOverlay === overlay) resumeWelcomeOverlay = null
                }.start()
            }
        }, 2700L)
    }

    /** GuestLandingView: the iPhone's first unauthenticated screen, including its live navigation. */
    private fun guestLandingScreen() {
        activeScreen = "guest"
        window.statusBarColor = if (night) Color.BLACK else navy
        window.navigationBarColor = appColor
        setupRoot(horizontalPadding = 18, topPadding = 10)
        val scroll = screenFrame?.parent as? ScrollView
        val anchors = mutableMapOf<String, View>()
        fun jumpTo(id: String) {
            val target = anchors[id] ?: return
            val area = Rect()
            target.getDrawingRect(area)
            root.offsetDescendantRectToMyCoords(target, area)
            scroll?.smoothScrollTo(0, (area.top - dp(12)).coerceAtLeast(0))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_ios_v4818)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "شعار المرشد الوزاري"
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(uiText("منصة المرشد الوزاري", 16f, true))
        labels.addView(uiText("مساحة تعلّمك", 12f, tint = mutedText))
        top.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(makeButton("تسجيل الدخول", secondary = true) { loginScreen() }.apply {
            setSingleLine(true)
            maxLines = 1
            textSize = 14f
        }, LinearLayout.LayoutParams(-2, -2))
        root.addView(top, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(28) })

        root.addView(uiText("تعلّم بتركيز. تقدّم بثقة.", 15f, true, blue),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        root.addView(uiText("خطوة واضحة،\nنحو نجاحك.", 38f, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) })
        root.addView(uiText("رتّب دراستك، اختبر فهمك، وارجع لما يحتاج مراجعة. مساحة هادئة تجمع رحلتك الدراسية في مكان واحد.",
            16f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(15) })
        val heroActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        heroActions.addView(makeButton("ابدأ رحلتك") { registerReturnScreen = "guest"; registerScreen() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        heroActions.addView(makeButton("اكتشف المنصة", secondary = true) { jumpTo("platform-value") },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
        root.addView(heroActions, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        root.addView(uiText("✓ منصة تعليمية مستقلة · غير تابعة لأي جهة حكومية", 12f,
            true, mutedText), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(25) })

        val example = uiCard()
        val exampleTitle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        exampleTitle.addView(IosGlyphView(this, "book", blue), LinearLayout.LayoutParams(dp(20), dp(20)))
        exampleTitle.addView(uiText("مساحة التدريب", 17f, true),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        exampleTitle.addView(uiText("مثال توضيحي", 12f, tint = mutedText))
        example.addView(exampleTitle)
        example.addView(uiText("كل سؤال، خطوة جديدة", 13f, true, blue),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        example.addView(uiText("ما ناتج 3² ؟", 23f, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(8) })
        for ((choice, correct) in listOf("6" to false, "9" to true, "12" to false)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = shape(if (correct) Color.argb(28, 34, 160, 80)
                    else if (night) Color.rgb(45,45,49) else Color.rgb(249,249,251), dp(13).toFloat())
            }
            row.addView(uiText(choice, 17f, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(IosGlyphView(this, if (correct) "check" else "circle",
                if (correct) Color.rgb(39, 167, 85) else mutedText), LinearLayout.LayoutParams(dp(21), dp(21)))
            example.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        example.addView(uiText("افهم الفكرة، ثم جرّب بنفسك.", 13f, tint = mutedText))
        addUiCard(example, bottom = 22)

        val jumps = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val jumpRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        for ((label, id) in listOf("لماذا المنصة؟" to "platform-value",
            "كيف تبدأ" to "how", "الأسئلة المجانية" to "instruction-3",
            "تحدي المليون" to "instruction-5", "خدمة العملاء" to "instruction-7")) {
            jumpRow.addView(makeButton(label, secondary = true) { jumpTo(id) },
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
        jumps.addView(jumpRow)
        root.addView(jumps, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(27) })

        val platform = uiText("مصممة لطريقتك في الدراسة", 15f, true, blue)
        anchors["platform-value"] = platform
        root.addView(platform, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        root.addView(uiText("لماذا تختار منصة المرشد الوزاري؟", 24f, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        root.addView(uiText("من اختيار موضوعك إلى مراجعة إجابتك، أدوات بسيطة تساعدك على معرفة خطوتك التالية.",
            15f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(15) })
        val features = listOf(
            Triple("أسئلة مرتبة حسب الموضوع", "تنقّل بين المواد والمواضيع المنشورة واعرف عدد الأسئلة قبل أن تبدأ.", "list"),
            Triple("تعلّم من إجابتك", "ارجع إلى الإجابات والتوضيحات المتاحة، وأبلغ عن أي ملاحظة لتُراجع.", "brain"),
            Triple("اختبارات سريعة ومستقرة", "تجربة خفيفة وواضحة تحفظ التقدم والنتائج داخل الحساب.", "check"),
            Triple("تجربة مجانية حقيقية", "15 سؤالًا مجانيًا للحساب الجديد لتجربة المنصة قبل الاشتراك.", "gift"),
            Triple("دفع إلكتروني سهل", "خطوات اشتراك واضحة مع تأكيد حالة الدفع داخل المنصة.", "creditcard"),
            Triple("نتائج وتفسير للأخطاء", "متابعة المستوى ومعرفة نقاط القوة والموضوعات التي تحتاج مراجعة.", "chart"),
            Triple("تحديثات مستمرة", "إضافة ومراجعة المحتوى باستمرار استعدادًا لمواعيد الامتحانات.", "history"),
            Triple("دعم سريع وموثوق", "خدمة عملاء واضحة لمتابعة مشكلات الحساب والاشتراك والاستخدام.", "chat")
        )
        for ((heading, description, glyph) in features) {
            val card = uiCard()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.TOP
            }
            row.addView(iconBubble(glyph, 38), LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                marginEnd = dp(13)
            })
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(uiText(heading, 17f, true))
            labels.addView(uiText(description, 14f, tint = mutedText),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)
            addUiCard(card, bottom = 13)
        }

        val how = uiText("تعليمات الاستخدام", 15f, true, blue)
        anchors["how"] = how
        root.addView(how, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16); bottomMargin = dp(6) })
        root.addView(uiText("كيف تبدأ وتستفيد من المنصة", 24f, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(15) })
        val instructions = listOf(
            "الحساب وتسجيل الدخول" to "استخدم بيانات صحيحة عند إنشاء الحساب، واحفظ كلمة المرور الخاصة بك. تسجيل الدخول متاح برقم الهاتف أو البريد الإلكتروني المسجل في الحساب.",
            "الاختبارات" to "اختر الصف والمادة والموضوع ثم ابدأ الاختبار. يتم حفظ الإجابات والتقدم والنتائج داخل حساب الطالب، ويمكن متابعة الاختبار من الموضع المحفوظ.",
            "الأسئلة المجانية" to "يتوفر للحساب الجديد رصيد مقداره 15 سؤالًا مجانيًا بالضبط. بعد استهلاك الرصيد المجاني يتطلب الوصول إلى الأسئلة الإضافية اشتراكًا فعالًا.",
            "قسم النجاح" to "يعرض قسم النجاح أداء الطالب استنادًا إلى إجاباته وتقدمه، ويتضمن المواد الأقوى والموضوعات التي تحتاج إلى مراجعة. هذه المؤشرات خاصة بمتابعة المستوى داخل المنصة.",
            "تحدي المليون" to "يعتمد الترتيب على النقاط المسجلة وفق نظام المنصة. يستطيع الطالب متابعة مركزه والمتصدرين والفارق بينه وبين المراكز الأعلى من صفحة التحدي.",
            "بيانات الحساب" to "لا تشارك كلمة المرور أو بيانات الدخول مع أي شخص. يجب استخدام الحساب من صاحبه فقط لضمان سلامة النتائج والنقاط وسجل الاختبارات.",
            "خدمة العملاء" to "عند وجود مشكلة في الحساب أو الاشتراك أو استخدام الموقع، استخدم زر خدمة العملاء الموجود داخل المنصة بعد الدخول لإرسال طلب الدعم."
        )
        instructions.forEachIndexed { index, (heading, description) ->
            val card = uiCard()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            val number = uiText((index + 1).toString().padStart(2, '0'), 14f, true, blue).apply {
                gravity = Gravity.CENTER
                background = shape(Color.argb(26, 45, 108, 223), dp(12).toFloat())
            }
            row.addView(number, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(13) })
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(uiText(heading, 17f, true))
            content.addView(uiText(description, 14f, tint = mutedText),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            row.addView(content, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)
            anchors["instruction-${index + 1}"] = card
            addUiCard(card, bottom = 13)
        }

        val finalCard = uiCard()
        finalCard.addView(uiText("بعد قراءة التعليمات", 15f, true, blue).apply { gravity = Gravity.CENTER })
        finalCard.addView(uiText("اختر طريقة الدخول إلى المنصة", 22f, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7); bottomMargin = dp(16) })
        finalCard.addView(makeButton("إنشاء حساب") { registerReturnScreen = "guest"; registerScreen() },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        finalCard.addView(makeButton("تسجيل الدخول", secondary = true) { loginScreen() })
        addUiCard(finalCard, bottom = 17)

        root.addView(uiText("© 2026 المرشد الوزاري · منصة تعليمية مستقلة", 12f, tint = mutedText)
            .apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val social = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        for ((symbol, label, link) in listOf(
            Triple("instagram", "Instagram", "https://www.instagram.com/murshid.iq/"),
            Triple("facebook", "Facebook", "https://www.facebook.com/Murshidiq/"),
            Triple("telegram", "Telegram", "https://t.me/El_Murshed_iq"))) {
            social.addView(tappable(FrameLayout(this).apply {
                addView(IosGlyphView(this@MainActivity, symbol, blue),
                    FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            }, label) { external(link) }, LinearLayout.LayoutParams(dp(52), dp(43)))
        }
        root.addView(social)
        root.addView(uiText("الثالث المتوسط · السادس العلمي · السادس الأدبي", 12f,
            tint = mutedText).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        val policies = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        policies.addView(makeButton("الخصوصية", secondary = true) { external("https://mur-iq.com/privacy.php") })
        policies.addView(makeButton("شروط الاستخدام", secondary = true) { external("https://mur-iq.com/terms.php") },
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        root.addView(policies)
    }

    /** Shared SwiftUI AuthHeader: graduation mark, product name and subtitle. */
    private fun authHeader(subtitle: String, topPadding: Int = 16) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(topPadding), dp(12), dp(14))
        }
        val mark = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(blue, navy))
                .apply { cornerRadius = dp(24).toFloat() }
            elevation = dp(6).toFloat()
            contentDescription = "المرشد الوزاري"
        }
        mark.addView(IosGlyphView(this, "graduation", Color.WHITE),
            FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
        header.addView(mark, LinearLayout.LayoutParams(dp(82), dp(82)).apply { bottomMargin = dp(14) })
        header.addView(uiText("المرشد الوزاري", 30f, true).apply { gravity = Gravity.CENTER })
        header.addView(uiText(subtitle, 15f, tint = mutedText).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    /** An RTL label with its SF-Symbol counterpart on the trailing/right edge. */
    private fun authFieldLabel(label: String, symbol: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        gravity = Gravity.CENTER_VERTICAL
        addView(IosGlyphView(this@MainActivity, symbol, textColor),
            LinearLayout.LayoutParams(dp(19), dp(19)).apply { marginEnd = dp(7) })
        addView(uiText(label, 15f, true))
    }

    private fun authV3Field(hint: String, initial: String = "", inputKind: Int = InputType.TYPE_CLASS_TEXT,
                            forceLTR: Boolean = false, secure: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        textSize = 16f
        setTextColor(textColor)
        setHintTextColor(mutedText)
        setSingleLine(true)
        minHeight = dp(54)
        setPadding(dp(14), dp(9), dp(14), dp(9))
        inputType = if (secure) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else inputKind
        if (secure) transformationMethod = PasswordTransformationMethod.getInstance()
        layoutDirection = if (forceLTR) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        textDirection = if (forceLTR) View.TEXT_DIRECTION_LTR else View.TEXT_DIRECTION_RTL
        gravity = (if (forceLTR) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL
        background = shape(if (night) Color.rgb(48, 48, 51) else Color.rgb(235, 235, 239), dp(15).toFloat())
        setText(initial)
    }

    private fun registrationPasswordReveal(field: EditText, description: String): FrameLayout {
        // The two iPhone registration SecureFields each have their own eye control.
        (field.parent as? ViewGroup)?.removeView(field)
        field.setPadding(dp(48), dp(10), dp(14), dp(10))
        val wrap = FrameLayout(this)
        wrap.addView(field, FrameLayout.LayoutParams(-1, -2))
        val reveal = FrameLayout(this).apply { isClickable = true; isFocusable = true }
        fun updateEye(visible: Boolean) {
            reveal.removeAllViews()
            reveal.addView(IosGlyphView(this, if (visible) "eye-off" else "eye", blue),
                FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
            reveal.contentDescription = if (visible) "إخفاء $description" else "إظهار $description"
        }
        var visible = false
        updateEye(false)
        reveal.setOnClickListener {
            visible = !visible
            field.transformationMethod = if (visible) HideReturnsTransformationMethod.getInstance()
                else PasswordTransformationMethod.getInstance()
            field.setSelection(field.text.length)
            updateEye(visible)
        }
        wrap.addView(reveal, FrameLayout.LayoutParams(dp(44), dp(48), Gravity.LEFT or Gravity.CENTER_VERTICAL))
        return wrap
    }

    private fun loginScreen() {
        activeScreen = "login"
        base("تسجيل الدخول", true)
        authHeader("سجّل الدخول وواصل دراستك من حيث توقفت", topPadding = 28)
        val form = uiCard()
        form.addView(uiText("تسجيل الدخول", 22f, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(17) })
        fun field(hint: String, passwordField: Boolean = false): EditText = EditText(this).apply {
            this.hint = hint
            textSize = 16f
            setSingleLine(true)
            setTextColor(textColor)
            setHintTextColor(mutedText)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            minHeight = dp(52)
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (night) Color.rgb(65, 65, 69) else Color.rgb(222, 222, 228))
            }
            if (passwordField) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                transformationMethod = PasswordTransformationMethod.getInstance()
            } else inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        form.addView(authFieldLabel("البريد الإلكتروني أو رقم الهاتف", "person"),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        val identifier = field("example@email.com أو 07XXXXXXXXX").apply {
            // The SwiftUI source explicitly forces this mixed Arabic/Latin field LTR.
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        }
        form.addView(identifier, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(17) })
        form.addView(authFieldLabel("كلمة المرور", "lock"),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        val passwordWrap = FrameLayout(this)
        val password = field("أدخل كلمة المرور", passwordField = true).apply {
            setPadding(dp(48), dp(10), dp(14), dp(10))
        }
        passwordWrap.addView(password, FrameLayout.LayoutParams(-1, -2))
        val revealControl = FrameLayout(this).apply {
            contentDescription = "إظهار كلمة المرور"
            isClickable = true
            isFocusable = true
        }
        fun setEye(revealed: Boolean) {
            revealControl.removeAllViews()
            revealControl.addView(IosGlyphView(this, if (revealed) "eye-off" else "eye", blue),
                FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
            revealControl.contentDescription = if (revealed) "إخفاء كلمة المرور" else "إظهار كلمة المرور"
        }
        setEye(false)
        var revealed = false
        revealControl.setOnClickListener {
            revealed = !revealed
            password.transformationMethod = if (revealed)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            password.setSelection(password.text.length)
            setEye(revealed)
        }
        passwordWrap.addView(revealControl, FrameLayout.LayoutParams(dp(44), dp(48),
            Gravity.LEFT or Gravity.CENTER_VERTICAL))
        form.addView(passwordWrap, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val remember = Switch(this).apply {
            text = "تذكرني على هذا الجهاز"
            textSize = 15f
            setTextColor(textColor)
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            isChecked = true // SwiftUI LoginView remembers by default.
        }
        form.addView(remember, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(15) })
        val submit = makeButton("دخول") {}
        form.addView(submit, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val forgot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER
            minimumHeight = dp(44)
            isClickable = true
            isFocusable = true
            setOnClickListener { recoverScreen() }
        }
        forgot.addView(IosGlyphView(this, "key", blue),
            LinearLayout.LayoutParams(dp(17), dp(17)).apply { marginEnd = dp(7) })
        forgot.addView(uiText("نسيت كلمة المرور؟", 15f, true, blue))
        form.addView(forgot)
        addUiCard(form, bottom = 17)
        val register = uiText("إنشاء حساب طالب جديد", 17f, true, blue).apply {
            gravity = Gravity.CENTER
            minHeight = dp(44)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                registerReturnScreen = "login"
                registerScreen()
            }
        }
        root.addView(register, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(32) })
        fun updateSubmit() { submit.isEnabled = identifier.text.toString().trim().isNotEmpty() && password.text.isNotEmpty() }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateSubmit()
            override fun afterTextChanged(s: Editable?) = Unit
        }
        identifier.addTextChangedListener(watcher)
        password.addTextChangedListener(watcher)
        updateSubmit()
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
                    updateSubmit()
                    submit.text = "دخول"
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
    }

    /** The seven usage instructions shipped in the iOS 4.x GuestLandingView. */
    private fun instructionsScreen() {
        activeScreen = "instructions"
        base("تعليمات الاستخدام", true)
        sectionHeader("كيف تبدأ وتستفيد من المنصة", "التعليمات نفسها المعروضة في iPhone", "book")
        val instructions = listOf(
            "الحساب وتسجيل الدخول" to
                "استخدم بيانات صحيحة عند إنشاء الحساب، واحفظ كلمة المرور الخاصة بك. تسجيل الدخول متاح برقم الهاتف أو البريد الإلكتروني المسجل في الحساب.",
            "الاختبارات" to
                "اختر الصف والمادة والموضوع ثم ابدأ الاختبار. يتم حفظ الإجابات والتقدم والنتائج داخل حساب الطالب، ويمكن متابعة الاختبار من الموضع المحفوظ.",
            "الأسئلة المجانية" to
                "يتوفر للحساب الجديد رصيد مقداره 15 سؤالًا مجانيًا بالضبط. بعد استهلاك الرصيد المجاني يتطلب الوصول إلى الأسئلة الإضافية اشتراكًا فعالًا.",
            "قسم النجاح" to
                "يعرض قسم النجاح أداء الطالب استنادًا إلى إجاباته وتقدمه، ويتضمن المواد الأقوى والموضوعات التي تحتاج إلى مراجعة. هذه المؤشرات خاصة بمتابعة المستوى داخل المنصة.",
            "تحدي المليون" to
                "يعتمد الترتيب على النقاط المسجلة وفق نظام المنصة. يستطيع الطالب متابعة مركزه والمتصدرين والفارق بينه وبين المراكز الأعلى من صفحة التحدي.",
            "بيانات الحساب" to
                "لا تشارك كلمة المرور أو بيانات الدخول مع أي شخص. يجب استخدام الحساب من صاحبه فقط لضمان سلامة النتائج والنقاط وسجل الاختبارات.",
            "خدمة العملاء" to
                "عند وجود مشكلة في الحساب أو الاشتراك أو استخدام الموقع، استخدم زر خدمة العملاء الموجود داخل المنصة بعد الدخول لإرسال طلب الدعم."
        )
        instructions.forEachIndexed { index, (heading, details) ->
            val card = uiCard()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.TOP
            }
            row.addView(FrameLayout(this).apply {
                background = shape(Color.argb(26, 45, 108, 223), dp(12).toFloat())
                addView(uiText((index + 1).toString().padStart(2, '0'), 15f, true, blue).apply {
                    gravity = Gravity.CENTER
                }, FrameLayout.LayoutParams(-1, -1))
            }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(13) })
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(uiText(heading, 17f, true))
                addView(uiText(details, 14f, tint = mutedText),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)
            addUiCard(card, bottom = 13)
        }
        button(if (instructionsReturnScreen == "account") "العودة إلى حسابي" else "تسجيل الدخول") {
            if (instructionsReturnScreen == "account") accountScreen() else loginScreen()
        }
        if (instructionsReturnScreen == "login") {
            button("إنشاء حساب", secondary = true) { registerScreen() }
        }
    }

    private fun verifyPrompt(response: JSONObject) {
        val kind = if (response.optBoolean("phone_verification_required")) "phone" else "email"
        val destination = if (kind == "phone") response.optString("masked_phone") else response.optString("masked_email")
        verificationScreen(kind, destination, response.optString("message"))
    }

    private fun verificationScreen(kind: String, destination: String, note: String) {
        activeScreen = "verification"
        base(if (kind == "phone") "تأكيد رقم الهاتف" else "تأكيد البريد الإلكتروني", true)
        authHeader(if (kind == "phone") "تأكيد رقم الهاتف" else "تأكيد البريد الإلكتروني")
        val intro = root.getChildAt(root.childCount - 1)
        line("رمز التحقق", true)
        caption(note.ifBlank { "أدخل الرمز المرسل إلى $destination" })
        val code = input("000000").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.CENTER
            letterSpacing = 0.17f
            textSize = 22f
            contentDescription = "رمز التحقق من ستة أرقام"
        }
        val verificationStatus = uiText("", 14f).apply {
            visibility = View.GONE
            setPadding(dp(12), dp(11), dp(12), dp(11))
        }
        root.addView(verificationStatus)
        fun showVerificationStatus(text: String, error: Boolean) {
            verificationStatus.text = text
            verificationStatus.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            verificationStatus.setTextColor(if (error) Color.rgb(181, 66, 54) else Color.rgb(29, 144, 75))
            verificationStatus.background = shape(
                if (error) (if (night) Color.rgb(68, 37, 35) else Color.rgb(255, 235, 232))
                else (if (night) Color.rgb(23, 56, 37) else Color.rgb(231, 247, 235)),
                dp(12).toFloat())
        }
        val verify = button("تأكيد الحساب") {}
        verify.isEnabled = false
        code.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                verify.isEnabled = !recoveryBusy &&
                    RecoveryRules.normalizedDigits(s?.toString().orEmpty()).matches(Regex("[0-9]{6}"))
                if (verificationStatus.visibility == View.VISIBLE) showVerificationStatus("", false)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        verify.setOnClickListener {
            val value = RecoveryRules.normalizedDigits(code.text.toString())
            if (!value.matches(Regex("[0-9]{6}"))) {
                showVerificationStatus("أدخل الرمز كاملاً من 6 أرقام.", true)
                return@setOnClickListener
            }
            if (recoveryBusy) return@setOnClickListener
            recoveryBusy = true
            verify.isEnabled = false
            showVerificationStatus("", false)
            recoveryRequest(mayHaveSubmitted = true, action = { api.verifyPending(kind, value) }) { result ->
                recoveryBusy = false
                if (activeScreen != "verification") return@recoveryRequest
                verify.isEnabled = code.text.length == 6
                if (result.optBoolean("ok")) {
                    api.setRemember(pendingVerificationRemember)
                    showHome(result)
                } else showVerificationStatus(
                    result.optString("message", "تعذر تأكيد الرمز. تحقق منه وأعد المحاولة."), true)
            }
        }
        val resend = button("إعادة إرسال الرمز", secondary = true) {}
        resend.setOnClickListener {
            if (recoveryBusy) return@setOnClickListener
            recoveryBusy = true
            resend.isEnabled = false
            showVerificationStatus("", false)
            recoveryRequest(mayHaveSubmitted = true, action = { api.resendPending(kind) }) { result ->
                recoveryBusy = false
                if (activeScreen == "verification") {
                    resend.isEnabled = true
                    showVerificationStatus(result.optString("message", "تعذر إعادة إرسال الرمز."),
                        !result.optBoolean("ok"))
                }
            }
        }
        val backToLogin = button("إلغاء والعودة لتسجيل الدخول", secondary = true) { loginScreen() }
        val fields = (0 until root.childCount).map { root.getChildAt(it) }
            .filter { it !== intro && it !== backToLogin }
        val form = uiCard()
        fields.forEach { field ->
            root.removeView(field)
            form.addView(field, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }
        root.removeView(backToLogin)
        addUiCard(form, bottom = 18)
        root.addView(backToLogin, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(30)
        })
    }

    private fun boundRecoveryScreen() {
        recoverReturnScreen = "account"
        activeScreen = "bound_recovery"
        base("كلمة المرور", true)
        caption("استعادة آمنة لبيانات حسابك الحالي")
        val generation = screenGeneration
        fetch("mobile/bound-password-recovery.php") { result ->
            if (activeScreen != "bound_recovery" || screenGeneration != generation) return@fetch
            if (!result.optBoolean("ok")) {
                button("إعادة المحاولة") { boundRecoveryScreen() }
                return@fetch
            }
            caption("تُرسل الاستعادة إلى وسيلة التواصل المسجلة في حسابك فقط. لا يمكن تعديل الوجهة من هنا.")
            val contacts = result.optJSONObject("contacts") ?: JSONObject()
            val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
            var selected = ""
            for (channel in listOf("email", "phone")) {
                val contact = contacts.optJSONObject(channel) ?: continue
                val value = contact.optString("value")
                if (value.isBlank()) continue
                val available = contact.optBoolean("enabled")
                val choice = RadioButton(this).apply {
                    id = View.generateViewId()
                    text = (if (channel == "email") "البريد الإلكتروني المسجل" else "رقم WhatsApp المسجل") +
                        "\n" + value + if (available) "" else "\nهذه الوسيلة غير مؤكدة أو غير مفعلة حالياً"
                    setTextColor(textColor); textSize = 15f
                    setPadding(dp(12), dp(16), dp(12), dp(16))
                    isEnabled = available
                    setOnClickListener { selected = channel }
                }
                group.addView(choice)
                if (available && selected.isEmpty()) { selected = channel; choice.isChecked = true }
            }
            root.addView(group)
            if (group.childCount == 0) caption("لا توجد وسيلة تواصل مرتبطة بحسابك. تواصل مع الدعم.")
            val send = button("إرسال إلى وسيلة الحساب المسجلة") {}
            send.isEnabled = selected.isNotEmpty() && !recoveryBusy
            send.setOnClickListener {
                if (recoveryBusy || selected.isEmpty()) return@setOnClickListener
                recoveryBusy = true; send.isEnabled = false
                fetch("mobile/bound-password-recovery.php", JSONObject().put("csrf", csrf).put("channel", selected)) { reply ->
                    recoveryBusy = false
                    if (activeScreen != "bound_recovery" || generation != screenGeneration) return@fetch
                    if (reply.optBoolean("ok")) {
                        if (reply.optString("channel") == "phone") phoneResetScreen(reply.optString("phone_token"))
                        else caption(reply.optString("message", "تم إرسال رابط الاستعادة إلى بريد حسابك."))
                    } else send.isEnabled = true
                }
            }
        }
    }

    private fun startSessionServices() {
        if (!appForeground || !::api.isInitialized || csrf.isBlank() || student.optInt("id") <= 0 || serviceTicker != null) return
        serviceTicker = object : Runnable {
            override fun run() {
                if (!appForeground || csrf.isBlank() || isDestroyed) { serviceTicker = null; return }
                val now = SystemClock.elapsedRealtime()
                if (now - lastHeartbeatPoll > 60000L || lastHeartbeatPoll == 0L) {
                    lastHeartbeatPoll = now
                    fetch("api/session-heartbeat.php", showError = false) { }
                }
                if (now - lastPopupPoll > 180000L || lastPopupPoll == 0L) pollSchoolAd()
                if (now - lastSchoolAdsBadgePoll >= 30000L || lastSchoolAdsBadgePoll == 0L)
                    pollSchoolAdsBadge()
                main.postDelayed(this, 30000L)
            }
        }.also { main.post(it) }
    }

    private fun pollSchoolAd() {
        if (popupChecking || currentAdDialog != null || activeScreen in setOf("exam", "practice_exam", "daily_challenge") || biometricOverlay != null) return
        lastPopupPoll = SystemClock.elapsedRealtime()
        popupChecking = true
        fetch("mobile/school-ad-popup.php", showError = false) { response ->
            val ads = response.optJSONArray("ads") ?: JSONArray()
            fun claim(index: Int) {
                if (!appForeground || index >= ads.length() || activeScreen in setOf("exam", "practice_exam", "daily_challenge") || biometricOverlay != null) {
                    popupChecking = false; return
                }
                val ad = ads.optJSONObject(index)
                if (ad == null || ad.optInt("id") <= 0 || ad.optInt("slot", -1) < 0 || ad.optString("school").isBlank()) {
                    claim(index + 1); return
                }
                fetch("mobile/school-ad-popup.php", JSONObject().put("csrf", csrf).put("id", ad.optInt("id"))
                    .put("slot", ad.optInt("slot")), showError = false) { claimed ->
                    if (claimed.optBoolean("shown") && appForeground && biometricOverlay == null) {
                        popupChecking = false
                        currentAdDialog = AlertDialog.Builder(this).setTitle(ad.optString("school"))
                            .setMessage(ad.optString("text"))
                            .setPositiveButton("إغلاق الإعلان", null).create().also { dialog ->
                                dialog.setOnDismissListener { currentAdDialog = null }
                                dialog.show()
                            }
                    } else claim(index + 1)
                }
            }
            claim(0)
        }
    }

    private fun questionNote(questionId: Int) {
        val generation = screenGeneration
        fetch("mobile/question-notes.php?question_id=$questionId") { result ->
            if (!result.optBoolean("ok") || generation != screenGeneration) return@fetch
            val field = EditText(this).apply {
                setText(result.optString("note")); hint = "مثال: راجع هذه القاعدة قبل الامتحان"
                minLines = 4; gravity = Gravity.TOP or Gravity.RIGHT
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setTextColor(textColor)
            }
            val dialog = AlertDialog.Builder(this).setTitle("ملاحظتي الخاصة").setView(field)
                .setPositiveButton("حفظ", null).setNegativeButton("إغلاق", null).create()
            dialog.setOnShowListener {
                val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                save.setOnClickListener {
                    save.isEnabled = false
                    fetch("mobile/question-notes.php", JSONObject().put("csrf", csrf)
                        .put("question_id", questionId).put("note", field.text.toString())) { reply ->
                        if (reply.optBoolean("ok")) dialog.dismiss() else save.isEnabled = true
                    }
                }
            }
            dialog.show()
        }
    }

    private fun contestInstructionsScreen() {
        activeScreen = "contest_instructions"
        base("تعليمات التحدي", true)
        val generation = screenGeneration
        caption("جارٍ تحميل القواعد وموعد الإغلاق…")
        fetch("mobile/contest-instructions.php") { result ->
            if (activeScreen != "contest_instructions" || generation != screenGeneration) return@fetch
            root.removeAllViews()
            if (!result.optBoolean("ok")) {
                caption("تعذر تحديث تعليمات التحدي.")
                button("إعادة المحاولة") { contestInstructionsScreen() }
                return@fetch
            }
            sectionHeader("تحدي المليون", "جائزة المركز الأول: ${result.optInt("prize_iqd", 1000000)} د.ع", "trophy")
            val status = when (result.optString("phase")) {
                "scheduled" -> "لم يبدأ التحدي بعد"
                "active" -> "التحدي جارٍ الآن"
                "ended" -> "انتهت فترة التحدي"
                else -> if (result.optBoolean("active")) "التحدي جارٍ الآن" else "حالة التحدي غير متاحة"
            }
            line(status, true)
            caption("البداية: " + result.optString("start_at", "يُحدد من الإدارة") + " · بتوقيت بغداد")
            caption("النهاية: " + result.optString("end_at", "يُحدد من الإدارة") + " · بتوقيت بغداد")
            for (key in listOf("rules", "points")) {
                val items = result.optJSONArray(key) ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    addUiCard(uiCard().apply {
                        addView(uiText(item.optString(if (key == "rules") "title" else "value"), 17f, true, blue))
                        addView(uiText(item.optString(if (key == "rules") "text" else "description"), 15f))
                    })
                }
            }
            caption(result.optString("points_note"))
            caption(result.optString("footer_note"))
        }
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
                    StudentPush.detach(this)
                    intent.removeExtra(StudentPush.inboxExtra)
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

    /** Exactly the three-step RegisterV3View shipped by the iPhone Build 498. */
    private class RegistrationDraft {
        var step = 1
        var fullName = ""
        val gradeIds = ArrayList<Int>()
        val gradeNames = ArrayList<String>()
        var gradeId = 0
        var gradesLoading = true
        var genderIndex = 0
        var contactIndex = 0
        var identifier = ""
        var password = ""
        var confirmation = ""
        var loading = false
        var fieldError = ""
        var generalError = ""
    }

    private var registrationDraft: RegistrationDraft? = null

    private fun registrationSegments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = shape(if (night) Color.rgb(48, 48, 51) else Color.rgb(231, 231, 236),
                dp(11).toFloat())
        }
        labels.forEachIndexed { index, label ->
            row.addView(uiText(label, 14f, tint = if (night) Color.WHITE else textColor).apply {
                gravity = Gravity.CENTER
                minHeight = dp(32)
                background = if (index == selected)
                    shape(if (night) Color.rgb(105, 105, 110) else Color.WHITE, dp(9).toFloat())
                else shape(Color.TRANSPARENT, dp(9).toFloat())
                isSelected = index == selected
                isClickable = true
                isFocusable = true
                contentDescription = label
                setOnClickListener { onSelect(index) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return row
    }

    private fun registrationNotice(value: String, warning: Boolean): TextView =
        uiText(value, 13f, tint = if (warning) Color.rgb(220, 139, 43) else blue).apply {
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = shape(
                if (warning) (if (night) Color.rgb(59, 44, 25) else Color.rgb(255, 246, 224))
                else (if (night) Color.rgb(25, 42, 69) else Color.rgb(234, 241, 253)),
                dp(12).toFloat())
        }

    private fun registrationWatch(field: EditText, onChange: (String) -> Unit) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onChange(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun validateRegistrationStep(draft: RegistrationDraft): Boolean {
        draft.fieldError = ""
        draft.generalError = ""
        when (draft.step) {
            1 -> {
                draft.fullName = AccountRules.normalizeName(draft.fullName)
                if (draft.fullName.split(' ').count { it.isNotBlank() } < 3) {
                    draft.fieldError = "اكتب الاسم الثلاثي على الأقل، مثل: يونس محمد عباس."
                } else if (draft.gradeId <= 0) draft.fieldError = "اختر الصف الدراسي."
            }
            2 -> {
                draft.identifier = if (draft.contactIndex == 1)
                    RecoveryRules.normalizedDigits(draft.identifier.trim()) else draft.identifier.trim()
                if (draft.contactIndex == 0 &&
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(draft.identifier).matches())
                    draft.fieldError = "أدخل بريدًا إلكترونيًا صحيحًا."
                else if (draft.contactIndex == 1 && !AccountRules.validIraqiPhone(draft.identifier))
                    draft.fieldError = "أدخل رقمًا عراقيًا صحيحًا يبدأ بـ 07 ويتكون من 11 رقمًا."
            }
            else -> {
                if (draft.password.length < 8)
                    draft.fieldError = "كلمة المرور يجب أن تكون 8 أحرف على الأقل."
                else if (draft.confirmation.isBlank() || draft.password != draft.confirmation)
                    draft.fieldError = "تأكد من تطابق كلمتي المرور."
            }
        }
        return draft.fieldError.isEmpty()
    }

    private fun registerScreen() {
        val draft = RegistrationDraft()
        registrationDraft = draft
        renderRegistration(draft)
        fetch("api/grades.php") { result ->
            if (activeScreen != "register" || registrationDraft !== draft) return@fetch
            val array = result.optJSONArray("grades") ?: JSONArray()
            for (index in 0 until array.length()) {
                val grade = array.optJSONObject(index) ?: continue
                val id = grade.optInt("id")
                if (id <= 0) continue
                draft.gradeIds.add(id)
                draft.gradeNames.add(grade.optString("name"))
            }
            draft.gradeId = draft.gradeIds.firstOrNull() ?: 0
            draft.gradesLoading = false
            if (draft.gradeId == 0) draft.generalError =
                result.optString("message", "تعذر تحميل الصفوف الدراسية. ارجع وحاول من جديد.")
            renderRegistration(draft)
        }
    }

    private fun renderRegistration(draft: RegistrationDraft) {
        if (registrationDraft !== draft) return
        activeScreen = "register"
        base("إنشاء حساب", true)
        authHeader("أنشئ حسابك بخطوات قصيرة وواضحة", topPadding = 12)

        val progress = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
            contentDescription = "الخطوة ${draft.step} من 3"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        for (index in 1..3) {
            val completed = index < draft.step
            val active = index <= draft.step
            progress.addView(uiText(if (completed) "✓" else "$index", 13f, true,
                if (active) Color.WHITE else mutedText).apply {
                gravity = Gravity.CENTER
                background = shape(if (active) blue
                    else if (night) Color.rgb(30, 30, 32) else Color.rgb(225, 225, 230),
                    dp(30).toFloat())
            }, LinearLayout.LayoutParams(dp(30), dp(30)))
            if (index < 3) {
                progress.addView(View(this).apply {
                    background = shape(if (index < draft.step) blue
                        else if (night) Color.rgb(32, 32, 34) else Color.rgb(225, 225, 230),
                        dp(2).toFloat())
                }, LinearLayout.LayoutParams(0, dp(4), 1f).apply {
                    marginStart = dp(7); marginEnd = dp(7)
                })
            }
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(17) })

        val form = uiCard()
        fun add(view: View, below: Int = 15) {
            form.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(below) })
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(uiText(when (draft.step) {
            1 -> "بيانات الطالب"; 2 -> "طريقة التسجيل"; else -> "حماية الحساب"
        }, 22f, true))
        titles.addView(uiText(when (draft.step) {
            1 -> "الاسم والصف والجنس"; 2 -> "البريد أو رقم الهاتف"; else -> "اختر كلمة مرور قوية"
        }, 13f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        heading.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(uiText("${draft.step} من 3", 13f, true, blue).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = shape(if (night) Color.rgb(25, 38, 59) else Color.rgb(235, 242, 253),
                dp(18).toFloat())
        })
        add(heading, 18)

        if (draft.generalError.isNotBlank()) add(registrationNotice(draft.generalError, true))

        if (draft.step == 1) {
            add(authFieldLabel("الاسم الثلاثي", "id-card"), 8)
            val name = authV3Field("مثال: يونس محمد عباس", draft.fullName)
            registrationWatch(name) { draft.fullName = it }
            add(name, 10)
            add(uiText("اكتب اسمك الثلاثي كما هو في بياناتك الرسمية. نتعامل تلقائيًا مع المسافات والرموز المخفية في لوحة المفاتيح.",
                13f, tint = mutedText), 16)

            add(authFieldLabel("الصف الدراسي", "books"), 8)
            val gradeBox = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(4), dp(14), dp(4))
                minimumHeight = dp(54)
                background = shape(if (night) Color.rgb(48, 48, 51) else Color.rgb(235, 235, 239),
                    dp(15).toFloat())
                isClickable = true
                isFocusable = true
            }
            val selectedGrade = draft.gradeNames.getOrNull(draft.gradeIds.indexOf(draft.gradeId))
                ?: if (draft.gradesLoading) "جاري تحميل الصفوف…" else "اختر الصف الدراسي"
            val gradeLabel = uiText(selectedGrade, 16f)
            gradeBox.addView(gradeLabel, LinearLayout.LayoutParams(0, -2, 1f))
            gradeBox.addView(uiText("⌃\n⌄", 12f, tint = mutedText).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(23), -2))
            gradeBox.setOnClickListener {
                if (draft.gradeNames.isEmpty()) return@setOnClickListener
                val menu = android.widget.PopupMenu(this, gradeBox)
                draft.gradeNames.forEachIndexed { index, text ->
                    menu.menu.add(text).setOnMenuItemClickListener {
                        draft.gradeId = draft.gradeIds[index]
                        draft.fieldError = ""
                        gradeLabel.text = text
                        true
                    }
                }
                menu.show()
            }
            add(gradeBox, 16)
            add(authFieldLabel("الجنس", "users"), 8)
            add(registrationSegments(listOf("ذكر", "أنثى"), draft.genderIndex) {
                draft.genderIndex = it
                draft.fieldError = ""
                renderRegistration(draft)
            }, 16)
        } else if (draft.step == 2) {
            add(authFieldLabel("طريقة التسجيل", "at"), 8)
            add(registrationSegments(listOf("البريد الإلكتروني", "رقم الهاتف"), draft.contactIndex) {
                if (draft.contactIndex != it) {
                    draft.contactIndex = it
                    draft.identifier = ""
                    draft.fieldError = ""
                    renderRegistration(draft)
                }
            }, 16)
            add(authFieldLabel(if (draft.contactIndex == 0) "البريد الإلكتروني" else "رقم الهاتف العراقي",
                if (draft.contactIndex == 0) "envelope" else "phone"), 8)
            val identifier = authV3Field(if (draft.contactIndex == 0) "example@email.com" else "07XXXXXXXXX",
                draft.identifier, if (draft.contactIndex == 0)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                else InputType.TYPE_CLASS_PHONE, forceLTR = true)
            registrationWatch(identifier) { draft.identifier = it }
            add(identifier, 16)
            add(registrationNotice(if (draft.contactIndex == 0)
                "سيصل رمز/رابط التأكيد إلى هذا البريد، لذلك استخدم بريدًا يمكنك فتحه."
                else "استخدم رقمك العراقي الفعلي حتى تتمكن من استعادة الحساب لاحقًا.", false), 16)
        } else {
            add(authFieldLabel("كلمة المرور", "lock"), 8)
            val password = authV3Field("8 أحرف على الأقل", draft.password,
                forceLTR = true, secure = true)
            add(registrationPasswordReveal(password, "كلمة المرور"), 13)

            val strengthTitle = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER_VERTICAL
            }
            strengthTitle.addView(uiText("قوة كلمة المرور", 13f, tint = mutedText),
                LinearLayout.LayoutParams(0, -2, 1f))
            val strengthText = uiText("ضعيفة", 13f, true, Color.rgb(232, 145, 48))
            strengthTitle.addView(strengthText)
            add(strengthTitle, 6)
            val strengthBar = LinearLayout(this).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                orientation = LinearLayout.HORIZONTAL
            }
            val strengths = (0 until 4).map { index ->
                View(this).also { strengthBar.addView(it, LinearLayout.LayoutParams(0, dp(5), 1f).apply {
                    if (index > 0) marginStart = dp(5)
                }) }
            }
            fun updateStrength() {
                val value = draft.password
                val raw = listOf(value.length >= 8, value.any { it.isDigit() },
                    value.any { it.isLetter() }, value.length >= 12 || value.any { !it.isLetterOrDigit() })
                    .count { it }
                val level = raw.coerceAtLeast(1)
                val color = when (level) {
                    1 -> Color.rgb(232, 145, 48)
                    2 -> Color.rgb(211, 172, 49)
                    3 -> blue
                    else -> Color.rgb(41, 167, 92)
                }
                strengthText.text = when (level) { 1 -> "ضعيفة"; 2 -> "مقبولة"; 3 -> "جيدة"; else -> "قوية" }
                strengthText.setTextColor(color)
                strengths.forEachIndexed { index, view ->
                    view.background = shape(if (index < level) color
                        else if (night) Color.rgb(47, 47, 50) else Color.rgb(226, 226, 231), dp(3).toFloat())
                }
            }
            registrationWatch(password) { draft.password = it; updateStrength() }
            updateStrength()
            add(strengthBar, 17)

            add(authFieldLabel("تأكيد كلمة المرور", "lock"), 8)
            val confirm = authV3Field("أعد كتابة كلمة المرور", draft.confirmation,
                forceLTR = true, secure = true)
            add(registrationPasswordReveal(confirm, "تأكيد كلمة المرور"), 10)
            val match = uiText("", 13f, true)
            fun updateMatch() {
                val same = draft.password == draft.confirmation
                match.text = if (same) "✓ كلمتا المرور متطابقتان" else "× كلمتا المرور غير متطابقتين"
                match.contentDescription = if (same) "كلمتا المرور متطابقتان" else "كلمتا المرور غير متطابقتين"
                match.setTextColor(if (same) Color.rgb(41, 167, 92) else Color.rgb(220, 139, 43))
                match.visibility = if (draft.confirmation.isEmpty()) View.GONE else View.VISIBLE
            }
            registrationWatch(password) { draft.password = it; updateMatch() }
            registrationWatch(confirm) { draft.confirmation = it; updateMatch() }
            updateMatch()
            add(match, 15)
            add(registrationNotice("لا تشارك كلمة المرور مع أي شخص. يمكن استعادة الحساب لاحقًا من داخل التطبيق.",
                false), 15)
        }

        if (draft.fieldError.isNotBlank()) add(registrationNotice(draft.fieldError, true), 14)
        val next = makeButton(if (draft.step < 3) "التالي  ←" else if (draft.loading)
            "جاري إنشاء الحساب…" else "إنشاء الحساب") {}
        next.contentDescription = if (draft.step < 3) "التالي" else "إنشاء الحساب"
        next.isEnabled = !draft.loading && (!draft.gradesLoading || draft.step > 1)
        next.setOnClickListener {
            if (draft.loading) return@setOnClickListener
            if (!validateRegistrationStep(draft)) {
                renderRegistration(draft)
                return@setOnClickListener
            }
            if (draft.step < 3) {
                draft.step++
                renderRegistration(draft)
                return@setOnClickListener
            }
            draft.loading = true
            val fullName = AccountRules.normalizeName(draft.fullName)
            val data = JSONObject()
                .put("full_name", fullName).put("name", fullName).put("fullName", fullName)
                .put("identifier", draft.identifier)
                .put("contact_type", if (draft.contactIndex == 0) "email" else "phone")
                .put("grade_id", draft.gradeId)
                .put("gender", if (draft.genderIndex == 0) "male" else "female")
                .put("password", draft.password)
                .put("password_confirmation", draft.confirmation)
                .put("company_website", "")
            renderRegistration(draft)
            fetch("api/register.php", data) { result ->
                if (activeScreen != "register" || registrationDraft !== draft) return@fetch
                draft.loading = false
                if (result.optBoolean("ok")) {
                    pendingVerificationRemember = false
                    verifyPrompt(result)
                } else if (needsMutationReconciliation(result)) {
                    draft.generalError = "قد يكون الحساب أُنشئ. حاول تسجيل الدخول أو استعادة كلمة المرور قبل إعادة التسجيل."
                    renderRegistration(draft)
                } else {
                    draft.generalError = result.optString("message", "تعذر إنشاء الحساب. راجع البيانات وحاول مجددًا.")
                    renderRegistration(draft)
                }
            }
        }
        add(next, 10)
        if (draft.step > 1 && !draft.loading) {
            val previous = uiText("رجوع للخطوة السابقة  →", 14f, true, mutedText).apply {
                gravity = Gravity.CENTER
                minHeight = dp(42)
                contentDescription = "رجوع للخطوة السابقة"
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    draft.fieldError = ""
                    draft.generalError = ""
                    draft.step--
                    renderRegistration(draft)
                }
            }
            add(previous, 0)
        }
        form.addView(uiText("نسخة iPhone 4.8.18 • Build 498", 11f,
            tint = if (night) Color.rgb(78, 78, 82) else Color.rgb(154, 154, 160)).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
        addUiCard(form, bottom = 18)
        root.addView(uiText("لدي حساب بالفعل", 17f, true, blue).apply {
            gravity = Gravity.CENTER
            minHeight = dp(44)
            isClickable = true
            isFocusable = true
            setOnClickListener { loginScreen() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(32) })
    }

    private fun refreshSchoolAdsBadge() {
        schoolAdsBadgeView?.apply {
            text = SchoolAdBadgeRules.badgeLabel(schoolAdsUnread)
            visibility = if (schoolAdsUnread > 0) View.VISIBLE else View.GONE
            contentDescription = if (schoolAdsUnread > 0) "إعلانات جديدة: $schoolAdsUnread" else ""
        }
    }

    private fun updateSchoolAdsBadge(data: JSONObject, markAsRead: Boolean = false) {
        val userId = student.optInt("id")
        val ads = data.optJSONArray("ads") ?: return
        if (userId <= 0) return
        val published = LinkedHashSet<Int>()
        for (index in 0 until ads.length()) {
            val ad = ads.optJSONObject(index) ?: continue
            val id = ad.optInt("id")
            if (id > 0 && ad.optBoolean("active", true) &&
                ad.optString("school").isNotBlank() && ad.optString("text").isNotBlank()) published.add(id)
        }
        val key = "seen_school_ads_v1_$userId"
        if (!prefs.contains(key)) {
            // The first install/update establishes a baseline; only subsequently
            // published ads raise the red badge.
            prefs.edit().putString(key, SchoolAdBadgeRules.encode(published)).apply()
            schoolAdsUnread = 0
        } else {
            val seen = SchoolAdBadgeRules.decode(prefs.getString(key, "").orEmpty())
            if (markAsRead) {
                prefs.edit().putString(key, SchoolAdBadgeRules.encode(seen + published)).apply()
                schoolAdsUnread = 0
            } else schoolAdsUnread = SchoolAdBadgeRules.unreadCount(published, seen)
        }
        refreshSchoolAdsBadge()
    }

    private fun pollSchoolAdsBadge() {
        val userId = student.optInt("id")
        if (!appForeground || userId <= 0 || csrf.isBlank() || biometricOverlay != null) return
        lastSchoolAdsBadgePoll = SystemClock.elapsedRealtime()
        fetch("mobile/bootstrap.php", showError = false) { response ->
            if (student.optInt("id") == userId && response.optBoolean("ok"))
                updateSchoolAdsBadge(response)
        }
    }

    private fun withoutFrenchSubjects(source: JSONObject): JSONObject {
        // UI defense-in-depth: French must not reappear if an older server
        // bootstrap, cached response, or imported question bank includes it.
        val cleaned = JSONObject(source.toString())
        val original = cleaned.optJSONArray("subjects") ?: return cleaned
        val allowed = JSONArray()
        for (i in 0 until original.length()) {
            val item = original.optJSONObject(i) ?: continue
            if (!EquationStudyRules.isFrenchSubject(item.optInt("id"), item.optString("name"))) {
                allowed.put(item)
            }
        }
        cleaned.put("subjects", allowed)
        return cleaned
    }

    private fun applyBootstrap(response: JSONObject) {
        homepage = withoutFrenchSubjects(response); csrf = response.optString("csrf", csrf)
        student = response.optJSONObject("user") ?: JSONObject()
        subscribed = response.optBoolean("subscribed")
        offlineCoachMode = false
        val offlineUser = student.optInt("id", 0)
        offlineExams.acceptVerifiedSubscription(offlineUser, subscribed,
            response.optLong("subscription_expires_at_unix", 0L))
        scheduleOfflineSync()
        OfflineCoachAccess(prefs).remember(student.optInt("id"))
        updateSchoolAdsBadge(response)
        StudentPush.attach(this, api, student.optInt("id"), csrf) { configured ->
            if (!isFinishing && !isDestroyed && configured && student.optInt("id") > 0 &&
                biometricOverlay == null && !biometricAuthInProgress && Build.VERSION.SDK_INT >= 33 &&
                !StudentPush.allowed(this) && !prefs.getBoolean("push_permission_prompted", false)) {
                prefs.edit().putBoolean("push_permission_prompted", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), StudentPush.requestCode)
            }
        }
        main.post { openPushInboxIfReady() }
    }

    private fun scheduleOfflineSync() {
        val uid = student.optInt("id")
        if (offlineSyncInFlight || uid <= 0 || csrf.isBlank() ||
            offlineCoachMode || offlineExams.pendingCount(uid) == 0) return
        offlineSyncInFlight = true
        val token = csrf
        val gen = sessionGeneration
        jobs.execute {
            val report = offlineExams.sync(api, token, uid)
            main.post {
                offlineSyncInFlight = false
                if (gen == sessionGeneration && student.optInt("id") == uid &&
                    report.confirmed > 0) {
                    message("تم تأكيد ${report.confirmed} إجابة من وضع عدم الاتصال. المتبقي ${report.remaining}.")
                    if (activeScreen == "offline_exam_topics") offlineExamTopicsScreen()
                }
            }
        }
    }

    private fun openPushInboxIfReady() {
        if (biometricOverlay != null || biometricAuthInProgress || student.optInt("id") <= 0) return
        if (StudentPush.matchesInboxIntent(this, intent, student.optInt("id"))) {
            intent.removeExtra(StudentPush.inboxExtra)
            notificationsScreen()
        }
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        intent = newIntent
        // onResume decides whether the biometric shield is required before routing.
    }

    private fun hasAnyInternetNetwork(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun canOpenOfflineCoach(): Boolean =
        OfflineCoachAccess(prefs).studentId() > 0

    /** Offline access never opens server-dependent tests, subscriptions or payments. */
    private fun showOfflineCoachHome() {
        val studentId = OfflineCoachAccess(prefs).studentId()
        if (studentId <= 0) { guestLandingScreen(); return }
        offlineCoachMode = true
        csrf = ""
        student = JSONObject().put("id", studentId).put("name", "طالبنا")
        subscribed = false
        homepage = JSONObject()
        activeScreen = "offline_coach_home"
        base("المرشد الوزاري")
        val welcome = uiCard()
        welcome.addView(uiText("مراجعتك معك أينما كنت", 23f, true, blue))
        welcome.addView(uiText("وضع المراجعة دون إنترنت", 15f, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        welcome.addView(uiText(
            "يمكنك فتح المدرب الذكي ومراجعة الأسئلة التي سبق حفظها على هذا الجهاز، دون اتصال ولا إرسال إجابات جديدة.",
            14f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        addUiCard(welcome, bottom = 16)
        addUiCard(uiRow("مدربي الذكي · مراجعة دون إنترنت",
            "افتح دفتر الأسئلة المحفوظة", "brain") { coachScreen() }, bottom = 14)
        if (offlineExams.topics(studentId).isNotEmpty()) {
            addUiCard(uiRow("اختباراتي المحفوظة",
                "حل الأسئلة المنزلة وتخزين الإجابات للمزامنة", "book") { offlineExamTopicsScreen() }, bottom = 14)
            caption("إجابات تنتظر المزامنة: ${offlineExams.pendingCount(studentId)}")
        }
        val count = OfflineCoachNotebook(prefs).cards(studentId).size
        caption(if (count > 0) "أسئلتك المحفوظة على الجهاز: $count"
            else "لا توجد أسئلة محفوظة بعد. اتصل بالإنترنت وأجب عن أسئلة الاختبارات أولاً.")
        button("العودة إلى الوضع المتصل", secondary = true) {
            if (!hasAnyInternetNetwork()) {
                message("لا يوجد اتصال الآن. يمكنك متابعة المراجعة دون إنترنت.")
            } else if (!api.hasRememberedSession()) {
                // The offline notebook is not an authentication token.
                loginScreen()
            } else {
                fetch("mobile/bootstrap.php", showError = false) { result ->
                    if (result.optBoolean("ok")) showHome(result)
                    else if (result.optBoolean("network_error"))
                        message("تعذر الوصول للخادم. المراجعة المحلية ما زالت متاحة.")
                    else loginScreen()
                }
            }
        }
        button("الواجهة العامة", secondary = true) { guestLandingScreen() }
    }

    /** Opens only previously downloaded, account-bound subscription snapshots.
     * These answers are NOT graded locally and never award points offline.
     */
    private fun offlineExamTopicsScreen() {
        activeScreen = "offline_exam_topics"
        val userId = student.optInt("id")
        base("اختباراتي دون إنترنت", true)
        caption("تظهر الموضوعات التي فتحتها سابقاً أثناء اشتراكك واتصالك بالإنترنت. المزامنة والتصحيح الرسمي بعد عودة الاتصال.")
        val topics = offlineExams.topics(userId)
        if (topics.isEmpty()) {
            caption("لا توجد اختبارات قابلة للفتح حالياً. افتح موضوعاً أثناء الاتصال، وتأكد من صلاحية اشتراكك.")
            button("العودة", secondary = true) {
                if (offlineCoachMode) showOfflineCoachHome() else coachScreen()
            }
            return
        }
        caption("إجابات مؤجلة للمزامنة: ${offlineExams.pendingCount(userId)}")
        topics.forEach { topic ->
            addUiCard(uiRow(topic.name,
                "${topic.count} سؤال متوافق مع الدراسة دون إنترنت", "book") {
                offlineExamQuestionScreen(topic.id, 0)
            }, bottom = 10)
        }
        if (!offlineCoachMode && offlineExams.pendingCount(userId) > 0) {
            button("مزامنة الإجابات الآن") {
                if (csrf.isBlank()) { message("سجّل الدخول أولاً لمزامنة الإجابات."); return@button }
                scheduleOfflineSync()
            }
        }
    }

    private fun offlineExamQuestionScreen(chapter: Int, index: Int) {
        offlineChapterId = chapter
        activeScreen = "offline_exam_question"
        val uid = student.optInt("id")
        val topic = offlineExams.topics(uid).firstOrNull { it.id == chapter }
        val list = offlineExams.questions(uid, chapter)
        if (topic == null || list == null) {
            offlineExamTopicsScreen()
            message("انتهت صلاحية النسخة المحفوظة؛ اتصل بالإنترنت لتجديدها.")
            return
        }
        if (index >= list.length()) {
            base("نهاية المراجعة المحفوظة", true)
            caption("انتهت أسئلة هذه النسخة. الإجابات المؤجلة لا تُحتسب درجات نهائية قبل وصول تأكيد الخادم.")
            caption("المتبقي للمزامنة: ${offlineExams.pendingCount(uid)}")
            button("العودة للمواضيع", secondary = true) { offlineExamTopicsScreen() }
            return
        }
        val question = list.optJSONObject(index)
        if (question == null) { offlineExamQuestionScreen(chapter, index + 1); return }
        val qid = question.optInt("id")
        val queued = offlineExams.queued(uid, qid)
        val submitted = question.optBoolean("submitted", false)
        base(topic.name, true)
        val header = uiCard()
        header.addView(uiText("السؤال ${index + 1} من ${list.length()}", 13f, true, blue))
        header.addView(uiText(question.optString("text"), 19f, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
        if (question.optString("meaning_word").isNotBlank())
            header.addView(uiText("المفردة: ${question.optString("meaning_word")}", 14f, tint = mutedText))
        addUiCard(header, bottom = 14)
        val type = question.optString("type")
        val options = question.optJSONArray("options") ?: JSONArray()
        if (!OfflineExamRules.supported(type, options.length())) {
            caption("يحتاج هذا النوع من السؤال إلى محرّر خاص واتصال. لن تُرسل إجابة غير مكتملة.")
            button("التالي", secondary = true) { offlineExamQuestionScreen(chapter, index + 1) }
            return
        }
        if (queued != null || submitted) {
            val accepted = uiCard()
            accepted.addView(uiText(if (submitted) "أجبت سابقاً على الخادم" else
                "تم حفظ إجابتك على هذا الجهاز ✓", 17f, true, blue))
            if (queued != null) {
                accepted.addView(uiText("إجابتك: ${queued.optString("answer")}", 15f),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
                accepted.addView(uiText("الدرجة والنقاط والرصيد ستُحدد من الخادم بعد المزامنة.",
                    13f, tint = mutedText))
            }
            val correct = question.optString("correct_answer")
            if (correct.isNotBlank()) {
                accepted.addView(uiText("الإجابة النموذجية: $correct", 15f, true),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
                val explanation = question.optString("explanation")
                if (explanation.isNotBlank()) accepted.addView(uiText(explanation, 13f),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            }
            addUiCard(accepted, bottom = 12)
            button(if (index == list.length() - 1) "إنهاء المراجعة" else "السؤال التالي") {
                offlineExamQuestionScreen(chapter, index + 1)
            }
            return
        }
        val answerCard = uiCard()
        answerCard.addView(uiText("إجابتك — تحفظ دون إنترنت", 16f, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        var selected = ""
        var selectedOptionId = 0
        val enter = if (options.length() > 0 || type == "true_false") null
            else input("اكتب الإجابة", multiline = true, parent = answerCard)
        if (enter == null) {
            val choices = if (options.length() > 0) options else JSONArray().put("صح").put("خطأ")
            val changes = mutableListOf<Pair<String, (Boolean) -> Unit>>()
            for (i in 0 until choices.length()) {
                val item = choices.optJSONObject(i)
                val label = item?.optString("text") ?: choices.optString(i)
                val optionId = item?.optInt("id", 0) ?: 0
                val change = examOptionButton(label, i, answerCard) {
                    selected = label
                    selectedOptionId = optionId
                    changes.forEach { (text, callback) -> callback(text == selected) }
                }
                changes.add(label to change)
            }
        }
        val button = makeButton("حفظ الإجابة على الجهاز") {
            val given = enter?.text?.toString()?.trim() ?: selected.trim()
            if (given.isEmpty()) { message("اكتب إجابتك أو اختر أحد الخيارات."); return@makeButton }
            val originalIndex = question.optInt("_offline_index", index + 1)
            if (offlineExams.queue(uid, chapter, qid, originalIndex, given, selectedOptionId)) {
                offlineExamQuestionScreen(chapter, index)
            } else message("تعذر الحفظ. قد تكون النسخة أو صلاحية اشتراكك انتهت.")
        }
        answerCard.addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        addUiCard(answerCard)
        caption("الدرجة الرسمية لا تُحتسب على الجهاز. تجنب تسجيل الخروج قبل مزامنة الإجابات المؤجلة.")
    }

    private fun showHome(response: JSONObject) {
        if (!response.optBoolean("ok")) { loginScreen(); return }
        competitionHistory.clear()
        applyBootstrap(response)
        activeScreen = "home"
        base("المرشد الوزاري", tab = "home")
        startSessionServices()

        val greeting = student.optString("name", "طالبنا").trim().split(' ').firstOrNull().orEmpty()
        val stats = response.optJSONObject("stats") ?: JSONObject()
        val total = stats.optInt("total_answers")
        val accuracy = if (total > 0) (100 * stats.optInt("correct_answers") / total) else 0
        val motivation = when {
            accuracy >= 85 && total >= 10 -> "مستواك ممتاز. حافظ على إيقاعك وراجع الأخطاء القليلة."
            total > 0 -> "كل إجابة اليوم تقرّبك أكثر من الدرجة التي تريدها."
            else -> "ابدأ بخطوة صغيرة اليوم، وسنبني تقدمك معك سؤالًا بعد سؤال."
        }
        addHomeWelcomeBanner(greeting, accuracy, total, motivation)

        if (lastValidatedInternet == false) addUiCard(uiCard().apply {
            addView(uiText("لا يوجد اتصال بالإنترنت. ستُعرض البيانات المحفوظة متى كانت متاحة.", 13f, tint = gold))
        }, bottom = 16)
        val subscription = uiCard()
        subscription.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val glyph = if (subscribed) "check" else "trophy"
            addView(iconBubble(glyph, 46, if (subscribed) Color.rgb(42,172,91) else gold),
                LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(12) })
            val labels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(uiText(if (subscribed) "اشتراكك فعّال · جميع الأسئلة متاحة"
                else "رصيد التجربة المجانية", 16f, true,
                if (subscribed) Color.rgb(52,199,89) else textColor))
            if (!subscribed) labels.addView(uiText(
                "متبقي ${response.optInt("free_remaining")} من ${response.optInt("free_limit")} سؤال",
                12f, tint = mutedText))
            addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            if (!subscribed) addView(IosGlyphView(this@MainActivity, "chevron", mutedText),
                LinearLayout.LayoutParams(dp(18), dp(18)))
        })
        if (subscribed) addUiCard(subscription, bottom = 16)
        else addUiCard(tappable(subscription, "الباقات والاشتراك") { plansScreen() }, bottom = 16)

        addHomeHeroSlider()

        homeMetricHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(homeMetricHolder, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        populateHomeMetrics()

        // Exact iPhone Build 498 home placement: below the four progress metrics
        // and above the ministerial exam simulator. Offline recall makes no
        // network requests and never decrements the student's answer quota.
        addUiCard(uiRow(
            "مدربي الذكي · مراجعة دون إنترنت",
            "بعد أول سؤال مصحّح سيُبنى لك دفتر مراجعة شخصيًا.",
            "brain"
        ) { coachScreen() }, bottom = 16)

        homeMockExamHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(homeMockExamHost, LinearLayout.LayoutParams(-1, -2))
        renderHomeMockExam()

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
        homeQuickToolsHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(homeQuickToolsHost, LinearLayout.LayoutParams(-1, -2))
        renderHomeQuickTools()

        sectionHeader("تعلّم من إجاباتك", "خطوة صغيرة بعد كل اختبار", "trend")
        val learningCard = uiCard()
        learningCard.addView(uiText("كل إجابة تساهم في تحليل نقاط قوتك والمواضيع التي تحتاج مراجعة.", 15f,
            tint = mutedText))
        val learningAction = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            addView(IosGlyphView(this@MainActivity, "book", blue),
                LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) })
            addView(uiText("افتح دفتر الأخطاء والتحليل", 16f, true, blue))
        }
        learningCard.addView(learningAction, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(15) })
        addUiCard(tappable(learningCard, "افتح دفتر الأخطاء والتحليل") { reviewScreen() }, bottom = 18)

        sectionHeader("تابعنا على منصات التواصل", "كل جديد من منصة المرشد الوزاري", "chat")
        val socialCard = uiCard()
        val socialRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER
        }
        listOf(
            Triple("إنستغرام", "instagram", "https://www.instagram.com/murshid.iq/"),
            Triple("فيسبوك", "facebook", "https://www.facebook.com/Murshidiq/"),
            Triple("تيليجرام", "telegram", "https://t.me/El_Murshed_iq")
        ).forEachIndexed { index, (name, symbol, url) ->
            val brandColor = when (index) {
                0 -> Color.rgb(193, 45, 126)
                1 -> Color.rgb(34, 87, 194)
                else -> Color.rgb(30, 151, 202)
            }
            val platform = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val icon = FrameLayout(this@MainActivity).apply {
                    background = shape(brandColor, dp(12).toFloat())
                    addView(IosGlyphView(this@MainActivity, symbol, Color.WHITE),
                        FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
                }
                addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(uiText(name, 12f, true).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
            socialRow.addView(tappable(platform, name) { external(url) },
                LinearLayout.LayoutParams(0, -2, 1f))
        }
        socialCard.addView(socialRow)
        addUiCard(socialCard, bottom = 18)

        val pendingPractice = prefs.getInt("practice_session_${student.optInt("id", 0)}", 0)
        if (pendingPractice > 0) button("متابعة الاختبار المخصص", secondary = true) { loadPracticeSession(pendingPractice) }
        if (!homeInsightsLoaded) loadHomeInsights()
        fetch("mobile/notifications.php", showError = false) { /* notification count is read on demand */ }
        fetch("mobile/app-config.php", showError = false) { config ->
            if (activeScreen != "home" || !config.optBoolean("ok")) return@fetch
            homeFeatureFlags = config.optJSONObject("features") ?: JSONObject()
            renderHomeMockExam()
            renderHomeQuickTools()
        }
    }

    /** iOS AppSession.featureEnabled uses `features[code].enabled`, defaulting to true. */
    private fun homeFeatureEnabled(code: String): Boolean =
        homeFeatureFlags.optJSONObject(code)?.optBoolean("enabled", true) ?: true

    /** The coach keeps an on-device notebook, separate from the server's review_schedule.
     *  Reading and rating these cards is offline and does not submit an exam answer.
     */
    private fun localLearningReviews(): List<LearningInsights.Review> =
        OfflineCoachNotebook(prefs).cards(student.optInt("id"))
            .map { card -> LearningInsights.Review(
                subject = card.optString("subject"),
                successfulReviews = card.optInt("level", 0),
                wasCorrect = if (card.has("graded_correct")) card.optBoolean("graded_correct") else null,
                dueAt = card.optLong("due_at", 0L),
                lastReviewedAt = card.optLong("last_reviewed_at", 0L)
            ) }

    private fun coachScreen() {
        activeScreen = "coach"
        base("مدربي الذكي", true)
        val userId = student.optInt("id")
        val notebook = OfflineCoachNotebook(prefs)
        val cards = notebook.cards(userId)
        val now = System.currentTimeMillis()
        val due = cards.count { it.optLong("due_at", 0L) <= now }

        val heading = uiCard()
        heading.addView(uiText("مدربي الذكي", 23f, true, blue))
        heading.addView(uiText("مراجعة دون إنترنت", 16f, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        heading.addView(uiText(
            "دفتر خاص على جهازك: راجع إجاباتك المصححة في أي وقت، دون صوت أو اتصال بالخادم.",
            13f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        addUiCard(heading, bottom = 14)

        val status = uiCard()
        status.addView(uiText("مراجعة اليوم", 20f, true))
        status.addView(uiText(
            if (cards.isEmpty()) "بعد أول سؤال مصحّح سيُبنى لك دفتر مراجعة شخصيًا."
            else if (due > 0) "لديك " + due + " سؤال للمراجعة الآن من أصل " + cards.size + " محفوظة."
            else "أحسنت! لا توجد مراجعات مستحقة الآن. يمكنك التدريب على أسئلتك المحفوظة.",
            14f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        addUiCard(status)

        // Local estimation, never an official exam grade or result.
        val reviewSignals = localLearningReviews()
        val studyGoal = LearningInsights.suggestedDailyTarget(reviewSignals, now)
        val summary = uiCard()
        summary.addView(uiText("هدف دراسة مقترح: $studyGoal سؤال يومياً", 17f, true, blue))
        summary.addView(uiText(LearningInsights.nextReview(reviewSignals, now), 13f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        summary.addView(uiText("الاقتراح مبني على دفتر المراجعة المحلي وليس نتيجة الامتحان الرسمية.",
            11f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        addUiCard(summary, bottom = 14)

        val masteryGroups = LearningInsights.mastery(reviewSignals, now).take(6)
        if (masteryGroups.isNotEmpty()) {
            sectionHeader("إتقان المواد", "تقدير محلي يعتمد عدد المراجعات الناجحة", "chart")
            masteryGroups.forEach { group ->
                val subjectCard = uiCard()
                subjectCard.addView(uiText("${group.subject} · ${group.percent}%", 16f, true))
                subjectCard.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = group.percent
                    progressTintList = ColorStateList.valueOf(blue)
                    contentDescription = "إتقان ${group.subject}: ${group.percent} بالمئة"
                }, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(8) })
                subjectCard.addView(uiText("${group.secure} متقنة من ${group.total} · ${group.due} للمراجعة",
                    12f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
                addUiCard(subjectCard, bottom = 8)
            }
        }

        if (offlineExams.topics(userId).isNotEmpty()) {
            button("الاختبارات المحفوظة دون إنترنت", secondary = true) { offlineExamTopicsScreen() }
        }
        if (cards.isNotEmpty()) {
            button(if (due > 0) "ابدأ مراجعة اليوم" else "استعرض دفتر المراجعة") {
                val next = cards.firstOrNull { it.optLong("due_at", 0L) <= now } ?: cards.first()
                coachQuestionScreen(next.optInt("id"))
            }
            sectionHeader("أسئلتي المحفوظة", "تُعرض من ذاكرة الجهاز حتى دون إنترنت", "brain")
            cards.take(18).forEach { card ->
                val label = card.optString("question").replace("\n", " ").take(95)
                val subtitle = card.optString("subject").ifBlank { "سؤال تمت مراجعته" }
                addUiCard(uiRow(label, subtitle, "book") {
                    coachQuestionScreen(card.optInt("id"))
                })
            }
        }

        val disclaimer = uiCard()
        disclaimer.addView(uiText(
            "هذه مراجعة محلية مستقلة. تقييم إتقانك هنا لا يرسل إجابات جديدة ولا يخصم سؤالًا مجانيًا، ولا يُحدّث جدول المدرب الموجود على الموقع.",
            12f, tint = mutedText))
        addUiCard(disclaimer)
        if (!offlineCoachMode) button("تحديث أخطائي المحفوظة من الخادم", secondary = true) {
            fetch("mobile/review.php", showError = false) { response ->
                if (activeScreen != "coach") return@fetch
                if (!response.optBoolean("ok")) {
                    message("تعذر التحديث. دفتر المراجعة المحلي ما زال متاحًا دون إنترنت.")
                    return@fetch
                }
                val mistakes = response.optJSONArray("mistakes") ?: JSONArray()
                var imported = 0
                for (i in 0 until mistakes.length()) {
                    val item = mistakes.optJSONObject(i) ?: continue
                    if (notebook.remember(
                        userId = userId,
                        questionId = item.optInt("question_id"),
                        question = item.optString("question_text"),
                        correctAnswer = item.optString("correct_answer"),
                        explanation = item.optString("explanation"),
                        subject = item.optString("subject_name") + " · " + item.optString("chapter_name"),
                        wasCorrect = false
                    )) imported++
                }
                message("تم تحديث " + imported + " سؤالًا من سجل أخطائك.")
                coachScreen()
            }
        }
    }

    private fun coachQuestionScreen(questionId: Int) {
        val userId = student.optInt("id")
        val notebook = OfflineCoachNotebook(prefs)
        val card = notebook.cards(userId).firstOrNull { it.optInt("id") == questionId }
        if (card == null) {
            coachScreen()
            return
        }
        activeScreen = "coach_question"
        base("جلسة مراجعة", true)
        sectionHeader("راجع وتذكّر", card.optString("subject"), "brain")
        val prompt = uiCard()
        prompt.addView(uiText(card.optString("question"), 20f, true))
        addUiCard(prompt, bottom = 15)

        val revealed = uiCard()
        revealed.addView(uiText("الإجابة الصحيحة", 15f, true, blue))
        revealed.addView(uiText(card.optString("correct_answer"), 19f, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        if (card.optString("explanation").isNotBlank()) {
            revealed.addView(uiText("التوضيح", 14f, true, blue),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(17) })
            revealed.addView(uiText(card.optString("explanation"), 15f),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        revealed.visibility = View.GONE
        val success = button("أتقنت هذا السؤال") {
            notebook.review(userId, questionId, mastered = true)
            coachScreen()
        }
        val retry = button("أحتاج إلى مراجعته مجددًا", secondary = true) {
            notebook.review(userId, questionId, mastered = false)
            coachScreen()
        }
        success.visibility = View.GONE
        retry.visibility = View.GONE
        // Place the answer immediately below the prompt, before the self-rating
        // controls. No TextToSpeech, audio controls or network calls are involved.
        root.addView(revealed, root.indexOfChild(success), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(13)
        })
        val reveal = button("أظهر الإجابة", secondary = true) {
            revealed.visibility = View.VISIBLE
            success.visibility = View.VISIBLE
            retry.visibility = View.VISIBLE
        }
        // Keep the reveal action above the answer. The revealed answer is
        // still hidden until the student explicitly asks to see it.
        root.removeView(reveal)
        root.addView(reveal, root.indexOfChild(revealed),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        caption("تقييمك هنا للمراجعة الذاتية فقط، وليس إرسالاً لإجابة امتحانية.")
    }

    private fun renderHomeMockExam() {
        val host = homeMockExamHost ?: return
        host.removeAllViews()
        if (!homeFeatureEnabled("custom_exam")) return
        sectionHeader("محاكاة الوزاري", "تدرّب على أجواء الامتحان الوزاري", "timer", host)
        addUiCard(uiRow("ابدأ محاكاة الوزاري", "30 سؤالًا · 45 دقيقة", "timer") {
            practiceCount = 30
            practiceMinutes = 45
            practiceBuilderScreen()
        }, parent = host, bottom = 16)
    }

    private fun renderHomeQuickTools() {
        val host = homeQuickToolsHost ?: return
        host.removeAllViews()
        val tiles = mutableListOf<View>()
        tiles.add(uiTile("راجع أخطاءك", "مراجعة ذكية", "brain") { reviewScreen() })
        if (homeFeatureEnabled("custom_exam")) tiles.add(
            uiTile("اختبار مخصص", "عدد · نوع · مؤقت", "sliders") { practiceBuilderScreen() })
        if (homeFeatureEnabled("daily_challenge")) tiles.add(
            uiTile("تحدي اليوم", "سؤال واحد كل 24 ساعة", "flame") { openCompetition("daily_challenge") })
        if (homeFeatureEnabled("student_question_submit")) tiles.add(
            uiTile("اقترح سؤالًا", "ساهم في البنك", "plus-bubble") { proposeQuestionScreen() })
        tiles.add(uiTile("طلباتك", "تابع المراجعة", "tray") { myQuestionSubmissionsScreen() })
        if (homeFeatureEnabled("attempt_history")) tiles.add(
            uiTile("سجل الاختبارات", "نتائجك السابقة", "history") { attemptHistoryScreen() })
        tiles.add(uiTile("تحدي المليون", "ترتيب ونقاط", "trophy") { openCompetition("contest") })
        tiles.add(uiTile("غيّر جو", "استراحة قصيرة", "sparkles") { storiesScreen() })
        uiTwoColumnGrid(tiles, host)
    }

    /** Owner-supplied iPhone home reference: blue greeting, circular avatar and three gold metrics. */
    private fun addHomeWelcomeBanner(firstName: String, accuracy: Int, total: Int, motivation: String) {
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(19), dp(18), dp(19))
            background = GradientDrawable(GradientDrawable.Orientation.TR_BL,
                intArrayOf(navy, Color.rgb((Color.red(navy) + Color.red(blue)) / 2,
                    (Color.green(navy) + Color.green(blue)) / 2,
                    (Color.blue(navy) + Color.blue(blue)) / 2), blue)).apply {
                cornerRadius = dp(28).toFloat()
            }
            elevation = dp(3).toFloat()
        }
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_account_placeholder)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            clipToOutline = true
            contentDescription = "الصورة الشخصية"
        }
        identity.addView(avatar, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(12) })
        fetchAvatarInto(avatar, student.optString("avatar_url"), student.optString("avatar_revision"))
        val identityLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        identityLabels.addView(uiText("أهلًا بك", 12f, tint = Color.argb(210,255,255,255)))
        identityLabels.addView(uiText(firstName.ifBlank { "طالبنا" }, 23f, true, Color.WHITE),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        identityLabels.addView(uiText(student.optString("grade"), 14f, true, gold),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        identity.addView(identityLabels, LinearLayout.LayoutParams(0, -2, 1f))
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Baghdad"))
        val time = String.format(java.util.Locale.US, "%02d:%02d", now.hour, now.minute)
        identity.addView(uiText(time, 13f, true, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(30, 255,255,255))
                cornerRadius = dp(12).toFloat()
            }
        })
        banner.addView(identity)
        banner.addView(uiText(motivation, 14f, tint = Color.argb(238,255,255,255)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = dp(14) })
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val metrics = listOf(
            (total.toString() to "إجابة"),
            ("$accuracy%" to "دقة الحل"),
            ((if (subscribed) "مفعّل" else "تجريبي") to "الاشتراك")
        )
        metrics.forEachIndexed { index, (value, label) ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(12), dp(4), dp(12))
                background = shape(Color.argb(27,255,255,255), dp(14).toFloat())
                addView(uiText(value, if (value.length > 5) 15f else 20f, true, gold).apply {
                    gravity = Gravity.CENTER
                })
                addView(uiText(label, 11f, tint = Color.argb(212,255,255,255)).apply {
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            }
            statsRow.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) marginStart = dp(7)
            })
        }
        banner.addView(statsRow)
        addUiCard(banner, bottom = 16)
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
        val previous = homeHeroIndex
        homeHeroIndex = ((index % heroNames.size) + heroNames.size) % heroNames.size
        val host = homeHeroContainer
        val changing = previous != homeHeroIndex && host != null && host.width > 0
        if (changing) {
            homeHeroOutgoing?.let { prior ->
                prior.animate().cancel()
                (prior.parent as? android.view.ViewGroup)?.removeView(prior)
            }
            val previousBitmap = heroBitmaps[previous]
            val outgoing: View = if (previousBitmap != null) ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(previousBitmap)
            } else uiText(heroNames[previous], 23f, true, Color.WHITE).apply {
                gravity = Gravity.CENTER
            }
            outgoing.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            host!!.addView(outgoing, FrameLayout.LayoutParams(-1, -1))
            homeHeroOutgoing = outgoing
        }
        val bitmap = heroBitmaps[homeHeroIndex]
        homeHeroView?.setImageBitmap(bitmap)
        homeHeroContainer?.contentDescription = heroNames[homeHeroIndex]
        homeHeroFallback?.apply {
            text = heroNames[homeHeroIndex]
            visibility = if (bitmap == null) View.VISIBLE else View.GONE
        }
        if (changing) {
            // SwiftUI PageTabView moves between pages in 350ms. Keep the old page
            // visible while the new one enters; reverse direction for back-swipes.
            val forward = (previous + 1) % heroNames.size == homeHeroIndex
            val offset = host!!.width.toFloat() * if (forward) 1f else -1f
            val easing = android.view.animation.AccelerateDecelerateInterpolator()
            listOfNotNull(homeHeroView, homeHeroFallback).forEach { view ->
                view.animate().cancel()
                view.translationX = offset
                view.animate().translationX(0f).setDuration(VisualRules.heroPageTransitionMs)
                    .setInterpolator(easing).start()
            }
            val outgoing = homeHeroOutgoing!!
            outgoing.animate().translationX(-offset).setDuration(VisualRules.heroPageTransitionMs)
                .setInterpolator(easing).withEndAction {
                    if (homeHeroOutgoing === outgoing) {
                        host?.removeView(outgoing)
                        homeHeroOutgoing = null
                    }
                }.start()
        }
        homeHeroDots?.apply {
            removeAllViews()
            for (i in heroNames.indices) {
                val indicator = View(this@MainActivity).apply {
                    background = shape(if (i == homeHeroIndex) blue else mutedText, dp(4).toFloat())
                }
                val targetWidth = if (i == homeHeroIndex) 22 else 7
                val startWidth = if (changing && i == previous) 22
                    else if (changing && i == homeHeroIndex) 7 else targetWidth
                addView(indicator, LinearLayout.LayoutParams(dp(startWidth), dp(7)).apply {
                    marginStart = dp(3); marginEnd = dp(3)
                })
                if (startWidth != targetWidth) {
                    android.animation.ValueAnimator.ofInt(startWidth, targetWidth).apply {
                        duration = VisualRules.heroDotTransitionMs
                        addUpdateListener { animation ->
                            val params = indicator.layoutParams as? LinearLayout.LayoutParams ?: return@addUpdateListener
                            params.width = dp(animation.animatedValue as Int)
                            indicator.layoutParams = params
                        }
                        start()
                    }
                }
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
        homeHeroOutgoing?.let { outgoing ->
            outgoing.animate().cancel()
            (outgoing.parent as? android.view.ViewGroup)?.removeView(outgoing)
        }
        homeHeroOutgoing = null
    }

    private fun startHomeHeroTicker() {
        stopHomeHeroTicker()
        val ticker = object : Runnable {
            override fun run() {
                if (activeScreen != "home" || homeHeroTicker !== this) return
                showHeroSlide(homeHeroIndex + 1)
                main.postDelayed(this, VisualRules.heroAutoAdvanceMs)
            }
        }
        homeHeroTicker = ticker
        main.postDelayed(ticker, VisualRules.heroAutoAdvanceMs)
    }

    private fun testsTabScreen() {
        activeScreen = "tests"
        base("الاختبارات", tab = "tests")
        val intro = uiCard()
        val introRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        introRow.addView(iconBubble("checklist", 54), LinearLayout.LayoutParams(dp(54), dp(54)).apply {
            marginEnd = dp(14)
        })
        val introLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        introLabels.addView(uiText("اختباراتك", 12f, true, blue))
        introLabels.addView(uiText("اختر المادة وابدأ بثقة", 22f, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        introLabels.addView(uiText("المواضيع مرتبة حسب المنهج، وتقدمك محفوظ داخل حسابك.",
            14f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        introRow.addView(introLabels, LinearLayout.LayoutParams(0, -2, 1f))
        intro.addView(introRow)
        addUiCard(intro, bottom = 14)
        val (searchFrame, search) = searchControl("ابحث عن مادة")
        root.addView(searchFrame, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rows)
        fun filterSubjects(text: String) {
            rows.removeAllViews()
            val subjects = homepage.optJSONArray("subjects") ?: JSONArray()
            var shown = 0
            for (i in 0 until subjects.length()) {
                val subject = subjects.optJSONObject(i) ?: continue
                val name = subject.optString("name")
                if (EquationStudyRules.isFrenchSubject(subject.optInt("id"),name)) continue
                if (!name.contains(text.trim(), ignoreCase = true)) continue
                shown++
                val number = i + 1
                val card = uiCard()
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(FrameLayout(this).apply {
                    background = shape(if (night) Color.rgb(29,39,56) else Color.rgb(231,239,254),
                        dp(13).toFloat())
                    addView(uiText(number.toString(), 20f, true, blue).apply {
                        gravity = Gravity.CENTER
                    }, FrameLayout.LayoutParams(-1, -1))
                }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(13) })
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(uiText(name, 17f, true))
                labels.addView(uiText("المواضيع والاختبارات", 12f, tint = mutedText),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(IosGlyphView(this, "chevron", mutedText),
                    LinearLayout.LayoutParams(dp(16), dp(16)))
                card.addView(row)
                addUiCard(tappable(card, name) {
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

    private fun predictionsTabScreen() {
        activeScreen = "predictions_tab"
        base("المرشحات", tab = "predictions")
        val hero = uiCard().apply {
            background = GradientDrawable(GradientDrawable.Orientation.TR_BL, intArrayOf(navy, blue))
                .apply { cornerRadius = dp(22).toFloat() }
            addView(uiText("الدور الأول • 2027", 13f, true, gold))
            addView(uiText("المرشحات الوزارية", 28f, true, Color.WHITE),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(uiText("مراجعة مرتبة اعتمادًا على السنوات السابقة وتكرار الموضوع وتوثيق الأسئلة.",
                15f, tint = Color.argb(220,255,255,255)),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        addUiCard(hero, bottom = 14)
        addUiCard(uiCard().apply {
            addView(uiText("المرشحات احتمالات للمراجعة وليست ضمانًا لورود السؤال، ولا تغني عن المنهج.",
                13f, tint = mutedText))
        }, bottom = 16)
        if (!subscribed) {
            addUiCard(uiCard().apply {
                addView(iconBubble("lock", 52, gold))
                addView(uiText("المرشحات متاحة للمشتركين", 20f, true))
                addView(uiText("فعّل اشتراكك للوصول إلى مرشحات موادك وإجاباتها.", 14f, tint = mutedText))
            })
            button("عرض الباقات والاشتراك") { plansScreen() }
            return
        }
        val subjects = homepage.optJSONArray("subjects") ?: JSONArray()
        if (subjects.length() == 0) { caption("لا توجد مواد متاحة حاليًا."); return }
        if ((0 until subjects.length()).none { subjects.optJSONObject(it)?.optInt("id") == selectedPredictionSubject })
            selectedPredictionSubject = subjects.optJSONObject(0)?.optInt("id") ?: 0
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        for (i in 0 until subjects.length()) {
            val subject = subjects.optJSONObject(i) ?: continue
            val id = subject.optInt("id")
            chips.addView(uiText(subject.optString("name"), 14f, true,
                if (id == selectedPredictionSubject) Color.WHITE else blue).apply {
                gravity = Gravity.CENTER
                minimumHeight = dp(44)
                setPadding(dp(18), dp(10), dp(18), dp(10))
                background = shape(if (id == selectedPredictionSubject) blue else surface, dp(22).toFloat())
                setOnClickListener { selectedPredictionSubject = id; predictionsTabScreen() }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(chips)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rows)
        rows.addView(uiText("جارٍ تحميل المرشحات…", 14f, tint = mutedText))
        val request = ++predictionGeneration
        val screen = screenGeneration
        fetch("mobile/predictions.php?subject_id=$selectedPredictionSubject", showError = false) { result ->
            if (activeScreen != "predictions_tab" || screen != screenGeneration || request != predictionGeneration) return@fetch
            rows.removeAllViews()
            if (!result.optBoolean("ok")) {
                rows.addView(uiText(result.optString("message", "تعذر تحميل المرشحات."), 14f, tint = mutedText))
                rows.addView(makeButton("إعادة المحاولة") { predictionsTabScreen() })
                return@fetch
            }
            val predictions = result.optJSONArray("predictions") ?: JSONArray()
            rows.addView(uiText("المرشحات • ${predictions.length()} سؤال", 18f, true),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            if (predictions.length() == 0) rows.addView(uiText("لا توجد مرشحات لهذه المادة حاليًا.", 14f, tint = mutedText))
            for (i in 0 until predictions.length()) {
                val item = predictions.optJSONObject(i) ?: continue
                val card = uiCard()
                val chapter = item.optString("chapter_name").ifBlank { item.optString("topic") }
                val question = item.optString("question_text").ifBlank {
                    item.optString("question").ifBlank { item.optString("text") }
                }
                card.addView(uiText(chapter, 12f, true, blue))
                card.addView(uiText(question, 17f, true),
                    LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(10) })
                val score = item.optString("prediction_score").ifBlank { item.optString("probability") }
                val confidence = item.optString("confidence")
                val metrics = listOfNotNull(
                    score.takeIf { it.isNotBlank() }?.let { "درجة الترشيح: $it" },
                    confidence.takeIf { it.isNotBlank() }?.let { "الثقة: $it" }
                ).joinToString(" • ")
                if (metrics.isNotEmpty()) card.addView(uiText(metrics, 12f, tint = mutedText),
                    LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(10) })
                val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
                val answer = item.optString("correct_answer")
                if (answer.isNotBlank()) {
                    details.addView(uiText("الإجابة النموذجية", 14f, true, Color.rgb(52,199,89)))
                    details.addView(uiText(answer, 16f))
                }
                val explanation = item.optString("explanation")
                if (explanation.isNotBlank()) {
                    details.addView(uiText("التوضيح", 14f, true, blue))
                    details.addView(uiText(explanation, 15f, tint = mutedText))
                }
                val reveal = uiText("عرض الإجابة والتوضيح⌄", 14f, true, blue).apply {
                    minimumHeight = dp(44); gravity = Gravity.CENTER_VERTICAL
                    setOnClickListener {
                        details.visibility = if (details.visibility == View.GONE) View.VISIBLE else View.GONE
                        text = if (details.visibility == View.VISIBLE) "إخفاء الإجابة⌃" else "عرض الإجابة والتوضيح⌄"
                    }
                }
                card.addView(reveal); card.addView(details)
                rows.addView(card, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(12) })
            }
        }
    }

    private fun advertisementsTabScreen() {
        activeScreen = "ads_tab"
        base("الإعلانات", tab = "ads")
        val screen = screenGeneration
        fun renderAds(data: JSONObject) {
            root.removeAllViews()
            sectionHeader("إعلانات المدارس", "آخر الإعلانات المنشورة", "ads-megaphone")
            val ads = data.optJSONArray("ads") ?: JSONArray()
            var shown = 0
            for (i in 0 until ads.length()) {
                val ad = ads.optJSONObject(i) ?: continue
                if (!ad.optBoolean("active", true) || ad.optString("school").isBlank() || ad.optString("text").isBlank()) continue
                shown++
                val card = uiCard()
                card.addView(uiText(ad.optString("school"), 19f, true, blue))
                card.addView(uiText(ad.optString("text"), 16f),
                    LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(12) })
                addUiCard(card, bottom = 14)
            }
            if (shown == 0) addUiCard(uiCard().apply {
                addView(iconBubble("ads-megaphone", 54, blue))
                addView(uiText("لا توجد إعلانات حاليًا", 20f, true))
                addView(uiText("ستظهر إعلانات المدارس هنا عند نشرها.", 14f, tint = mutedText))
            })
        }
        renderAds(homepage)
        updateSchoolAdsBadge(homepage, markAsRead = true)
        fetch("mobile/bootstrap.php", showError = false) { result ->
            if (result.optBoolean("ok")) {
                applyBootstrap(result)
                if (activeScreen == "ads_tab" && screenGeneration == screen) {
                    renderAds(result)
                    updateSchoolAdsBadge(result, markAsRead = true)
                }
            }
        }
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
    private fun uncertainCheckoutKey(): String = "uncertain_payment_checkout_" + student.optInt("id", 0)

    /** The *active* iPhone Build 498 V31SubscriptionView: external checkout, no in-app payment picker. */
    private fun plansScreen(statusText: String = "") {
        if (activeScreen != "plans") plansReturnScreen = if (activeScreen == "account") "account" else "home"
        activeScreen = "plans"
        base("الاشتراك", true)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(21), dp(21), dp(21), dp(21))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(navy, blue)).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.argb(115, 255, 190, 53))
            }
        }
        val eyebrow = LinearLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        eyebrow.addView(IosGlyphView(this, "crown", gold), LinearLayout.LayoutParams(dp(27), dp(27)).apply {
            marginEnd = dp(7)
        })
        eyebrow.addView(uiText("المرشد الوزاري", 16f, true, gold), LinearLayout.LayoutParams(0, -2, 1f))
        eyebrow.addView(IosGlyphView(this, if (subscribed) "check" else "sparkles", gold),
            LinearLayout.LayoutParams(dp(29), dp(29)))
        hero.addView(eyebrow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        hero.addView(uiText(if (subscribed) "دراستك مستمرة بلا حدّ الأسئلة المجانية"
            else "لا تحفظ الإجابة فقط… افهم أين تتطور", 22f, true, Color.WHITE),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        hero.addView(uiText(if (subscribed) "وصولك إلى بنك أسئلة صفك فعّال وفق مدة اشتراكك."
            else "تدرّب على أسئلة صفك، راجع أخطاءك، واستخدم المحاكاة والمؤشرات المتاحة لتعرف الخطوة التالية.",
            14f, tint = Color.argb(224, 255, 255, 255)),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = if (subscribed) 0 else dp(14) })
        if (!subscribed) {
            val freeLimit = homepage.optInt("free_limit", 15)
            val freeRemaining = homepage.optInt("free_remaining", freeLimit)
            val gift = LinearLayout(this).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(9), dp(12), dp(9))
                background = shape(gold, dp(25).toFloat())
            }
            gift.addView(IosGlyphView(this, "gift", navy), LinearLayout.LayoutParams(dp(19), dp(19)).apply {
                marginEnd = dp(8)
            })
            gift.addView(uiText("تجربة مجانية: $freeLimit سؤال · المتبقي ${freeRemaining.coerceAtLeast(0)}",
                14f, true, navy))
            hero.addView(gift, LinearLayout.LayoutParams(-2, -2))
        }
        addUiCard(hero, bottom = 16)
        if (subscribed) {
            val active = uiCard()
            active.addView(uiText("✓ لا تحتاج إلى أي عملية دفع الآن.", 16f, true,
                Color.rgb(52, 199, 89)))
            addUiCard(active)
            return
        }

        val benefits = uiCard()
        benefits.addView(uiText("ماذا تكسب عند الاشتراك؟", 21f, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(15) })
        fun benefit(title: String, detail: String, glyph: String) {
            val row = LinearLayout(this).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            row.addView(iconBubble(glyph, 35), LinearLayout.LayoutParams(dp(35), dp(35)).apply {
                marginEnd = dp(12)
            })
            val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            copy.addView(uiText(title, 16f, true))
            copy.addView(uiText(detail, 12f, tint = mutedText),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
            row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            benefits.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        benefit("أسئلة صفّك", "تابع الحل بعد انتهاء الرصيد المجاني مع إظهار الإجابة الصحيحة.", "check")
        benefit("دفتر الأخطاء", "ارجع للأسئلة التي أخطأت بها والمواضيع الأضعف.", "book")
        if (homeFeatureEnabled("custom_exam"))
            benefit("اختبار ومحاكاة", "حدد أسئلة للتدريب أو جرّب محاكاة مؤقتة والتصحيح بعد النهاية.", "timer")
        benefits.addView(uiText("ⓘ الشرح التفصيلي والمصادر يظهران فقط حيث تتوفر بيانات صحيحة لهما.",
            12f, tint = mutedText))
        addUiCard(benefits, bottom = 16)

        val plans = homepage.optJSONArray("plans") ?: JSONArray()
        if (plans.length() == 0) {
            val missing = uiCard()
            missing.addView(uiText("لم تصل الباقات من الخادم.", 15f, tint = mutedText))
            addUiCard(missing)
            button("إعادة التحميل") { refreshSubscriptionStatus() }
            return
        }
        sectionHeader("اختر الباقة", "يمكنك تغييرها قبل الانتقال للدفع", "calendar")
        val validIds = ArrayList<String>()
        var monthlyAmount = 0L
        for (i in 0 until plans.length()) {
            val plan = plans.optJSONObject(i) ?: continue
            val id = SubscriptionPlanRules.identifier(plan.optString("id"), plan.optString("key"))
            if (!SubscriptionPlanRules.selectable(id, plan.optLong("amount"))) continue
            if (id !in validIds) validIds.add(id)
            if (id == "month1") monthlyAmount = plan.optLong("amount")
        }
        paymentPlan = SubscriptionPlanRules.selected(validIds, paymentPlan)
        if (validIds.isEmpty()) {
            val errorCard = uiCard()
            errorCard.addView(uiText("تعذر قراءة الباقات من الخادم. حاول تحديث الاشتراك.", 15f, tint = mutedText))
            addUiCard(errorCard)
            button("تحديث الباقات") { refreshSubscriptionStatus() }
            return
        }
        for (i in 0 until plans.length()) {
            val plan = plans.optJSONObject(i) ?: continue
            val id = SubscriptionPlanRules.identifier(plan.optString("id"), plan.optString("key"))
            if (!SubscriptionPlanRules.selectable(id, plan.optLong("amount"))) continue
            val selected = id == paymentPlan
            val amount = plan.optLong("amount", 0)
            val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale("ar", "IQ"))
            val row = LinearLayout(this).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(15), dp(15), dp(15), dp(15))
                background = shape(if (selected) (if (night) Color.rgb(4, 15, 35)
                    else Color.rgb(235, 243, 255)) else surface, dp(18).toFloat())
            }
            val circle = uiText(if (selected) "✓" else "◯", 18f, selected,
                if (selected) blue else mutedText).apply { gravity = Gravity.CENTER }
            row.addView(circle, LinearLayout.LayoutParams(dp(25), dp(28)).apply { marginEnd = dp(12) })
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(uiText(plan.optString("label"), 17f, true))
            val discount = id == "month3" && monthlyAmount > 0 && monthlyAmount * 3 > amount
            labels.addView(uiText(if (discount) "توفّر ${formatter.format(monthlyAmount * 3 - amount)} د.ع مقارنة بثلاث باقات شهرية"
                else "وصول إلى المحتوى حسب مدة الباقة", 13f, discount,
                if (discount) Color.rgb(42, 190, 89) else mutedText),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(uiText("${formatter.format(amount)} د.ع", 17f, true, blue),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
            addUiCard(tappable(row, plan.optString("label")) {
                if (!paymentBusy) {
                    val scrollY = contentScroll?.scrollY ?: 0
                    paymentPlan = id
                    plansScreen(statusText)
                    contentScroll?.post { contentScroll?.scrollTo(0, scrollY) }
                }
            }, bottom = 11)
        }

        if (statusText.isNotBlank()) addUiCard(uiCard().apply {
            addView(registrationNotice(statusText, listOf("تعذر", "غير مؤكدة", "لم يُرجع",
                "مغلقة", "فشل", "غير صحيح").any { statusText.contains(it) }))
        })
        val checkout = button(if (paymentBusy) "جاري تجهيز الرابط…" else "الانتقال إلى الدفع الخارجي") { beginCheckout() }
        checkout.isEnabled = !paymentBusy && paymentPlan.isNotBlank()
        if (prefs.getBoolean(uncertainCheckoutKey(), false)) {
            line("قد يكون الخادم أنشأ طلب دفع لم نستلم رقمه. تحقق من حالة طلباتك مع الدعم قبل إنشاء طلب آخر.", true)
            button("مراسلات الدعم", secondary = true) { supportScreen() }
        }
        val lastOrder = prefs.getInt(lastPaymentKey(), 0)
        if (lastOrder > 0) button("تحقق من حالة آخر عملية", secondary = true) {
            checkPaymentStatus(lastOrder)
        }
        caption("بعد اختيار الباقة ستنتقل إلى صفحة الدفع الخارجية الآمنة. لا توجد طريقة دفع يختارها الطالب داخل التطبيق.")
    }

    private fun beginCheckout() {
        if (paymentBusy || subscribed || paymentPlan.isBlank()) return
        if (prefs.getBoolean(uncertainCheckoutKey(), false)) {
            AlertDialog.Builder(this).setTitle("هل تحققت من طلب الدفع السابق؟")
                .setMessage("ربما أنشأ الخادم طلباً سابقاً رغم فقدان الاستجابة. تحقق من الطلبات مع الإدارة لتجنب طلبات دفع مكررة.")
                .setPositiveButton("تحققت، إنشاء طلب جديد") { _, _ ->
                    prefs.edit().remove(uncertainCheckoutKey()).apply()
                    beginCheckout()
                }.setNegativeButton("رجوع", null).show()
            return
        }
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
                val ambiguous = needsMutationReconciliation(result)
                if (ambiguous) prefs.edit().putBoolean(uncertainCheckoutKey(), true).apply()
                plansScreen(if (ambiguous) "حالة إنشاء طلب الدفع غير مؤكدة. راجع الإدارة قبل إنشاء طلب آخر." else
                    result.optString("message", "تعذر تجهيز الدفع."))
                return@fetch
            }
            if (result.optBoolean("subscribed")) {
                prefs.edit().remove(uncertainCheckoutKey()).apply()
                // An authenticated checkout response can confirm the entitlement;
                // the next bootstrap still reconciles its duration and limits.
                subscribed = true
                homepage.put("subscribed", true)
                refreshSubscriptionStatus(); return@fetch
            }
            val url = result.optString("redirect")
            val order = result.optInt("order_id")
            if (order <= 0 || !AccountRules.safePaymentRedirect(url)) {
                prefs.edit().putBoolean(uncertainCheckoutKey(), true).apply()
                plansScreen("لم يُرجع الخادم رابط دفع آمن ورقم طلب صالحاً. تحقق من الطلب قبل المحاولة مجدداً.")
                return@fetch
            }
            prefs.edit().putInt(lastPaymentKey(), order).remove(uncertainCheckoutKey()).apply()
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
                // An authenticated paid status is authoritative even if follow-up
                // bootstrap fails; it will reconcile the expiry when available.
                subscribed = true
                homepage.put("subscribed", true)
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
            if (EquationStudyRules.isFrenchSubject(subjectId,name)) continue
            button(name) { if (predictions) predictionsScreen(subjectId, name) else topicsScreen(subjectId, name) }
        }
        if (subjects.length() == 0) line("لا توجد مواد متاحة حالياً.")
    }

    private fun topicsScreen(subjectId: Int, subjectName: String) {
        if (EquationStudyRules.isFrenchSubject(subjectId,subjectName)) {
            message("هذه المادة غير متاحة في المنهج الحالي.")
            return
        }
        if (activeScreen != "exam" && activeScreen != "topics") topicsReturnScreen = activeScreen
        topicsSubjectId = subjectId; topicsSubjectName = subjectName
        val expectedScreenGeneration = screenGeneration
        fetch("mobile/topics.php?subject_id=" + subjectId) { response ->
            if (!response.optBoolean("ok") || expectedScreenGeneration != screenGeneration ||
                topicsSubjectId != subjectId) return@fetch
            activeScreen = "topics"
            base(subjectName, true)
            val topicsScreenGeneration = screenGeneration
            val topics = response.optJSONArray("topics") ?: JSONArray()
            var topicMetrics = if (homeInsightsLoaded) homeInsights else JSONObject()
            val (searchFrame, search) = searchControl("ابحث عن موضوع", 14)
            root.addView(searchFrame, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
            val intro = uiCard()
            intro.addView(uiText(subjectName, 12f, true, blue))
            intro.addView(uiText("اختر الموضوع", 23f, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            intro.addView(uiText("شاهد تقدمك وعدد الأسئلة قبل البدء.", 15f, tint = mutedText),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            addUiCard(intro, bottom = 14)
            val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            root.addView(rows)
            fun filterTopics(query: String) {
                rows.removeAllViews()
                var shown = 0
                for (i in 0 until topics.length()) {
                    val topic = topics.optJSONObject(i) ?: continue
                    if (RetiredFrenchContent.isChapter(topic.optInt("id"))) continue
                    val count = topic.optInt("count")
                    val name = topic.optString("name")
                    if (!name.contains(query.trim(), ignoreCase = true)) continue
                    shown++
                    // Match iOS V3TopicsView's mobile/insights.php chapters map.
                    // Never use a daily suggestion as the authoritative metric.
                    val metrics = topicMetrics.optJSONArray("chapters")
                    val metric = metrics?.let { chapters ->
                        (0 until chapters.length()).mapNotNull { chapters.optJSONObject(it) }
                            .firstOrNull { it.optInt("id") == topic.optInt("id") &&
                                it.optInt("subject_id") == subjectId }
                    }
                    val coverage = (metric?.optInt("coverage") ?: 0).coerceIn(0, 100)
                    val accuracy = (metric?.optInt("accuracy") ?: 0).coerceIn(0, 100)
                    val completed = metric?.optBoolean("completed") ?: false
                    val state = when {
                        metric == null -> "التقدم غير متاح"
                        completed -> "مكتمل"
                        coverage > 0 -> "قيد التقدم"
                        else -> "لم يبدأ"
                    }
                    val card = uiCard()
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutDirection = View.LAYOUT_DIRECTION_RTL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    row.addView(iconBubble(if (completed) "check" else "file", 48,
                        if (completed) Color.rgb(52,199,89) else blue),
                        LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                        marginEnd = dp(12)
                    })
                    val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    labels.addView(uiText(name, 17f, true))
                    labels.addView(uiText("$count سؤال", 12f, tint = mutedText),
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                    row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(uiText(state, 12f, true, when {
                        completed -> Color.rgb(52,199,89)
                        coverage > 0 -> blue
                        else -> mutedText
                    }))
                    row.addView(IosGlyphView(this, "chevron", mutedText),
                        LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginStart = dp(7) })
                    card.addView(row)
                    if (metric != null && (coverage > 0 || accuracy > 0)) {
                        card.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                            max = 100
                            progress = coverage
                            progressTintList = ColorStateList.valueOf(
                                if (completed) Color.rgb(52,199,89) else blue)
                            progressBackgroundTintList = ColorStateList.valueOf(
                                if (night) Color.rgb(57,57,60) else Color.rgb(222,224,230))
                        }, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(12) })
                        val accuracyLabel = if (metric.optInt("attempts") > 0) " · دقة $accuracy%" else ""
                        card.addView(uiText("$coverage%$accuracyLabel", 12f, tint = mutedText),
                            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
                    }
                    addUiCard(tappable(card, name) {
                        if (count > 0) loadExam(topic.optInt("id"), name)
                        else message("لم تتم إضافة أسئلة لهذا الموضوع بعد.")
                    }, rows, bottom = 12)
                }
                if (shown == 0) rows.addView(uiText(
                    if (topics.length() == 0) "لم تُضف مواضيع بعد." else "لم نجد موضوعًا مطابقًا للبحث.",
                    15f, tint = mutedText))
            }
            filterTopics("")
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterTopics(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            // The iPhone fetches this endpoint on each topics entry. Refreshing
            // here gives every chapter its real coverage/accuracy/completed state.
            fetch("mobile/insights.php", showError = false) insightsDone@{ insightsResult ->
                if (!insightsResult.optBoolean("ok") || screenGeneration != topicsScreenGeneration ||
                    activeScreen != "topics" || topicsSubjectId != subjectId) return@insightsDone
                topicMetrics = insightsResult.optJSONObject("insights") ?: JSONObject()
                homeInsights = topicMetrics
                homeWeekly = insightsResult.optJSONObject("weekly") ?: JSONObject()
                homeInsightsLoaded = true
                filterTopics(search.text.toString())
            }
        }
    }

    private fun loadExam(id: Int, name: String, fromSearch: Boolean = false, targetText: String = "") {
        if (RetiredFrenchContent.isChapter(id) ||
            EquationStudyRules.isFrenchSubject(topicsSubjectId,topicsSubjectName)) {
            message("المادة الفرنسية حُذفت من المنهج الحالي.")
            return
        }
        if (chapterId != id || examStartedAt == 0L) examStartedAt = SystemClock.elapsedRealtime()
        examFromSearch = fromSearch
        examSearchTarget = if (fromSearch) targetText else ""
        chapterId = id; chapterName = name
        val expectedScreenGeneration = screenGeneration
        fetch("mobile/exam.php?chapter_id=" + id) { result ->
            if (!result.optBoolean("ok") || chapterId != id ||
                expectedScreenGeneration != screenGeneration) return@fetch
            csrf = result.optString("csrf", csrf)
            applyServerQuota(result)
            val fetchedQuestions = result.optJSONArray("questions") ?: JSONArray()
            if (subscribed && student.optInt("id") > 0) {
                offlineExams.saveTopic(student.optInt("id"), id, name, fetchedQuestions)
            }
            examQuestions = JSONArray().apply {
                for (i in 0 until fetchedQuestions.length()) {
                    val candidate = fetchedQuestions.optJSONObject(i) ?: continue
                    if (!RetiredFrenchContent.isQuestion(candidate.optInt("id"))) put(candidate)
                }
            }
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

    private fun applyServerQuota(result: JSONObject) {
        if (result.has("subscribed") && !result.isNull("subscribed"))
            subscribed = result.optBoolean("subscribed")
        if (result.has("free_remaining") && !result.isNull("free_remaining"))
            homepage.put("free_remaining", result.optInt("free_remaining"))
        if (result.has("free_limit") && !result.isNull("free_limit"))
            homepage.put("free_limit", result.optInt("free_limit"))
    }

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

    private fun examProgressHeader() {
        val card = uiCard()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(uiText(topicsSubjectName, 13f, true, blue))
        labels.addView(uiText(chapterName, 16f, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(uiText("${questionIndex + 1} / ${examQuestions.length()}", 15f, true).apply {
            // Numeric fractions must keep the iOS 1 / 73 order inside RTL rows.
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = shape(if (night) Color.rgb(29,39,56) else Color.rgb(231,239,254), dp(14).toFloat())
        })
        card.addView(row)
        card.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = examQuestions.length().coerceAtLeast(1)
            progress = questionIndex + 1
            progressTintList = ColorStateList.valueOf(blue)
            progressBackgroundTintList = ColorStateList.valueOf(if (night) Color.rgb(58,58,62) else Color.rgb(220,224,233))
        }, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(12); bottomMargin = dp(10) })
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        val answered = (0 until examQuestions.length()).count { index ->
            examQuestions.optJSONObject(index)?.let(::isSubmitted) == true
        }
        bottom.addView(uiText("تمت الإجابة: $answered", 12f, tint = mutedText),
            LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(uiText(if (subscribed) "✓ مشترك" else
            "متبقٍ ${homepage.optInt("free_remaining")} سؤال مجاني", 12f, true,
            if (subscribed) Color.rgb(52,199,89) else gold))
        card.addView(bottom)
        addUiCard(card, bottom = 14)
    }

    private fun examQuestionCard(question: JSONObject) {
        val card = uiCard()
        val eyebrow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        eyebrow.addView(uiText("سؤال ${questionIndex + 1}", 12f, true, blue))
        for (key in listOf("exam_year", "exam_round")) {
            val value = question.optString(key).takeUnless { question.isNull(key) }.orEmpty()
            if (value.isBlank()) continue
            eyebrow.addView(uiText(value, 12f, tint = mutedText),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        }
        card.addView(eyebrow)
        card.addView(uiText(question.optString("text"), 20f, true).apply {
            // Keep signed coordinates in mathematical order inside Arabic RTL.
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(11) })
        val questionText = question.optString("text")
        if (EquationStudyRules.applies(questionText, topicsSubjectName, question.optString("type"))) {
            val guide = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = shape(if (night) Color.rgb(32,42,58) else Color.rgb(235,242,254),
                    dp(12).toFloat())
                addView(uiText("طريقة الحل خطوة بخطوة", 15f, true, blue))
                EquationStudyRules.steps(questionText, topicsSubjectName, chapterName)
                    .forEachIndexed { index, instruction ->
                        addView(uiText("${index + 1}- $instruction", 14f),
                            LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(8) })
                    }
                addView(uiText("إرشادات للفهم وليست الإجابة النموذجية.", 11f, tint = mutedText),
                    LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(10) })
            }
            card.addView(guide, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(12) })
        }
        addUiCard(card, bottom = 16)
    }

    private fun examOptionButton(answer: String, index: Int, parent: LinearLayout,
                                 action: () -> Unit): (Boolean) -> Unit {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minimumHeight = dp(50)
        }
        val letter = uiText(ExamRules.optionLetter(index), 15f, true, blue).apply { gravity = Gravity.CENTER }
        content.addView(letter, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
        content.addView(uiText(answer, 16f), LinearLayout.LayoutParams(0, -2, 1f))
        val check = FrameLayout(this)
        content.addView(check, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(8) })
        parent.addView(tappable(content, "إجابة: $answer", action),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        return { selected ->
            content.isSelected = selected
            content.background = GradientDrawable().apply {
                cornerRadius = dp(15).toFloat()
                setColor(if (selected) Color.argb(21,45,108,223)
                    else if (night) Color.rgb(40,40,43) else Color.rgb(249,249,250))
                setStroke(dp(1), if (selected) Color.argb(77,45,108,223)
                    else if (night) Color.rgb(51,51,54) else Color.rgb(237,237,240))
            }
            letter.setTextColor(if (selected) Color.WHITE else blue)
            letter.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (selected) blue else Color.argb(26,45,108,223))
            }
            check.removeAllViews()
            check.addView(IosGlyphView(this, if (selected) "check" else "circle",
                if (selected) blue else mutedText), FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun examFeedbackCard(question: JSONObject, previous: JSONObject?) {
        val correct = ExamRules.knownBoolean(previous?.opt("correct"))
        val correctAnswer = question.optString("correct_answer")
        val feedback = uiCard()
        val verdict = when (correct) {
            true -> "إجابة صحيحة"
            false -> "راجع الإجابة"
            null -> "تم حفظ إجابتك"
        }
        feedback.addView(uiText(verdict, 17f, true, if (correct == true) Color.rgb(41,158,80) else gold))
        val storedAnswer = previous?.optString("text")?.takeIf { it.isNotBlank() }
            ?: previous?.optString("selected_text").orEmpty()
        if (storedAnswer.isNotBlank()) feedback.addView(uiText("إجابتك: $storedAnswer", 15f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        feedback.addView(uiText("الإجابة النموذجية: " + correctAnswer.ifBlank { "غير متاحة من الخادم" },
            15f, true, Color.rgb(41,158,80)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        feedback.addView(uiText("لماذا؟", 12f, true, blue),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        feedback.addView(uiText(ExamRules.explanation(question.optString("explanation"), correctAnswer),
            15f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        addUiCard(feedback, bottom = 16)
    }

    private fun examResultMetric(label: String, value: String, icon: String): View = uiCard().apply {
        addView(IosGlyphView(this@MainActivity, icon, blue),
            LinearLayout.LayoutParams(dp(26), dp(26)).apply { bottomMargin = dp(9) })
        addView(uiText(value, 24f, true).apply {
            if (value.any { it.isDigit() }) {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                textDirection = View.TEXT_DIRECTION_LTR
            }
        })
        addView(uiText(label, 12f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
    }

    /** SwiftUI V3ExamResultView geometry and copy, with server-confirmed grades only. */
    private fun renderExamResult(total: Int, answered: Int, graded: Int, correct: Int) {
        val score = ExamResultRules.Summary(answered, graded, correct)
        val resultColor = when {
            graded == 0 -> mutedText
            score.percent >= 85 -> Color.rgb(52, 199, 89)
            score.percent >= 60 -> blue
            else -> Color.rgb(255, 149, 0)
        }
        val card = uiCard().apply { gravity = Gravity.CENTER_HORIZONTAL }
        val symbol = if (score.percent >= 85 && graded > 0) "trophy"
            else if (score.percent >= 60 && graded > 0) "check" else "book"
        card.addView(FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(26, Color.red(resultColor), Color.green(resultColor), Color.blue(resultColor)))
            }
            addView(IosGlyphView(this@MainActivity, symbol, resultColor),
                FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(116), dp(116)).apply { bottomMargin = dp(15) })
        card.addView(uiText(score.title, 22f, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        card.addView(uiText("$topicsSubjectName • $chapterName", 14f, tint = mutedText).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        card.addView(uiText("${score.percent}%", 48f, true, resultColor).apply {
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
        })
        addUiCard(card, bottom = 15)

        val elapsed = if (examStartedAt > 0L) ((SystemClock.elapsedRealtime() - examStartedAt)
            .coerceAtLeast(0) / 1000).toInt() else 0
        val timeLabel = String.format(java.util.Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60)
        uiTwoColumnGrid(listOf(
            examResultMetric("الصحيحة", score.correct.toString(), "check"),
            examResultMetric("الخاطئة", score.wrong.toString(), "circle"),
            examResultMetric("تمت الإجابة", "${score.answered}/$total", "file"),
            examResultMetric("الوقت", timeLabel, "clock")
        ))
        val next = uiCard()
        next.addView(uiText("✦ خطوتك التالية", 17f, true, blue))
        next.addView(uiText(score.advice, 15f, tint = mutedText),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        if (score.pending > 0) next.addView(uiText(
            "${score.pending} إجابة لم يصل تصحيحها المؤكد بعد؛ لا تُحسب ضمن الأخطاء.",
            13f, tint = gold), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        addUiCard(next, bottom = 14)
        button(if (score.wrong > 0) "راجع الأسئلة التي تحتاج انتباها" else "مراجعة جميع الأسئلة") {
            questionIndex = (0 until examQuestions.length()).firstOrNull { index ->
                val q = examQuestions.optJSONObject(index)
                q != null && isSubmitted(q) &&
                    ExamRules.knownBoolean(q.optJSONObject("answer")?.opt("correct")) == false
            } ?: 0
            renderQuestion()
        }
        button("فتح المراجعة الذكية", secondary = true) { reviewScreen() }
        button("طلب أسئلة جديدة", secondary = true) { questionRequest() }
        button(if (examFromSearch) "العودة إلى نتائج البحث" else "العودة إلى المواضيع", secondary = true) {
            leaveExam()
        }
    }

    private fun renderQuestion() {
        activeScreen = "exam"
        examSubmitButton = null
        base(if (questionIndex >= examQuestions.length() && examQuestions.length() > 0)
            "نتيجة الاختبار" else chapterName, true)
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
            renderExamResult(total, submitted, graded, correct)
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
        examProgressHeader()
        examQuestionCard(question)
        if (!question.isNull("meaning_word") && question.optString("meaning_word").isNotBlank())
            caption("المفردة: " + question.optString("meaning_word"))
        val previous = question.optJSONObject("answer")
        if (submitted) {
            examFeedbackCard(question, previous)
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
                    val answersCard = uiCard()
                    answersCard.addView(uiText("إجابتك", 17f, true),
                        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
                    val selectionViews = ArrayList<Pair<String, (Boolean) -> Unit>>()
                    var selectedText = question.optString("_draft_text")
                    var selectedId = question.optInt("_draft_option")
                    val submit = makeButton("إرسال الإجابة") {
                        sendAnswer(question, selectedText, selectedId)
                    }
                    examSubmitButton = submit
                    submit.isEnabled = selectedText.isNotBlank() && !busyAnswer && !submissionUncertain
                    for (i in 0 until options.length()) {
                        val option = options.optJSONObject(i)
                        val chosen = option?.optString("text") ?: options.optString(i)
                        val optionId = option?.optInt("id") ?: 0
                        val update = examOptionButton(chosen, i, answersCard) {
                            if (!busyAnswer && !submissionUncertain) {
                                selectedText = chosen; selectedId = optionId
                                question.put("_draft_text", chosen).put("_draft_option", optionId)
                                selectionViews.forEach { (text, updateState) -> updateState(text == chosen) }
                                submit.isEnabled = true
                            }
                        }
                        selectionViews.add(chosen to update)
                        update(chosen == selectedText)
                    }
                    answersCard.addView(submit, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                    answersCard.addView(uiText("لا يُخصم من رصيدك المجاني إلا بعد إرسال الإجابة.",
                        11f, tint = mutedText).apply { gravity = Gravity.CENTER },
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
                    addUiCard(answersCard, bottom = 16)
                }
                else -> {
                    val answersCard = examAnswersCard()
                    val field = input("اكتب إجابتك هنا", multiline = true, parent = answersCard).apply {
                        minHeight = dp(120)
                        background = shape(if (night) Color.rgb(44,44,46) else Color.rgb(242,242,247), dp(14).toFloat())
                    }
                    field.setText(question.optString("_draft_text"))
                    val submit = makeButton("إرسال الإجابة") {
                        sendAnswer(question, field.text.toString().trim(), 0)
                    }
                    examSubmitButton = submit
                    submit.isEnabled = field.text.isNotBlank() && !busyAnswer && !submissionUncertain
                    field.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            question.put("_draft_text", s?.toString().orEmpty())
                            submit.isEnabled = !s.isNullOrBlank() && !busyAnswer && !submissionUncertain
                        }
                        override fun afterTextChanged(s: Editable?) = Unit
                    })
                    if (EquationStudyRules.applies(question.optString("text"), topicsSubjectName, question.optString("type"))) {
                        addEquationTools(field, answersCard, question)
                    }
                    answersCard.addView(submit, LinearLayout.LayoutParams(-1, -2))
                    addUiCard(answersCard, bottom = 16)
                }
            }
        }
        // Exam actions live in the top overflow menu, matching iOS.
        // Back navigation is already present in the top bar.
    }

    private fun leaveExam() {
        examStartedAt = 0L
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
        val submitControl = examSubmitButton
        submitControl?.isEnabled = false
        submitControl?.text = "جاري حفظ الإجابة…"
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
            submitControl?.text = "إرسال الإجابة"
            submitControl?.isEnabled = true
            if (!result.optBoolean("ok")) {
                if (!result.optBoolean("payment_required") &&
                    NetworkRules.requiresReconciliation(result.optBoolean("network_error"),
                        result.optBoolean("transport_uncertain"))) {
                    submissionUncertain = true
                    submitControl?.isEnabled = false
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
            applyServerQuota(result)
            val grade = result.optJSONObject("grades")?.optJSONObject(id.toString())
            // Server is the only source of truth; record a submitted answer after its ack.
            val confirmed = grade != null
            if (!confirmed) {
                submissionUncertain = true
                submitControl?.isEnabled = false
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
            // Cache only AFTER the server acknowledges grading. Merely
            // selecting an option must never populate the offline notebook.
            runCatching {
                OfflineCoachNotebook(prefs).remember(
                    userId = student.optInt("id"),
                    questionId = id,
                    question = question.optString("text").ifBlank { question.optString("question_text") },
                    correctAnswer = correctAnswer,
                    explanation = explanation,
                    subject = chapterName,
                    wasCorrect = ExamRules.knownBoolean(grade.opt("is_correct"))
                )
            }
            if (activeScreen == "exam" && sentChapterId == chapterId && questionIndex == sentIndex) {
                // iOS presents the graded answer and explanation inline. The
                // extra Android modal duplicated this card and blocked Next.
                renderQuestion()
            }
        }
    }


    /**
     * Mathematical keyboard integrated into the TOP edge of the actual answer
     * field (not a separate row of buttons underneath the answer).
     * The input keeps all of its existing draft and server submission listeners.
     */
    private fun addEquationTools(field: EditText, parent: LinearLayout, question: JSONObject) {
        val fieldIndex = parent.indexOfChild(field)
        if (fieldIndex < 0) return
        parent.removeView(field)
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (night) Color.rgb(44, 44, 46) else Color.rgb(242, 242, 247))
                setStroke(dp(1), if (night) Color.rgb(75, 75, 79) else Color.rgb(218, 221, 227))
            }
            clipToOutline = true
        }
        parent.addView(holder, fieldIndex, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })

        val toolStrip = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val symbols = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        val keys = listOf(
            "−" to "-", "+" to "+", "×" to "*", "÷" to "/",
            "=" to "=", "(" to "(", ")" to ")", "س" to "x", "ص" to "y",
            "²" to "^2", "³" to "^3", "√" to "sqrt(", "π" to "pi", "/" to "/"
        )
        keys.forEach { (shown, inserted) ->
            val chip = TextView(this).apply {
                text = shown
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(if (night) Color.rgb(220, 230, 250) else blue)
                background = shape(
                    if (night) Color.rgb(56, 58, 63) else Color.WHITE, dp(9).toFloat()
                )
                contentDescription = "إدراج الرمز $shown"
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val editable = field.text ?: return@setOnClickListener
                    val first = field.selectionStart.coerceIn(0, editable.length)
                    val last = field.selectionEnd.coerceIn(0, editable.length)
                    val from = minOf(first, last)
                    val to = maxOf(first, last)
                    editable.replace(from, to, inserted)
                    field.requestFocus()
                    field.setSelection((from + inserted.length).coerceAtMost(editable.length))
                }
            }
            symbols.addView(chip, LinearLayout.LayoutParams(dp(43), dp(38)).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            })
        }
        toolStrip.addView(symbols, android.widget.FrameLayout.LayoutParams(-2, -2))
        holder.addView(toolStrip, LinearLayout.LayoutParams(-1, dp(46)))
        holder.addView(View(this).apply {
            setBackgroundColor(if (night) Color.rgb(75, 75, 79) else Color.rgb(218, 221, 227))
        }, LinearLayout.LayoutParams(-1, dp(1)))

        // Move the existing EditText below the integrated symbol strip.
        field.background = null
        field.setPadding(dp(14), dp(12), dp(14), dp(12))
        holder.addView(field, LinearLayout.LayoutParams(-1, -2))
        if (EquationStudyRules.coordinateQuestion(question.optString("text"), chapterName)) {
            parent.addView(uiText("ارسم نقاطك ثم أدرج إحداثياتها في الإجابة.", 12f, tint = mutedText),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            val graph = NativeCoordinateGrid(this)
            parent.addView(graph, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(6) })
            parent.addView(makeButton("إدراج النقاط المرسومة") {
                val encoded = graph.coordinates()
                if (encoded.isNotBlank()) field.text.insert(
                    field.selectionStart.coerceAtLeast(0).coerceAtMost(field.text.length), encoded
                )
            })
            parent.addView(makeButton("مسح الشبكة") { graph.reset() })
        }
    }

    private fun examAnswersCard(): LinearLayout = uiCard().apply {
        addView(uiText("إجابتك", 17f, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) })
    }

    /** Per-question drafts stay attached to the current exam, never to API answer payloads. */
    private fun examFieldsDraft(question: JSONObject): JSONObject =
        question.optJSONObject("_draft_fields") ?: JSONObject().also { question.put("_draft_fields", it) }

    private fun examDraftInput(question: JSONObject, key: String, hint: String,
                               parent: LinearLayout, number: Boolean = false): EditText =
        input(hint, parent = parent).apply {
            minHeight = dp(48)
            if (number) inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            background = shape(if (night) Color.rgb(44,44,46) else Color.rgb(242,242,247), dp(12).toFloat())
            setText(examFieldsDraft(question).optString(key))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    examFieldsDraft(question).put(key, s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }

    private fun examDraftPicker(question: JSONObject, key: String, keys: List<String>,
                                labels: List<String>, parent: LinearLayout,
                                description: String = "اختر الإجابة"): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
        minimumHeight = dp(48)
        contentDescription = description
        setSelection(keys.indexOf(examFieldsDraft(question).optString(key)).coerceAtLeast(0))
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(view: AdapterView<*>?, item: View?, position: Int, id: Long) {
                examFieldsDraft(question).put(key, keys.getOrNull(position).orEmpty())
            }
            override fun onNothingSelected(view: AdapterView<*>?) = Unit
        }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun stagesQuestion(question: JSONObject, field: String) {
        val stages = question.optJSONObject(field)?.optJSONArray("stages") ?: JSONArray()
        if (stages.length() == 0) { line("هذا النوع من الأسئلة يحتاج إلى بيانات مراحل غير متاحة حالياً."); return }
        val values = LinkedHashMap<String, EditText>()
        val selections = LinkedHashMap<String, Spinner>()
        val card = examAnswersCard()
        for (i in 0 until stages.length()) {
            val stage = stages.optJSONObject(i) ?: continue
            val key = stage.optString("key", "step" + i)
            card.addView(uiText(stage.optString("label", key), 15f, true),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8); topMargin = dp(5) })
            if (stage.optString("kind") == "info" || stage.optBoolean("ungraded")) {
                val info = stage.optString("value")
                if (info.isNotBlank()) card.addView(uiText(info, 15f, tint = mutedText),
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
                continue
            }
            if (stage.optString("kind") == "punnett") {
                val cells = stage.optJSONArray("cells") ?: JSONArray()
                for (j in 0 until cells.length()) {
                    val cellKey = key + "_" + j
                    card.addView(uiText("المربع " + (j + 1), 13f, tint = mutedText))
                    values[cellKey] = examDraftInput(question, cellKey,
                        cells.optJSONObject(j)?.optString("placeholder")?.takeIf { it.isNotBlank() }
                            ?: "أدخل الإجابة", card)
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
                val spinner = examDraftPicker(question, key, keys, labels, card, stage.optString("label", "اختر الإجابة"))
                selections[key] = spinner
                spinner.tag = keys
            } else {
                values[key] = examDraftInput(question, key,
                    stage.optString("placeholder", "أدخل الإجابة"), card, stage.optString("kind") == "number")
                val unit = stage.optString("unit").takeUnless { stage.isNull("unit") }.orEmpty()
                if (unit.isNotBlank()) card.addView(uiText(unit, 13f, tint = mutedText),
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
        }
        val submit = makeButton("إرسال الإجابة") submit@{
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
            if (payload.length() != needed) { message("أكمل جميع مراحل الحل أولاً."); return@submit }
            sendAnswer(question, JSONObject().put("stages", payload).toString(), 0)
        }
        examSubmitButton = submit
        card.addView(submit, LinearLayout.LayoutParams(-1, -2))
        addUiCard(card, bottom = 16)
    }

    private fun tajweedQuestion(question: JSONObject) {
        val items = question.optJSONArray("match_items") ?: JSONArray()
        if (items.length() == 0) { line("لم ترد عناصر المطابقة من الخادم."); return }
        val types = listOf("اختر نوع المد", "مد طبيعي", "مد بدل", "مد متصل", "مد منفصل", "مد لازم", "مد عارض للسكون", "مد لين")
        val card = examAnswersCard()
        val fields = ArrayList<Triple<Int, Spinner, EditText>>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val itemId = item.optInt("id")
            card.addView(uiText(item.optString("mad_word"), 17f, true, blue),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            val spinner = examDraftPicker(question, "match_${itemId}_type",
                listOf("") + types.drop(1), types, card, "نوع المد: " + item.optString("mad_word"))
            val reason = examDraftInput(question, "match_${itemId}_reason", "اكتب السبب", card)
            fields.add(Triple(item.optInt("id"), spinner, reason))
        }
        val submit = makeButton("إرسال الإجابة") submit@{
            val responses = JSONObject()
            for ((id, spinner, field) in fields) {
                if (spinner.selectedItemPosition == 0 || field.text.isBlank()) {
                    message("حدد نوع المد واكتب السبب لكل كلمة."); return@submit
                }
                responses.put(id.toString(), JSONObject().put("type",types[spinner.selectedItemPosition]).put("reason", field.text.toString().trim()))
            }
            sendAnswer(question, JSONObject().put("items", responses).toString(), 0)
        }
        examSubmitButton = submit
        card.addView(submit, LinearLayout.LayoutParams(-1, -2))
        addUiCard(card, bottom = 16)
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
        val card = examAnswersCard()
        val leftKeys = left.keys().asSequence().toList().sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        for (key in leftKeys) {
            card.addView(uiText(key + ". " + left.optString(key), 17f, true),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8); topMargin = dp(5) })
            val spinner = examDraftPicker(question, "english_$key", keys, choices, card, "مطابقة العبارة $key")
            pairFields.add(key to spinner)
        }
        val submit = makeButton("إرسال الإجابة") submit@{
            val lines = ArrayList<String>()
            val used = HashSet<String>()
            for ((key, spinner) in pairFields) {
                val selected = keys.getOrNull(spinner.selectedItemPosition).orEmpty()
                if (selected.isBlank()) { message("أكمل جميع التوصيلات أولاً."); return@submit }
                if (!used.add(selected)) { message("لا يمكن وصل خيار واحد بأكثر من عبارة."); return@submit }
                lines.add(key + "-" + selected)
            }
            sendAnswer(question, lines.joinToString(", "), 0)
        }
        examSubmitButton = submit
        card.addView(submit, LinearLayout.LayoutParams(-1, -2))
        addUiCard(card, bottom = 16)
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
                    if (needsMutationReconciliation(response)) accountScreen()
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
            minimumHeight = dp(154)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TR_BL,
                intArrayOf(navy, blue)).apply { cornerRadius = dp(28).toFloat() }
            elevation = dp(4).toFloat()
        }
        val avatarColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }
        val avatarFrame = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            clipChildren = false
            clipToPadding = false
        }
        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_account_placeholder)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            clipToOutline = true
            contentDescription = "الصورة الشخصية الحالية"
        }
        avatarFrame.addView(avatar, FrameLayout.LayoutParams(-1, -1))
        avatarFrame.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Color.argb(87, 255, 255, 255))
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -1))
        activeAvatar = java.lang.ref.WeakReference(avatar)
        fetchAvatarInto(avatar, account.optString("avatar_url"), account.optString("avatar_revision"))
        val photoChange = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "اختيار صورة شخصية جديدة"
            addView(FrameLayout(this@MainActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                addView(IosGlyphView(this@MainActivity, "camera", blue),
                    FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER))
            }, FrameLayout.LayoutParams(dp(29), dp(29), Gravity.CENTER))
            setOnClickListener { chooseAvatar() }
        }
        avatarFrame.addView(photoChange, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = -dp(7)
            bottomMargin = -dp(7)
        })
        avatarColumn.addView(avatarFrame, LinearLayout.LayoutParams(dp(78), dp(78)))
        val avatarStatusText = when (account.optString("avatar_status")) {
            "approved" -> "الصورة مفعلة"
            "blocked", "rejected" -> "الصورة غير مقبولة"
            else -> "اضغط لتغيير الصورة"
        }
        avatarColumn.addView(uiText(avatarStatusText, 11f, true, Color.argb(194, 255, 255, 255)).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(7) })
        card.addView(avatarColumn)
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), 0, 0, 0)
        }
        fun detail(value: String, size: Float, strong: Boolean, opacity: Int = 255) {
            details.addView(TextView(this).apply {
                typography.apply(this, value, strong); textSize = MurshidTypeScale.sizeSp(size)
                setTextColor(Color.argb(opacity, 255,255,255))
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
                    } else if (needsMutationReconciliation(result)) {
                        dialog.dismiss()
                        accountScreen() // Re-read the one-time rename state before allowing another attempt.
                    }
                }
            }
        }
        dialog.show()
    }

    private fun accountSetting(card: LinearLayout, title: String, subtitle: String,
                               symbol: String, action: () -> Unit): TextView {
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
        val subtitleLabel = uiText(subtitle, 12f, tint = mutedText)
        details.addView(subtitleLabel,
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(IosGlyphView(this, "chevron", mutedText), LinearLayout.LayoutParams(dp(15), dp(15)))
        card.addView(tappable(row, title, action), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(5); bottomMargin = dp(5)
        })
        return subtitleLabel
    }

    private fun accountSwitch(card: LinearLayout, title: String, symbol: String,
                              checked: Boolean): Switch {
        if (card.childCount > 0) card.addView(View(this).apply {
            setBackgroundColor(if (night) Color.rgb(52,52,55) else Color.rgb(235,235,239))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(3); bottomMargin = dp(3) })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54)
        }
        row.addView(IosGlyphView(this, symbol, textColor),
            LinearLayout.LayoutParams(dp(23), dp(23)).apply { marginEnd = dp(12) })
        row.addView(uiText(title, 16f), LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply {
            isChecked = checked
            contentDescription = title
        }
        row.addView(toggle)
        card.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(9); bottomMargin = dp(9)
        })
        return toggle
    }

    private fun accountAppearanceSection() {
        sectionHeader("الألوان والمظهر", "غيّر تصميم تطبيقك وقتما تريد", "sparkles")
        val card = uiCard()
        card.addView(uiText("اختر هويتك اللونية", 17f, true, blue),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) })
        // RTL layout: first child is on the right, matching Aurora | Violet | Royal
        // as seen right-to-left on the owner's iPhone screenshot.
        val palettes = listOf(
            Triple(AppearanceRules.AURORA, "أورورا", "فيروزي وأزرق"),
            Triple(AppearanceRules.VIOLET, "بنفسجي", "بنفسجي ووردي"),
            Triple(AppearanceRules.ROYAL, "ملكي", "أزرق وذهبي")
        )
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        palettes.forEachIndexed { index, (mode, name, description) ->
            val selected = accentStyle == mode
            val colors = when (mode) {
                AppearanceRules.VIOLET -> intArrayOf(Color.rgb(244,146,196), Color.rgb(177, 98, 216))
                AppearanceRules.AURORA -> intArrayOf(Color.rgb(78,224,200), Color.rgb(39,175,226))
                else -> intArrayOf(Color.rgb(246,188,81), Color.rgb(55,112,212))
            }
            val option = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(13), dp(4), dp(13))
                background = GradientDrawable().apply {
                    setColor(if (selected) (if (night) Color.rgb(30, 39, 57) else Color.rgb(236, 241, 251))
                        else surface)
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(if (selected) 2 else 1), if (selected) blue
                        else if (night) Color.rgb(40, 49, 66) else Color.rgb(226,230,236))
                }
                minimumHeight = dp(128)
            }
            val swatch = FrameLayout(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                    cornerRadius = dp(17).toFloat()
                }
                addView(IosGlyphView(this@MainActivity, if (selected) "check" else "sparkles", Color.WHITE),
                    FrameLayout.LayoutParams(dp(29), dp(29), Gravity.CENTER))
            }
            option.addView(swatch, LinearLayout.LayoutParams(dp(54), dp(54)))
            option.addView(uiText(name, 15f, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
            option.addView(uiText(description, 10f, tint = mutedText).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            choices.addView(tappable(option, "اختيار ألوان $name") {
                if (accentStyle != mode) {
                    accentStyle = mode
                    prefs.edit().putString("accent_style", mode).apply()
                    accountScreen()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index < palettes.lastIndex) marginEnd = dp(6)
            })
        }
        card.addView(choices)
        card.addView(View(this).apply {
            setBackgroundColor(if (night) Color.rgb(48,48,52) else Color.rgb(229,231,235))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(15); bottomMargin = dp(13)
        })
        card.addView(uiText("إضاءة التطبيق", 17f, true, blue),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val segment = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = shape(if (night) Color.rgb(45,45,48) else Color.rgb(232,232,237),
                dp(13).toFloat())
        }
        for ((mode, label) in listOf(
            AppearanceRules.SYSTEM to "النظام",
            AppearanceRules.LIGHT to "فاتح",
            AppearanceRules.DARK to "داكن"
        )) {
            val selected = appearanceMode == mode
            val choice = uiText(label, 14f, selected, if (selected) textColor else mutedText).apply {
                gravity = Gravity.CENTER
                minimumHeight = dp(39)
                background = if (selected) shape(if (night) Color.rgb(98,98,103)
                    else Color.WHITE, dp(11).toFloat()) else null
            }
            segment.addView(tappable(choice, "إضاءة $label") {
                if (appearanceMode != mode) {
                    appearanceMode = mode
                    val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                    night = AppearanceRules.isDark(mode, systemDark)
                    prefs.edit().putString("appearance_mode", mode).remove("night").apply()
                    setTheme(if (night) R.style.MurshidDark else R.style.MurshidLight)
                    accountScreen()
                }
            }, LinearLayout.LayoutParams(0, dp(39), 1f))
        }
        card.addView(segment)
        card.addView(uiText("يُحفظ اختيارك على هذا الجهاز، ولا يغيّر بيانات حسابك أو اشتراكك.",
            12f, tint = mutedText).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        addUiCard(card, bottom = 16)
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

            val subscriptionCard = uiCard()
            val subscriptionRow = LinearLayout(this).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            subscriptionRow.addView(IosGlyphView(this, if (subscribed) "check" else "external",
                if (subscribed) Color.rgb(52, 199, 89) else blue),
                LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(13) })
            val subscriptionCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            subscriptionCopy.addView(uiText("اشتراك", 17f, true, if (subscribed) textColor else blue))
            subscriptionCopy.addView(uiText(if (subscribed) "الأيام المتبقية: ${response.optInt("subscription_days_remaining")} يوم"
                else "الدفع خارجي ولا نطلب رقمًا إضافيًا من هنا.", 13f, tint = mutedText),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            subscriptionRow.addView(subscriptionCopy, LinearLayout.LayoutParams(0, -2, 1f))
            subscriptionRow.addView(IosGlyphView(this, "chevron", mutedText),
                LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(9) })
            subscriptionCard.addView(tappable(subscriptionRow, "اشتراك") { plansScreen() })
            addUiCard(subscriptionCard, bottom = 16)
            val activationCard = uiCard()
            val activationHeading = LinearLayout(this).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            activationHeading.addView(IosGlyphView(this, "ticket", blue),
                LinearLayout.LayoutParams(dp(21), dp(21)).apply { marginEnd = dp(9) })
            activationHeading.addView(uiText("تفعيل الاشتراك بكود", 17f, true, blue))
            activationCard.addView(activationHeading)
            activationCard.addView(uiText(
                "إذا حصلت على كود من إدارة المنصة، أدخله هنا لتفعيل اشتراكك لمدة شهر أو أكثر حسب صلاحية الكود.",
                15f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            val activationInput = EditText(this).apply {
                hint = "MW-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX"
                textSize = 14f
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                textDirection = View.TEXT_DIRECTION_LTR
                gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
                setTextColor(textColor)
                setHintTextColor(mutedText)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = shape(if (night) Color.BLACK else Color.rgb(235, 235, 239), dp(8).toFloat())
            }
            activationCard.addView(activationInput, LinearLayout.LayoutParams(-1, dp(48)).apply {
                topMargin = dp(12)
            })
            lateinit var activateButton: Button
            activateButton = makeButton("تفعيل الاشتراك") {
                attemptSubscriptionCodeRedemption(activationInput.text.toString(), activateButton)
            }
            activateButton.isEnabled = false
            activationInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (activateButton.text != "جاري التحقق من الكود…" &&
                        activateButton.text != "حالة التفعيل غير مؤكدة")
                        activateButton.isEnabled = !s.isNullOrBlank()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            activationCard.addView(activateButton, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
            })
            activationCard.addView(uiText("كل كود يُستخدم مرة واحدة فقط، ومدته يحددها إصدار الكود من الإدارة.",
                11f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
            addUiCard(activationCard, bottom = 16)

            sectionHeader("الحساب والخدمات", "إدارة كل ما يخص حسابك", "grid")
            val services = uiCard()
            if (response.optBoolean("can_change_name")) accountSetting(services, "تغيير الاسم",
                "متاح مرة واحدة", "person") { promptRename(profile.optString("name")) }
            val notificationSubtitle = accountSetting(services, "الإشعارات", "لا توجد إشعارات جديدة", "bell") {
                notificationsScreen()
            }
            accountSetting(services, "خدمة العملاء", "محادثة مباشرة مع الدعم", "chat") { supportScreen() }
            accountSetting(services, "تشخيص الأداء", "إحصاءات محلية بلا بيانات شخصية", "chart") {
                performanceDiagnosticsScreen()
            }
            addUiCard(services, bottom = 16)
            fetch("mobile/notifications.php", showError = false) { notices ->
                if (activeScreen == "account" && notices.optBoolean("ok")) {
                    val unread = notices.optInt("unread", 0).coerceAtLeast(0)
                    notificationSubtitle.text = if (unread > 0) "لديك $unread غير مقروء"
                        else "لا توجد إشعارات جديدة"
                }
            }

            accountAppearanceSection()

            sectionHeader("الأمان والتذكير", "خيارات خاصة بجهازك", "lock")
            val security = uiCard()
            val lockToggle = accountSwitch(security, "قفل حقيقي ببصمة الجهاز", "id-card",
                prefs.getBoolean("biometric_lock", false))
            val biometricAvailable = deviceBiometricAvailable()
            if (!biometricAvailable) {
                lockToggle.isEnabled = false
                security.addView(uiText("البصمة غير متاحة أو غير مهيأة من إعدادات الجهاز.",
                    12f, tint = mutedText))
            }
            var editingLockSwitch = false
            lockToggle.setOnCheckedChangeListener { _, selected ->
                if (editingLockSwitch) return@setOnCheckedChangeListener
                if (!selected) {
                    prefs.edit().putBoolean("biometric_lock", false).remove("biometric_background_at")
                        .remove("biometric_unlock_required").apply()
                    message("تم إيقاف قفل البصمة.")
                } else {
                    editingLockSwitch = true
                    lockToggle.isChecked = false
                    editingLockSwitch = false
                    authenticateBiometric("أكد بصمة الجهاز لتفعيل قفل المرشد الوزاري") { success ->
                        if (success) {
                            prefs.edit().putBoolean("biometric_lock", true)
                                .remove("biometric_background_at").apply()
                            editingLockSwitch = true
                            lockToggle.isChecked = true
                            editingLockSwitch = false
                            message("تم تفعيل قفل البصمة. سيُطلب بعد العودة للتطبيق من الخلفية.")
                        } else message("لم يتم تفعيل قفل البصمة.")
                    }
                }
            }
            val reminderToggle = accountSwitch(security, "تذكير دراسة يومي", "bell",
                prefs.getBoolean("study_reminder", false))
            reminderToggle.setOnCheckedChangeListener { _, selected -> setStudyReminder(selected) }
            if (prefs.getBoolean("study_reminder", false)) {
                val hour = prefs.getInt("study_reminder_hour", 19)
                val minute = prefs.getInt("study_reminder_minute", 0)
                val localTime = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
                accountSetting(security, "وقت التذكير", localTime, "timer") {
                    TimePickerDialog(this, { _, h, m ->
                        if (DailyStudyReminder.enable(this, h, m)) accountScreen()
                        else message("تعذر حفظ التذكير. تحقق من إذن الإشعارات.")
                    }, hour, minute, true).show()
                }
            }
            accountSetting(security, "استعادة / تغيير كلمة المرور", "تأكيد الهوية لحماية حسابك", "lock") {
                boundRecoveryScreen()
            }
            addUiCard(security, bottom = 16)

            // V31AccountView has a standalone red logout card below security.
            val logoutCard = uiCard()
            val logoutRow = LinearLayout(this).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(46)
            }
            logoutRow.addView(IosGlyphView(this, "logout", Color.rgb(255, 69, 79)),
                LinearLayout.LayoutParams(dp(25), dp(25)).apply { marginEnd = dp(10) })
            logoutRow.addView(uiText("تسجيل الخروج", 17f, true, Color.rgb(255, 69, 79)))
            logoutCard.addView(tappable(logoutRow, "تسجيل الخروج") {
                AlertDialog.Builder(this).setTitle("تسجيل الخروج؟")
                    .setMessage("سيتم إنهاء جلسة حسابك على هذا الجهاز.")
                    .setPositiveButton("تسجيل الخروج") { _, _ ->
                        fun clearAfterLogout() {
                            sessionGeneration++
                            StudentPush.detach(this@MainActivity)
                            intent.removeExtra(StudentPush.inboxExtra)
                            api.clear(); csrf = ""; student = JSONObject()
                            offlineExams.clear() // Explicit logout clears private downloaded exams and un-synced answers.
                            OfflineCoachAccess(prefs).clear()
                            offlineCoachMode = false
                            homeFeatureFlags = JSONObject()
                            homeInsightsLoaded = false; homeInsights = JSONObject(); homeWeekly = JSONObject()
                            guestLandingScreen()
                        }
                        if (csrf.isBlank()) {
                            clearAfterLogout()
                        } else fetch("mobile/logout.php", StudentPush.logoutPayload(this, csrf), showError = false) { result ->
                            if (LogoutRules.mayClearLocalSession(csrf, result.optBoolean("ok"))) {
                                clearAfterLogout()
                            } else {
                                message(result.optString("message", "تعذر تسجيل الخروج؛ لم تُحذف جلستك. حاول مجددًا."))
                            }
                        }
                    }.setNegativeButton("إلغاء", null).show()
            })
            addUiCard(logoutCard, bottom = 16)

        }
    }

    /** Counts only functional categories, never request URLs or personal details. */
    private fun performanceDiagnosticsScreen() {
        activeScreen = "performance_diagnostics"
        base("تشخيص الأداء", true)
        caption("هذه الإحصاءات محفوظة على جهازك فقط. لا تتضمن إجابات أو أسماء أو بيانات دخول.")
        val metrics = LocalReliability(prefs)
        val data = metrics.counters()
        val titles = mapOf(
            "bootstrap" to "تسجيل الدخول والجلسة",
            "exam" to "تحميل الاختبارات",
            "answers" to "إرسال الإجابات",
            "study_plan" to "خطط الدراسة",
            "review" to "المراجعات",
            "notifications" to "الإشعارات",
            "mobile_other" to "خدمات الطالب",
            "api_other" to "خدمات أخرى",
            "other" to "اتصالات أخرى"
        )
        if (data.length() == 0) caption("لا توجد قياسات بعد. استخدم التطبيق ثم راجع هذه الصفحة.")
        for ((category, title) in titles) {
            val row = data.optJSONObject(category) ?: continue
            val card = uiCard()
            card.addView(uiText(title, 16f, true))
            card.addView(uiText("الطلبات: ${row.optInt("requests")} · الإخفاقات: ${row.optInt("errors")}", 13f),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            card.addView(uiText("اتصال: ${row.optInt("network_errors")} · بطيئة: ${row.optInt("slow")}", 12f,
                tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            addUiCard(card, bottom = 8)
        }
        button("مسح قياسات هذا الجهاز", secondary = true) {
            AlertDialog.Builder(this).setTitle("حذف سجل الأداء؟")
                .setMessage("سيحذف أعداد الطلبات فقط، ولن يؤثر على أي إجابة أو اشتراك.")
                .setPositiveButton("حذف") { _, _ ->
                    metrics.clear()
                    performanceDiagnosticsScreen()
                }.setNegativeButton("رجوع", null).show()
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
                            else if (needsMutationReconciliation(result)) {
                                message("حالة إلغاء الطلب غير مؤكدة. تحقق من حالة الخصوصية المحدثة.")
                                accountScreen()
                            }
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
                                else if (needsMutationReconciliation(result)) {
                                    message("حالة طلب الحذف غير مؤكدة. تحقق من حالة الخصوصية قبل المحاولة مجدداً.")
                                    accountScreen()
                                }
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
            if (!StudentPush.allowed(this)) button("تفعيل إشعارات الحساب", secondary = true) {
                if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("push_permission_prompted", false)) {
                    prefs.edit().putBoolean("push_permission_prompted", true).apply()
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), StudentPush.requestCode)
                } else startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
            }
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
        val reviews = localLearningReviews()
        var target = LearningInsights.suggestedDailyTarget(reviews, System.currentTimeMillis())
        caption("الهدف المقترح $target سؤال يومياً، محسوب من مراجعاتك المحلية ويمكنك تغييره.")
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
                } else if (needsMutationReconciliation(result)) {
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
        if (activeScreen != "school_ad") schoolAdReturnScreen = activeScreen
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
                        } else if (needsMutationReconciliation(result)) {
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
            textSize = 40f; gravity = Gravity.CENTER; typeface = typography.strong()
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
                    } else if (needsMutationReconciliation(result)) {
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
                    } else if (needsMutationReconciliation(result)) {
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
                        val ambiguous = needsMutationReconciliation(result) ||
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
            textSize = 20f; typeface = typography.strong()
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
                val ambiguous = needsMutationReconciliation(response) ||
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
                } else if (needsMutationReconciliation(response)) {
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
            lateinit var send: Button
            send = button("إرسال الرسالة") {
                if (msg.text.isBlank()) { message("اكتب رسالتك."); return@button }
                send.isEnabled = false
                send.text = "جارٍ إرسال الرسالة…"
                fetch("mobile/support.php",JSONObject().put("csrf",csrf).put("message",msg.text.toString())) { result ->
                    if (result.optBoolean("ok")) supportScreen()
                    else if (needsMutationReconciliation(result)) {
                        send.text = "حالة الإرسال غير مؤكدة"
                        message("قد تكون الرسالة وصلت. تحقق من مراسلات الدعم قبل إرسالها مجدداً.")
                    } else {
                        send.isEnabled = true
                        send.text = "إرسال الرسالة"
                    }
                }
            }
        }
    }

    private fun redeemScreen() {
        base("تفعيل الاشتراك", true)
        line("أدخل رمز الاشتراك الرسمي الذي حصلت عليه من إدارة المنصة.")
        val code = input("رمز الاشتراك")
        lateinit var activate: Button
        activate = button("تفعيل الكود") {
            attemptSubscriptionCodeRedemption(code.text.toString(), activate)
        }
    }

    /** Shared by the standalone activation page and the iPhone-matched inline account card. */
    private fun attemptSubscriptionCodeRedemption(raw: String, activate: Button) {
        val code = raw.uppercase(java.util.Locale.ROOT).filterNot { it == '-' || it.isWhitespace() }
        if (!code.matches(Regex("MW[A-F0-9]{24}"))) {
            message("صيغة الكود غير صحيحة. تأكد من كتابة الكود الصادر من الإدارة كاملاً.")
            return
        }
        if (!activate.isEnabled) return
        val pendingKey = "uncertain_redeem_" + student.optInt("id", 0)
        if (prefs.getBoolean(pendingKey, false)) {
            AlertDialog.Builder(this).setTitle("تحقق من عملية التفعيل السابقة")
                .setMessage("ربما استُخدم رمز في طلب سابق لم يصل رده. افحص الاشتراك لدى الإدارة قبل تجربة رمز آخر.")
                .setPositiveButton("تحديث الاشتراك") { _, _ ->
                    fetch("mobile/bootstrap.php", showError = false) { updated ->
                        if (updated.optBoolean("ok")) {
                            applyBootstrap(updated)
                            if (subscribed) prefs.edit().remove(pendingKey).apply()
                            if (activeScreen == "account") accountScreen() else showHome(updated)
                        }
                    }
                }
                .setNeutralButton("تأكدت من الإدارة") { _, _ ->
                    prefs.edit().remove(pendingKey).apply()
                    message("يمكنك المحاولة مجددًا بعد التحقق من حالة الطلب لدى الإدارة.")
                }.setNegativeButton("رجوع", null).show()
            return
        }
        activate.isEnabled = false
        activate.text = "جاري التحقق من الكود…"
        if (!prefs.edit().putBoolean(pendingKey, true).commit()) {
            activate.isEnabled = true
            activate.text = "تفعيل الاشتراك"
            message("تعذر تأمين سجل عملية التفعيل على الجهاز. حاول لاحقًا.")
            return
        }
        fetch("mobile/redeem-subscription-code.php",
            JSONObject().put("csrf", csrf).put("code", code), showError = false) { result ->
            when {
                result.optBoolean("ok") && result.optBoolean("subscribed") -> {
                    prefs.edit().remove(pendingKey).apply()
                    // The code is single-use. Preserve the confirmed grant even
                    // if the follow-up bootstrap cannot be fetched immediately.
                    subscribed = true
                    homepage.put("subscribed", true)
                    message(result.optString("message", "تم تأكيد تفعيل اشتراكك من الخادم."))
                    if (activeScreen == "account") refreshAccountAfterMutation() else refreshHome()
                }
                result.optBoolean("ok") || needsMutationReconciliation(result) -> {
                    activate.text = "حالة التفعيل غير مؤكدة"
                    message("لم يؤكد الخادم تفعيل الاشتراك. نتحقق من الحالة قبل إعادة استخدام الكود.")
                    fetch("mobile/bootstrap.php", showError = false) { updated ->
                        if (updated.optBoolean("ok")) {
                            applyBootstrap(updated)
                            if (subscribed) prefs.edit().remove(pendingKey).apply()
                            if (activeScreen == "account") accountScreen()
                            else if (subscribed) showHome(updated)
                        }
                    }
                }
                else -> {
                    prefs.edit().remove(pendingKey).apply()
                    activate.isEnabled = true
                    activate.text = "تفعيل الاشتراك"
                    message(result.optString("message", "تعذر تفعيل هذا الكود."))
                }
            }
        }
    }

    private fun predictionsScreen(subjectId: Int, subject: String) {
        if (!subscribed) { message("المرشحات متاحة للمشتركين فقط."); return }
        predictionsReturnScreen = if (activeScreen == "subjects") "subjects" else "predictions_tab"
        activeScreen = "predictions_list"
        fetch("mobile/predictions.php?subject_id=" + subjectId) { response ->
            if (activeScreen != "predictions_list" || !response.optBoolean("ok")) return@fetch
            base("مرشحات " + subject, true)
            val predictions = response.optJSONArray("predictions") ?: JSONArray()
            for (i in 0 until predictions.length()) {
                val p = predictions.optJSONObject(i) ?: continue
                val question = p.optString("question_text").ifBlank {
                    p.optString("title", "مرشح ${i + 1}")
                }
                val topic = p.optString("topic_name").ifBlank {
                    p.optString("chapter_name", subject)
                }
                val explanation = p.optString("explanation")
                val card = uiCard().apply {
                    addView(uiText(topic, 12f, tint = mutedText))
                    addView(uiText("س${i + 1}: $question", 17f, true, blue),
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                    if (explanation.isNotBlank()) addView(uiText("افتح لعرض الشرح والتفاصيل", 12f,
                        tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
                }
                addUiCard(tappable(card, "تفاصيل المرشح ${i + 1}") {
                    AlertDialog.Builder(this).setTitle("مرشح ${i + 1} · $topic")
                        .setMessage(question + if (explanation.isBlank()) "" else "\n\nالشرح: $explanation")
                        .setPositiveButton("إغلاق", null).show()
                })
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

    private fun dailyDraftKey(): String = "daily_challenge_draft_v1_" + student.optInt("id", 0)

    private fun dailyDraftSignature(): String =
        DailyDraftRules.signature(student.optInt("id", 0), dailyQuestions)

    private fun clearDailyDraft() {
        prefs.edit().remove(dailyDraftKey()).apply()
    }

    private fun saveDailyDraft(pendingReceipt: Boolean = dailyUncertain): Boolean {
        val signature = dailyDraftSignature()
        if (signature.isBlank()) return false
        val answers = JSONObject()
        DailyDraftRules.validAnswers(dailyQuestions, dailyAnswers).forEach { (id, answer) ->
            answers.put(id.toString(), answer)
        }
        val editor = prefs.edit().putString(dailyDraftKey(), JSONObject()
            .put("signature", signature).put("answers", answers)
            .put("pending_receipt", pendingReceipt).toString())
        // A pending POST flag must reach disk before network I/O, even if the app is killed.
        return if (pendingReceipt) editor.commit() else { editor.apply(); true }
    }

    /** Recover only the current account/day/question set; a pending POST needs manual reconciliation. */
    private fun restoreDailyDraft() {
        dailyAnswers.clear()
        dailyUncertain = false
        if (dailyDone) { clearDailyDraft(); return }
        // A transiently unavailable or malformed GET is not proof that an earlier
        // POST was rejected. Keep its persisted receipt marker until a valid GET.
        if (!DailyDraftRules.mayReconcileDraft(dailyAvailable, dailyQuestions)) return
        val signature = dailyDraftSignature()
        if (signature.isBlank()) return
        val saved = try { JSONObject(prefs.getString(dailyDraftKey(), "").orEmpty()) }
            catch (_: Exception) { null }
        val baghdadDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Baghdad"))
        val isLegacy = saved != null && (0L..1L).any { days ->
            saved.optString("signature") == DailyDraftRules.legacySignature(student.optInt("id", 0),
                baghdadDate.minusDays(days).toString(), dailyQuestions)
        }
        if (saved?.optString("signature") != signature && !isLegacy) {
            // A new day or question must not erase evidence of an uncertain POST.
            // A previously accepted answer may be in flight even after midnight.
            if (saved?.optBoolean("pending_receipt") == true) dailyUncertain = true
            else clearDailyDraft()
            return
        }
        val answers = saved?.optJSONObject("answers") ?: JSONObject()
        val restored = LinkedHashMap<Int, String>()
        for (question in dailyQuestions) {
            val answer = answers.optString(question.id.toString())
            if (answer.isNotBlank()) restored[question.id] = answer
        }
        dailyAnswers.putAll(DailyDraftRules.validAnswers(dailyQuestions, restored))
        dailyUncertain = saved?.optBoolean("pending_receipt") == true
        if (isLegacy) saveDailyDraft(pendingReceipt = dailyUncertain)
    }

    private fun loadDailyChallenge() {
        activeScreen = "daily_challenge"
        base("تحدي اليوم", true)
        caption("جارٍ تحميل أسئلة التحدي ونتيجتك من الخادم…")
        fetch("mobile/challenge.php") { response ->
            if (activeScreen != "daily_challenge") return@fetch
            if (!response.optBoolean("ok")) {
                base("تحدي اليوم", true)
                caption("تعذر تحميل أسئلة التحدي. تحقق من الإنترنت وأعد المحاولة؛ لن تُرسل أي إجابة من جديد.")
                button("إعادة المحاولة") { loadDailyChallenge() }
                return@fetch
            }
            csrf = response.optString("csrf", csrf)
            dailyAvailable = ExamRules.knownBoolean(response.opt("available")) ?: true
            dailyDone = ExamRules.knownBoolean(response.opt("done")) == true
            dailyQuotaBlocked = response.optBoolean("quota_blocked") || response.optBoolean("payment_required")
            dailyScore = response.optInt("score")
            dailyTotal = response.optInt("total")
            dailyReward = response.optInt("reward")
            dailyGivenAnswer = response.optString("given_answer")
            dailyCorrectAnswer = response.optString("correct_answer")
            dailyExplanation = response.optString("explanation")
            dailyNextResetAt = response.optString("next_reset_at")
            dailyQuestions = parseDailyQuestions(response.optJSONArray("questions") ?: JSONArray())
            restoreDailyDraft()
            dailyStatusMessage = ""
            renderDailyChallenge()
        }
    }

    private fun renderDailyChallenge() {
        activeScreen = "daily_challenge"
        base("تحدي اليوم", true)
        line(if (dailyDone) "أكملت تحدي اليوم!" else "سؤال واحد يصنع فرقاً", true)
        caption("سؤال صعب واحد كل 24 ساعة، لا يستهلك رصيد الأسئلة المجانية ولا يُضاف إلى نقاط المسابقة.")
        if (!dailyAvailable) {
            line("لا يوجد تحدٍ متاح الآن.")
            button("تحديث التحدي", secondary = true) { loadDailyChallenge() }
            return
        }
        if (dailyDone) {
            line("نتيجتك: $dailyScore / $dailyTotal", true)
            if (dailyReward > 0) line("المكافأة الممنوحة: +$dailyReward XP")
            if (dailyGivenAnswer.isNotBlank()) caption("إجابتك: $dailyGivenAnswer")
            if (dailyCorrectAnswer.isNotBlank()) caption("الإجابة الصحيحة: $dailyCorrectAnswer")
            if (dailyExplanation.isNotBlank()) addUiCard(uiCard().apply {
                addView(uiText("التوضيح", 15f, true, blue))
                addView(uiText(dailyExplanation, 15f),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            })
            val nextTime = try {
                java.time.OffsetDateTime.parse(dailyNextResetAt)
                    .atZoneSameInstant(java.time.ZoneId.of("Asia/Baghdad"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy · h:mm a", java.util.Locale("ar", "IQ")))
            } catch (_: Exception) { "" }
            caption(if (nextTime.isNotBlank()) "التحدي القادم: $nextTime بتوقيت بغداد"
                else "يتاح التحدي التالي بعد مرور 24 ساعة على محاولتك.")
            button("عرض ترتيبي في تحدي المليون") { openCompetition("contest") }
            button("متابعة إنجازاتي") { openCompetition("achievements") }
            button("تحديث النتيجة", secondary = true) { loadDailyChallenge() }
            return
        }
        if (dailyQuotaBlocked) {
            line("الخادم منع التحدي بسبب حالة الرصيد أو الاشتراك. لن نعيد إرسال إجابة تلقائياً.")
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
            caption("احتفظنا بإجاباتك على هذا الجهاز. تحقق من حالتك على الخادم أولاً؛ لن تُرسل الإجابات ثانية تلقائياً.")
            button("التحقق من حالة التحدي") { loadDailyChallenge() }
            button("تحققت، إعادة المحاولة يدوياً", secondary = true) {
                AlertDialog.Builder(this).setTitle("إعادة إرسال التحدي؟")
                    .setMessage("تحقق من حالة التحدي أو الدعم قبل المتابعة. قد يكون التسليم السابق وصل للخادم رغم فقدان الرد، ولن نعيده تلقائياً.")
                    .setPositiveButton("تحققت، متابعة") { _, _ ->
                        dailyUncertain = false
                        saveDailyDraft(pendingReceipt = false)
                        renderDailyChallenge()
                    }.setNegativeButton("إلغاء", null).show()
            }
            return
        }
        for ((index, question) in dailyQuestions.withIndex()) {
            val questionCard = uiCard()
            questionCard.addView(uiText("سؤال ${index + 1} من ${dailyQuestions.size}", 12f, true, blue),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            questionCard.addView(uiText(question.text, 17f, true),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
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
                        isEnabled = !dailyBusy
                        buttonTintList = ColorStateList.valueOf(blue)
                        setPadding(dp(9), dp(7), dp(9), dp(7))
                        background = shape(if (night) Color.rgb(49,49,52)
                            else Color.rgb(242,242,247), dp(12).toFloat())
                    }
                    ids[radio.id] = choice
                    group.addView(radio, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
                }
                group.setOnCheckedChangeListener { _, selected ->
                    if (dailyBusy) return@setOnCheckedChangeListener
                    ids[selected]?.let { dailyAnswers[question.id] = it; saveDailyDraft() }
                }
                questionCard.addView(group)
            } else {
                val field = EditText(this).apply {
                    hint = "اكتب إجابتك هنا"
                    textSize = 16f
                    setTextColor(textColor)
                    setHintTextColor(mutedText)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    minLines = 2
                    maxLines = 5
                    isEnabled = !dailyBusy
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = shape(if (night) Color.rgb(49,49,52)
                        else Color.rgb(242,242,247), dp(12).toFloat())
                    setText(dailyAnswers[question.id].orEmpty())
                }
                field.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        if (dailyBusy) return
                        dailyAnswers[question.id] = s?.toString().orEmpty()
                        saveDailyDraft()
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                questionCard.addView(field, LinearLayout.LayoutParams(-1, -2))
            }
            addUiCard(questionCard, bottom = 14)
        }
        val submit = button(if (dailyBusy) "جارٍ التصحيح…" else "إنهاء تحدي اليوم") {
            if (!CompetitionRules.canSubmit(dailyQuestions, dailyAnswers, dailyAvailable, dailyDone,
                    dailyBusy, dailyUncertain)) {
                message("أجب عن السؤال قبل التسليم.")
            } else {
                AlertDialog.Builder(this).setTitle("تسليم تحدي اليوم")
                    .setMessage("سيتم إرسال الإجابة للتصحيح مرة واحدة، ولن يتاح تحدٍ جديد قبل مرور 24 ساعة. هل أنت مستعد؟")
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
        if (dailyDraftSignature().isBlank()) {
            message("تعذر تحديد حسابك أو أسئلة التحدي للحفظ الآمن. حدّث بيانات الحساب أولاً.")
            return
        }
        dailyBusy = true
        val answerObject = JSONObject()
        CompetitionRules.submission(dailyQuestions, dailyAnswers).forEach { (id, answer) ->
            answerObject.put(id, answer)
        }
        val payload = JSONObject().put("csrf", csrf).put("answers", answerObject)
        if (!saveDailyDraft(pendingReceipt = true)) {
            dailyBusy = false
            message("تعذر حفظ حالة التحدي على الجهاز؛ لن نرسل الإجابات قبل تأمين الاستعادة.")
            return
        }
        renderDailyChallenge()
        fetch("mobile/challenge.php", payload, showError = true) { response ->
            dailyBusy = false
            if (response.optBoolean("quota_blocked") || response.optBoolean("payment_required")) {
                dailyStatusMessage = "انتهى رصيدك المجاني أثناء التحدي. يمكنك تفعيل الاشتراك والمتابعة."
                dailyUncertain = false
                dailyQuotaBlocked = true
                saveDailyDraft(pendingReceipt = false)
                if (activeScreen == "daily_challenge") renderDailyChallenge()
                return@fetch
            }
            if (!response.optBoolean("ok")) {
                dailyUncertain = true
                saveDailyDraft(pendingReceipt = true)
                if (activeScreen == "daily_challenge") renderDailyChallenge()
                return@fetch
            }
            dailyDone = ExamRules.knownBoolean(response.opt("done")) == true
            dailyScore = response.optInt("score", dailyScore)
            dailyTotal = response.optInt("total", dailyTotal)
            dailyReward = response.optInt("reward", dailyReward)
            dailyGivenAnswer = response.optString("given_answer", dailyGivenAnswer)
            dailyCorrectAnswer = response.optString("correct_answer", dailyCorrectAnswer)
            dailyExplanation = response.optString("explanation", dailyExplanation)
            dailyNextResetAt = response.optString("next_reset_at", dailyNextResetAt)
            if (!dailyDone) {
                // An accepted POST without a completion receipt must be reconciled.
                dailyUncertain = true
                dailyStatusMessage = "استجاب الخادم دون تأكيد إنهاء التحدي. تحقق من حالته."
                saveDailyDraft(pendingReceipt = true)
            } else {
                dailyStatusMessage = ""
                clearDailyDraft()
                dailyAnswers.clear()
                fetch("mobile/challenge.php", showError = false) { latest ->
                    if (latest.optBoolean("ok") && ExamRules.knownBoolean(latest.opt("done")) == true) {
                        dailyScore = latest.optInt("score", dailyScore)
                        dailyTotal = latest.optInt("total", dailyTotal)
                        dailyReward = latest.optInt("reward", dailyReward)
                        dailyGivenAnswer = latest.optString("given_answer", dailyGivenAnswer)
                        dailyCorrectAnswer = latest.optString("correct_answer", dailyCorrectAnswer)
                        dailyExplanation = latest.optString("explanation", dailyExplanation)
                        dailyNextResetAt = latest.optString("next_reset_at", dailyNextResetAt)
                        if (activeScreen == "daily_challenge") renderDailyChallenge()
                    }
                }
                fetch("mobile/bootstrap.php", showError = false) { updated ->
                    if (updated.optBoolean("ok")) {
                        homepage = withoutFrenchSubjects(updated)
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
            if (activeScreen != "contest") return@fetch
            if (!first.optBoolean("ok")) {
                base("تحدي المليون", true)
                caption("تعذر تحميل الترتيب. راجع اتصال الإنترنت ثم أعد المحاولة.")
                button("إعادة المحاولة") { loadContest() }
                return@fetch
            }
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
        addUiCard(uiCard().apply {
            addView(uiText("تحدي المليون", 12f, true, blue))
            addView(uiText("تقدمك بين الطلاب", 21f, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(uiText("الترتيب يعتمد على نقاط المنصة والإجابات الصحيحة ضمن فترة التحدي.",
                14f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }, bottom = 16)
        val configuration = contest.optJSONObject("config") ?: JSONObject()
        val phase = configuration.optString("phase")
        val phaseLabel = when (phase) {
            "scheduled" -> "لم يبدأ تحدي المليون بعد"
            "active" -> "تحدي المليون جارٍ الآن"
            "ended" -> "انتهت فترة تحدي المليون"
            else -> if (configuration.optBoolean("active")) "تحدي المليون جارٍ الآن" else "حالة التحدي غير متاحة"
        }
        addUiCard(uiCard().apply {
            addView(uiText(phaseLabel, 17f, true, blue))
            addView(uiText("موعد البداية: " + configuration.optString("start_at", "لم يحدد بعد"),
                14f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
            addView(uiText("موعد النهاية: " + configuration.optString("end_at", "لم يحدد بعد"),
                14f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            addView(uiText("التوقيت: بغداد · يتحدث الموعد عند فتح التحدي أو تحديثه.",
                12f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }, bottom = 14)
        val mine = contest.optJSONObject("mine")
        val active = ExamRules.knownBoolean(configuration.opt("active"))
        addUiCard(uiCard().apply {
            addView(uiText("مركزك الحالي", 13f, tint = mutedText))
            addView(uiText(if (mine == null) "ابدأ الإجابة لتدخل الترتيب"
                else "#${mine.optInt("rank")}", if (mine == null) 18f else 32f, true, blue),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            if (mine != null) addView(uiText("${mine.optInt("xp")} XP • ${mine.optInt("correct")} إجابة صحيحة",
                13f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            if (active == true) addView(uiText("● نشط", 13f, true, Color.rgb(41,158,80)),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }, bottom = 18)
        // Both leaderboards are visible together as on iOS. Expanding one does not hide the other.
        for ((mode, heading, data) in listOf(
            Triple("contest", "متصدرو تحدي المليون", contest),
            Triple("season", "ترتيب الموسم", season))) {
            val ranking = data.optJSONArray("leaderboard") ?: JSONArray()
            sectionHeader(heading, if (ranking.length() == 0) "لا توجد بيانات بعد" else "أعلى المراكز", "chart")
            if (ranking.length() == 0) {
                addUiCard(uiCard().apply { addView(uiText(if (mode == "season" && data.length() == 0)
                    "تعذر تحميل ترتيب الموسم الآن. حدّث الصفحة لاحقاً."
                    else "سيظهر الترتيب هنا عند توفر البيانات.", 14f, tint = mutedText)) })
            }
            val expanded = contestMode == mode && contestShowAll
            val limit = if (expanded) ranking.length() else minOf(ranking.length(), 10)
            for (i in 0 until limit) {
                val entry = ranking.optJSONObject(i) ?: continue
                val rank = entry.optInt("rank")
                val name = entry.optString("name", "طالب")
                val xp = entry.optInt("xp")
                val grade = entry.optString("grade")
                addUiCard(uiRow("#$rank  $name", "$xp XP" + if (grade.isBlank()) "" else " • $grade", "trophy") {
                    AlertDialog.Builder(this).setTitle("المركز #$rank — $name")
                        .setMessage(buildString {
                            if (grade.isNotBlank()) append("الصف: $grade\n")
                            append("النقاط: $xp XP")
                            if (entry.has("correct")) append("\nالإجابات الصحيحة: ${entry.optInt("correct")}")
                        })
                        .setPositiveButton("حسناً", null).show()
                }, bottom = 9)
            }
            if (ranking.length() > 10) button(if (expanded) "عرض أول 10 مراكز" else "عرض جميع المراكز", secondary = true) {
                contestMode = mode
                contestShowAll = !expanded
                renderContest()
            }
        }
        button("تعليمات التحدي", secondary = true) { contestInstructionsScreen() }
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
            if (activeScreen != "achievements") return@fetch
            if (!response.optBoolean("ok")) {
                base("الإنجازات", true)
                caption("تعذر تحديث الشارات والمستوى. لن تُعرض بيانات قديمة بوصفها حديثة.")
                button("إعادة المحاولة") { loadAchievements() }
                return@fetch
            }
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
        sectionHeader("مستواك", "نقاطك وشاراتك من الخادم", "trophy")
        addUiCard(uiCard().apply {
            addView(uiText("المستوى ${achievementStats.optInt("level", 1)}", 22f, true, blue))
            addView(uiText("${achievementStats.optInt("xp")} XP • $earned من ${achievements.length()} شارات مكتسبة",
                14f, tint = mutedText), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }, bottom = 17)
        sectionHeader("الإنجازات", "اضغط على الشارة لعرض تفاصيلها وتقدمك", "check")
        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        for ((mode, label) in listOf( "all" to "الجميع", "earned" to "المكتسبة", "locked" to "المتبقية")) {
            filters.addView(makeButton(label, secondary = achievementFilter != mode) {
                achievementFilter = mode; renderAchievements()
            }, LinearLayout.LayoutParams(0, dp(47), 1f).apply {
                marginEnd = dp(3); marginStart = dp(3)
            })
        }
        addUiCard(filters, bottom = 15)
        var shown = 0
        for (i in 0 until achievements.length()) {
            val item = achievements.optJSONObject(i) ?: continue
            val unlocked = isEarned(item)
            if (achievementFilter == "earned" && !unlocked || achievementFilter == "locked" && unlocked) continue
            shown++
            val name = item.optString("title", "إنجاز")
            val description = item.optString("description")
            val subtitle = (if (unlocked) "مكتسب" else "لم تحصل عليه بعد") +
                (if (description.isBlank()) "" else " • $description")
            addUiCard(uiRow(name, subtitle, if (unlocked) "trophy" else "lock") {
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
            }, bottom = 9)
        }
        if (shown == 0) caption(if (achievements.length() == 0) "لم يرسل الخادم إنجازات بعد." else "لا توجد شارات ضمن هذا التصنيف.")
        button("تحدي اليوم • حقق إنجازاً جديداً") { openCompetition("daily_challenge") }
        button("أكمل اختباراتك") { subjectsScreen() }
        button("تحديث الإنجازات", secondary = true) { loadAchievements() }
    }

    private fun navigateBack() {
        if (biometricOverlay != null) {
            moveTaskToBack(true)
            return
        }
        if (activeScreen == "plans" && paymentBusy) {
            message("انتظر اكتمال التحقق من طلب الدفع حتى لا تضيع حالته.")
            return
        }
        when (activeScreen) {
            "offline_exam_question" -> offlineExamTopicsScreen()
            "offline_exam_topics" -> if (offlineCoachMode) showOfflineCoachHome() else coachScreen()
            "coach_question" -> coachScreen()
            "performance_diagnostics" -> accountScreen()
            "coach" -> if (offlineCoachMode) showOfflineCoachHome() else showHome(homepage)
            "offline_coach_home" -> moveTaskToBack(true)
            "bound_recovery" -> accountScreen()
            "contest_instructions" -> loadContest()
            "phone_reset" -> if (recoverReturnScreen == "account") boundRecoveryScreen() else recoverScreen()
            "recover" -> if (recoverReturnScreen == "account") accountScreen() else loginScreen()
            "practice_exam" -> { stopPracticeTicker(); practiceBuilderScreen() }
            "practice_result" -> attemptHistoryScreen()
            "daily_challenge", "contest", "achievements" -> {
                val previous = CompetitionRules.backDestination(
                    if (competitionHistory.isEmpty()) "home" else competitionHistory.removeLast())
                when (previous) {
                    "daily_challenge" -> loadDailyChallenge()
                    "contest" -> loadContest()
                    "achievements" -> loadAchievements()
                    "account" -> accountScreen()
                    "tests" -> testsTabScreen()
                    "success" -> successTabScreen()
                    "subjects" -> subjectsScreen()
                    "topics" -> topicsScreen(topicsSubjectId, topicsSubjectName)
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
            "predictions_list" -> if (predictionsReturnScreen == "subjects") subjectsScreen(true)
                else predictionsTabScreen()
            "school_ad" -> when (schoolAdReturnScreen) {
                "ads_tab" -> advertisementsTabScreen()
                "account" -> accountScreen()
                else -> showHome(homepage)
            }
            "tests", "success", "predictions_tab", "ads_tab" -> showHome(homepage)
            "instructions" -> if (instructionsReturnScreen == "account") accountScreen() else loginScreen()
            "account" -> showHome(homepage)
            "plans" -> if (plansReturnScreen == "account") accountScreen() else showHome(homepage)
            "register" -> {
                val draft = registrationDraft
                if (draft != null && draft.step > 1 && !draft.loading) {
                    draft.step--
                    draft.fieldError = ""
                    draft.generalError = ""
                    renderRegistration(draft)
                } else if (registerReturnScreen == "login") loginScreen() else guestLandingScreen()
            }
            "verification" -> loginScreen()
            "login" -> guestLandingScreen()
            "guest" -> finish()
            else -> if (homepage.optBoolean("ok")) showHome(homepage) else guestLandingScreen()
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
            // System connectivity can change several times during cold start.
            // Do not obscure the welcome/guest/login screen with a transient toast.
            if (old == false && validated && student.optInt("id") > 0) {
                if (!offlineCoachMode && offlineExams.pendingCount(student.optInt("id")) > 0 &&
                    csrf.isNotBlank()) {
                    scheduleOfflineSync() // The queue checks existing server events before every retry.
                } else message("عاد اتصال الإنترنت. يمكنك تحديث البيانات بأمان.")
            }
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
        val timeAwayMs = if (backgroundEnteredAt != 0L) {
            SystemClock.elapsedRealtime() - backgroundEnteredAt
        } else 0L
        backgroundEnteredAt = 0L
        appForeground = true
        startSessionServices()
        if (::api.isInitialized) {
            val recorded = prefs.getLong("biometric_background_at", 0L)
            val elapsed = if (biometricBackgroundAt > 0L) {
                SystemClock.elapsedRealtime() - biometricBackgroundAt
            } else if (recorded > 0L) {
                (System.currentTimeMillis() - recorded).coerceAtLeast(0L)
            } else 0L
            if (BiometricRules.shouldLock(prefs.getBoolean("biometric_lock", false),
                    student.optInt("id") > 0 || api.hasRememberedSession(),
                    biometricAuthInProgress, biometricOverlay != null,
                    prefs.getBoolean("biometric_unlock_required", false), elapsed,
                    SystemClock.elapsedRealtime() - biometricLastSuccessAt)) {
                showBiometricLock()
            }
        }
        if (checkoutReturnPending && activeScreen == "plans") {
            checkoutReturnPending = false
            val order = prefs.getInt(lastPaymentKey(), 0)
            if (order > 0) checkPaymentStatus(order)
        }
        if (student.optInt("id") > 0) StudentPush.attach(this, api, student.optInt("id"), csrf)
        openPushInboxIfReady()
        if (timeAwayMs >= 3000L && activeScreen != "welcome") {
            if (biometricOverlay != null || biometricAuthInProgress) {
                resumeWelcomeAfterUnlock = true
            } else replayWelcomeAfterBackground()
        }
    }

    override fun onStop() {
        appForeground = false
        backgroundEnteredAt = SystemClock.elapsedRealtime()
        serviceTicker?.let { main.removeCallbacks(it) }
        serviceTicker = null
        if (prefs.getBoolean("biometric_lock", false) && !biometricAuthInProgress) {
            biometricBackgroundAt = SystemClock.elapsedRealtime()
            prefs.edit().putLong("biometric_background_at", System.currentTimeMillis()).apply()
        }
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
        serviceTicker?.let { main.removeCallbacks(it) }
        currentAdDialog?.dismiss()
        heroJobs.shutdownNow()
        jobs.shutdownNow()
        super.onDestroy()
    }
}
