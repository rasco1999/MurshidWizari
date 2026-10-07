package com.muriq.murshid

import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

@Composable fun AccountPage(app: AppState) {
    var account by remember { mutableStateOf(JSONObject()) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var newName by remember { mutableStateOf("") }
    var rename by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        try {
            account = app.api.call("mobile/account.php")
            val link = account.str("avatar_url")
            photo = if(link.startsWith("https://")) app.api.avatarImage(link) else null
        } catch(e:Exception) { app.show(e.message.orEmpty()) }
        finally {loading=false}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) app.job {
            val data = app.api.preparedPhoto(uri)
            val result = app.api.uploadAvatar(data, app.csrf)
            app.show(result.readMessage("تم تغيير الصورة فورًا."))
            tick++
        }
    }
    if(rename) AlertDialog(onDismissRequest={rename=false},title={Text("تغيير الاسم")},
        text={OutlinedTextField(newName,{newName=it},label={Text("الاسم الثلاثي")})},
        confirmButton={TextButton(onClick={rename=false;app.job {
            app.api.call("mobile/account.php",data=payload("csrf" to app.csrf,"action" to "change_name","name" to newName))
            app.bootstrap();tick++;app.show("تم تغيير الاسم.")
        }}){Text("حفظ")}},
        dismissButton={TextButton(onClick={rename=false}){Text("إلغاء")}})
    PageColumn {
        Title("الملف الشخصي", "متزامن مع حسابك في الموقع")
        Panel {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                if(photo!=null) Image(photo!!.asImageBitmap(),null, modifier=Modifier.size(88.dp).clip(CircleShape))
                else Icon(Icons.Default.AccountCircle,null,modifier=Modifier.size(88.dp),tint=MaterialTheme.colorScheme.primary)
                Column {
                    Text(app.user.str("name"),fontWeight=FontWeight.Bold)
                    Text(app.user.str("grade"),style=MaterialTheme.typography.bodySmall)
                    Text("صورة الحساب تتغير فورًا",style=MaterialTheme.typography.labelSmall)
                }
            }
            Action(if(app.busy)"جارٍ رفع الصورة..." else "تغيير صورة الحساب",enabled=!app.busy,
                icon={Icon(Icons.Default.AddAPhoto,null)}){launcher.launch("image/*")}
        }
        Tile("الاسم الشخصي","تغيير لمرة واحدة حسب سياسة المنصة",{
            Icon(Icons.Default.Edit,null,tint=MaterialTheme.colorScheme.primary)
        }){newName=app.user.str("name");rename=true}
        Tile("الاشتراك","اعرف رصيدك وباقاتك",{Icon(Icons.Default.WorkspacePremium,null)}){app.push(Route("subscription"))}
        Tile("الإشعارات","آخر الرسائل",{Icon(Icons.Default.Notifications,null)}){app.push(Route("notifications"))}
        Tile("خدمة العملاء","راسل الإدارة",{Icon(Icons.Default.SupportAgent,null)}){app.push(Route("support"))}
        Tile("إنجازاتي","المستوى والنقاط",{Icon(Icons.Default.EmojiEvents,null)}){app.push(Route("achievements"))}
        Action("تحديث الحساب"){tick++;app.job{app.bootstrap()}}
        OutlinedButton(onClick={app.signOut()},modifier=Modifier.fillMaxWidth()){Text("تسجيل الخروج")}
        Text("Android 1.0 • منصة المرشد الوزاري",style=MaterialTheme.typography.labelSmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun SubscriptionPage(app:AppState,openUrl:(String)->Unit) {
    val context= LocalContext.current
    var selected by remember { mutableStateOf("") }
    PageColumn {
        Title("الاشتراك", "تفعيل حسابك يرتبط بالموقع والتطبيق معًا")
        Panel {
            Text(if(app.session.bool("subscribed"))"الاشتراك فعّال" else "لديك ${app.session.int("free_remaining")} سؤال مجاني")
            if(app.session.bool("subscribed"))Text("كل الأسئلة متاحة")
        }
        app.session.arr("plans").forEach { plan ->
            val key=plan.str("key")
            Tile(plan.str("label"),"${plan.int("amount")} د.ع",{
                Icon(if(selected==key)Icons.Default.CheckCircle else Icons.Default.Payments,null,tint=MaterialTheme.colorScheme.primary)
            }){selected=key}
        }
        Text("طريقة الدفع تُفتح خارجيًا كما في الموقع.",style=MaterialTheme.typography.bodySmall)
        Action("الانتقال إلى صفحة الاشتراك") {
            openUrl("$SERVER/student/subscription.php")
        }
        OutlinedButton(onClick={app.job{app.bootstrap();app.show("تم تحديث حالة الاشتراك.")}},
            modifier=Modifier.fillMaxWidth()){Text("التحقق من التفعيل")}
    }
}

@Composable fun CustomExamBuilder(app:AppState) {
    var sid by remember { mutableIntStateOf(0) }
    var subject by remember { mutableStateOf("") }
    var count by remember { mutableFloatStateOf(20f) }
    var type by remember { mutableStateOf("all") }
    var state by remember { mutableStateOf("all") }
    var minutes by remember { mutableStateOf("0") }
    PageColumn {
        Title("أنشئ اختبارك", "التصحيح يظهر بعد إنهاء المحاولة")
        Panel {
            Text("المادة",fontWeight=FontWeight.Bold)
            ChoiceOptions(subject,app.subjects.map{it.int("id").toString() to it.str("name")}){subject=it}
            Text("عدد الأسئلة: ${count.toInt()}",fontWeight=FontWeight.Bold)
            Slider(count,{count=it},valueRange=5f..50f,steps=8)
            Text("النوع",fontWeight=FontWeight.Bold)
            ChoiceOptions(type,listOf("all" to "الكل","mcq" to "اختيارات",
                "true_false" to "صح / خطأ","text" to "نصي","fill" to "أكمل","meaning" to "معاني")) {type=it}
            Text("حالة السؤال",fontWeight=FontWeight.Bold)
            ChoiceOptions(state,listOf("all" to "الكل","unanswered" to "لم أجب","wrong" to "أخطائي",
                "favorite" to "المفضلة")) {state=it}
            Text("المدة",fontWeight=FontWeight.Bold)
            ChoiceOptions(minutes,listOf("0" to "بدون مؤقت","15" to "15 دقيقة","30" to "30 دقيقة","60" to "ساعة")){minutes=it}
        }
        Action("بدء الاختبار",enabled=subject.isNotBlank() && !app.busy) {
            app.job {
                val d=app.api.call("mobile/practice.php",data=payload("csrf" to app.csrf,"action" to "create",
                    "subject_id" to subject.toInt(),"count" to count.toInt(),"type" to type,"state" to state,
                    "minutes" to minutes.toInt()))
                sid=d.int("session_id")
                if(sid>0)app.push(Route("practice",sid))
            }
        }
    }
}

@Composable fun CustomExamPage(app:AppState) {
    val route=app.route
    var questions by remember(route){mutableStateOf(emptyList<Json>())}
    var current by remember(route){mutableIntStateOf(0)}
    val answers=remember(route){mutableStateMapOf<Int,String>()}
    var loading by remember(route){mutableStateOf(true)}
    var result by remember(route){mutableStateOf<Json?>(null)}
    var remaining by remember(route){mutableIntStateOf(0)}
    LaunchedEffect(route) {
        try {
            val d=app.api.call("mobile/practice.php",query=mapOf("session_id" to route.id.toString()))
            questions=d.arr("questions")
            remaining=d.obj("session").int("duration_seconds")
        }catch(e:Exception){app.show(e.message.orEmpty())}
        finally {loading=false}
    }
    LaunchedEffect(remaining,loading,result) {
        if(!loading && result==null && remaining>0) {
            kotlinx.coroutines.delay(1000L)
            remaining--
            if(remaining==0) {
                val map=JSONObject();answers.forEach{(k,v)->map.put(k.toString(),v)}
                try {result=app.api.call("mobile/practice.php",data=payload(
                    "csrf" to app.csrf,"action" to "finish","session_id" to route.id,"answers" to map))}
                catch(e:Exception){app.show(e.message.orEmpty())}
            }
        }
    }
    if(loading)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
    else if(result!=null)PageColumn {
        Title("نتيجة الاختبار", "تم تصحيح إجاباتك")
        Panel {
            Text("${result!!.int("correct")} صحيحة من ${result!!.int("total")}",fontWeight=FontWeight.Bold)
            Text("${result!!.optDouble("score",0.0).toInt()}%",style=MaterialTheme.typography.headlineLarge)
        }
        result!!.arr("review").filter{!it.bool("is_correct")}.forEach{r->Panel {
            Text(r.str("question_text"),fontWeight=FontWeight.Bold)
            Text("إجابتك: ${r.str("submitted_answer")}")
            Text("الصحيح: ${r.str("correct_answer")}")
            Text(r.str("explanation"))
        }}
        Action("إنشاء اختبار جديد"){app.push(Route("builder"))}
    } else if(questions.isEmpty())PageColumn{Text("لا توجد أسئلة لهذا الاختبار.")}
    else {
        val q=questions[current]
        val id=q.int("id")
        val options=q.arr("options").map{it.str("text")}
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("${current+1} / ${questions.size}",fontWeight=FontWeight.Bold)
                if(remaining>0)Text("الوقت: ${remaining/60}:${(remaining%60).toString().padStart(2,'0')}")
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Panel {Text(q.str("text"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
                Panel {
                    if(options.isNotEmpty()) ChoiceOptions(answers[id].orEmpty(),options.map{it to it}) {answers[id]=it}
                    else OutlinedTextField(answers[id].orEmpty(),{answers[id]=it},label={Text("الإجابة")},
                        modifier=Modifier.fillMaxWidth(),minLines=3)
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                OutlinedButton(onClick={current--},enabled=current>0,modifier=Modifier.weight(1f)){Text("السابق")}
                Button(onClick={
                    if(current<questions.lastIndex)current++ else app.job {
                        val map=JSONObject(); answers.forEach{(k,v)->map.put(k.toString(),v)}
                        result=app.api.call("mobile/practice.php",data=payload(
                            "csrf" to app.csrf,"action" to "finish","session_id" to route.id,"answers" to map))
                        app.bootstrap()
                    }
                }, enabled=!answers[id].isNullOrBlank() && !app.busy,modifier=Modifier.weight(1f)) {
                    Text(if(current<questions.lastIndex)"التالي" else "إنهاء وتصحيح")
                }
            }
        }
    }
}

@Composable fun DailyChallengePage(app:AppState) {
    var questions by remember{mutableStateOf(emptyList<Json>())}
    val answers=remember{mutableStateMapOf<Int,String>()}
    var completed by remember{mutableStateOf(false)}
    var available by remember{mutableStateOf(true)}
    var score by remember{mutableIntStateOf(0)}
    var total by remember{mutableIntStateOf(0)}
    var loading by remember{mutableStateOf(true)}
    LaunchedEffect(Unit) {
        try {
            val d=app.api.call("mobile/challenge.php")
            questions=d.arr("questions")
            completed=d.bool("done")
            available=d.bool("available")
            score=d.int("score")
            total=d.int("total")
        }catch(e:Exception){app.show(e.message.orEmpty())}
        finally {loading=false}
    }
    PageColumn {
        Title("تحدي اليوم", "أجب عن جميع الأسئلة مرة واحدة")
        if(loading)CircularProgressIndicator()
        else if(!available)Text("لا يوجد تحدٍّ متاح اليوم.")
        else if(completed)Panel {Metric("نتيجتك","$score / $total")}
        else {
            questions.forEachIndexed { idx,q ->
                val id=q.int("id")
                val options=q.arr("options").map{it.str("text")}
                Panel {
                    Text("${idx+1}. ${q.str("question_text")}",fontWeight=FontWeight.Bold)
                    if(options.isNotEmpty())ChoiceOptions(answers[id].orEmpty(),options.map{it to it}){answers[id]=it}
                    else OutlinedTextField(answers[id].orEmpty(),{answers[id]=it},modifier=Modifier.fillMaxWidth(),
                        label={Text("الإجابة")})
                }
            }
            Action("إرسال التحدي",enabled=questions.isNotEmpty() && questions.all{!answers[it.int("id")].isNullOrBlank()} && !app.busy) {
                app.job {
                    val map=JSONObject();answers.forEach{(id,v)->map.put(id.toString(),v)}
                    val d=app.api.call("mobile/challenge.php",data=payload("csrf" to app.csrf,"answers" to map))
                    completed=d.bool("done")
                    score=d.int("score")
                    total=d.int("total")
                    app.bootstrap()
                }
            }
        }
    }
}

@Composable fun QuestionSubmissionPage(app:AppState) {
    var subject by remember{mutableStateOf("")}
    var topics by remember{mutableStateOf(emptyList<Json>())}
    var topic by remember{mutableStateOf("")}
    var type by remember{mutableStateOf("text")}
    var text by remember{mutableStateOf("")}
    var answer by remember{mutableStateOf("")}
    var explanation by remember{mutableStateOf("")}
    var source by remember{mutableStateOf("")}
    var year by remember{mutableStateOf("")}
    var round by remember{mutableStateOf("")}
    val options=remember{mutableStateListOf("","","","")}
    LaunchedEffect(subject) {
        if(subject.isNotBlank()) {
            try {
                topics=app.api.call("mobile/topics.php",query=mapOf("subject_id" to subject)).arr("topics")
                topic=topics.firstOrNull()?.int("id")?.toString().orEmpty()
            }catch(e:Exception){app.show(e.message.orEmpty())}
        }
    }
    PageColumn {
        Title("اقترح سؤالًا", "يُراجع السؤال من فريق المحتوى قبل نشره")
        Panel {
            Text("المادة",fontWeight=FontWeight.Bold)
            ChoiceOptions(subject,app.subjects.map{it.int("id").toString() to it.str("name")}){subject=it}
            if(subject.isNotBlank()) {
                Text("الموضوع",fontWeight=FontWeight.Bold)
                ChoiceOptions(topic,topics.map{it.int("id").toString() to it.str("name")}){topic=it}
            }
            Text("نوع السؤال",fontWeight=FontWeight.Bold)
            ChoiceOptions(type,listOf("text" to "نصي","mcq" to "اختيارات","true_false" to "صح/خطأ",
                "fill" to "أكمل","meaning" to "معاني")){type=it}
            OutlinedTextField(text,{text=it},label={Text("نص السؤال")},modifier=Modifier.fillMaxWidth(),minLines=3)
            if(type=="mcq") {
                options.indices.forEach { i ->
                    OutlinedTextField(options[i],{options[i]=it},label={Text("خيار ${i+1}")},modifier=Modifier.fillMaxWidth())
                }
            }
            if(type=="true_false")ChoiceOptions(answer,listOf("صح" to "صح","خطأ" to "خطأ")){answer=it}
            else OutlinedTextField(answer,{answer=it},label={Text("الإجابة الصحيحة")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(explanation,{explanation=it},label={Text("الشرح")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(source,{source=it},label={Text("المصدر أو الملاحظة")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(year,{year=it},label={Text("السنة إن وجدت")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(round,{round=it},label={Text("الدور إن وجد")},modifier=Modifier.fillMaxWidth())
        }
        Action("إرسال السؤال للمراجعة",enabled=topic.isNotBlank() && text.length>=6 && answer.isNotBlank() && !app.busy) {
            app.job {
                if(type=="mcq" && answer.trim() !in options.map{it.trim()}) {
                    app.show("الإجابة الصحيحة يجب أن تكون ضمن الخيارات.");return@job
                }
                val a=JSONArray();options.filter{it.isNotBlank()}.forEach{a.put(it.trim())}
                val d=app.api.call("mobile/question-submit.php",data=payload("csrf" to app.csrf,
                    "chapter_id" to topic.toInt(), "question_type" to type,"question_text" to text,
                    "correct_answer" to answer,"explanation" to explanation,"source_text" to source,
                    "exam_year" to year,"exam_round" to round,"options" to a))
                app.show(d.readMessage("تم إرسال السؤال."))
                text="";answer="";explanation="";source=""
            }
        }
    }
}

@Composable fun MoreQuestionsPage(app:AppState) {
    val route=app.route
    var data by remember(route){mutableStateOf(JSONObject())}
    var loading by remember(route){mutableStateOf(true)}
    var tick by remember {mutableIntStateOf(0)}
    LaunchedEffect(route,tick) {
        try{data=app.api.call("mobile/question-request.php",query=mapOf("chapter_id" to route.id.toString()))}
        catch(e:Exception){app.show(e.message.orEmpty())}
        finally{loading=false}
    }
    PageColumn {
        Title("طلب أسئلة جديدة",route.title)
        if(loading)CircularProgressIndicator()
        else Panel {
            Text("تم حل ${data.int("answered")} من ${data.int("total")} سؤال",fontWeight=FontWeight.Bold)
            Text(if(data.bool("pending"))"طلبك قيد المراجعة" else if(data.bool("complete"))
                "أكملت جميع الأسئلة!" else "أكمل أسئلة هذا الموضوع أولًا")
            Action("إرسال الطلب",enabled=data.bool("complete") && !data.bool("pending") && !app.busy) {
                app.job {
                    val d=app.api.call("mobile/question-request.php",data=payload("csrf" to app.csrf,"chapter_id" to route.id))
                    app.show(d.readMessage("تم استلام طلبك."));tick++
                }
            }
        }
    }
}

@Composable fun RequestsPage(app:AppState) {
    var result by remember{mutableStateOf(JSONObject())}
    var loading by remember{mutableStateOf(true)}
    LaunchedEffect(Unit){
        try{result=app.api.call("mobile/my-requests.php")}catch(e:Exception){app.show(e.message.orEmpty())}
        finally{loading=false}
    }
    PageColumn{
        if(loading)CircularProgressIndicator()
        else {
            listOf("question_requests" to "طلبات المزيد", "submissions" to "الأسئلة المقترحة", "reports" to "البلاغات")
                .forEach{(key,label)->
                    Title(label)
                    val items=result.arr(key)
                    if(items.isEmpty())Text("لا يوجد")
                    items.forEach{row->Panel {
                        Text(row.str("question_text").ifBlank{row.str("chapter_name").ifBlank{row.str("message")}},fontWeight=FontWeight.Bold)
                        Text("الحالة: ${row.str("status")}")
                        if(row.str("admin_note").isNotBlank())Text("الإدارة: ${row.str("admin_note")}")
                    }}
                }
        }
    }
}

@Composable fun SupportPage(app:AppState) {
    var messages by remember{mutableStateOf(emptyList<Json>())}
    var text by remember{mutableStateOf("")}
    var tick by remember{mutableIntStateOf(0)}
    LaunchedEffect(tick){
        try{messages=app.api.call("mobile/support.php").arr("messages")}
        catch(e:Exception){app.show(e.message.orEmpty())}
    }
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Title("تحدث مع فريق الدعم", "رسائلك خاصة بحسابك")
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){
            items(messages){m ->Panel {
                Text(m.str("message").ifBlank{m.str("body")})
                Text(m.str("created_at"),style=MaterialTheme.typography.labelSmall)
            }}
        }
        OutlinedTextField(text,{text=it},modifier=Modifier.fillMaxWidth(),minLines=2,label={Text("رسالتك")})
        Action("إرسال",enabled=text.isNotBlank() && !app.busy){
            app.job{
                app.api.call("mobile/support.php",data=payload("csrf" to app.csrf,"message" to text.trim()))
                text="";tick++
            }
        }
    }
}

@Composable fun StudyPlanPage(app:AppState) {
    var plans by remember{mutableStateOf(emptyList<Json>())}
    var title by remember{mutableStateOf("خطة مراجعة الوزاري")}
    var days by remember{mutableFloatStateOf(30f)}
    var target by remember{mutableFloatStateOf(20f)}
    var tick by remember{mutableIntStateOf(0)}
    LaunchedEffect(tick){
        try{plans=app.api.call("mobile/study-plan.php").arr("plans")}
        catch(e:Exception){app.show(e.message.orEmpty())}
    }
    PageColumn {
        Title("خطة الدراسة", "حدد هدفًا واقعيًا والتزم به")
        plans.forEach{p->Panel {
            Text(p.str("title"),fontWeight=FontWeight.Bold)
            Text("الحالة: ${p.str("status")} • الهدف: ${p.int("daily_target")} سؤال/يوم")
        }}
        Panel {
            OutlinedTextField(title,{title=it},label={Text("اسم الخطة")},modifier=Modifier.fillMaxWidth())
            Text("المدة ${days.toInt()} يوم")
            Slider(days,{days=it},valueRange=7f..90f)
            Text("هدف اليوم: ${target.toInt()} سؤال")
            Slider(target,{target=it},valueRange=5f..100f)
            Action("حفظ خطة جديدة",enabled=title.isNotBlank() && !app.busy){
                app.job{
                    app.api.call("mobile/study-plan.php",data=payload(
                        "csrf" to app.csrf,"title" to title,"days" to days.toInt(),"target" to target.toInt()))
                    tick++;app.show("تم حفظ خطة الدراسة.")
                }
            }
        }
    }
}
