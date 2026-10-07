package com.muriq.murshid

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

fun questionNeedsOptions(text: String): Boolean {
    val t = text.trim()
    return t.startsWith("اختر ") || t.startsWith("اختاري ") ||
        t.startsWith("أي مما") || t.startsWith("أي من الآتي") ||
        t.startsWith("أي من التالي") || t.startsWith("أي كلمة") ||
        t.startsWith("أي عبارة") || t.contains("الاختيار الصحيح") ||
        t.contains("الخيار الصحيح")
}
fun validQuestion(q: Json): Boolean {
    val type = q.str("type")
    val opts = q.arr("options").map { it.str("text").trim() }.filter { it.isNotBlank() }.distinct()
    val semantic = questionNeedsOptions(q.str("text"))
    if ((type == "mcq" || semantic) && opts.size < 2) return false
    if (type == "true_false" && opts.size < 2) return false
    if (type == "match" && q.arr("match_items").isEmpty()) return false
    if ((type == "calculation" || type == "genetics") && q.obj(type).arr("stages").isEmpty()) return false
    if (q.str("correct_answer").isBlank() && !q.str("text").contains("Match /")) return false
    return true
}

@Composable fun SubjectsPage(app: AppState) {
    PageColumn {
        Title("اختر المادة", "الأسئلة حسب منهج ${app.user.str("grade")}")
        app.subjects.forEach { subject ->
            Tile(subject.str("name"), "اضغط لعرض المواضيع", {
                Icon(Icons.Default.MenuBook, contentDescription=null, tint = MaterialTheme.colorScheme.primary)
            }) { app.push(Route("topics", subject.int("id"), subject.str("name"))) }
        }
        if (app.subjects.isEmpty()) Text("لم تُحمَّل مواد هذا الصف. افتح الرئيسية للتحديث.")
        OutlinedButton(onClick={app.job { app.bootstrap(); app.retry() }}, modifier=Modifier.fillMaxWidth()) { Text("تحديث المواد") }
    }
}

@Composable fun TopicsPage(app: AppState) {
    val route = app.route
    var data by remember(route) { mutableStateOf(emptyList<Json>()) }
    var loading by remember(route) { mutableStateOf(true) }
    var error by remember(route) { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(route, tick, app.refresh) {
        loading = true
        try {
            data = app.api.call("mobile/topics.php", query=mapOf("subject_id" to route.id.toString())).arr("topics")
            error = ""
        } catch(e:Exception) { error = e.message.orEmpty() }
        finally { loading=false }
    }
    PageColumn {
        Title("مواضيع ${route.title}", "اختر الموضوع للبدء أو متابعة الحل")
        LoadingOrError(loading, error) { tick++ }
        if(!loading && error.isEmpty()) {
            data.forEach { topic ->
                Tile(topic.str("name"), "${topic.int("count")} سؤال", {
                    Icon(Icons.Default.Quiz, null, tint=MaterialTheme.colorScheme.primary)
                }) { app.push(Route("exam", topic.int("id"), topic.str("name"), route.id)) }
            }
            if(data.isEmpty()) Text("لا توجد مواضيع منشورة لهذه المادة.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExamPage(app: AppState) {
    val route = app.route
    var data by remember(route) { mutableStateOf(emptyList<Json>()) }
    var current by remember(route) { mutableIntStateOf(0) }
    var loading by remember(route) { mutableStateOf(true) }
    var error by remember(route) { mutableStateOf("") }
    var type by remember(route) { mutableStateOf("all") }
    var state by remember(route) { mutableStateOf("all") }
    var year by remember(route) { mutableStateOf("all") }
    var round by remember(route) { mutableStateOf("all") }
    var difficulty by remember(route) { mutableStateOf("all") }
    var shuffle by remember(route) { mutableStateOf(false) }
    var filterDialog by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("wrong_answer") }
    var reportText by remember { mutableStateOf("") }
    var noteDialog by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var attempt by remember(route) { mutableIntStateOf(0) }
    LaunchedEffect(route, attempt) {
        loading = true
        error = ""
        try {
            val d = app.api.call("mobile/exam.php", query = buildMap {
                put("chapter_id", route.id.toString())
                if(type!="all") put("type",type)
                if(state!="all") put("state",state)
                if(year!="all") put("year",year)
                if(round!="all") put("round",round)
                if(difficulty!="all") put("difficulty",difficulty)
                if(shuffle) put("shuffle","1")
            })
            data = d.arr("questions").filter(::validQuestion)
            current = 0
            app.csrf = d.str("csrf").ifBlank { app.csrf }
        } catch(e:Exception) { error = e.message.orEmpty() }
        finally { loading=false }
    }
    val q = data.getOrNull(current)
    if(filterDialog) {
        AlertDialog(onDismissRequest={filterDialog=false}, title={Text("مرشحات الأسئلة")},
            text={
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("نوع السؤال")
                    ChoiceOptions(type, listOf("all" to "الكل", "mcq" to "اختيارات", "true_false" to "صح وخطأ",
                        "text" to "نصي", "fill" to "أكمل", "meaning" to "معاني",
                        "match" to "مطابقة", "calculation" to "مسألة", "genetics" to "وراثة")) { type=it }
                    Text("الحالة")
                    ChoiceOptions(state,listOf("all" to "الكل","unanswered" to "لم أجب",
                        "wrong" to "أخطأت سابقًا","correct" to "أجبت صحيحًا","favorite" to "المفضلة")) {state=it}
                    OutlinedTextField(if(year=="all") "" else year,{year=it.ifBlank { "all" }},label={Text("السنة — اختياري")})
                    OutlinedTextField(if(round=="all") "" else round,{round=it.ifBlank { "all" }},label={Text("الدور — اختياري")})
                    Text("الصعوبة")
                    ChoiceOptions(difficulty,listOf("all" to "الكل","easy" to "سهل","medium" to "متوسط","hard" to "صعب")) {difficulty=it}
                    Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(shuffle,{shuffle=it}); Text("ترتيب عشوائي") }
                }
            },
            confirmButton={TextButton(onClick={filterDialog=false; attempt++}) {Text("تطبيق")}},
            dismissButton={TextButton(onClick={type="all";state="all";year="all";round="all";difficulty="all";shuffle=false; filterDialog=false;attempt++}){Text("إعادة ضبط")}}
        )
    }
    if(showReport && q!=null) {
        AlertDialog(onDismissRequest={showReport=false}, title={Text("الإبلاغ عن السؤال")},
            text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                ChoiceOptions(reportReason,listOf("wrong_answer" to "الإجابة خاطئة",
                    "unclear" to "السؤال غير واضح","duplicate" to "السؤال مكرر",
                    "source" to "مشكلة بالمصدر","other" to "سبب آخر")){reportReason=it}
                OutlinedTextField(reportText,{reportText=it},label={Text("ملاحظتك")},minLines=2)
            }},
            confirmButton={TextButton(onClick={showReport=false;app.job {
                app.api.call("api/question-tools.php",data=payload("csrf" to app.csrf,
                    "question_id" to q.int("id"), "action" to "report",
                    "reason" to reportReason, "message" to reportText))
                app.show("تم إرسال البلاغ للمراجعة.")
            }}){Text("إرسال")}},
            dismissButton={TextButton(onClick={showReport=false}){Text("إلغاء")}}
        )
    }
    if(noteDialog && q!=null) {
        AlertDialog(onDismissRequest={noteDialog=false}, title={Text("ملاحظتي الخاصة")},
            text={OutlinedTextField(note,{note=it},minLines=3, label={Text("لن تظهر للآخرين")})},
            confirmButton={TextButton(onClick={noteDialog=false;app.job {
                app.api.call("mobile/question-notes.php",data=payload("csrf" to app.csrf,"question_id" to q.int("id"),"note" to note))
                app.show("تم حفظ الملاحظة.")
            }}){Text("حفظ")}},
            dismissButton={TextButton(onClick={noteDialog=false}){Text("إلغاء")}}
        )
    }
    Column(Modifier.fillMaxSize()) {
        if(q!=null) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                Text("سؤال ${current+1} من ${data.size}", fontWeight=FontWeight.Bold)
                Row {
                    IconButton(onClick={filterDialog=true}) { Icon(Icons.Default.Tune,"المرشحات") }
                    IconButton(onClick={showReport=true}) { Icon(Icons.Default.Report,"إبلاغ") }
                    IconButton(onClick={app.job {
                        note = app.api.call("mobile/question-notes.php",query=mapOf("question_id" to q.int("id").toString())).str("note")
                        noteDialog = true
                    }}) { Icon(Icons.Default.Notes,"ملاحظتي") }
                    IconButton(onClick={app.job {
                        val was=q.bool("favorite")
                        app.api.call("api/question-tools.php",data=payload("csrf" to app.csrf,
                            "question_id" to q.int("id"),"action" to if(was)"unfavorite" else "favorite"))
                        q.put("favorite",!was)
                        data=data.toList()
                        app.show(if(was)"أزيل من المفضلة" else "أضيف إلى المفضلة")
                    }}) { Icon(if(q.bool("favorite")) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "مفضلة") }
                }
            }
            LinearProgressIndicator(progress={ (current+1f)/data.size.coerceAtLeast(1) },
                modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp))
        }
        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            error.isNotBlank() -> PageColumn { LoadingOrError(false,error){attempt++} }
            data.isEmpty() -> PageColumn {
                Title("لا توجد أسئلة مطابقة", "يمكنك تغيير المرشحات أو فتح موضوع آخر")
                Action("المرشحات") {filterDialog=true}
            }
            else -> {
                val currentQuestion = data[current]
                val id=currentQuestion.int("id")
                var entry by remember(id) { mutableStateOf(currentQuestion.obj("answer").str("text")) }
                var submitted by remember(id) { mutableStateOf(currentQuestion.bool("submitted")) }
                var grade by remember(id) { mutableStateOf(JSONObject()) }
                val options=currentQuestion.arr("options").map { it.str("text") }.filter { it.isNotBlank() }
                val typeValue=currentQuestion.str("type")
                val isChoice=options.isNotEmpty()
                val stages=currentQuestion.obj(if(typeValue=="genetics")"genetics" else "calculation").arr("stages")
                val items=currentQuestion.arr("match_items")
                val englishMatch=currentQuestion.obj("english_match")
                val englishLeft=englishMatch.obj("left")
                val englishRight=englishMatch.obj("right")
                val specialMap=remember(id){ mutableStateMapOf<String,String>() }
                val typeMap=remember(id){ mutableStateMapOf<String,String>() }
                fun encoded(): String {
                    return when {
                        typeValue=="match" && items.isNotEmpty() -> {
                            val values=JSONObject()
                            items.forEach { m -> values.put(m.int("id").toString(),
                                payload("type" to typeMap[m.int("id").toString()].orEmpty(),"reason" to specialMap[m.int("id").toString()].orEmpty())) }
                            payload("items" to values).toString()
                        }
                        englishLeft.length()>0 && englishRight.length()>0 -> {
                            englishLeft.keys().asSequence().sorted().mapNotNull { key ->
                                specialMap[key]?.takeIf{it.isNotBlank()}?.let{"$key-$it"}
                            }.joinToString(", ")
                        }
                        stages.isNotEmpty() -> payload("stages" to JSONObject().apply {
                            specialMap.forEach { (k,v)->put(k,v) }
                        }).toString()
                        else -> entry.trim()
                    }
                }
                val complete = when {
                    submitted -> true
                    typeValue=="match" && items.isNotEmpty() -> items.all {
                        val key=it.int("id").toString()
                        !specialMap[key].isNullOrBlank() && !typeMap[key].isNullOrBlank()
                    }
                    englishLeft.length()>0 -> englishLeft.keys().asSequence().all { !specialMap[it].isNullOrBlank() }
                    stages.isNotEmpty() -> stages.filter { it.str("kind")!="info" && !it.bool("ungraded") }
                        .all { !specialMap[it.str("key")].isNullOrBlank() }
                    else -> entry.isNotBlank()
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Panel {
                        Text("وزاري",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
                        Text(currentQuestion.str("text"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                        val meta=listOf(currentQuestion.str("exam_year"),currentQuestion.str("exam_round"))
                            .filter{it.isNotBlank()}.joinToString(" • ")
                        if(meta.isNotBlank())Text(meta,style=MaterialTheme.typography.labelMedium)
                    }
                    Panel {
                        Text("إجابتك",fontWeight=FontWeight.Bold)
                        when {
                            isChoice -> ChoiceOptions(entry,options.map {it to it},enabled=!submitted){ selected ->
                                entry=selected
                                app.job {
                                    val result=app.api.call("api/progress.php",data=payload("csrf" to app.csrf,
                                        "chapter_id" to route.id,"question_index" to current+1,
                                        "completed" to (current==data.lastIndex),
                                        "answers" to payload(id.toString() to payload("text" to selected,"selected_text" to selected,"submitted" to true))))
                                    grade=result.obj("grades").obj(id.toString())
                                    if(grade.length()>0) submitted=true
                                }
                            }
                            typeValue=="match" && items.isNotEmpty() -> items.forEach { m->
                                val key=m.int("id").toString()
                                Text(m.str("mad_word"),fontWeight=FontWeight.SemiBold)
                                val types=listOf("مد طبيعي","مد بدل","مد متصل","مد منفصل","مد لازم","مد عارض للسكون","مد لين")
                                ChoiceOptions(typeMap[key].orEmpty(),types.map{it to it},enabled=!submitted){typeMap[key]=it}
                                OutlinedTextField(specialMap[key].orEmpty(),{specialMap[key]=it},enabled=!submitted,
                                    label={Text("سبب الاختيار")},modifier=Modifier.fillMaxWidth())
                            }
                            englishLeft.length()>0 && englishRight.length()>0 -> englishLeft.keys().asSequence().sorted().toList().forEach{ key->
                                Text("$key — ${englishLeft.optString(key)}",fontWeight=FontWeight.Bold)
                                ChoiceOptions(specialMap[key].orEmpty(),englishRight.keys().asSequence().sorted().map {
                                    it to "$it — ${englishRight.optString(it)}"
                                }.toList(),enabled=!submitted){specialMap[key]=it}
                            }
                            stages.isNotEmpty() -> stages.forEach { stage ->
                                if(stage.str("label").isNotBlank()) Text(stage.str("label"),fontWeight=FontWeight.SemiBold)
                                val key=stage.str("key")
                                if(stage.str("kind")!="info" && !stage.bool("ungraded")) {
                                    OutlinedTextField(specialMap[key].orEmpty(),{specialMap[key]=it},
                                        label={Text(stage.str("prompt").ifBlank {stage.str("label").ifBlank {"الإجابة"}})},
                                        modifier=Modifier.fillMaxWidth(),enabled=!submitted)
                                }
                            }
                            else -> OutlinedTextField(entry,{entry=it}, enabled=!submitted,
                                label={Text("اكتب الإجابة")},modifier=Modifier.fillMaxWidth(),minLines=3)
                        }
                        if(!submitted && !isChoice) Action("إرسال الإجابة",enabled=complete && !app.busy) {
                            val answer=encoded()
                            app.job {
                                val result=app.api.call("api/progress.php",data=payload("csrf" to app.csrf,
                                    "chapter_id" to route.id,"question_index" to current+1,
                                    "completed" to (current==data.lastIndex),
                                    "answers" to payload(id.toString() to payload("text" to answer,"selected_text" to answer,"submitted" to true))))
                                grade=result.obj("grades").obj(id.toString())
                                if(grade.length()>0) submitted=true
                            }
                        }
                    }
                    if(submitted) Panel {
                        val correct=if(grade.length()>0)grade.bool("is_correct") else currentQuestion.obj("answer").bool("correct")
                        Text(if(correct)"إجابة صحيحة ✓" else "راجع الإجابة الصحيحة",
                            color=if(correct) androidx.compose.ui.graphics.Color(0xFF15803D) else MaterialTheme.colorScheme.error,
                            fontWeight=FontWeight.Bold)
                        val answer=grade.str("correct_answer").ifBlank { currentQuestion.str("correct_answer") }
                        Text("الإجابة النموذجية: $answer")
                        val explanation=grade.str("explanation").ifBlank {currentQuestion.str("explanation")}
                        if(explanation.isNotBlank())Text(explanation)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick={ if(current>0)current-- },enabled=current>0,modifier=Modifier.weight(1f)) {Text("السابق")}
                    Button(onClick={
                        if(current<data.lastIndex) current++ else app.push(Route("more",route.id,route.title,route.parent))
                    },enabled=submitted,modifier=Modifier.weight(1f)) {Text(if(current<data.lastIndex)"التالي" else "طلب أسئلة أكثر")}
                }
            }
        }
    }
}

@Composable fun GenericListPage(app:AppState,path:String,listKey:String,emptyMessage:String) {
    var rows by remember(path){mutableStateOf(emptyList<Json>())}
    var loading by remember(path){mutableStateOf(true)}
    var error by remember(path){mutableStateOf("")}
    var tick by remember{mutableIntStateOf(0)}
    LaunchedEffect(path,tick,app.refresh) {
        loading=true
        try { rows=app.api.call(path).arr(listKey);error="" } catch(e:Exception){error=e.message.orEmpty()}
        finally {loading=false}
    }
    PageColumn {
        LoadingOrError(loading,error){tick++}
        if(!loading && error.isBlank()) {
            if(rows.isEmpty()) Text(emptyMessage)
            rows.forEach { row ->
                Panel {
                    Text(row.str("title").ifBlank {row.str("question_text").ifBlank {row.str("name")}},
                        fontWeight=FontWeight.Bold)
                    listOf("chapter_name","subject_name","description","message","created_at","score","answer_text")
                        .forEach{ k -> if(row.str(k).isNotBlank())Text(row.str(k),style=MaterialTheme.typography.bodySmall)}
                }
            }
        }
    }
}

@Composable fun SearchPage(app:AppState) {
    var query by remember{mutableStateOf("")}
    var items by remember{mutableStateOf(emptyList<Json>())}
    var loading by remember{mutableStateOf(false)}
    PageColumn {
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),
            leadingIcon={Icon(Icons.Default.Search,null)},label={Text("ابحث عن سؤال أو موضوع")})
        Action("بحث",enabled=query.trim().length>=2 && !loading){
            app.job {
                loading=true
                try {items=app.api.call("api/search.php",query=mapOf("q" to query.trim())).arr("results")}
                finally {loading=false}
            }
        }
        if(items.isEmpty() && !loading) Text("ستظهر نتائج البحث هنا.")
        items.forEach { row ->
            val topicId = Regex("chapter_id=([0-9]+)").find(row.str("url"))?.groupValues?.getOrNull(1)?.toIntOrNull()
            Tile(row.str("title"), row.str("meta"), {
                Icon(Icons.Default.FindInPage, null, tint = MaterialTheme.colorScheme.primary)
            }) {
                if(topicId != null) app.push(Route("exam", topicId, row.str("meta")))
                else app.show("تعذر فتح هذا السؤال.")
            }
        }
    }
}
