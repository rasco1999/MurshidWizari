package com.muriq.murshid.iosparity

import android.content.Intent
import android.net.Uri

import androidx.compose.foundation.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muriq.murshid.iosparity.data.*
import com.muriq.murshid.iosparity.ui.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val motivations = listOf(
    "كل إجابة اليوم تقرّبك من هدفك.",
    "خطوة صغيرة كل يوم تصنع فرقاً كبيراً.",
    "ركّز على فهم الفكرة، ثم أتقن الحل.",
    "استمر، فالنجاح حصيلة محاولاتك.",
    "مستقبلك يبدأ من اجتهادك اليوم.",
    "كل سؤال تتعلمه فرصة جديدة للتفوق."
)

@Composable
fun DashboardScreen(vm: MurshidViewModel) {
    val dash = vm.dashboard
    if (dash == null) { LoadingPanel(); return }
    var time by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { time = Date(); delay(15000) }
    }
    val clock = remember(time) { SimpleDateFormat("hh:mm a", Locale("ar", "IQ")).format(time) }
    val motivation = motivations[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % motivations.size]
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("المرشد الوزاري", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.changeAppearance(if (vm.appearance == "dark") "light" else "dark") }) {
                    Icon(if (vm.appearance == "dark") Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "تغيير الوضع")
                }
                IconButton(onClick = { vm.openFeature(Feature.NOTIFICATIONS) }) {
                    BadgedBox(badge = {
                        if (vm.noticeInbox.unread > 0) {
                            Badge { Text(vm.noticeInbox.unread.coerceAtMost(99).toString()) }
                        }
                    }) {
                        Icon(Icons.Default.Notifications, contentDescription = "الإشعارات")
                    }
                }
            }
        }
        item {
            HeroPanel(
                "أهلًا، ${dash.user.name.substringBefore(' ')}",
                dash.user.grade.ifBlank { "رحلتك الدراسية تبدأ هنا" }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val avatar = vm.accountData.text("avatar_url")
                    if (avatar.startsWith("https://")) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(avatar)
                                .memoryCacheKey(avatar + vm.accountData.text("avatar_revision"))
                                .build(),
                            contentDescription = "صورة الطالب",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(45.dp).clip(RoundedCornerShape(13.dp))
                        )
                    } else {
                        Box(
                            Modifier.size(45.dp).clip(RoundedCornerShape(13.dp))
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.School, null, tint = Color.White) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(motivation, color = Color.White, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroMetric("النقاط", dash.stats.num("xp").toString(), Modifier.weight(1f))
                    HeroMetric("الصحيحة", dash.stats.num("correct_answers").toString(), Modifier.weight(1f))
                    HeroMetric("الوقت", clock, Modifier.weight(1.3f))
                }
            }
        }
        item {
            MurshidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (dash.subscribed) Icons.Default.Verified else Icons.Default.CardGiftcard,
                        null, tint = MurshidBlue)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (dash.subscribed) "اشتراك فعّال" else "رصيد الأسئلة المجانية",
                            fontWeight = FontWeight.Bold)
                        Text(
                            if (dash.subscribed)
                                (dash.remainingDays?.let { "متبقي $it يوم" } ?: "كل الأسئلة متاحة")
                            else "المتبقي ${dash.freeRemaining} من ${dash.freeLimit} سؤال",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!dash.subscribed) {
                        TextButton(onClick = { vm.openFeature(Feature.SUBSCRIPTION) }) {
                            Text("الاشتراك")
                        }
                    }
                }
            }
        }
        item { HomeSlider(vm) }
        item {
            SectionTitle("ابدأ المراجعة", "اختبر فهمك اليوم",
                "انتقل مباشرة إلى أسئلة الصف الدراسي.")
            PrimaryAction("الاختبارات والمواد", { vm.selectTab(1) })
        }
        item { SectionTitle("أدوات تساعدك", "كل ما تحتاجه في مكان واحد") }
        item {
            val actions = listOf(
                Triple("قسم النجاح", Icons.Default.Insights, Feature.INSIGHTS),
                Triple("راجع أخطاءك", Icons.Default.HistoryEdu, Feature.REVIEW),
                Triple("المفضلة", Icons.Default.Bookmark, Feature.FAVORITES),
                Triple("تحدي المليون", Icons.Default.EmojiEvents, Feature.CONTEST),
                Triple("غيّر جو", Icons.Default.AutoStories, Feature.STORIES),
                Triple("خطة الدراسة", Icons.Default.EventNote, Feature.STUDY_PLAN)
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (title, icon, feature) ->
                            MurshidCard(Modifier.weight(1f).clickable { vm.openFeature(feature) }) {
                                Icon(icon, null, tint = MurshidBlue, modifier = Modifier.size(25.dp))
                                Text(title, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionTitle("المواد الدراسية", "تابع دراستك حسب المنهج") }
        items(dash.subjects, key = { "dashboard-${it.id}" }) { subject ->
            SubjectListCard(subject) { vm.openTopics(subject) }
        }
        item { OfficialSocialLinks() }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun HeroMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontSize = 14.sp,
            fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(title, color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp)
    }
}

@Composable
private fun SubjectListCard(subject: Subject, onClick: () -> Unit) {
    MurshidCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(47.dp).clip(RoundedCornerShape(15.dp))
                    .background(MurshidBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MenuBook, null, tint = MurshidBlue)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(subject.name, fontWeight = FontWeight.Bold)
                if (subject.count > 0) Text("${subject.count} سؤال",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronLeft, "فتح", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun TestsScreen(vm: MurshidViewModel) {
    val subjects = vm.dashboard?.subjects.orEmpty()
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, subjects) {
        subjects.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitle("اختباراتك", "اختر المادة وابدأ بثقة",
                "المواضيع والأسئلة تُحمّل من حسابك مباشرة.")
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("ابحث عن مادة") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
        if (filtered.isEmpty()) {
            item { EmptyPanel("لا توجد مواد مطابقة", "جرّب البحث باسم آخر.") }
        }
        items(filtered, key = { it.id }) { subject ->
            SubjectListCard(subject) { vm.openTopics(subject) }
        }
    }
}

@Composable
fun TopicsScreen(vm: MurshidViewModel) {
    var search by remember { mutableStateOf("") }
    val items = vm.topics.filter { it.name.contains(search.trim(), ignoreCase = true) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle("المنهج", "مواضيع ${vm.subject?.name.orEmpty()}",
            "كل إجابة تُحفظ في حسابك بعد تأكيد الخادم.") }
        item {
            OutlinedTextField(search, { search = it },
                label = { Text("بحث سريع عن موضوع") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (items.isEmpty()) item { EmptyPanel("لا توجد مواضيع متاحة") }
        items(items, key = { it.id }) { topic ->
            MurshidCard(Modifier.clickable { vm.openExam(topic) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FactCheck, null, tint = MurshidBlue)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(topic.name, fontWeight = FontWeight.Bold)
                        Text(if (topic.count > 0) "${topic.count} سؤال" else if (topic.count == 0) "لا توجد أسئلة منشورة" else "اضغط لعرض الأسئلة",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronLeft,
                        null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun PredictionsTab(vm: MurshidViewModel) {
    val dash = vm.dashboard
    LaunchedEffect(dash?.user?.id, dash?.subscribed) {
        if (dash?.subscribed == true &&
            dash.subjects.isNotEmpty() &&
            dash.subjects.none { it.id == vm.selectedPredictionSubjectId }) {
            vm.selectPredictionSubject(dash.subjects.first())
        }
    }
    val predictions = vm.predictionsData.array("predictions")
        .ifEmpty { vm.predictionsData.array("items") }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(MurshidNavy, MurshidBlue)
                    )).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GpsFixed, null, tint = MurshidGold,
                        modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("الدور الأول • 2027", color = MurshidGold,
                        fontWeight = FontWeight.Bold)
                }
                Text("المرشحات الوزارية",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White, fontWeight = FontWeight.Bold)
                Text("مراجعة مرتبة اعتمادًا على السنوات السابقة وتكرار الموضوع وتوثيق الأسئلة.",
                    color = Color(0xFFE6EEFC),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            MurshidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text("المرشحات احتمالات للمراجعة وليست ضمانًا لورود السؤال، ولا تغني عن المنهج.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text("اختر المادة", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
        if (dash?.subscribed != true) {
            item {
                MurshidCard {
                    Icon(Icons.Default.Lock, null, tint = MurshidBlue,
                        modifier = Modifier.size(42.dp))
                    Text("المرشحات متاحة بعد الاشتراك", fontWeight = FontWeight.Bold)
                    PrimaryAction("عرض الاشتراكات") { vm.openFeature(Feature.SUBSCRIPTION) }
                }
            }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    dash.subjects.forEach { subject ->
                        FilterChip(
                            selected = vm.selectedPredictionSubjectId == subject.id,
                            onClick = { vm.selectPredictionSubject(subject) },
                            label = { Text(subject.name, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
            when {
                vm.predictionLoading -> item {
                    LoadingPanel("جارٍ تحميل مرشحات المادة…")
                }
                vm.predictionError.isNotBlank() -> item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                            .background(Color(0xFF221209)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, null, tint = Color(0xFFFF9F32))
                            Spacer(Modifier.width(8.dp))
                            Text(vm.predictionError, color = Color(0xFFFF9F32))
                        }
                        TextButton(onClick = {
                            dash.subjects.firstOrNull { it.id == vm.selectedPredictionSubjectId }
                                ?.let(vm::selectPredictionSubject)
                        }) { Text("إعادة المحاولة") }
                    }
                }
                predictions.isEmpty() -> item {
                    EmptyPanel("لا توجد مرشحات لهذه المادة حاليًا.")
                }
                else -> {
                    item { Text("المرشحات • ${predictions.size} سؤال",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium) }
                    items(predictions) { prediction ->
                        var expanded by remember(prediction.toString()) { mutableStateOf(false) }
                        MurshidCard {
                            val chapter = prediction.text("chapter_name",
                                prediction.text("topic"))
                            if (chapter.isNotBlank()) {
                                Text(chapter, color = MurshidBlue,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold)
                            }
                            Text(prediction.text("question_text",
                                prediction.text("question", prediction.text("text"))),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge)
                            val score = prediction.text("prediction_score",
                                prediction.text("probability"))
                            val confidence = prediction.text("confidence")
                            val info = listOfNotNull(
                                score.takeIf(String::isNotBlank)?.let { "درجة الترشيح: $it" },
                                confidence.takeIf(String::isNotBlank)?.let { "الثقة: $it" }
                            ).joinToString(" • ")
                            if (info.isNotBlank()) {
                                Text(info, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "إخفاء الإجابة" else "عرض الإجابة والتوضيح")
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null)
                            }
                            if (expanded) {
                                val answer = prediction.text("correct_answer")
                                val explanation = prediction.text("explanation")
                                if (answer.isNotBlank()) {
                                    Text("الإجابة النموذجية", color = Color(0xFF34C759),
                                        fontWeight = FontWeight.Bold)
                                    Text(answer)
                                }
                                if (explanation.isNotBlank()) {
                                    Text("التوضيح", color = MurshidBlue,
                                        fontWeight = FontWeight.Bold)
                                    Text(explanation)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvertisementsTab(vm: MurshidViewModel) {
    val ads = vm.dashboard?.ads.orEmpty().filter {
        it.text("school").isNotBlank() && it.text("text").isNotBlank() &&
            it.optBoolean("active", true)
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("الإعلانات", "إعلانات المدارس", "تُعرض الإعلانات المنشورة من إدارة المنصة.") }
        if (ads.isEmpty()) item {
            EmptyPanel("لا توجد إعلانات مدرسية الآن", "ستظهر الإعلانات بعد نشرها.")
        }
        items(ads) { ad ->
            MurshidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, null, tint = MurshidBlue)
                    Spacer(Modifier.width(12.dp))
                    Text(ad.text("school"), fontWeight = FontWeight.Bold)
                }
                Text(ad.text("text"))
            }
        }
        item { SecondaryAction("تحديث الإعلانات") { vm.refreshAds() } }
    }
}

@Composable
fun AccountTab(vm: MurshidViewModel) {
    val dash = vm.dashboard ?: return
    val account = vm.accountData
    val actualUser = account.optJSONObject("user")
    val name = actualUser?.text("name", dash.user.name) ?: dash.user.name
    val avatarUrl = account.text("avatar_url")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { image ->
        if (image != null) vm.uploadAvatar(image)
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { SectionTitle("حسابي", "الملف الشخصي", "بياناتك الرسمية المسجلة في المنصة.") }
        item {
            MurshidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (avatarUrl.startsWith("https://")) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(avatarUrl)
                                .memoryCacheKey(avatarUrl + account.text("avatar_revision"))
                                .build(),
                            contentDescription = "صورة صاحب الحساب",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(60.dp).clip(CircleShape)
                        )
                    } else {
                        Box(Modifier.size(60.dp).clip(CircleShape).background(MurshidBlue.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = MurshidBlue, modifier = Modifier.size(35.dp))
                        }
                    }
                    Spacer(Modifier.width(13.dp))
                    Column {
                        Text(name, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Text(dash.user.grade, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SecondaryAction("رفع صورة شخصية") { picker.launch("image/*") }
                if (avatarUrl.isNotBlank()) {
                    SecondaryAction("حذف الصورة الشخصية", vm::removeAvatar)
                }
                SecondaryAction("تعديل بيانات الحساب") { vm.openFeature(Feature.ACCOUNT) }
            }
        }
        item {
            MurshidCard {
                Text(if (dash.subscribed) "اشتراكك فعّال" else "الاشتراك والرصيد",
                    fontWeight = FontWeight.Bold)
                Text(if (dash.subscribed) "الأيام المتبقية: ${dash.remainingDays ?: "—"}"
                    else "الأسئلة المجانية المتبقية: ${dash.freeRemaining}")
                PrimaryAction("إدارة الاشتراك") { vm.openFeature(Feature.SUBSCRIPTION) }
            }
        }
        item {
            val context = LocalContext.current
            val supported = remember {
                androidx.biometric.BiometricManager.from(context).canAuthenticate(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
            }
            MurshidCard {
                Text("الدخول بالبصمة", fontWeight = FontWeight.Bold)
                Text("بعد تسجيل الدخول وتفعيل تذكرني، افتح حسابك ببصمة الإصبع أو الوجه أو رمز قفل الجهاز. التحقق مرة واحدة عند فتح التطبيق، وليس عند التنقل بين الصفحات.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تفعيل فتح الحساب بالبصمة", modifier = Modifier.weight(1f))
                    Switch(
                        checked = vm.biometricEnabled,
                        enabled = supported && vm.api.hasSavedSession(),
                        onCheckedChange = vm::updateBiometricEnabled
                    )
                }
                if (!supported) Text("هذا الجهاز لا يدعم طريقة تحقق آمنة مفعّلة حالياً.")
                else if (!vm.api.hasSavedSession()) Text("فعّل «تذكرني على هذا الجهاز» عند تسجيل الدخول أولاً.")
            }
        }
        item { SectionTitle("خدمات الطالب", "أدوات حسابك") }
        items(
            listOf(
                Feature.NOTIFICATIONS, Feature.FAVORITES, Feature.REVIEW,
                Feature.SUPPORT, Feature.ACHIEVEMENTS, Feature.STUDY_PLAN, Feature.CONTEST
            )
        ) { feature ->
            SecondaryAction(feature.title) { vm.openFeature(feature) }
        }
        item {
            MurshidCard {
                Text("ألوان التطبيق", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("royal" to "ملكي", "aurora" to "أورورا", "orchid" to "بنفسجي")
                        .forEach { (id, label) ->
                            FilterChip(
                                selected = vm.studentPalette == id,
                                onClick = { vm.changeStudentPalette(id) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                }
            }
        }
        item {
            MurshidCard {
                Text("المظهر", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("light" to "فاتح", "dark" to "داكن", "system" to "النظام").forEach { (mode, text) ->
                        FilterChip(selected = vm.appearance == mode,
                            onClick = { vm.changeAppearance(mode) },
                            label = { Text(text, fontSize = 12.sp) })
                    }
                }
            }
        }
        item {
            val ctx = LocalContext.current
            MurshidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تابعنا", fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.instagram.com/murshid.iq/"))) }) {
                        Icon(painterResource(R.drawable.murshid_social_instagram), "إنستغرام",
                            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.facebook.com/Murshidiq/"))) }) {
                        Image(painterResource(R.drawable.murshid_social_facebook), "فيسبوك",
                            modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://t.me/El_Murshed_iq"))) }) {
                        Image(painterResource(R.drawable.murshid_social_telegram), "تلغرام",
                            modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        item {
            SecondaryAction("تسجيل الخروج", vm::logout)
        }
    }
}
