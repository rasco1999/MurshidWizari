import SwiftUI

// MARK: - v4.0 student tools

struct V40QuestionFilter: Equatable {
    var query = ""
    var type = "all"
    var year = "all"
    var round = "all"
    var status = "all"

    var isActive: Bool {
        !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || type != "all" || year != "all" || round != "all" || status != "all"
    }

    func matches(_ q: Question) -> Bool {
        let search = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if !search.isEmpty {
            let haystack = [q.text, q.meaningWord, q.examYear, q.examRound].joined(separator: " ")
            if !haystack.localizedCaseInsensitiveContains(search) { return false }
        }
        if type != "all" {
            let normalized = q.type.lowercased()
            switch type {
            case "choice": if q.options.isEmpty && normalized != "true_false" { return false }
            case "text": if !q.options.isEmpty || ["match","calculation","genetics"].contains(normalized) { return false }
            default: if normalized != type { return false }
            }
        }
        if year != "all" && q.examYear != year { return false }
        if round != "all" && q.examRound != round { return false }
        switch status {
        case "new": if q.submitted { return false }
        case "answered": if !q.submitted { return false }
        case "wrong": if q.storedCorrect != false { return false }
        case "favorite": if !q.favorite { return false }
        default: break
        }
        return true
    }
}

struct V40QuestionFilterSheet: View {
    let questions: [Question]
    @Binding var filter: V40QuestionFilter
    let onApply: () -> Void
    @Environment(.dismiss) private var dismiss

    private var years: [String] {
        Array(Set(questions.map(.examYear).filter { !$0.isEmpty })).sorted(by: >)
    }
    private var rounds: [String] {
        Array(Set(questions.map(.examRound).filter { !$0.isEmpty })).sorted()
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("بحث داخل الأسئلة") {
                    TextField("كلمة من نص السؤال", text: $filter.query)
                }
                Section("نوع السؤال") {
                    Picker("النوع", selection: $filter.type) {
                        Text("الكل").tag("all")
                        Text("اختيارات").tag("choice")
                        Text("نصي / أكمل").tag("text")
                        Text("صح وخطأ").tag("true_false")
                        Text("مطابقة").tag("match")
                        Text("مسائل").tag("calculation")
                    }
                }
                if !years.isEmpty {
                    Section("السنة") {
                        Picker("السنة", selection: $filter.year) {
                            Text("كل السنوات").tag("all")
                            ForEach(years, id: .self) { Text($0).tag($0) }
                        }
                    }
                }
                if !rounds.isEmpty {
                    Section("الدور") {
                        Picker("الدور", selection: $filter.round) {
                            Text("كل الأدوار").tag("all")
                            ForEach(rounds, id: .self) { Text($0).tag($0) }
                        }
                    }
                }
                Section("حالة السؤال") {
                    Picker("الحالة", selection: $filter.status) {
                        Text("الكل").tag("all")
                        Text("لم أجب عنه").tag("new")
                        Text("أجبت عنه").tag("answered")
                        Text("أخطأت به سابقًا").tag("wrong")
                        Text("المفضلة").tag("favorite")
                    }
                }
                Section {
                    Button("إعادة ضبط المرشحات") { filter = V40QuestionFilter() }
                        .foregroundStyle(.red)
                }
            }
            .navigationTitle("مرشحات الأسئلة")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("إلغاء") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("تطبيق") { onApply(); dismiss() }.fontWeight(.semibold)
                }
            }
        }
    }
}

struct V40QuestionNoteSheet: View {
    @EnvironmentObject var app: AppSession
    let question: Question
    @Environment(.dismiss) private var dismiss
    @State private var note = ""
    @State private var loading = true
    @State private var saving = false
    @State private var message = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("السؤال") {
                    Text(murshidQuestionDisplayText(question.text)).font(.subheadline)
                }
                Section("ملاحظتي الخاصة") {
                    TextEditor(text: $note).frame(minHeight: 150)
                    Text("هذه الملاحظة خاصة بك ولا تظهر لأي طالب آخر.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if !message.isEmpty { Section { Text(message).foregroundStyle(.secondary) } }
            }
            .overlay { if loading { ProgressView() } }
            .navigationTitle("ملاحظة السؤال")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("إغلاق") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(saving ? "جارٍ الحفظ…" : "حفظ") { Task { await save() } }
                        .disabled(saving || loading)
                }
            }
            .task { await load() }
        }
    }

    private func load() async {
        do {
            let d = try await APIClient.shared.request("mobile/question-notes.php", query: [URLQueryItem(name: "question_id", value: "(question.id)")])
            await MainActor.run { note = jString(d["note"]); loading = false }
        } catch { await MainActor.run { loading = false; message = error.localizedDescription } }
    }

    private func save() async {
        saving = true
        do {
            let d = try await APIClient.shared.request("mobile/question-notes.php", method: "POST", body: ["csrf": app.csrf, "question_id": question.id, "note": note])
            await MainActor.run { saving = false; message = jString(d["message"], default: "تم الحفظ."); haptic() }
        } catch { await MainActor.run { saving = false; message = error.localizedDescription; haptic(.error) } }
    }
}

struct V40QuestionRequestView: View {
    @EnvironmentObject var app: AppSession
    let topic: Topic
    let subject: Subject
    @State private var loading = true
    @State private var complete = false
    @State private var pending = false
    @State private var answered = 0
    @State private var total = 0
    @State private var sending = false
    @State private var message = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                V3IntroCard(eyebrow: subject.name, title: "طلب أسئلة جديدة", text: "إذا أكملت أسئلة هذا الموضوع يمكنك إرسال طلب مباشر إلى فريق المحتوى.", icon: "plus.bubble.fill")
                MurshidCard {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack { Text(topic.name).font(.headline); Spacer(); Text("(answered) / (total)").font(.subheadline.monospacedDigit()).foregroundStyle(.secondary) }
                        ProgressView(value: Double(answered), total: Double(max(1,total))).tint(.murshidBlue)
                        if pending { Label("طلبك موجود لدى الإدارة", systemImage: "clock.badge.checkmark").foregroundStyle(.orange) }
                        else if complete { Label("أكملت كل الأسئلة الحالية", systemImage: "checkmark.seal.fill").foregroundStyle(.green) }
                        else { Text("أكمل الأسئلة الحالية أولًا، ثم سيُفتح زر الطلب تلقائيًا.").font(.footnote).foregroundStyle(.secondary) }
                    }
                }
                Button { Task { await send() } } label: {
                    HStack { if sending { ProgressView().tint(.white) }; Image(systemName: "paperplane.fill"); Text(pending ? "تم إرسال الطلب" : "إرسال طلب أسئلة جديدة") }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(loading || sending || pending || !complete)
                if !message.isEmpty { V3InlineMessage(text: message, icon: "info.circle.fill", tone: .info) }
            }.padding(16)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("طلب أسئلة")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func load() async {
        do {
            let d = try await APIClient.shared.request("mobile/question-request.php", query: [URLQueryItem(name: "chapter_id", value: "(topic.id)")])
            await MainActor.run { complete = jBool(d["complete"]); pending = jBool(d["pending"]); answered = jInt(d["answered"]); total = jInt(d["total"]); loading = false }
        } catch { await MainActor.run { loading = false; message = error.localizedDescription } }
    }

    private func send() async {
        sending = true
        do {
            let d = try await APIClient.shared.request("mobile/question-request.php", method: "POST", body: ["csrf": app.csrf, "chapter_id": topic.id])
            await MainActor.run { sending = false; pending = true; message = jString(d["message"], default: "تم إرسال الطلب."); haptic() }
        } catch { await MainActor.run { sending = false; message = error.localizedDescription; haptic(.error) } }
    }
}

struct V40QuestionContributionView: View {
    @EnvironmentObject var app: AppSession
    @State private var subjectID = 0
    @State private var topics: [Topic] = []
    @State private var topicID = 0
    @State private var questionText = ""
    @State private var proposedAnswer = ""
    @State private var sourceText = ""
    @State private var loadingTopics = false
    @State private var sending = false
    @State private var message = ""

    var body: some View {
        Form {
            Section("مكان السؤال") {
                Picker("المادة", selection: $subjectID) {
                    ForEach(app.subjects) { Text($0.name).tag($0.id) }
                }
                .onChange(of: subjectID) { _ in Task { await loadTopics() } }
                Picker("الموضوع", selection: $topicID) {
                    Text("غير محدد").tag(0)
                    ForEach(topics) { Text($0.name).tag($0.id) }
                }
                .disabled(loadingTopics)
            }
            Section("السؤال المقترح") {
                TextEditor(text: $questionText).frame(minHeight: 120)
            }
            Section("الإجابة المقترحة") {
                TextEditor(text: $proposedAnswer).frame(minHeight: 90)
            }
            Section("المصدر - اختياري") {
                TextField("مثال: وزاري 2025 الدور الأول", text: $sourceText)
            }
            if !message.isEmpty { Section { Text(message).foregroundStyle(.secondary) } }
            Section {
                Button { Task { await submit() } } label: {
                    HStack { if sending { ProgressView() }; Text(sending ? "جاري الإرسال…" : "إرسال للمراجعة") }
                }.disabled(sending || questionText.trimmingCharacters(in: .whitespacesAndNewlines).count < 8 || proposedAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .navigationTitle("اقترح سؤالًا")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if subjectID == 0 { subjectID = app.subjects.first?.id ?? 0 }
            await loadTopics()
        }
    }

    private func loadTopics() async {
        guard subjectID > 0 else { return }
        loadingTopics = true
        do {
            let d = try await APIClient.shared.request("mobile/topics.php", query: [URLQueryItem(name: "subject_id", value: "(subjectID)")])
            let loaded = jArray(d["topics"]).map(Topic.init)
            await MainActor.run { topics = loaded; if !topics.contains(where: {$0.id == topicID}) { topicID = 0 }; loadingTopics = false }
        } catch { await MainActor.run { topics = []; topicID = 0; loadingTopics = false; message = error.localizedDescription } }
    }

    private func submit() async {
        sending = true; message = ""
        do {
            let d = try await APIClient.shared.request("mobile/question-contribution.php", method: "POST", body: ["csrf": app.csrf, "subject_id": subjectID, "chapter_id": topicID, "question_text": questionText, "proposed_answer": proposedAnswer, "source_text": sourceText])
            await MainActor.run { sending = false; message = jString(d["message"], default: "تم الإرسال."); questionText = ""; proposedAnswer = ""; sourceText = ""; haptic() }
        } catch { await MainActor.run { sending = false; message = error.localizedDescription; haptic(.error) } }
    }
}

struct V40HistoryView: View {
    @State private var attempts: [JSON] = []
    @State private var loading = true
    @State private var message = ""

    var body: some View {
        Group {
            if loading { ProgressView() }
            else if attempts.isEmpty { EmptyStateView(systemImage: "clock.arrow.circlepath", title: "لا توجد محاولات", message: "ستظهر اختباراتك المكتملة هنا.") }
            else {
                List {
                    ForEach(Array(attempts.enumerated()), id: .offset) { _, row in
                        VStack(alignment: .leading, spacing: 7) {
                            HStack { Text(jString(row["chapter_name"])).font(.headline); Spacer(); Text("(jInt(row["score"]))%").font(.headline.monospacedDigit()).foregroundStyle(jInt(row["score"]) >= 70 ? .green : .orange) }
                            Text(jString(row["subject_name"])).font(.subheadline).foregroundStyle(.secondary)
                            HStack { Label("(jInt(row["correct"])) صحيح", systemImage: "checkmark.circle"); Spacer(); Text(jString(row["started_at"])).font(.caption).foregroundStyle(.secondary) }.font(.caption)
                        }.padding(.vertical, 5)
                    }
                }
            }
        }
        .navigationTitle("سجل الاختبارات")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
        .overlay(alignment: .bottom) { if !message.isEmpty { Text(message).font(.caption).padding(8) } }
    }

    private func load() async {
        do { let d = try await APIClient.shared.request("mobile/history.php"); await MainActor.run { attempts = jArray(d["attempts"]); loading = false; message = "" } }
        catch { await MainActor.run { loading = false; message = error.localizedDescription } }
    }
}

struct V40MyRequestsView: View {
    @State private var requests: [JSON] = []
    @State private var reports: [JSON] = []
    @State private var submissions: [JSON] = []
    @State private var loading = true
    @State private var message = ""

    var body: some View {
        List {
            if !requests.isEmpty {
                Section("طلبات أسئلة جديدة") { ForEach(Array(requests.enumerated()), id: .offset) { _, row in requestRow(row, title: jString(row["chapter_name"], default: jString(row["subject_name"]))) } }
            }
            if !submissions.isEmpty {
                Section("الأسئلة التي اقترحتها") { ForEach(Array(submissions.enumerated()), id: .offset) { _, row in requestRow(row, title: String(jString(row["question_text"]).prefix(80))) } }
            }
            if !reports.isEmpty {
                Section("بلاغات الأسئلة") { ForEach(Array(reports.enumerated()), id: .offset) { _, row in requestRow(row, title: String(jString(row["question_text"]).prefix(80))) } }
            }
            if !loading && requests.isEmpty && submissions.isEmpty && reports.isEmpty { Section { Text("لا توجد طلبات حتى الآن.").foregroundStyle(.secondary) } }
        }
        .overlay { if loading { ProgressView() } }
        .navigationTitle("طلباتك")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
        .overlay(alignment: .bottom) { if !message.isEmpty { Text(message).font(.caption).padding(8) } }
    }

    private func requestRow(_ row: JSON, title: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title.isEmpty ? "طلب" : title).font(.subheadline.weight(.semibold)).lineLimit(2)
            HStack { Text(statusArabic(jString(row["status"]))).font(.caption.bold()).foregroundStyle(statusColor(jString(row["status"]))); Spacer(); Text(jString(row["created_at"])).font(.caption2).foregroundStyle(.secondary) }
            let note = jString(row["admin_note"])
            if !note.isEmpty { Text(note).font(.caption).foregroundStyle(.secondary) }
        }.padding(.vertical, 4)
    }

    private func statusArabic(_ raw: String) -> String {
        switch raw { case "pending": return "جديد"; case "reviewing": return "قيد المراجعة"; case "fulfilled","accepted": return "مقبول"; case "resolved","published": return "تم"; case "rejected","cancelled": return "مرفوض"; default: return raw }
    }
    private func statusColor(_ raw: String) -> Color { ["fulfilled","accepted","resolved","published"].contains(raw) ? .green : (["rejected","cancelled"].contains(raw) ? .red : .orange) }
    private func load() async {
        do { let d = try await APIClient.shared.request("mobile/my-requests.php"); await MainActor.run { requests = jArray(d["question_requests"]); reports = jArray(d["reports"]); submissions = jArray(d["submissions"]); loading = false; message = "" } }
        catch { await MainActor.run { loading = false; message = error.localizedDescription } }
    }
}

struct V40PracticeQuestion: Identifiable {
    let id: Int
    let type: String
    let text: String
    let options: [String]
    init(_ json: JSON) {
        id = jInt(json["id"]); type = jString(json["type"], default: "text"); text = jString(json["question_text"], default: jString(json["text"]))
        if let arr = json["options"] as? [String] { options = arr.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty } }
        else { options = jArray(json["options"]).map { jString($0["text"], default: jString($0["answer_text"])) }.filter { !$0.isEmpty } }
    }
}

struct V40PracticeBuilderView: View {
    @EnvironmentObject var app: AppSession
    @State private var subjectID = 0
    @State private var count = 10
    @State private var busy = false
    @State private var message = ""
    @State private var sessionID = 0
    @State private var showSession = false

    var body: some View {
        Form {
            Section("المادة") { Picker("المادة", selection: $subjectID) { ForEach(app.subjects) { Text($0.name).tag($0.id) } } }
            Section("عدد الأسئلة") { Picker("العدد", selection: $count) { Text("10").tag(10); Text("20").tag(20); Text("30").tag(30) }.pickerStyle(.segmented) }
            Section { Text("هذا وضع امتحان: لن يظهر التصحيح بعد كل سؤال، وستظهر النتيجة في النهاية.").font(.footnote).foregroundStyle(.secondary) }
            if !message.isEmpty { Section { Text(message).foregroundStyle(.secondary) } }
            Section { Button { Task { await create() } } label: { HStack { if busy { ProgressView() }; Text(busy ? "جاري الإنشاء…" : "إنشاء الاختبار") } }.disabled(busy || subjectID == 0) }
        }
        .navigationTitle("اختبار مخصص")
        .navigationBarTitleDisplayMode(.inline)
        .task { if subjectID == 0 { subjectID = app.subjects.first?.id ?? 0 } }
        .navigationDestination(isPresented: $showSession) { V40PracticeSessionView(sessionID: sessionID) }
    }

    private func create() async {
        busy = true; message = ""
        do { let d = try await APIClient.shared.request("mobile/practice.php", method: "POST", body: ["csrf": app.csrf, "action": "create", "subject_id": subjectID, "count": count]); let sid=jInt(d["session_id"]); await MainActor.run { busy=false; sessionID=sid; showSession=sid>0 } }
        catch { await MainActor.run { busy=false; message=error.localizedDescription; haptic(.error) } }
    }
}

struct V40PracticeSessionView: View {
    @EnvironmentObject var app: AppSession
    let sessionID: Int
    @State private var questions: [V40PracticeQuestion] = []
    @State private var answers: [Int:String] = [:]
    @State private var current = 0
    @State private var loading = true
    @State private var finishing = false
    @State private var score: Int?
    @State private var resultText = ""
    @State private var message = ""

    var body: some View {
        Group {
            if loading { ProgressView() }
            else if let score {
                ScrollView { VStack(spacing: 18) { V3IntroCard(eyebrow: "النتيجة", title: "(score)%", text: resultText, icon: "chart.bar.fill"); NavigationLink("إنشاء اختبار آخر", destination: V40PracticeBuilderView()).buttonStyle(PrimaryButtonStyle()) }.padding(16) }
            } else if questions.isEmpty { EmptyStateView(systemImage: "doc.questionmark", title: "لا توجد أسئلة", message: message.isEmpty ? "تعذر إنشاء الاختبار." : message) }
            else { exam }
        }
        .navigationTitle("اختبار مخصص")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private var exam: some View {
        let q = questions[current]
        return VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 14) {
                    MurshidCard { HStack { Text("سؤال (current+1) من (questions.count)").font(.headline); Spacer(); ProgressView(value: Double(current+1), total: Double(questions.count)).frame(width: 110) } }
                    MurshidCard { Text(murshidQuestionDisplayText(q.text)).font(.title3.weight(.semibold)).frame(maxWidth: .infinity, alignment: .leading) }
                    MurshidCard {
                        if q.type == "true_false" && q.options.isEmpty {
                            optionList(q, options: ["صح","خطأ"])
                        } else if !q.options.isEmpty {
                            optionList(q, options: q.options)
                        } else {
                            TextField("اكتب إجابتك", text: Binding(get: { answers[q.id] ?? "" }, set: { answers[q.id] = $0 }), axis: .vertical).lineLimit(3...7).textFieldStyle(.roundedBorder)
                        }
                    }
                    if !message.isEmpty { V3InlineMessage(text: message, icon: "exclamationmark.triangle.fill", tone: .warning) }
                }.padding(16)
            }
            Divider()
            HStack {
                Button("السابق") { if current > 0 { current -= 1 } }.disabled(current == 0)
                Spacer()
                if current == questions.count-1 { Button(finishing ? "جاري التصحيح…" : "إنهاء الاختبار") { Task { await finish() } }.buttonStyle(.borderedProminent).disabled(finishing) }
                else { Button("التالي") { current += 1 }.buttonStyle(.borderedProminent).disabled((answers[q.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
            }.padding(16).background(.ultraThinMaterial)
        }
    }

    private func optionList(_ q: V40PracticeQuestion, options: [String]) -> some View {
        VStack(spacing: 9) { ForEach(Array(options.enumerated()), id: .offset) { idx, option in Button { answers[q.id]=option; selectionHaptic() } label: { HStack { Text(["أ","ب","ج","د","هـ"].indices.contains(idx) ? ["أ","ب","ج","د","هـ"][idx] : "(idx+1)").font(.subheadline.bold()).frame(width:32,height:32).background(Color.murshidBlue.opacity(0.1),in:Circle()); Text(option).foregroundStyle(.primary); Spacer(); Image(systemName: answers[q.id]==option ? "checkmark.circle.fill" : "circle").foregroundStyle(Color.murshidBlue) }.padding(10).background(answers[q.id]==option ? Color.murshidBlue.opacity(0.08) : Color.primary.opacity(0.025), in: RoundedRectangle(cornerRadius:13)) }.buttonStyle(.plain) } }
    }

    private func load() async {
        do { let d=try await APIClient.shared.request("mobile/practice.php",query:[URLQueryItem(name:"session_id",value:"(sessionID)")]); let qs=jArray(d["questions"]).map(V40PracticeQuestion.init); await MainActor.run { questions=qs; loading=false } }
        catch { await MainActor.run { loading=false; message=error.localizedDescription } }
    }
    private func finish() async {
        finishing=true; var payload:JSON=[:]; for (id,value) in answers { payload[String(id)] = value }
        do { let d=try await APIClient.shared.request("mobile/practice.php",method:"POST",body:["csrf":app.csrf,"action":"finish","session_id":sessionID,"answers":payload]); let s=Int(jDouble(d["score"]).rounded()); await MainActor.run { finishing=false; score=s; resultText="أجبت عن (jInt(d["answered"])) من (jInt(d["total"]))، والصحيح (jInt(d["correct"]))."; haptic() } }
        catch { await MainActor.run { finishing=false; message=error.localizedDescription; haptic(.error) } }
    }
}

struct V40DailyChallengeView: View {
    @EnvironmentObject var app: AppSession
    @State private var questions:[V40PracticeQuestion]=[]
    @State private var answers:[Int:String]=[:]
    @State private var loading=true
    @State private var done=false
    @State private var score=0
    @State private var total=0
    @State private var busy=false
    @State private var message=""

    var body: some View {
        ScrollView {
            VStack(spacing:14) {
                V3IntroCard(eyebrow:"تحدي اليوم",title:done ? "أنجزت تحدي اليوم" : "اختبر نفسك بسرعة",text:done ? "نتيجتك (score) من (total)." : "أجب عن الأسئلة ثم أرسل التحدي مرة واحدة.",icon:"flame.fill")
                if loading { ProgressView().padding() }
                else if done { MurshidCard { Label("(score) / (total)",systemImage:"trophy.fill").font(.title2.bold()).foregroundStyle(.green) } }
                else {
                    ForEach(Array(questions.enumerated()),id:.element.id){ index,q in MurshidCard { VStack(alignment:.leading,spacing:10){ Text("(index+1). (murshidQuestionDisplayText(q.text))").font(.headline); if q.type=="true_false" && q.options.isEmpty { choice(q,["صح","خطأ"]) } else if !q.options.isEmpty { choice(q,q.options) } else { TextField("إجابتك",text:Binding(get:{answers[q.id] ?? ""},set:{answers[q.id]=$0})).textFieldStyle(.roundedBorder) } } } }
                    Button { Task { await submit() } } label:{ HStack{ if busy{ProgressView().tint(.white)}; Text(busy ? "جاري التصحيح…" : "إرسال تحدي اليوم") } }.buttonStyle(PrimaryButtonStyle()).disabled(busy || questions.isEmpty)
                }
                if !message.isEmpty { V3InlineMessage(text:message,icon:"info.circle.fill",tone:.info) }
            }.padding(16)
        }.background(Color.murshidBackground.ignoresSafeArea()).navigationTitle("تحدي اليوم").navigationBarTitleDisplayMode(.inline).task{await load()}
    }
    private func choice(_ q:V40PracticeQuestion,_ opts:[String])->some View { VStack(spacing:7){ForEach(opts,id:.self){o in Button{answers[q.id]=o;selectionHaptic()}label:{HStack{Text(o).foregroundStyle(.primary);Spacer();Image(systemName:answers[q.id]==o ? "checkmark.circle.fill":"circle")}.padding(10).background(answers[q.id]==o ? Color.murshidBlue.opacity(0.1):Color.primary.opacity(0.02),in:RoundedRectangle(cornerRadius:12))}.buttonStyle(.plain)}} }
    private func load() async { do{let d=try await APIClient.shared.request("mobile/challenge.php");await MainActor.run{done=jBool(d["done"]);score=jInt(d["score"]);total=jInt(d["total"]);questions=jArray(d["questions"]).map(V40PracticeQuestion.init);loading=false}}catch{await MainActor.run{loading=false;message=error.localizedDescription}} }
    private func submit() async {busy=true;var payload:JSON=[:];for(id,v)in answers{payload[String(id)]=v};do{let d=try await APIClient.shared.request("mobile/challenge.php",method:"POST",body:["csrf":app.csrf,"answers":payload]);await MainActor.run{busy=false;done=jBool(d["done"]);score=jInt(d["score"]);total=jInt(d["total"]);haptic()}}catch{await MainActor.run{busy=false;message=error.localizedDescription;haptic(.error)}}}
}


struct V40ExamFilterSheet: View {
    @Binding var type: String
    @Binding var year: String
    @Binding var round: String
    @Binding var difficulty: String
    @Binding var state: String
    @Binding var shuffle: Bool
    let availableTypes: [String]
    let availableYears: [String]
    let availableRounds: [String]
    let availableDifficulties: [String]
    let onApply: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("نوع السؤال") {
                    Picker("النوع", selection: $type) {
                        Text("الكل").tag("all")
                        ForEach(availableTypes, id: \.self) { value in Text(typeLabel(value)).tag(value) }
                    }
                }
                if !availableYears.isEmpty {
                    Section("السنة الوزارية") {
                        Picker("السنة", selection: $year) {
                            Text("كل السنوات").tag("all")
                            ForEach(availableYears, id: \.self) { Text($0).tag($0) }
                        }
                    }
                }
                if !availableRounds.isEmpty {
                    Section("الدور") {
                        Picker("الدور", selection: $round) {
                            Text("كل الأدوار").tag("all")
                            ForEach(availableRounds, id: \.self) { Text($0).tag($0) }
                        }
                    }
                }
                if !availableDifficulties.isEmpty {
                    Section("الصعوبة") {
                        Picker("الصعوبة", selection: $difficulty) {
                            Text("كل المستويات").tag("all")
                            ForEach(availableDifficulties, id: \.self) { Text(difficultyLabel($0)).tag($0) }
                        }
                    }
                }
                Section("حالة السؤال") {
                    Picker("الحالة", selection: $state) {
                        Text("الكل").tag("all")
                        Text("لم أجب").tag("new")
                        Text("أجبت سابقًا").tag("answered")
                        Text("أخطأت به").tag("wrong")
                        Text("المفضلة").tag("favorite")
                    }
                    Toggle("ترتيب عشوائي", isOn: $shuffle)
                }
                Section {
                    Button("إعادة الضبط", role: .destructive) {
                        type = "all"; year = "all"; round = "all"; difficulty = "all"; state = "all"; shuffle = false
                    }
                }
            }
            .navigationTitle("مرشحات الأسئلة")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("إلغاء") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("تطبيق") { onApply(); dismiss() }.fontWeight(.semibold)
                }
            }
        }
    }

    private func typeLabel(_ value: String) -> String {
        switch value {
        case "mcq": return "اختيارات"
        case "text": return "نصي"
        case "true_false": return "صح / خطأ"
        case "fill": return "أكمل"
        case "match": return "مطابقة"
        case "meaning": return "معاني"
        case "genetics": return "وراثة"
        case "calculation": return "مسائل"
        default: return value
        }
    }

    private func difficultyLabel(_ value: String) -> String {
        switch value.lowercased() {
        case "easy": return "سهل"
        case "medium": return "متوسط"
        case "hard": return "صعب"
        default: return value
        }
    }
}
