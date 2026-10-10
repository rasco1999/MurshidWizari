package com.muriq.murshid.iosparity

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muriq.murshid.iosparity.data.*
import com.muriq.murshid.iosparity.ui.*
import org.json.JSONArray
import org.json.JSONObject

private val maddKinds = listOf(
    "مد طبيعي", "مد بدل", "مد متصل", "مد منفصل",
    "مد لازم", "مد عارض للسكون", "مد لين"
)

@Composable
fun ExamScreen(vm: MurshidViewModel) {
    val questions = vm.questions
    if (questions.isEmpty()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            EmptyPanel("لا توجد أسئلة متاحة", "قد يكون الرصيد المجاني انتهى أو لم تُنشر أسئلة لهذا الموضوع.")
            PrimaryAction("الاشتراك") { vm.openFeature(Feature.SUBSCRIPTION) }
        }
        return
    }
    val q = questions.getOrNull(vm.questionIndex) ?: return
    var text by remember(q.id) { mutableStateOf("") }
    val matchDraft = remember(q.id) { mutableStateMapOf<String, String>() }
    val englishDraft = remember(q.id) { mutableStateMapOf<String, String>() }
    val stagesDraft = remember(q.id) { mutableStateMapOf<String, String>() }
    var showReport by remember { mutableStateOf(false) }
    var reportMessage by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf("wrong_answer") }
    var showNote by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var showResults by remember { mutableStateOf(false) }
    val result = vm.answerResult[q.id]
    val answered = q.submitted || result != null
    val correct = result?.optBoolean("is_correct") ?: q.savedAnswer?.optBoolean("correct")
    val options = if (q.options.isNotEmpty()) q.options else if (q.type == "true_false")
        listOf(AnswerOption("1", "صح"), AnswerOption("2", "خطأ")) else emptyList()

    if (showReport) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("الإبلاغ عن السؤال") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("wrong_answer" to "الإجابة غير صحيحة", "incorrect_question" to "خطأ في صياغة السؤال",
                        "other" to "ملاحظة أخرى").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { reportReason = value }) {
                            RadioButton(reportReason == value, onClick = { reportReason = value })
                            Text(label)
                        }
                    }
                    OutlinedTextField(reportMessage, { reportMessage = it },
                        label = { Text("تفاصيل البلاغ (اختياري)") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.reportQuestion(q.id, reportReason, reportMessage)
                    showReport = false; reportMessage = ""
                }) { Text("إرسال") }
            },
            dismissButton = { TextButton(onClick = { showReport = false }) { Text("إلغاء") } }
        )
    }
    if (showNote) {
        AlertDialog(
            onDismissRequest = { showNote = false },
            title = { Text("ملاحظتي الخاصة") },
            text = { OutlinedTextField(note, { note = it },
                label = { Text("اكتب ملاحظتك عن السؤال") }, minLines = 3) },
            confirmButton = {
                TextButton(onClick = { vm.saveNote(q.id, note); showNote = false }) {
                    Text("حفظ")
                }
            },
            dismissButton = { TextButton(onClick = { showNote = false }) { Text("إلغاء") } }
        )
    }
    if (showResults) {
        QuizSummary(vm, onReview = { showResults = false; vm.reviewExam() })
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallPill("السؤال ${vm.questionIndex + 1} من ${questions.size}")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.toggleFavorite(q.id) }) {
                    Icon(if (q.raw.optBoolean("favorite")) Icons.Default.Favorite
                        else Icons.Default.FavoriteBorder,
                        contentDescription = "المفضلة",
                        tint = if (q.raw.optBoolean("favorite")) Color.Red else MurshidBlue)
                }
                IconButton(onClick = { showReport = true }) {
                    Icon(Icons.Default.Flag, contentDescription = "الإبلاغ")
                }
                IconButton(onClick = { vm.loadNote(q.id) { value -> note = value; showNote = true } }) {
                    Icon(Icons.Default.NoteAlt, contentDescription = "ملاحظة")
                }
            }
        }
        item {
            LinearProgressIndicator(
                progress = { (vm.questionIndex + 1f) / questions.size },
                modifier = Modifier.fillMaxWidth(),
                color = MurshidBlue
            )
        }
        item {
            MurshidCard {
                Row {
                    Text("وزاري", fontWeight = FontWeight.Bold, color = MurshidBlue)
                    Spacer(Modifier.width(9.dp))
                    Text(q.type.ifBlank { "سؤال" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(q.text, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                if (q.raw.text("meaning_word").isNotBlank()) {
                    Text("المفردة: ${q.raw.text("meaning_word")}", color = MurshidBlue)
                }
            }
        }
        item {
            MurshidCard {
                Text("إجابتك", fontWeight = FontWeight.Bold)
                when {
                    options.isNotEmpty() -> {
                        options.forEachIndexed { i, option ->
                            val wasSelected = q.savedAnswer?.text("text") == option.title
                            val correctChoice = answered && option.title.trim() ==
                                (result?.text("correct_answer", q.correctAnswer) ?: q.correctAnswer).trim()
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = !answered && !vm.loading) {
                                        vm.sendAnswer(option.title, option)
                                    },
                                shape = RoundedCornerShape(15.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (correctChoice) Color(0xFFE6F6EB)
                                    else if (wasSelected && answered && correct == false) Color(0xFFFFECEC)
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(32.dp).background(
                                            MurshidBlue.copy(alpha = 0.12f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(('أ' + i).toString(), fontWeight = FontWeight.Bold,
                                            color = MurshidBlue)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(option.title, modifier = Modifier.weight(1f))
                                    if (correctChoice) Icon(Icons.Default.CheckCircle, null,
                                        tint = Color(0xFF2B8F49))
                                }
                            }
                        }
                    }
                    answered -> {
                        Text(q.savedAnswer?.text("text", "تم حفظ الإجابة") ?: "تم حفظ الإجابة.")
                    }
                    q.raw.optJSONObject("english_match") != null ->
                        EnglishMatchingEditor(q.raw.optJSONObject("english_match")!!, englishDraft)
                    q.type == "match" && (q.raw.optJSONArray("match_items")?.length() ?: 0) > 0 ->
                        TajweedMatchingEditor(q.raw.optJSONArray("match_items")!!, matchDraft)
                    questionStages(q).isNotEmpty() ->
                        MultiStageEditor(questionStages(q), stagesDraft)
                    else -> {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it },
                            placeholder = { Text("اكتب الإجابة هنا…") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4, maxLines = 8
                        )
                    }
                }
                if (!answered && options.isEmpty()) {
                    val encoded = when {
                        q.raw.optJSONObject("english_match") != null ->
                            englishDraft.entries.sortedBy { it.key.toIntOrNull() ?: 0 }
                                .filter { it.value.isNotBlank() }
                                .joinToString(", ") { "${it.key}-${it.value}" }
                        q.type == "match" && (q.raw.optJSONArray("match_items")?.length() ?: 0) > 0 -> {
                            val items = JSONObject()
                            q.raw.optJSONArray("match_items")?.objects()?.forEach { item ->
                                val id = item.num("id").toString()
                                items.put(id, JSONObject()
                                    .put("type", matchDraft["${id}_type"].orEmpty())
                                    .put("reason", matchDraft["${id}_reason"].orEmpty()))
                            }
                            JSONObject().put("items", items).toString()
                        }
                        questionStages(q).isNotEmpty() ->
                            JSONObject().put("stages", JSONObject().apply {
                                questionStages(q).forEach { stage ->
                                    val key = stage.text("key")
                                    if (key.isNotBlank()) put(key, stagesDraft[key].orEmpty())
                                }
                            }).toString()
                        else -> text.trim()
                    }
                    PrimaryAction(if (vm.loading) "جارٍ تصحيح الإجابة…" else "إرسال الإجابة",
                        onClick = { vm.sendAnswer(encoded) },
                        enabled = encoded.isNotBlank() && !vm.loading && !vm.uncertainSubmission)
                }
            }
        }
        if (vm.uncertainSubmission) item {
            MurshidCard {
                Text("تعذر التأكد من وصول الإجابة. لن نعيد إرسالها تلقائياً.",
                    color = MaterialTheme.colorScheme.error)
                PrimaryAction("تحديث السؤال من الخادم") { vm.reloadExamAfterUncertain() }
            }
        }
        if (answered) item {
            MurshidCard {
                Text(if (correct == true) "✓ إجابة صحيحة"
                    else "تم حفظ الإجابة – راجع التوضيح",
                    fontWeight = FontWeight.Bold,
                    color = if (correct == true) Color(0xFF248844)
                    else MaterialTheme.colorScheme.primary)
                val correctAnswer = result?.text("correct_answer", q.correctAnswer) ?: q.correctAnswer
                if (correctAnswer.isNotBlank()) {
                    Text("الإجابة النموذجية", fontWeight = FontWeight.SemiBold)
                    Text(correctAnswer)
                }
                if (q.explanation.isNotBlank()) {
                    HorizontalDivider()
                    Text("الشرح", fontWeight = FontWeight.SemiBold)
                    Text(q.explanation)
                }
            }
        }
        item {
            if (answered) {
                PrimaryAction(if (vm.questionIndex == questions.lastIndex)
                    "عرض نتيجة الاختبار" else "السؤال التالي", {
                    if (vm.questionIndex == questions.lastIndex) showResults = true
                    else vm.nextQuestion()
                })
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun questionStages(q: QuizQuestion): List<JSONObject> {
    val calculation = q.raw.optJSONObject("calculation")
    val genetics = q.raw.optJSONObject("genetics")
    return (calculation?.optJSONArray("stages") ?: genetics?.optJSONArray("stages") ?: JSONArray()).objects()
}

@Composable
private fun TajweedMatchingEditor(items: JSONArray, values: MutableMap<String, String>) {
    items.objects().forEach { item ->
        val id = item.num("id").toString()
        Text(item.text("word", item.text("text")), fontWeight = FontWeight.Bold)
        var expanded by remember(id) { mutableStateOf(false) }
        Box {
            SecondaryAction(values["${id}_type"].orEmpty().ifEmpty { "اختر نوع المد" },
                { expanded = true })
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                maddKinds.forEach { kind ->
                    DropdownMenuItem(text = { Text(kind) },
                        onClick = { values["${id}_type"] = kind; expanded = false })
                }
            }
        }
        OutlinedTextField(values["${id}_reason"].orEmpty(),
            { values["${id}_reason"] = it }, label = { Text("سبب المد") },
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EnglishMatchingEditor(definition: JSONObject, values: MutableMap<String, String>) {
    val left = definition.optJSONObject("left") ?: JSONObject()
    val right = definition.optJSONObject("right") ?: JSONObject()
    left.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 0 }.forEach { key ->
        Text("${key}. ${left.optString(key)}", fontWeight = FontWeight.Bold)
        var expanded by remember(key) { mutableStateOf(false) }
        Box {
            SecondaryAction(values[key].orEmpty().ifEmpty { "اختر المطابقة" }) { expanded = true }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                right.keys().asSequence().toList().sorted().forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option} — ${right.optString(option)}") },
                        onClick = { values[key] = option; expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiStageEditor(stages: List<JSONObject>, values: MutableMap<String, String>) {
    stages.forEach { stage ->
        val key = stage.text("key")
        Text(stage.text("label"), fontWeight = FontWeight.SemiBold)
        val kind = stage.text("kind")
        when {
            kind == "info" -> Text(stage.text("value"),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            kind == "choice" && (stage.optJSONArray("options")?.length() ?: 0) > 0 -> {
                var expanded by remember(key) { mutableStateOf(false) }
                Box {
                    SecondaryAction(values[key].orEmpty().ifEmpty { "اختر" }) { expanded = true }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        stage.optJSONArray("options")?.objects()?.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.text("label")) },
                                onClick = {
                                    values[key] = option.text("value")
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            else -> OutlinedTextField(values[key].orEmpty(), { values[key] = it },
                label = { Text(stage.text("placeholder", "الإجابة")) },
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun QuizSummary(vm: MurshidViewModel, onReview: () -> Unit) {
    val total = vm.questions.size
    val answered = vm.questions.count { it.submitted }
    val correct = vm.questions.count { q ->
        vm.answerResult[q.id]?.optBoolean("is_correct") ?: q.savedAnswer?.optBoolean("correct") ?: false
    }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroPanel("أحسنت، أكملت الاختبار!", vm.topic?.name.orEmpty()) }
        item {
            MurshidCard {
                Text("ملخص نتيجتك", fontWeight = FontWeight.Bold)
                Text("الإجابات: $answered من $total")
                Text("الصحيحة: $correct")
                Text("الدقة: ${if (answered > 0) (correct * 100 / answered) else 0}%")
            }
        }
        item { PrimaryAction("مراجعة الأسئلة", onReview) }
        item { SecondaryAction("العودة إلى المواضيع", vm::back) }
    }
}
