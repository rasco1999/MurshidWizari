package com.muriq.murshid.iosparity

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muriq.murshid.iosparity.data.*
import com.muriq.murshid.iosparity.ui.*
import org.json.JSONObject

@Composable
fun FeatureScreen(vm: MurshidViewModel) {
    when (vm.feature) {
        Feature.SUBSCRIPTION -> SubscriptionScreen(vm)
        Feature.ACCOUNT -> AccountDetailsScreen(vm)
        Feature.SUPPORT -> SupportScreen(vm)
        Feature.STORIES -> StoriesScreen(vm)
        Feature.PREDICTIONS -> PredictionDetailsScreen(vm)
        Feature.ADS -> AdvertisementsTab(vm)
        Feature.QUESTION_REQUEST -> QuestionRequestScreen(vm)
        else -> GenericFeatureScreen(vm)
    }
}

@Composable
private fun SubscriptionScreen(vm: MurshidViewModel) {
    val dash = vm.dashboard ?: return
    val context = LocalContext.current
    val redirect = vm.paymentUrl
    LaunchedEffect(redirect) {
        if (redirect.startsWith("https://")) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(redirect)))
            } finally { vm.clearPaymentUrl() }
        }
    }
    var code by remember { mutableStateOf("") }
    var plan by remember(dash.plans) { mutableStateOf(dash.plans.firstOrNull()?.id.orEmpty()) }
    var customerPhone by remember { mutableStateOf("") }
    val plans = dash.plans
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            HeroPanel("المرشد الوزاري", if (dash.subscribed)
                "اشتراكك فعّال. واصل رحلتك الدراسية."
                else "تدرّب، راجع أخطاءك وتقدّم بثقة.") {
                if (!dash.subscribed) SmallPill("المتبقي من المجاني: ${dash.freeRemaining}", MurshidGold)
            }
        }
        if (dash.subscribed) item {
            MurshidCard {
                Text("كل الأسئلة متاحة الآن", fontWeight = FontWeight.Bold)
                Text("الأيام المتبقية: ${dash.remainingDays ?: "—"}")
            }
        }
        if (!dash.subscribed) {
            item { SectionTitle("الاشتراكات", "اختر الباقة المناسبة",
                "يُستكمل الدفع من خلال البوابة الخارجية الرسمية فقط.") }
            items(plans) { p ->
                MurshidCard(Modifier.clickable { plan = p.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = plan == p.id, onClick = { plan = p.id })
                        Column(Modifier.weight(1f)) {
                            Text(p.label, fontWeight = FontWeight.Bold)
                            Text("${p.amount} دينار عراقي", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                MurshidCard {
                    OutlinedTextField(customerPhone, { customerPhone = it },
                        label = { Text("رقم هاتف التواصل لإتمام الدفع") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    PrimaryAction("متابعة الدفع الخارجي", onClick = {
                        vm.checkout(plan, customerPhone)
                    }, enabled = plan.isNotBlank() && customerPhone.isNotBlank() && !vm.loading)
                    SecondaryAction("التحقق من حالة الدفع", vm::checkPaymentStatus)
                }
            }
        }
        item {
            MurshidCard {
                Text("لديك رمز اشتراك؟", fontWeight = FontWeight.Bold)
                OutlinedTextField(code, { code = it }, label = { Text("رمز الاشتراك") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                PrimaryAction("تفعيل الرمز", { vm.redeem(code) },
                    enabled = code.isNotBlank() && !vm.loading)
            }
        }
    }
}

@Composable
private fun AccountDetailsScreen(vm: MurshidViewModel) {
    val user = vm.accountData.optJSONObject("user")
    val name = user?.text("name") ?: vm.dashboard?.user?.name.orEmpty()
    var newName by remember(name) { mutableStateOf(name) }
    var confirmation by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val deletionStatus = vm.accountData.optJSONObject("deletion")?.text("status").orEmpty()
    LazyColumn(contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            MurshidCard {
                Text("الملف الشخصي", fontWeight = FontWeight.Bold)
                Text(name)
                Text(vm.dashboard?.user?.grade.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(newName, { newName = it },
                    label = { Text("تغيير الاسم الثلاثي") },
                    modifier = Modifier.fillMaxWidth())
                PrimaryAction("حفظ الاسم الجديد") { vm.rename(newName) }
            }
        }
        item {
            MurshidCard {
                Text("الخصوصية وإدارة الحساب", fontWeight = FontWeight.Bold)
                Text(if (deletionStatus == "pending") "طلب حذف حسابك قيد المعالجة"
                    else "يمكنك تقديم طلب حذف حسابك وتراجعه الإدارة.")
                if (deletionStatus == "pending") {
                    SecondaryAction("إلغاء طلب حذف الحساب", vm::cancelDeletion)
                } else {
                    SecondaryAction("طلب حذف الحساب") { showDeleteDialog = true }
                }
            }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("تأكيد طلب حذف الحساب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("هذا إجراء حساس وقد يؤدي إلى فقدان بياناتك. للتأكيد اكتب: حذف حسابي")
                    OutlinedTextField(confirmation, { confirmation = it },
                        label = { Text("عبارة التأكيد") })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmation.trim() == "حذف حسابي",
                    onClick = { vm.requestDeletion(); showDeleteDialog = false }
                ) { Text("تقديم الطلب") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun SupportScreen(vm: MurshidViewModel) {
    var text by remember { mutableStateOf("") }
    val rows = vm.featureData.array("messages").ifEmpty {
        vm.featureData.array("items")
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle("الدعم", "خدمة العملاء", "أرسل سؤالك وتابع الرد في حسابك.") }
        items(rows) { row ->
            MurshidCard {
                Text(row.text("message", row.text("text")), fontWeight = FontWeight.Medium)
                val response = row.text("reply")
                if (response.isNotBlank()) Text("رد الإدارة: $response", color = MurshidBlue)
                Text(row.text("created_at"), style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            MurshidCard {
                OutlinedTextField(text, { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اكتب رسالتك للإدارة") }, minLines = 4)
                PrimaryAction("إرسال الرسالة", { vm.sendSupport(text) },
                    enabled = text.isNotBlank() && !vm.loading)
            }
        }
    }
}

@Composable
private fun StoriesScreen(vm: MurshidViewModel) {
    val stories = vm.featureData.array("stories")
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(selected!!.text("title")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(selected!!.text("content", selected!!.text("body")))
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("إغلاق") } }
        )
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { SectionTitle("استراحة خفيفة", "غيّر جو",
            "اختر عنوان قصة لقراءتها. قصص قصيرة تمنحك دفعة جديدة.") }
        if (vm.loading && stories.isEmpty()) item { LoadingPanel() }
        else if (stories.isEmpty()) item { EmptyPanel("لا توجد قصص منشورة حالياً") }
        items(stories) { story ->
            MurshidCard(Modifier.clickable { selected = story }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoStories, null, tint = MurshidBlue)
                    Spacer(Modifier.width(11.dp))
                    Text(story.text("title"), modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ChevronLeft, null)
                }
            }
        }
    }
}

@Composable
private fun PredictionDetailsScreen(vm: MurshidViewModel) {
    val data = vm.predictionsData
    val rows = data.array("items").ifEmpty { data.array("predictions") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("المرشحات الوزارية", vm.subject?.name ?: "المادة",
            "المحتوى الذي نشرته إدارة المنصة للمشتركين فقط.") }
        if (vm.dashboard?.subscribed != true) {
            item { PrimaryAction("عرض الاشتراكات") { vm.openFeature(Feature.SUBSCRIPTION) } }
        } else if (rows.isEmpty()) {
            item { EmptyPanel("لم تُنشر مرشحات لهذه المادة بعد") }
        } else {
            items(rows) { row -> InfoItem(row) }
        }
    }
}

@Composable
private fun QuestionRequestScreen(vm: MurshidViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            MurshidCard {
                Text("طلب المزيد من الأسئلة", fontWeight = FontWeight.Bold)
                Text("يمكن إرسال الطلب بعد إكمال أسئلة الموضوع.")
                PrimaryAction("إرسال طلب إضافة أسئلة", { vm.requestMoreQuestions() },
                    enabled = vm.topic != null && !vm.loading)
            }
        }
    }
}

@Composable
private fun GenericFeatureScreen(vm: MurshidViewModel) {
    val json = vm.featureData
    val title = vm.feature.title
    val fields = when (vm.feature) {
        Feature.NOTIFICATIONS -> listOf("items")
        Feature.FAVORITES -> listOf("items")
        Feature.REVIEW -> listOf("mistakes", "weak")
        Feature.INSIGHTS -> listOf("insights", "weekly")
        Feature.ACHIEVEMENTS -> listOf("achievements", "items")
        Feature.CONTEST -> listOf("leaderboard", "leaders", "items")
        Feature.STUDY_PLAN -> listOf("days", "items", "tasks")
        else -> listOf("items")
    }
    val rows = fields.flatMap { field -> json.array(field) }.distinctBy { it.toString() }
    val insights = json.optJSONObject("insights")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("متابعة الطالب", title, "البيانات محفوظة ومتصلة بحسابك.") }
        if (vm.loading) item { LoadingPanel() }
        if (vm.feature == Feature.INSIGHTS && insights != null) {
            item {
                MurshidCard {
                    Text("ملخص أدائك", fontWeight = FontWeight.Bold)
                    Text("المستوى العام: ${insights.num("accuracy", 0)}%")
                    Text("عدد الإجابات: ${insights.num("answered", 0)}")
                    LinearProgressIndicator(
                        progress = { insights.num("accuracy", 0).coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(), color = MurshidBlue
                    )
                }
            }
        }
        if (rows.isEmpty() && !vm.loading) item {
            EmptyPanel("لا توجد بيانات في هذا القسم حالياً",
                "ستظهر المعلومات تلقائياً عند توفرها في حسابك.")
        }
        items(rows) { row -> InfoItem(row) }
        item { SecondaryAction("تحديث البيانات", vm::loadFeature) }
    }
}

@Composable
private fun InfoItem(json: JSONObject) {
    val title = sequenceOf("title", "name", "question", "subject_name", "text")
        .map { json.text(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
    val description = sequenceOf("message", "description", "explanation", "body", "content")
        .map { json.text(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
    MurshidCard {
        Text(title.ifBlank { "عنصر محفوظ" }, fontWeight = FontWeight.Bold)
        if (description.isNotBlank() && description != title) Text(description)
        val date = json.text("created_at")
        if (date.isNotBlank()) Text(date, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
