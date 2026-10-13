package com.muriq.murshid.iosparity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.muriq.murshid.iosparity.notifications.NoticeDelivery
import kotlinx.coroutines.isActive
import com.muriq.murshid.iosparity.ui.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val noticeOpenRequest = mutableStateOf<Pair<Int, Int>?>(null)

    private fun captureNotificationIntent(intent: Intent?) {
        if (intent?.action != NoticeDelivery.ACTION_OPEN_NOTICES) return
        val studentId = intent.getIntExtra(NoticeDelivery.EXTRA_STUDENT_ID, 0)
        val noticeId = intent.getIntExtra(NoticeDelivery.EXTRA_NOTICE_ID, 0)
        if (studentId > 0) noticeOpenRequest.value = studentId to noticeId
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNotificationIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureNotificationIntent(intent)
        setContent {
            val vm: MurshidViewModel = viewModel()
            val request by noticeOpenRequest
            val authenticatedStudent = vm.dashboard?.user?.id ?: 0
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(authenticatedStudent, lifecycleOwner) {
                if (authenticatedStudent <= 0) return@LaunchedEffect
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (isActive) {
                        vm.syncNotices(silent = true)
                        delay(75_000)
                    }
                }
            }
            LaunchedEffect(request, authenticatedStudent) {
                val target = request ?: return@LaunchedEffect
                if (target.first == authenticatedStudent && authenticatedStudent > 0) {
                    vm.openNoticeFromSystem(target.first, target.second)
                    noticeOpenRequest.value = null
                }
            }
            val systemDark = isSystemInDarkTheme()
            val dark = when (vm.appearance) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            MurshidTheme(dark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppShell(vm)
                }
            }
        }
    }
}

private val tabTitles = listOf("الرئيسية", "الاختبارات", "المرشحات", "الإعلانات", "حسابي")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(vm: MurshidViewModel) {
    var splash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(2600); splash = false }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        val value = vm.message
        if (value.isNotBlank()) { vm.clearMessage(); snack.showSnackbar(value) }
    }
    BackHandler(enabled = !splash && vm.screen != Screen.GUEST) { vm.back() }
    if (splash) {
        NativeWelcomeScreen(vm.dashboard?.user?.name.orEmpty())
        return
    }
    val topLevel = vm.screen == Screen.MAIN
    val authenticated = vm.dashboard != null
    val showTopBar = vm.screen in setOf(Screen.TOPICS, Screen.EXAM, Screen.FEATURE, Screen.RECOVER, Screen.VERIFY)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(when (vm.screen) {
                            Screen.TOPICS -> vm.subject?.name ?: "المواضيع"
                            Screen.EXAM -> vm.topic?.name ?: "الاختبار"
                            Screen.FEATURE -> vm.feature.title
                            Screen.RECOVER -> "استعادة الحساب"
                            Screen.VERIFY -> "تأكيد الحساب"
                            else -> ""
                        }, fontWeight = FontWeight.Bold, maxLines = 1)
                    },
                    navigationIcon = {
                        IconButton(onClick = vm::back) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (topLevel && authenticated) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.heightIn(min = 65.dp)
                ) {
                    val icons = listOf(
                        Icons.Default.Home, Icons.Default.Checklist, Icons.Default.GpsFixed,
                        Icons.Default.Campaign, Icons.Default.Person
                    )
                    val visibleTabs = com.muriq.murshid.iosparity.data.IosPortRules.visibleTabs(vm.dashboard?.subscribed == true)
                    visibleTabs.forEach { index ->
                        val label = tabTitles[index]
                        NavigationBarItem(
                            selected = vm.tab == index,
                            onClick = { vm.selectTab(index) },
                            icon = {
                                if (index == 3 && vm.unreadSchoolAds > 0) {
                                    BadgedBox(badge = {
                                        Badge { Text(if (vm.unreadSchoolAds > 99) "99+" else vm.unreadSchoolAds.toString()) }
                                    }) {
                                        Icon(icons[index], contentDescription = label, modifier = Modifier.size(21.dp))
                                    }
                                } else {
                                    Icon(icons[index], contentDescription = label, modifier = Modifier.size(21.dp))
                                }
                            },
                            label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxSize().widthIn(max = 780.dp)) {
                when (vm.screen) {
                Screen.GUEST -> Column {
                    if (vm.api.hasSavedSession()) {
                        TextButton(
                            onClick = { vm.refreshDashboard() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !vm.loading
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("إعادة الاتصال بحسابي المحفوظ")
                        }
                    }
                    GuestLandingScreen(onLogin = vm::showLogin, onRegister = vm::showRegister)
                }
                Screen.LOGIN -> LoginScreen(vm)
                Screen.REGISTER -> RegisterScreen(vm)
                Screen.RECOVER -> RecoveryScreen(vm)
                Screen.VERIFY -> VerificationScreen(vm)
                Screen.MAIN -> when (vm.tab) {
                    0 -> DashboardScreen(vm)
                    1 -> TestsScreen(vm)
                    2 -> PredictionsTab(vm)
                    3 -> AdvertisementsTab(vm)
                    else -> AccountTab(vm)
                }
                Screen.TOPICS -> TopicsScreen(vm)
                Screen.EXAM -> ExamScreen(vm)
                Screen.FEATURE -> FeatureScreen(vm)
                }
            }
            if (vm.loading) {
                LinearProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    color = MurshidBlue
                )
            }
        }
    }
}

@Composable
private fun NativeWelcomeScreen(name: String) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF041022), Color(0xFF10294A), Color(0xFF0B1F33)))
        ), contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            Box(
                Modifier.size(142.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(R.drawable.murshid_original),
                    contentDescription = "شعار المرشد الوزاري",
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(26.dp)),
                    contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(24.dp))
            Text("منصة المرشد الوزاري", color = Color.White, fontSize = 29.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(Modifier.width(130.dp), color = MurshidGold.copy(alpha = 0.65f))
            Spacer(Modifier.height(15.dp))
            Text("تعلّم بثقة، واصنع مستقبلك", color = Color(0xFFE9E6DB),
                textAlign = TextAlign.Center)
            if (name.isNotBlank()) {
                Spacer(Modifier.height(15.dp))
                Text("أهلًا، ${name.split(" ").first()}", color = MurshidGold)
            }
        }
        Text("العراق • طريق النجاح الوزاري", color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp))
    }
}

private data class LandingItem(val title: String, val description: String)

@Composable
private fun GuestLandingScreen(onLogin: () -> Unit, onRegister: () -> Unit) {
    val features = listOf(
        LandingItem("أسئلة مرتبة حسب الموضوع", "اختر المادة والموضوع وشاهد الأسئلة المنشورة."),
        LandingItem("تعلّم من إجابتك", "اعرف الإجابة النموذجية والتفسير بعد إرسال الحل."),
        LandingItem("اختبارات تحفظ تقدمك", "تابع نتائجك ومحاولاتك من حسابك الشخصي."),
        LandingItem("15 إجابة مجانية", "ابدأ التجربة قبل الاشتراك في المنصة."),
        LandingItem("نتائج وتفسير للأخطاء", "حدد نقاط قوتك والموضوعات التي تحتاج مراجعة."),
        LandingItem("خدمة دعم ومتابعة", "تواصل مع إدارة المنصة من داخل حسابك.")
    )
    val rules = listOf(
        LandingItem("الحساب وتسجيل الدخول", "استخدم بريدك أو هاتفك المسجل ولا تشارك كلمة المرور."),
        LandingItem("الاختبارات", "اختر صفك الدراسي، ثم المادة والموضوع، وأجب عن الأسئلة بالترتيب."),
        LandingItem("الأسئلة المجانية", "يتوفر للحساب الجديد 15 سؤالاً مجانياً، ويُحتسب عند إرسال الإجابة."),
        LandingItem("تحدي المليون", "تُحتسب النقاط والترتيب وفق قواعد المسابقة على الخادم."),
        LandingItem("خدمة العملاء", "يمكنك متابعة مشكلات الحساب والاشتراك من داخل التطبيق.")
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, null, tint = MurshidBlue, modifier = Modifier.size(43.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("منصة المرشد الوزاري", fontWeight = FontWeight.Bold)
                    Text("مساحة تعلّمك", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onLogin) { Text("دخول") }
            }
        }
        item {
            HeroPanel("خطوة واضحة، نحو نجاحك.",
                "رتّب دراستك، اختبر فهمك، وارجع لما يحتاج مراجعة. رحلة دراسية في مكان واحد.") {
                Text("تعلّم بتركيز. تقدّم بثقة.", color = MurshidGold, fontWeight = FontWeight.Bold)
                PrimaryAction("ابدأ الآن", onLogin)
                OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) {
                    Text("إنشاء حساب جديد", color = Color.White)
                }
            }
        }
        item { SectionTitle("لماذا المرشد؟", "كل ما تحتاجه للمراجعة", "تعلم خطوة بخطوة وراجع تقدمك بوضوح.") }
        items(features) { feature ->
            MurshidCard {
                Text(feature.title, fontWeight = FontWeight.Bold)
                Text(feature.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SectionTitle("تعليمات الاستخدام", "كيف تبدأ وتستفيد من المنصة") }
        items(rules) { rule ->
            MurshidCard {
                Text(rule.title, fontWeight = FontWeight.Bold)
                Text(rule.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { PrimaryAction("تسجيل الدخول", onLogin) }
    }
}

@Composable
private fun AuthHeading(subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.School, contentDescription = null, tint = MurshidBlue,
            modifier = Modifier.size(58.dp))
        Text("منصة المرشد الوزاري", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(subtitle, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoginScreen(vm: MurshidViewModel) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(true) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item { AuthHeading("سجّل الدخول وواصل دراستك من حيث توقفت") }
        item {
            MurshidCard {
                Text("تسجيل الدخول", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = identifier, onValueChange = { identifier = it },
                    label = { Text("البريد الإلكتروني أو رقم الهاتف") },
                    placeholder = { Text("example@email.com أو 07XXXXXXXXX") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("كلمة المرور") }, singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "إخفاء كلمة المرور" else "إظهار كلمة المرور")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تذكرني على هذا الجهاز", modifier = Modifier.weight(1f))
                    Switch(checked = remember, onCheckedChange = { remember = it })
                }
                PrimaryAction(if (vm.loading) "جارٍ الدخول…" else "دخول",
                    onClick = { vm.login(identifier, password, remember) },
                    enabled = identifier.isNotBlank() && password.isNotBlank() && !vm.loading)
                TextButton(onClick = vm::showRecovery, modifier = Modifier.fillMaxWidth()) {
                    Text("نسيت كلمة المرور؟")
                }
            }
        }
        item {
            SecondaryAction("إنشاء حساب طالب جديد", vm::showRegister)
        }
    }
}

@Composable
private fun RegisterScreen(vm: MurshidViewModel) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var name by rememberSaveable { mutableStateOf("") }
    var identifier by rememberSaveable { mutableStateOf("") }
    var contactType by rememberSaveable { mutableStateOf("email") }
    var gradeId by rememberSaveable { mutableIntStateOf(0) }
    var gradeExpanded by remember { mutableStateOf(false) }
    var gender by rememberSaveable { mutableStateOf("male") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf("") }
    val labels = listOf("بيانات الطالب", "معلومات التواصل", "حماية الحساب")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AuthHeading("أنشئ حسابك بخطوات واضحة كما في تطبيق الآيفون") }
        item {
            MurshidCard {
                Text("الخطوة " + step.toString() + " من 3", color = MurshidBlue,
                    fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { step / 3f }, color = MurshidBlue,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                Text(labels[step - 1], style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                when (step) {
                    1 -> {
                        OutlinedTextField(name, { name = it; localError = "" },
                            label = { Text("الاسم الثلاثي الكامل") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("الصف الدراسي", fontWeight = FontWeight.SemiBold)
                        Box {
                            SecondaryAction(vm.grades.firstOrNull { it.first == gradeId }?.second
                                ?: "اختر الصف الدراسي", { gradeExpanded = true })
                            DropdownMenu(expanded = gradeExpanded,
                                onDismissRequest = { gradeExpanded = false }) {
                                vm.grades.forEach { (id, title) ->
                                    DropdownMenuItem(text = { Text(title) }, onClick = {
                                        gradeId = id; gradeExpanded = false; localError = ""
                                    })
                                }
                            }
                        }
                        Text("الجنس", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(selected = gender == "male", onClick = { gender = "male" },
                                label = { Text("ذكر") })
                            FilterChip(selected = gender == "female", onClick = { gender = "female" },
                                label = { Text("أنثى") })
                        }
                    }
                    2 -> {
                        Text("وسيلة التواصل", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(selected = contactType == "email",
                                onClick = { contactType = "email"; identifier = ""; localError = "" },
                                label = { Text("البريد الإلكتروني") })
                            FilterChip(selected = contactType == "phone",
                                onClick = { contactType = "phone"; identifier = ""; localError = "" },
                                label = { Text("رقم الهاتف") })
                        }
                        OutlinedTextField(identifier, { identifier = it; localError = "" },
                            label = { Text(if (contactType == "email") "البريد الإلكتروني"
                                else "رقم الهاتف العراقي") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType =
                                if (contactType == "email") KeyboardType.Email else KeyboardType.Phone))
                        Text("سيُستخدم هذا العنوان أو الرقم للتحقق واستعادة الحساب.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        OutlinedTextField(password, { password = it; localError = "" },
                            label = { Text("كلمة المرور - 8 أحرف على الأقل") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showPassword) VisualTransformation.None
                                else PasswordVisualTransformation())
                        OutlinedTextField(confirm, { confirm = it; localError = "" },
                            label = { Text("تأكيد كلمة المرور") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showPassword) VisualTransformation.None
                                else PasswordVisualTransformation())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("إظهار كلمة المرور", modifier = Modifier.weight(1f))
                            Switch(checked = showPassword,
                                onCheckedChange = { showPassword = it })
                        }
                    }
                }
                if (localError.isNotBlank()) {
                    Text(localError, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
                PrimaryAction(if (step == 3) "إنشاء الحساب" else "متابعة", {
                    val error = when (step) {
                        1 -> com.muriq.murshid.iosparity.data.RegistrationParityRules.personalError(name, gradeId)
                        2 -> com.muriq.murshid.iosparity.data.RegistrationParityRules.contactError(identifier, contactType)
                        else -> com.muriq.murshid.iosparity.data.RegistrationParityRules.passwordError(password, confirm)
                    }
                    if (error != null) localError = error
                    else if (step < 3) { step += 1; localError = "" }
                    else vm.register(name, identifier, contactType, gradeId, gender, password, confirm)
                }, enabled = !vm.loading)
                if (step > 1) {
                    SecondaryAction("رجوع للخطوة السابقة") { step -= 1; localError = "" }
                }
            }
        }
        item {
            TextButton(onClick = vm::showLogin, modifier = Modifier.fillMaxWidth()) {
                Text("لديك حساب؟ سجّل الدخول")
            }
        }
    }
}

@Composable
private fun VerificationScreen(vm: MurshidViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    val email = vm.verification.optBoolean("email_verification_required")
    val destination = vm.verification.optString(if (email) "masked_email" else "masked_phone")
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.VerifiedUser, null, tint = MurshidBlue, modifier = Modifier.size(58.dp))
        Text(if (email) "تأكيد البريد الإلكتروني" else "تأكيد رقم الهاتف",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(vm.verification.optString("message",
            "أدخل الرمز المرسل إلى وسيلة التواصل المسجلة.") +
            if (destination.isBlank()) "" else "\n" + destination,
            textAlign = TextAlign.Center)
        OutlinedTextField(
            value = code, onValueChange = {
                code = com.muriq.murshid.iosparity.net.VerificationFormRules.normalizedDigits(it)
                    .filter(Char::isDigit).take(6)
            },
            label = { Text("رمز التحقق (6 أرقام)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        PrimaryAction("تأكيد الحساب", { vm.verifyPendingCode(code) },
            enabled = !vm.loading && code.length == 6)
        SecondaryAction("إعادة إرسال الرمز", vm::resendPendingVerification)
        SecondaryAction("العودة لتسجيل الدخول", vm::showLogin)
    }
}

@Composable
private fun RecoveryScreen(vm: MurshidViewModel) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("الحساب", "استعادة كلمة المرور", "الاستعادة الآمنة بالبريد أو WhatsApp.") }
        if (vm.recoveryToken.isBlank() && !vm.recoveryEmailSent) item {
            MurshidCard {
                OutlinedTextField(identifier, { identifier = it }, label = { Text("البريد أو رقم الهاتف المسجل") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                PrimaryAction("طلب الاستعادة", { vm.beginRecovery(identifier) },
                    enabled = identifier.isNotBlank() && !vm.loading)
            }
        }
        if (vm.recoveryToken.isNotBlank()) item {
            MurshidCard {
                Text("التحقق عبر WhatsApp", fontWeight = FontWeight.Bold)
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("رمز التحقق (6 أرقام)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور الجديدة") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(confirm, { confirm = it }, label = { Text("تأكيد كلمة المرور") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("إظهار كلمة المرور", modifier = Modifier.weight(1f))
                    Switch(showPassword, onCheckedChange = { showPassword = it })
                }
                PrimaryAction("تغيير كلمة المرور", { vm.finishRecovery(code, password, confirm) },
                    enabled = !vm.loading && code.length == 6 && password.length >= 8 && password == confirm)
                SecondaryAction("إعادة إرسال الرمز", vm::resendRecovery)
            }
        }
        if (vm.recoveryFeedback.isNotBlank()) item { MurshidCard { Text(vm.recoveryFeedback) } }
        if (vm.recoveryEmailSent) item { Text("افتح رابط الاستعادة من بريدك، ثم سجّل الدخول.") }
        item { SecondaryAction("العودة لتسجيل الدخول", vm::showLogin) }
    }
}
