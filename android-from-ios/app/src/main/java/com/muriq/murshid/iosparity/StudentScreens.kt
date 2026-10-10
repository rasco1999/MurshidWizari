package com.muriq.murshid.iosparity

import androidx.compose.foundation.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
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
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("المرشحات", "المرشحات الوزارية", "قسم خاص بالمشتركين.") }
        if (dash?.subscribed != true) {
            item {
                MurshidCard {
                    Icon(Icons.Default.Lock, null, tint = MurshidBlue, modifier = Modifier.size(42.dp))
                    Text("المرشحات متاحة بعد الاشتراك", fontWeight = FontWeight.Bold)
                    PrimaryAction("عرض الاشتراكات") { vm.openFeature(Feature.SUBSCRIPTION) }
                }
            }
        } else {
            items(dash.subjects, key = { it.id }) { item ->
                SubjectListCard(item) { vm.openPredictions(item) }
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
            SecondaryAction("تسجيل الخروج", vm::logout)
        }
    }
}
