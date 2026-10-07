package com.muriq.murshid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

data class Route(val kind: String, val id: Int = 0, val title: String = "", val parent: Int = 0)

class AppState(val api: MurshidApi, val scope: CoroutineScope) {
    var route by mutableStateOf(Route("login"))
    private val stack = mutableStateListOf<Route>()
    var booting by mutableStateOf(true)
    var busy by mutableStateOf(false)
    var notice by mutableStateOf("")
    var csrf by mutableStateOf("")
    var session by mutableStateOf(JSONObject())
    var subjects by mutableStateOf(emptyList<Json>())
    var dark by mutableStateOf(false)
    var refresh by mutableIntStateOf(0)

    val user: Json get() = session.obj("user")
    val stats: Json get() = session.obj("stats")
    fun push(route: Route) { stack.add(this.route); this.route = route }
    fun root(kind: String) { stack.clear(); route = Route(kind) }
    fun back() { if (stack.isNotEmpty()) route = stack.removeAt(stack.lastIndex) else root("home") }
    fun show(message: String) { notice = message }
    fun retry() { refresh++ }

    fun job(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try { block() }
            catch (e: ServerError) {
                if (e.isSessionExpired) { api.clearSession(); root("login"); show("انتهت الجلسة، سجل الدخول مجددًا.") }
                else show(e.message)
            } catch (e: Exception) { show(e.message ?: "تعذر الاتصال بالخادم.") }
            finally { busy = false }
        }
    }

    suspend fun bootstrap(): Boolean {
        val d = api.call("mobile/bootstrap.php")
        session = d
        csrf = d.str("csrf")
        subjects = d.arr("subjects")
        return true
    }

    suspend fun initialize() {
        try {
            bootstrap()
            root("home")
        } catch (_: Exception) { root("login") }
        finally { booting = false }
    }

    fun signOut() = job {
        try { api.call("mobile/logout.php", data = payload("csrf" to csrf)) }
        catch (_: Exception) { }
        api.clearSession()
        session = JSONObject()
        csrf = ""
        subjects = emptyList()
        root("login")
        show("تم تسجيل الخروج.")
    }
}

private val Blue = Color(0xFF2463CE)
private val Navy = Color(0xFF112B56)
private val OffWhite = Color(0xFFF6F8FC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = getSharedPreferences("murshid_ui", MODE_PRIVATE)
        setContent {
            val scope = rememberCoroutineScope()
            val app = remember { AppState(MurshidApi(applicationContext), scope) }
            LaunchedEffect(Unit) {
                app.dark = settings.getBoolean("dark", false)
                app.initialize()
            }
            val colorScheme = if (app.dark) darkColorScheme(primary = Color(0xFF92B8FF), background = Color(0xFF0E1625), surface = Color(0xFF172234))
            else lightColorScheme(primary = Blue, background = OffWhite, surface = Color.White)
            MaterialTheme(colorScheme = colorScheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MurshidRoot(app, onTheme = {
                        app.dark = !app.dark
                        settings.edit().putBoolean("dark", app.dark).apply()
                    }, openUrl = { address ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MurshidRoot(app: AppState, onTheme: () -> Unit, openUrl: (String) -> Unit) {
    BackHandler(enabled = app.route.kind !in listOf("home","login")) { app.back() }
    val notice = app.notice
    if (notice.isNotEmpty()) AlertDialog(onDismissRequest = { app.notice = "" },
        confirmButton = { TextButton(onClick = { app.notice = "" }) { Text("حسنًا") } },
        title = { Text("المرشد الوزاري") }, text = { Text(notice) })
    if (app.booting) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val loggedIn = app.route.kind !in listOf("login", "register")
    val title = when (app.route.kind) {
        "home" -> "المرشد الوزاري"
        "subjects" -> "المواد الدراسية"
        "topics" -> app.route.title
        "exam" -> app.route.title
        "account" -> "حسابي"
        "login" -> "تسجيل الدخول"
        "register" -> "حساب جديد"
        "review" -> "مراجعة الأخطاء"
        "favorites" -> "المفضلة"
        "search" -> "البحث في الأسئلة"
        "builder" -> "اختبار مخصص"
        "practice" -> "اختبارك"
        "challenge" -> "تحدي اليوم"
        "submit" -> "اقترح سؤالًا"
        "requests" -> "طلباتي"
        "more" -> "طلب أسئلة جديدة"
        "history" -> "سجل المحاولات"
        "notifications" -> "الإشعارات"
        "support" -> "خدمة العملاء"
        "plan" -> "خطة الدراسة"
        "achievements" -> "الإنجازات"
        "subscription" -> "الاشتراك"
        "notes" -> "ملاحظتي على السؤال"
        else -> app.route.title.ifBlank { "المرشد الوزاري" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (loggedIn && app.route.kind != "home") {
                        IconButton(onClick = { app.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "الرجوع")
                        }
                    }
                },
                actions = {
                    if (loggedIn) {
                        IconButton(onClick = onTheme) {
                            Icon(if (app.dark) Icons.Default.LightMode else Icons.Default.DarkMode, "تبديل المظهر")
                        }
                        IconButton(onClick = { app.root("home") }) { Icon(Icons.Default.Home, "الرئيسية") }
                    }
                }
            )
        },
        bottomBar = {
            if (loggedIn && app.route.kind !in listOf("exam","practice")) NavigationBar {
                val links = listOf(Triple("home", "الرئيسية", Icons.Default.Home),
                    Triple("subjects", "المواد", Icons.Default.MenuBook),
                    Triple("review", "مراجعة", Icons.Default.AutoStories),
                    Triple("account", "حسابي", Icons.Default.Person))
                links.forEach { (kind,label,icon) ->
                    NavigationBarItem(
                        selected = app.route.kind == kind,
                        onClick = { app.root(kind) },
                        icon = { Icon(icon, label) },
                        label = { Text(label, maxLines = 1, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (app.route.kind) {
                "login" -> AuthPage(app, false, openUrl)
                "register" -> AuthPage(app, true, openUrl)
                "home" -> HomePage(app)
                "subjects" -> SubjectsPage(app)
                "topics" -> TopicsPage(app)
                "exam" -> ExamPage(app)
                "account" -> AccountPage(app)
                "review" -> GenericListPage(app, "mobile/review.php", "mistakes", "أسئلة تحتاج مراجعة")
                "favorites" -> GenericListPage(app, "mobile/favorites.php", "items", "أسئلتك المفضلة")
                "search" -> SearchPage(app)
                "builder" -> CustomExamBuilder(app)
                "practice" -> CustomExamPage(app)
                "challenge" -> DailyChallengePage(app)
                "submit" -> QuestionSubmissionPage(app)
                "requests" -> RequestsPage(app)
                "more" -> MoreQuestionsPage(app)
                "history" -> GenericListPage(app, "mobile/history.php", "attempts", "آخر محاولاتك")
                "notifications" -> GenericListPage(app, "mobile/notifications.php", "items", "آخر إشعاراتك")
                "support" -> SupportPage(app)
                "plan" -> StudyPlanPage(app)
                "achievements" -> GenericListPage(app, "mobile/achievements.php", "achievements", "إنجازاتك")
                "subscription" -> SubscriptionPage(app, openUrl)
                else -> HomePage(app)
            }
            if (app.busy && app.route.kind !in listOf("exam","practice")) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable fun Title(text: String, subtitle: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun Action(label: String, enabled: Boolean = true, icon: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        if (icon != null) { icon(); Spacer(Modifier.width(7.dp)) }
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable fun Tile(title: String, subtitle: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(43.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=0.10f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center) { icon() }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun LoadingOrError(loading: Boolean, error: String, retry: () -> Unit) {
    if (loading) Box(Modifier.fillMaxWidth().padding(38.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else if (error.isNotBlank()) Panel {
        Text(error, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = retry) { Text("إعادة المحاولة") }
    }
}

@Composable fun ChoiceOptions(value: String, options: List<Pair<String,String>>, enabled: Boolean = true, onPick: (String)->Unit) {
    options.forEach { (raw,label) ->
        val selected = raw == value
        OutlinedCard(onClick = { if(enabled) onPick(raw) }, enabled = enabled,
            colors = CardDefaults.outlinedCardColors(containerColor =
                if(selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(label, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable fun AuthPage(app: AppState, register: Boolean, openUrl: (String)->Unit) {
    var identifier by remember(register) { mutableStateOf("") }
    var password by remember(register) { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("male") }
    var gradeId by remember { mutableIntStateOf(0) }
    var grades by remember { mutableStateOf(emptyList<Json>()) }
    var rememberMe by remember { mutableStateOf(true) }
    LaunchedEffect(register) { if(register) try { grades = app.api.call("api/grades.php").arr("grades") } catch(e: Exception) { app.show(e.message.orEmpty()) } }

    PageColumn {
        Spacer(Modifier.height(8.dp))
        Text("✦", fontSize = 42.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
        Title(if(register) "إنشاء حسابك" else "مرحبًا بك في المرشد الوزاري",
            if(register) "اختر صفك وأنشئ حسابك بالبريد الإلكتروني" else "سجل الدخول للوصول إلى أسئلتك وتقدمك")
        Panel {
            if(register) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم الثلاثي") }, modifier = Modifier.fillMaxWidth())
                Text("المرحلة الدراسية", fontWeight = FontWeight.SemiBold)
                ChoiceOptions(gradeId.toString(), grades.map { it.int("id").toString() to it.str("name") }) {
                    gradeId = it.toIntOrNull() ?: 0
                }
                Text("الجنس", fontWeight = FontWeight.SemiBold)
                ChoiceOptions(gender, listOf("male" to "ذكر", "female" to "أنثى")) { gender = it }
            }
            OutlinedTextField(identifier, {identifier=it}, label={ Text(if(register) "البريد الإلكتروني" else "البريد الإلكتروني أو رقم الهاتف") },
                modifier=Modifier.fillMaxWidth(), singleLine=true)
            OutlinedTextField(password, {password=it}, label={ Text("كلمة المرور") },
                visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier=Modifier.fillMaxWidth(), singleLine=true)
            if(register) OutlinedTextField(confirm, {confirm=it}, label={ Text("تأكيد كلمة المرور") },
                visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier=Modifier.fillMaxWidth(), singleLine=true)
            else Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(rememberMe, {rememberMe=it}); Text("تذكرني على هذا الجهاز")
            }
            Action(if(register) "إنشاء الحساب" else "تسجيل الدخول", enabled = !app.busy) {
                app.job {
                    if(register) {
                        if(name.trim().split(Regex("\\s+")).size < 3) { app.show("اكتب الاسم الثلاثي كاملًا.");return@job }
                        if(gradeId == 0 || password != confirm) { app.show("اختر الصف وتحقق من تطابق كلمة المرور.");return@job }
                        val d = app.api.call("api/register.php", data=payload(
                            "full_name" to name.trim(), "grade_id" to gradeId, "gender" to gender,
                            "contact_type" to "email", "identifier" to identifier.trim(),
                            "password" to password, "password_confirmation" to confirm))
                        app.show(d.readMessage("راجع بريدك الإلكتروني لتفعيل الحساب."))
                        val redirect = d.str("redirect")
                        if(redirect.startsWith("https://")) openUrl(redirect)
                        app.root("login")
                    } else {
                        val d = app.api.call("api/login.php", data=payload(
                            "identifier" to identifier.trim(), "password" to password,
                            "remember_me" to rememberMe))
                        try {
                            app.bootstrap()
                            app.root("home")
                        } catch (e: Exception) {
                            app.show(d.readMessage("أكمل التحقق من حسابك قبل الدخول."))
                        }
                    }
                }
            }
        }
        TextButton(onClick={ app.root(if(register) "login" else "register") },
            modifier=Modifier.align(Alignment.CenterHorizontally)) {
            Text(if(register) "لديك حساب؟ تسجيل الدخول" else "ليس لديك حساب؟ إنشاء حساب")
        }
        TextButton(onClick={ openUrl("$SERVER/forgot-password.php") },
            modifier=Modifier.align(Alignment.CenterHorizontally)) { Text("نسيت كلمة المرور؟") }
        Text("الحساب والأسئلة والإجابات متزامنة مع الموقع", color=MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.labelSmall, modifier=Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable fun HomePage(app: AppState) {
    PageColumn {
        Title("أهلًا، ${app.user.str("name").substringBefore(" ")} 👋", "كل إجابة تقرّبك من هدفك الوزاري")
        Panel {
            Text(app.user.str("grade"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Metric("الإجابات", app.stats.str("total_answers").ifBlank { "0" })
                Metric("الصحيحة", app.stats.str("correct_answers").ifBlank { "0" })
                Metric("XP", app.stats.str("xp").ifBlank { "0" })
            }
            val remaining = app.session.str("free_remaining")
            Text(if(app.session.bool("subscribed")) "اشتراكك فعّال ✓"
                 else "الأسئلة المجانية المتبقية: ${remaining.ifBlank{"0"}}")
        }
        Title("ابدأ المذاكرة", "اختر المادة ثم الموضوع وابدأ الاختبار")
        Tile("المواد الدراسية", "${app.subjects.size} مواد", { Icon(Icons.Default.MenuBook, null, tint=MaterialTheme.colorScheme.primary) }) { app.push(Route("subjects")) }
        Title("أدواتك", "الوصول السريع للاختبارات والمراجعة")
        val tools = listOf(
            Triple("اختبار مخصص", "builder", "حدد عدد الأسئلة والمرشحات"),
            Triple("مراجعة أخطائي", "review", "راجع الأسئلة التي تحتاج تركيزًا"),
            Triple("تحدي اليوم", "challenge", "أسئلة جديدة وتقييم فوري"),
            Triple("البحث في الأسئلة", "search", "ابحث داخل البنك"),
            Triple("المفضلة", "favorites", "الأسئلة التي حفظتها"),
            Triple("اقترح سؤالًا", "submit", "ساهم في بنك الأسئلة"),
            Triple("طلباتي", "requests", "تابع طلبات الأسئلة"),
            Triple("سجل المحاولات", "history", "راجع تقدمك السابق"),
            Triple("خطة الدراسة", "plan", "أهدافك اليومية"),
            Triple("إنجازاتي", "achievements", "XP والمستويات"),
            Triple("الإشعارات", "notifications", "آخر التحديثات"))
        tools.forEach { (name,route,sub) ->
            Tile(name, sub, { Icon(Icons.Default.Star, null, tint=MaterialTheme.colorScheme.primary) }) { app.push(Route(route)) }
        }
    }
}
