package com.muriq.murshid.iosparity

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muriq.murshid.iosparity.data.*
import com.muriq.murshid.iosparity.net.ApiProblem
import com.muriq.murshid.iosparity.net.MurshidApi
import com.muriq.murshid.iosparity.net.PasswordRecovery
import com.muriq.murshid.iosparity.notifications.NoticeDelivery
import com.muriq.murshid.iosparity.notifications.NoticeInbox
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

enum class Screen { GUEST, LOGIN, REGISTER, VERIFY, MAIN, TOPICS, EXAM, FEATURE, RECOVER }
enum class Feature(val title: String, val endpoint: String) {
    NOTIFICATIONS("الإشعارات", "mobile/notifications.php"),
    FAVORITES("المفضلة", "mobile/favorites.php"),
    REVIEW("راجع أخطاءك", "mobile/review.php"),
    STORIES("غيّر جو", "mobile/stories.php"),
    SUPPORT("خدمة العملاء", "mobile/support.php"),
    INSIGHTS("قسم النجاح", "mobile/insights.php"),
    ACHIEVEMENTS("الإنجازات", "mobile/achievements.php"),
    CONTEST("تحدي المليون", "mobile/contest.php"),
    STUDY_PLAN("خطة الدراسة", "mobile/study-plan.php"),
    SUBSCRIPTION("الاشتراك", ""),
    ACCOUNT("حسابي", "mobile/account.php"),
    PREDICTIONS("المرشحات", ""),
    ADS("الإعلانات", ""),
    QUESTION_REQUEST("طلب إضافة أسئلة", "")
}

/**
 * New Android state machine modeled on the iOS AppSession / MainV3TabView.
 * Screens are 100% Compose; all student data and decisions remain server-owned.
 */
class MurshidViewModel(app: Application) : AndroidViewModel(app) {
    val api = MurshidApi(app.applicationContext)
    private val recovery = PasswordRecovery()
    private val noticeDelivery = NoticeDelivery(app.applicationContext)
    private var noticeBoundStudentId = 0
    var noticeInbox by mutableStateOf(NoticeInbox(0, emptyList()))
        private set
    var noticeLoading by mutableStateOf(false)
        private set
    var noticeSaving by mutableStateOf(false)
        private set
    var noticeError by mutableStateOf("")
        private set
    var highlightedNoticeId by mutableStateOf(0)
        private set

    var recoveryToken by mutableStateOf("")
        private set
    var recoveryFeedback by mutableStateOf("")
        private set
    var recoveryEmailSent by mutableStateOf(false)
        private set
    private val prefs = app.getSharedPreferences("murshid_compose_preferences", 0)
    var appearance by mutableStateOf(prefs.getString("appearance", "system") ?: "system")
        private set
    var studentPalette by mutableStateOf(
        prefs.getString("student_palette", "royal")?.takeIf { it in setOf("royal", "aurora", "orchid") }
            ?: "royal"
    )
        private set
    fun changeStudentPalette(value: String) {
        if (value !in setOf("royal", "aurora", "orchid")) return
        studentPalette = value
        prefs.edit().putString("student_palette", value).apply()
    }
    var screen by mutableStateOf(Screen.GUEST)
        private set
    var tab by mutableStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var dashboard by mutableStateOf<StudentDashboard?>(null)
        private set
    var unreadSchoolAds by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var grades by mutableStateOf<List<Pair<Int, String>>>(emptyList())
        private set
    var subject by mutableStateOf<Subject?>(null)
        private set
    var topics by mutableStateOf<List<Topic>>(emptyList())
        private set
    var topic by mutableStateOf<Topic?>(null)
        private set
    var questions by mutableStateOf<List<QuizQuestion>>(emptyList())
        private set
    var questionIndex by mutableStateOf(0)
        private set
    var answerResult by mutableStateOf<Map<Int, JSONObject>>(emptyMap())
        private set
    var uncertainSubmission by mutableStateOf(false)
        private set
    var feature by mutableStateOf(Feature.NOTIFICATIONS)
        private set
    var featureData by mutableStateOf(JSONObject())
    var accountData by mutableStateOf(JSONObject())
        private set
    var predictionsData by mutableStateOf(JSONObject())
        private set
    var paymentUrl by mutableStateOf("")
        private set
    var verification by mutableStateOf(JSONObject())
        private set
    var textFeedback by mutableStateOf("")
        private set
    private var inflight = false

    var biometricEnabled by mutableStateOf(prefs.getBoolean("biometric_enabled", false))
        private set
    fun updateBiometricEnabled(enabled: Boolean) {
        biometricEnabled = enabled
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }
    init {
        if (api.hasSavedSession() && !biometricEnabled) refreshDashboard(firstLoad = true)
    }
    fun unlockRememberedSession() {
        if (dashboard == null && api.hasSavedSession() && !loading) refreshDashboard(firstLoad = true)
    }

    fun clearMessage() { message = "" }
    fun changeAppearance(value: String) {
        if (value !in setOf("dark", "light", "system")) return
        appearance = value
        prefs.edit().putString("appearance", value).apply()
    }
    fun showGuest() { screen = Screen.GUEST }
    fun showLogin() { screen = Screen.LOGIN; clearMessage() }
    fun showRegister() { screen = Screen.REGISTER; clearMessage(); loadGrades() }
    fun showRecovery() {
        recoveryToken = ""
        recoveryFeedback = ""
        recoveryEmailSent = false
        screen = Screen.RECOVER
        clearMessage()
    }

    fun beginRecovery(identifier: String) {
        if (loading || identifier.isBlank()) return
        viewModelScope.launch {
            loading = true
            try {
                val result = recovery.begin(identifier)
                recoveryToken = result.phoneToken.orEmpty()
                recoveryEmailSent = result.phoneToken == null
                recoveryFeedback = result.message
            } catch (error: Exception) {
                recoveryFeedback = error.message ?: "تعذر الاتصال بخدمة الاستعادة."
            } finally { loading = false }
        }
    }

    fun resendRecovery() {
        val token = recoveryToken
        if (loading || token.isBlank()) return
        viewModelScope.launch {
            loading = true
            try { recoveryFeedback = recovery.resend(token) }
            catch (error: Exception) { recoveryFeedback = error.message ?: "تعذر إعادة الإرسال." }
            finally { loading = false }
        }
    }

    fun finishRecovery(code: String, password: String, confirm: String) {
        val token = recoveryToken
        if (loading || token.isBlank()) return
        viewModelScope.launch {
            loading = true
            try {
                val success = recovery.finish(token, code, password, confirm)
                clearNotices()
                api.clearSession()
                dashboard = null
                recoveryToken = ""
                recoveryFeedback = ""
                recoveryEmailSent = false
                screen = Screen.LOGIN
                message = success
            } catch (error: Exception) {
                recoveryFeedback = error.message ?: "تعذر تغيير كلمة المرور."
            } finally { loading = false }
        }
    }

    private fun fail(error: Throwable) {
        if (error is ApiProblem && error.sessionExpired) {
            clearNotices()
            api.clearSession()
            dashboard = null
            screen = Screen.LOGIN
        }
        message = error.message ?: "تعذر تنفيذ الطلب."
    }

    fun loadGrades() {
        viewModelScope.launch {
            try {
                grades = api.get("api/grades.php").array("grades")
                    .map { it.num("id") to it.text("name") }
                    .filter { it.first > 0 }
            } catch (error: Exception) { fail(error) }
        }
    }

    fun login(identifier: String, password: String, remember: Boolean) {
        if (loading) return
        if (identifier.isBlank() || password.isBlank()) {
            message = "أدخل بيانات الدخول كاملة."
            return
        }
        viewModelScope.launch {
            loading = true; clearMessage()
            try {
                val response = api.post("api/login.php", JSONObject()
                    .put("identifier", identifier.trim())
                    .put("password", password)
                    .put("remember_me", remember))
                if (response.optBoolean("email_verification_required") ||
                    response.optBoolean("phone_verification_required")) {
                    pendingVerificationRemember = remember
                    verification = response; screen = Screen.VERIFY
                } else {
                    api.setRemember(remember)
                    val bootstrap = api.get("mobile/bootstrap.php")
                    dashboard = StudentDashboard.from(bootstrap)
                    bindNoticesToStudent()
                    reconcileSchoolAds()
                    loadAccount()
                    tab = 0; screen = Screen.MAIN
                }
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    private var pendingVerificationRemember = false

    fun verifyPendingCode(value: String) {
        if (loading) return
        val kind = if (verification.optBoolean("phone_verification_required")) "phone" else "email"
        viewModelScope.launch {
            loading = true
            try {
                val result = api.verifyPendingAccount(kind, value)
                if (result.optBoolean("activated", false)) {
                    api.setRemember(pendingVerificationRemember)
                    dashboard = StudentDashboard.from(result)
                    bindNoticesToStudent()
                    reconcileSchoolAds()
                    tab = 0; screen = Screen.MAIN
                    loadAccount()
                    message = "تم تأكيد الحساب بنجاح."
                } else {
                    screen = Screen.LOGIN
                    message = result.optString("message", "أكمل تسجيل الدخول بعد التحقق.")
                }
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun resendPendingVerification() {
        if (loading) return
        val kind = if (verification.optBoolean("phone_verification_required")) "phone" else "email"
        viewModelScope.launch {
            loading = true
            try { message = api.resendPendingAccountCode(kind) }
            catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun register(
        name: String, identifier: String, contactType: String,
        gradeId: Int, gender: String, password: String, confirmation: String
    ) {
        if (loading) return
        if (name.trim().split(Regex("\\s+")).size < 3) { message = "أدخل الاسم الثلاثي الكامل."; return }
        if (gradeId <= 0 || identifier.isBlank()) { message = "اختر الصف وأدخل بيانات التواصل."; return }
        if (password.length < 8 || password != confirmation) {
            message = "كلمة المرور لا تقل عن 8 أحرف ويجب أن تتطابق مع التأكيد."
            return
        }
        viewModelScope.launch {
            loading = true; clearMessage()
            try {
                val response = api.post("api/register.php", JSONObject()
                    .put("full_name", name.trim())
                    .put("identifier", identifier.trim())
                    .put("contact_type", contactType)
                    .put("grade_id", gradeId)
                    .put("gender", gender)
                    .put("password", password)
                    .put("password_confirmation", confirmation))
                if (response.optBoolean("email_verification_required") ||
                    response.optBoolean("phone_verification_required")) {
                    pendingVerificationRemember = false
                    verification = response; screen = Screen.VERIFY
                    message = "أكمل تأكيد حسابك."
                } else {
                    screen = Screen.LOGIN
                    message = response.text("message", "تم إنشاء الحساب.")
                }
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun refreshDashboard(firstLoad: Boolean = false) {
        if (loading && !firstLoad) return
        viewModelScope.launch {
            loading = true
            try {
                dashboard = StudentDashboard.from(api.get("mobile/bootstrap.php"))
                bindNoticesToStudent()
                reconcileSchoolAds()
                loadAccount()
                tab = 0; screen = Screen.MAIN; clearMessage()
            } catch (error: Exception) {
                if (firstLoad && error !is ApiProblem) {
                    // A weak connection is not evidence that a remembered session expired.
                    // Keep the encrypted cookie and let the student retry manually.
                    screen = Screen.GUEST
                    message = "تعذر الاتصال بالخادم. يمكنك إعادة المحاولة دون فقدان جلستك."
                } else if (firstLoad && error is ApiProblem && !error.sessionExpired) {
                    screen = Screen.GUEST
                    message = error.message ?: "تعذر الاتصال بالخادم. حاول مجدداً."
                } else fail(error)
            } finally { loading = false }
        }
    }

    private fun reconcileSchoolAds() {
        val dash = dashboard ?: run { unreadSchoolAds = 0; return }
        val key = "school_ads_seen_" + dash.user.id.toString()
        val seen = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        val visible = dash.ads.filter { ad ->
            ad.optInt("id") > 0 && ad.optString("school").isNotBlank() &&
                ad.optString("text").isNotBlank() && ad.optBoolean("active", true)
        }
        val keys = visible.map(com.muriq.murshid.iosparity.data.SchoolAdReadRules::revision)
        if (screen == Screen.MAIN && tab == 3) {
            seen.addAll(keys)
            prefs.edit().putStringSet(key, seen).apply()
            unreadSchoolAds = 0
        } else {
            unreadSchoolAds = keys.count { it !in seen }
        }
    }

    fun selectTab(index: Int) {
        if (index !in 0..4) return
        // Keep all five tabs visible, with a locked predictions screen for nonsubscribers.
        tab = index; screen = Screen.MAIN; clearMessage()
        reconcileSchoolAds()
        if (index == 4) loadAccount()
    }

    fun openTopics(item: Subject) {
        if (loading) return
        subject = item
        viewModelScope.launch {
            loading = true; clearMessage()
            try {
                val json = api.get("mobile/topics.php?subject_id=${item.id}")
                topics = json.array("topics").map(Topic::from)
                screen = Screen.TOPICS
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun openExam(item: Topic) {
        if (loading) return
        topic = item
        viewModelScope.launch {
            loading = true; clearMessage()
            try {
                val response = api.get("mobile/exam.php?chapter_id=${item.id}")
                questions = response.array("questions").map(QuizQuestion::from)
                    .filter { it.id > 0 && QuestionPresentation.display(it.text).isNotBlank() }
                    .distinctBy { it.id }
                answerResult = emptyMap()
                questionIndex = questions.indexOfFirst { !it.submitted }.takeIf { it >= 0 } ?: 0
                uncertainSubmission = false
                screen = Screen.EXAM
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun sendAnswer(answer: String, selected: AnswerOption? = null) {
        if (inflight || loading || uncertainSubmission) return
        val q = questions.getOrNull(questionIndex) ?: return
        if (q.submitted || answerResult.containsKey(q.id) || answer.isBlank()) return
        val currentTopic = topic ?: return
        val token = dashboard?.csrf.orEmpty()
        if (token.isBlank()) { message = "انتهت بيانات الجلسة. حدّث حسابك."; return }
        viewModelScope.launch {
            inflight = true; loading = true; clearMessage()
            val body = JSONObject()
                .put("csrf", token)
                .put("chapter_id", currentTopic.id)
                .put("question_index", questionIndex + 1)
                .put("answers", JSONObject().put(q.id.toString(), JSONObject()
                    .put("submitted", true)
                    .put("text", answer.trim())
                    .put("selected_text", answer.trim())
                    .also { row -> if (selected != null) row.put("selected", selected.id) }))
                .put("completed", questionIndex == questions.lastIndex)
            try {
                val response = api.post("api/progress.php", body)
                val grade = response.optJSONObject("grades")?.optJSONObject(q.id.toString()) ?: JSONObject()
                answerResult = answerResult + (q.id to grade)
                questions = questions.toMutableList().also { list ->
                    list[questionIndex] = q.copy(
                        submitted = true,
                        savedAnswer = JSONObject().put("text", answer.trim())
                            .put("correct", grade.optBoolean("is_correct"))
                    )
                }
                val old = dashboard
                if (old != null) {
                    dashboard = old.copy(
                        freeRemaining = response.num("free_remaining", old.freeRemaining),
                        freeUsed = response.num("free_used", old.freeUsed),
                        subscribed = response.optBoolean("subscribed", old.subscribed)
                    )
                }
            } catch (error: ApiProblem) {
                if (error.code == -1) uncertainSubmission = true
                if (error.needsSubscription) {
                    message = "انتهى الرصيد المجاني. يمكنك تفعيل الاشتراك."
                } else fail(error)
            } catch (error: Exception) { fail(error) }
            finally { inflight = false; loading = false }
        }
    }

    fun nextQuestion() {
        val q = questions.getOrNull(questionIndex) ?: return
        if (!q.submitted && !answerResult.containsKey(q.id)) {
            message = "أجب عن السؤال أولاً."; return
        }
        if (questionIndex < questions.lastIndex) questionIndex++
        else { message = "أكملت أسئلة هذا الموضوع. أحسنت!" }
    }

    fun reloadExamAfterUncertain() {
        val target = topic ?: return
        openExam(target)
    }

    fun openFeature(item: Feature) {
        feature = item; screen = Screen.FEATURE; featureData = JSONObject(); clearMessage()
        if (item == Feature.ACCOUNT) { loadAccount(); return }
        if (item == Feature.NOTIFICATIONS) { syncNotices(silent = false); return }
        if (item == Feature.PREDICTIONS || item == Feature.ADS ||
            item == Feature.SUBSCRIPTION || item == Feature.QUESTION_REQUEST) return
        loadFeature()
    }

    fun loadFeature() {
        if (feature == Feature.NOTIFICATIONS) { syncNotices(silent = false); return }
        if (feature.endpoint.isBlank()) return
        viewModelScope.launch {
            loading = true; clearMessage()
            try { featureData = api.get(feature.endpoint) }
            catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }


    private fun bindNoticesToStudent() {
        val id = dashboard?.user?.id ?: return
        if (id <= 0) return
        if (noticeBoundStudentId != id) {
            noticeBoundStudentId = id
            noticeInbox = NoticeInbox(0, emptyList())
            highlightedNoticeId = 0
            noticeError = ""
        }
        noticeDelivery.bind(id, api.hasSavedSession())
        syncNotices(silent = true)
    }

    private fun clearNotices() {
        noticeDelivery.clear()
        noticeBoundStudentId = 0
        noticeInbox = NoticeInbox(0, emptyList())
        noticeError = ""
        highlightedNoticeId = 0
    }

    fun syncNotices(silent: Boolean = false) {
        val id = dashboard?.user?.id ?: return
        if (id <= 0 || noticeLoading || noticeSaving) return
        viewModelScope.launch {
            noticeLoading = true
            if (!silent) noticeError = ""
            try {
                val data = api.get("mobile/notifications.php")
                if (dashboard?.user?.id == id && noticeBoundStudentId == id) {
                    val inbox = NoticeInbox.from(data)
                    noticeInbox = inbox
                    noticeDelivery.deliver(id, inbox)
                    if (highlightedNoticeId > 0 && inbox.items.any { it.id == highlightedNoticeId && !it.read }) {
                        markNoticeRead(highlightedNoticeId)
                    }
                }
            } catch (error: Exception) {
                if (error is ApiProblem && error.sessionExpired) {
                    clearNotices()
                    fail(error)
                } else if (!silent) {
                    noticeError = error.message ?: "تعذر تحديث الإشعارات."
                }
            } finally { noticeLoading = false }
        }
    }

    fun openNoticeFromSystem(studentId: Int, noticeId: Int) {
        if (studentId <= 0 || dashboard?.user?.id != studentId) return
        highlightedNoticeId = noticeId.coerceAtLeast(0)
        openFeature(Feature.NOTIFICATIONS)
    }

    fun markNoticeRead(noticeId: Int) = updateNoticeRead(noticeId, false)
    fun markAllNoticesRead() = updateNoticeRead(0, true)

    private fun updateNoticeRead(noticeId: Int, all: Boolean) {
        val student = dashboard ?: return
        if (student.user.id <= 0 || student.csrf.isBlank() || noticeSaving) return
        if (!all && (noticeId <= 0 || noticeInbox.items.none { it.id == noticeId })) return
        viewModelScope.launch {
            noticeSaving = true
            noticeError = ""
            try {
                val body = JSONObject().put("csrf", student.csrf)
                if (all) body.put("read_all", true) else body.put("read_id", noticeId)
                val data = api.post("mobile/notifications.php", body)
                if (dashboard?.user?.id == student.user.id) {
                    noticeInbox = NoticeInbox.from(data)
                }
            } catch (error: Exception) {
                if (error is ApiProblem && error.sessionExpired) {
                    clearNotices()
                    fail(error)
                } else {
                    noticeError = error.message ?: "تعذر تحديث حالة الإشعار."
                }
            } finally { noticeSaving = false }
        }
    }

    fun loadAccount() {
        viewModelScope.launch {
            try { accountData = api.get("mobile/account.php") }
            catch (error: Exception) { fail(error) }
        }
    }

    fun sendSupport(text: String) {
        if (text.isBlank() || loading) return
        viewModelScope.launch {
            loading = true
            try {
                api.post("mobile/support.php",
                    JSONObject().put("csrf", dashboard?.csrf).put("message", text.trim()))
                message = "تم إرسال رسالتك."
                if (feature == Feature.SUPPORT) loadFeature()
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun redeem(code: String) {
        if (code.isBlank() || loading) return
        viewModelScope.launch {
            loading = true
            try {
                api.post("mobile/redeem-subscription-code.php",
                    JSONObject().put("csrf", dashboard?.csrf).put("code", code.trim()))
                message = "تم تفعيل الرمز."
                dashboard = StudentDashboard.from(api.get("mobile/bootstrap.php"))
                bindNoticesToStudent()
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun rename(name: String) {
        if (name.trim().split(Regex("\\s+")).size < 3) {
            message = "اكتب الاسم الثلاثي على الأقل."; return
        }
        viewModelScope.launch {
            loading = true
            try {
                api.post("mobile/account.php", JSONObject().put("csrf", dashboard?.csrf)
                    .put("action", "change_name").put("name", name.trim()))
                dashboard = StudentDashboard.from(api.get("mobile/bootstrap.php"))
                bindNoticesToStudent()
                accountData = api.get("mobile/account.php"); featureData = accountData
                message = "تم تحديث الاسم."
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun openPredictions(item: Subject) {
        if (dashboard?.subscribed != true) {
            message = "المرشحات متاحة للمشتركين فقط."; return
        }
        subject = item
        feature = Feature.PREDICTIONS
        screen = Screen.FEATURE
        viewModelScope.launch {
            loading = true
            try { predictionsData = api.get("mobile/predictions.php?subject_id=${item.id}") }
            catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun refreshAds(silent: Boolean = false) {
        if (loading && !silent) return
        viewModelScope.launch {
            if (!silent) loading = true
            try {
                val latest = StudentDashboard.from(api.get("mobile/bootstrap.php"))
                if (dashboard?.user?.id != latest.user.id) return@launch
                dashboard = latest
                bindNoticesToStudent()
                reconcileSchoolAds()
                if (!silent) message = "تم تحديث الإعلانات."
            } catch (error: Exception) { if (!silent) fail(error) }
            finally { if (!silent) loading = false }
        }
    }

    fun clearPaymentUrl() { paymentUrl = "" }

    fun checkout(planId: String, phone: String) {
        if (loading || planId.isBlank()) return
        if (!phone.matches(Regex("07[0-9]{9}"))) {
            message = "أدخل رقم هاتف عراقي صحيحاً من 11 رقماً."; return
        }
        viewModelScope.launch {
            loading = true
            try {
                val result = api.post("api/payment-checkout.php", JSONObject()
                    .put("csrf", dashboard?.csrf)
                    .put("action", "checkout")
                    .put("plan", planId)
                    .put("customer_name", dashboard?.user?.name.orEmpty())
                    .put("customer_phone", phone))
                if (result.optBoolean("subscribed")) {
                    dashboard = StudentDashboard.from(api.get("mobile/bootstrap.php"))
                bindNoticesToStudent()
                    message = "اشتراكك فعّال بالفعل."
                } else {
                    val url = result.text("redirect")
                    if (!url.startsWith("https://")) throw ApiProblem("رابط الدفع الخارجي غير آمن.")
                    val orderId = result.num("order_id")
                    prefs.edit().putInt("last_payment_order", orderId).apply()
                    paymentUrl = url
                }
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun checkPaymentStatus() {
        val orderId = prefs.getInt("last_payment_order", 0)
        if (orderId <= 0) { message = "لا يوجد طلب دفع سابق للتحقق منه."; return }
        viewModelScope.launch {
            loading = true
            try {
                val result = api.post("api/payment-checkout.php", JSONObject()
                    .put("csrf", dashboard?.csrf)
                    .put("action", "status")
                    .put("order_id", orderId))
                if (result.optBoolean("subscribed")) {
                    dashboard = StudentDashboard.from(api.get("mobile/bootstrap.php"))
                bindNoticesToStudent()
                    prefs.edit().remove("last_payment_order").apply()
                }
                message = result.text("message", "تم تحديث حالة الدفع.")
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun requestDeletion() {
        viewModelScope.launch {
            loading = true
            try {
                api.post("mobile/account.php", JSONObject().put("csrf", dashboard?.csrf)
                    .put("action", "request_delete").put("confirmation", "حذف حسابي"))
                accountData = api.get("mobile/account.php")
                message = "تم تقديم طلب حذف الحساب."
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun cancelDeletion() {
        viewModelScope.launch {
            loading = true
            try {
                api.post("mobile/account.php", JSONObject().put("csrf", dashboard?.csrf)
                    .put("action", "cancel_delete"))
                accountData = api.get("mobile/account.php")
                message = "تم إلغاء طلب حذف الحساب."
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun toggleFavorite(questionId: Int) {
        val q = questions.firstOrNull { it.id == questionId } ?: return
        viewModelScope.launch {
            try {
                val newStatus = !q.raw.optBoolean("favorite")
                api.post("api/question-tools.php", JSONObject().put("csrf", dashboard?.csrf)
                    .put("question_id", questionId)
                    .put("action", if (newStatus) "favorite" else "unfavorite"))
                questions = questions.map { item ->
                    if (item.id == questionId) item.copy(raw = JSONObject(item.raw.toString())
                        .put("favorite", newStatus)) else item
                }
            } catch (error: Exception) { fail(error) }
        }
    }

    fun reportQuestion(questionId: Int, reason: String, details: String) {
        viewModelScope.launch {
            loading = true
            try {
                val result = api.post("api/question-tools.php", JSONObject()
                    .put("csrf", dashboard?.csrf).put("question_id", questionId)
                    .put("action", "report").put("reason", reason)
                    .put("message", details.trim()))
                message = result.text("message", "تم إرسال البلاغ للمراجعة.")
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun saveNote(questionId: Int, note: String) {
        viewModelScope.launch {
            loading = true
            try {
                api.post("mobile/question-notes.php", JSONObject().put("csrf", dashboard?.csrf)
                    .put("question_id", questionId).put("note", note))
                message = "تم حفظ ملاحظتك."
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun loadNote(questionId: Int, done: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.get("mobile/question-notes.php?question_id=$questionId")
                done(response.text("note"))
            } catch (error: Exception) { fail(error) }
        }
    }

    fun reviewExam() { questionIndex = 0 }

    fun requestMoreQuestions() {
        val current = topic ?: return
        viewModelScope.launch {
            loading = true
            try {
                val status = api.get("mobile/question-request.php?chapter_id=${current.id}")
                if (!status.optBoolean("complete")) {
                    message = "أكمل أسئلة الموضوع أولاً."
                } else if (status.optBoolean("pending")) {
                    message = "طلب إضافة الأسئلة قيد المراجعة."
                } else {
                    val result = api.post("mobile/question-request.php",
                        JSONObject().put("csrf", dashboard?.csrf).put("chapter_id", current.id))
                    message = result.text("message", "تم إرسال طلب إضافة أسئلة.")
                }
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun uploadAvatar(image: android.net.Uri) {
        val csrf = dashboard?.csrf ?: return
        viewModelScope.launch {
            loading = true
            try {
                api.uploadAvatar(image, csrf)
                accountData = api.get("mobile/account.php")
                message = "تم تحديث الصورة الشخصية."
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun removeAvatar() {
        val csrf = dashboard?.csrf ?: return
        viewModelScope.launch {
            loading = true
            try {
                api.removeAvatar(csrf)
                accountData = api.get("mobile/account.php")
                message = "تم حذف الصورة الشخصية."
            } catch (error: Exception) { fail(error) }
            finally { loading = false }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                val token = dashboard?.csrf.orEmpty()
                if (token.isNotBlank()) api.post("mobile/logout.php", JSONObject().put("csrf", token))
            } catch (_: Exception) {
                // Always let the student explicitly clear their own local credentials.
            } finally {
                updateBiometricEnabled(false)
                clearNotices(); api.clearSession(); dashboard = null; unreadSchoolAds = 0; featureData = JSONObject()
                screen = Screen.GUEST; tab = 0; clearMessage()
            }
        }
    }

    fun back() {
        when (screen) {
            Screen.EXAM -> screen = Screen.TOPICS
            Screen.TOPICS -> { screen = Screen.MAIN; tab = 1 }
            Screen.FEATURE -> { screen = Screen.MAIN; if (feature == Feature.ACCOUNT) tab = 4 }
            Screen.REGISTER, Screen.VERIFY, Screen.RECOVER -> screen = Screen.LOGIN
            Screen.LOGIN -> screen = Screen.GUEST
            Screen.MAIN -> tab = 0
            Screen.GUEST -> Unit
        }
    }
}
