// Murshid v4.0 major release
import SwiftUI
import Foundation

enum V40Labels {
    static func questionType(_ value: String) -> String {
        switch value {
        case "mcq": return "اختيارات"
        case "true_false": return "صح / خطأ"
        case "fill": return "أكمل"
        case "meaning": return "معاني"
        case "match": return "مطابقة"
        case "calculation": return "مسائل"
        case "genetics": return "وراثة"
        case "text": return "نصي"
        default: return "الكل"
        }
    }
    static func state(_ value: String) -> String {
        switch value {
        case "unanswered": return "لم أجب"
        case "wrong": return "أخطأت سابقًا"
        case "correct": return "أجبت صحيحًا"
        case "favorite": return "المفضلة"
        default: return "الكل"
        }
    }
    static func difficulty(_ value: String) -> String {
        switch value {
        case "easy": return "سهل"
        case "medium": return "متوسط"
        case "hard": return "صعب"
        default: return "الكل"
        }
    }
}

struct V40ExamFilterSheet: View {
    @Environment(\.dismiss) private var dismiss
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

    var body: some View {
        NavigationStack {
            Form {
                Section("نوع السؤال") {
                    Picker("النوع", selection: $type) {
                        Text("الكل").tag("all")
                        ForEach(availableTypes.filter { !$0.isEmpty }, id: \.self) { value in
                            Text(V40Labels.questionType(value)).tag(value)
                        }
                    }
                }
                if !availableYears.isEmpty {
                    Section("السنة") {
                        Picker("السنة", selection: $year) {
                            Text("الكل").tag("all")
                            ForEach(availableYears, id: \.self) { Text($0).tag($0) }
                        }
                    }
                }
                if !availableRounds.isEmpty {
                    Section("الدور") {
                        Picker("الدور", selection: $round) {
                            Text("الكل").tag("all")
                            ForEach(availableRounds, id: \.self) { Text($0).tag($0) }
                        }
                    }
                }
                if !availableDifficulties.isEmpty {
                    Section("الصعوبة") {
                        Picker("الصعوبة", selection: $difficulty) {
                            Text("الكل").tag("all")
                            ForEach(availableDifficulties, id: \.self) {
                                Text(V40Labels.difficulty($0)).tag($0)
                            }
                        }
                    }
                }
                Section("حالة السؤال") {
                    Picker("الحالة", selection: $state) {
                        Text("الكل").tag("all")
                        Text("لم أجب").tag("unanswered")
                        Text("أخطأت سابقًا").tag("wrong")
                        Text("أجبت صحيحًا").tag("correct")
                        Text("المفضلة").tag("favorite")
                    }
                    Toggle("ترتيب عشوائي", isOn: $shuffle)
                }
                Section {
                    Button("إعادة ضبط المرشحات", role: .destructive) {
                        type = "all"
                        year = "all"
                        round = "all"
                        difficulty = "all"
                        state = "all"
                        shuffle = false
                    }
                }
            }
            .navigationTitle("تصفية الأسئلة")
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

struct V40StudentQuestionSubmitView: View {
    @EnvironmentObject var app: AppSession
    @State private var subjectID = 0
    @State private var topics: [Topic] = []
    @State private var topicID = 0
    @State private var type = "text"
    @State private var questionText = ""
    @State private var correctAnswer = ""
    @State private var explanation = ""
    @State private var sourceText = ""
    @State private var examYear = ""
    @State private var examRound = ""
    @State private var options = ["", "", "", ""]
    @State private var loadingTopics = false
    @State private var submitting = false
    @State private var message = ""
    @State private var isError = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                V3IntroCard(
                    eyebrow: "ساهم معنا",
                    title: "اقترح سؤالًا",
                    text: "أرسل سؤالًا مع إجابته ومصدره. يصل لفريق المحتوى للمراجعة قبل النشر.",
                    icon: "plus.bubble.fill"
                )
                MurshidCard {
                    VStack(alignment: .leading, spacing: 14) {
                        Picker("المادة", selection: $subjectID) {
                            Text("اختر المادة").tag(0)
                            ForEach(app.subjects) { Text($0.name).tag($0.id) }
                        }
                        .onChange(of: subjectID) { _ in Task { await loadTopics() } }

                        if loadingTopics {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Picker("الموضوع", selection: $topicID) {
                                Text("اختر الموضوع").tag(0)
                                ForEach(topics) { Text($0.name).tag($0.id) }
                            }
                        }

                        Picker("نوع السؤال", selection: $type) {
                            Text("نصي").tag("text")
                            Text("اختيارات").tag("mcq")
                            Text("صح / خطأ").tag("true_false")
                            Text("أكمل").tag("fill")
                            Text("معاني").tag("meaning")
                        }

                        TextField("نص السؤال", text: $questionText, axis: .vertical)
                            .lineLimit(3...8)
                            .padding(12)
                            .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 12))

                        if type == "mcq" {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("الخيارات").font(.subheadline.bold())
                                ForEach(options.indices, id: \.self) { index in
                                    TextField("الخيار \(index + 1)", text: Binding(
                                        get: { options[index] },
                                        set: { options[index] = $0 }
                                    ))
                                    .padding(11)
                                    .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 11))
                                }
                            }
                        }

                        if type == "true_false" {
                            Picker("الإجابة الصحيحة", selection: $correctAnswer) {
                                Text("اختر").tag("")
                                Text("صح").tag("صح")
                                Text("خطأ").tag("خطأ")
                            }
                            .pickerStyle(.segmented)
                        } else {
                            TextField("الإجابة الصحيحة", text: $correctAnswer, axis: .vertical)
                                .lineLimit(1...4)
                                .padding(12)
                                .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 12))
                        }

                        TextField("شرح مختصر — اختياري", text: $explanation, axis: .vertical)
                            .lineLimit(2...6)
                            .padding(12)
                            .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 12))

                        TextField("المصدر / الملاحظة", text: $sourceText)
                            .padding(12)
                            .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 12))

                        HStack {
                            TextField("السنة", text: $examYear).keyboardType(.numberPad)
                            TextField("الدور", text: $examRound)
                        }
                        .textFieldStyle(.roundedBorder)
                    }
                }

                if !message.isEmpty {
                    V3InlineMessage(
                        text: message,
                        icon: isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill",
                        tone: isError ? .warning : .success
                    )
                }

                Button {
                    Task { await submit() }
                } label: {
                    HStack {
                        if submitting { ProgressView().tint(.white) }
                        Image(systemName: "paperplane.fill")
                        Text(submitting ? "جاري الإرسال…" : "إرسال للمراجعة")
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(submitting || !canSubmit)
                .opacity(canSubmit ? 1 : 0.55)
            }
            .padding(16)
            .padding(.bottom, 28)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("اقترح سؤالًا")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if subjectID == 0, let first = app.subjects.first {
                subjectID = first.id
                Task { await loadTopics() }
            }
        }
    }

    private var canSubmit: Bool {
        topicID > 0
        && questionText.trimmingCharacters(in: .whitespacesAndNewlines).count >= 6
        && !correctAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && (type != "mcq" || options.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }.count >= 2)
    }

    private func loadTopics() async {
        guard subjectID > 0 else { return }
        await MainActor.run { loadingTopics = true; topicID = 0 }
        do {
            let d = try await APIClient.shared.request(
                "mobile/topics.php",
                query: [URLQueryItem(name: "subject_id", value: "\(subjectID)")]
            )
            let loaded = jArray(d["topics"]).map(Topic.init)
            await MainActor.run {
                topics = loaded
                topicID = loaded.first?.id ?? 0
                loadingTopics = false
            }
        } catch {
            await MainActor.run {
                loadingTopics = false
                message = error.localizedDescription
                isError = true
            }
        }
    }

    private func submit() async {
        guard canSubmit else { return }
        await MainActor.run { submitting = true; message = ""; isError = false }
        do {
            let cleanOptions = options.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
            _ = try await APIClient.shared.request(
                "mobile/question-submit.php",
                method: "POST",
                body: [
                    "csrf": app.csrf,
                    "chapter_id": topicID,
                    "question_type": type,
                    "question_text": questionText,
                    "correct_answer": correctAnswer,
                    "explanation": explanation,
                    "source_text": sourceText,
                    "exam_year": examYear,
                    "exam_round": examRound,
                    "options": cleanOptions
                ]
            )
            await MainActor.run {
                submitting = false
                message = "وصل اقتراحك إلى فريق المحتوى. يمكنك متابعة حالته من «طلباتك»."
                isError = false
                questionText = ""
                correctAnswer = ""
                explanation = ""
                sourceText = ""
                examYear = ""
                examRound = ""
                options = ["", "", "", ""]
                haptic()
            }
        } catch {
            await MainActor.run {
                submitting = false
                message = error.localizedDescription
                isError = true
                haptic(.error)
            }
        }
    }
}

struct V40MyQuestionSubmissionsView: View {
    @State private var rows: [JSON] = []
    @State private var loading = true
    @State private var error = ""

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if !error.isEmpty {
                ErrorStateView(message: error, retry: { Task { await load() } })
            } else if rows.isEmpty {
                EmptyStateView(
                    systemImage: "tray",
                    title: "لا توجد طلبات",
                    message: "اقتراحات الأسئلة التي ترسلها ستظهر هنا مع حالة المراجعة."
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                            MurshidCard {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        Text(statusLabel(jString(row["status"])))
                                            .font(.caption.bold())
                                            .foregroundStyle(statusColor(jString(row["status"])))
                                        Spacer()
                                        Text(jString(row["created_at"]))
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                    Text(jString(row["question_text"]))
                                        .font(.headline)
                                    Text(jString(row["subject_name"]) + " • " + jString(row["chapter_name"]))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    if !jString(row["admin_note"]).isEmpty {
                                        Divider()
                                        Text("ملاحظة المراجعة: " + jString(row["admin_note"]))
                                            .font(.footnote)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }
                    .padding(16)
                }
                .background(Color.murshidBackground)
            }
        }
        .navigationTitle("طلباتك")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        do {
            let d = try await APIClient.shared.request("mobile/question-submit.php")
            await MainActor.run { rows = jArray(d["submissions"]); loading = false; error = "" }
        } catch {
            await MainActor.run { loading = false; self.self.error = error.localizedDescription }
        }
    }

    private func statusLabel(_ value: String) -> String {
        switch value {
        case "new": return "جديد"
        case "reviewing": return "قيد المراجعة"
        case "accepted": return "مقبول"
        case "published": return "تم النشر"
        case "rejected": return "مرفوض"
        default: return value
        }
    }

    private func statusColor(_ value: String) -> Color {
        switch value {
        case "published", "accepted": return .green
        case "rejected": return .red
        default: return .orange
        }
    }
}

struct V40CustomExamBuilderView: View {
    @EnvironmentObject var app: AppSession
    @State private var subjectID = 0
    @State private var topics: [Topic] = []
    @State private var selectedTopics: Set<Int> = []
    @State private var count = 20
    @State private var type = "all"
    @State private var state = "all"
    @State private var minutes = 0
    @State private var loading = false
    @State private var error = ""
    @State private var sessionID = 0
    @State private var goExam = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                V3IntroCard(
                    eyebrow: "اختبارك",
                    title: "أنشئ اختبارًا مخصصًا",
                    text: "حدد المادة والمواضيع وعدد الأسئلة، ويمكنك إضافة مؤقت. التصحيح يظهر في النهاية.",
                    icon: "slider.horizontal.3"
                )

                MurshidCard {
                    VStack(alignment: .leading, spacing: 14) {
                        Picker("المادة", selection: $subjectID) {
                            Text("اختر المادة").tag(0)
                            ForEach(app.subjects) { Text($0.name).tag($0.id) }
                        }
                        .onChange(of: subjectID) { _ in Task { await loadTopics() } }

                        if !topics.isEmpty {
                            Text("المواضيع — اتركها بدون تحديد لاستخدام كل المادة")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            ForEach(topics) { topic in
                                Button {
                                    if selectedTopics.contains(topic.id) { selectedTopics.remove(topic.id) }
                                    else { selectedTopics.insert(topic.id) }
                                } label: {
                                    HStack {
                                        Image(systemName: selectedTopics.contains(topic.id) ? "checkmark.circle.fill" : "circle")
                                            .foregroundStyle(Color.murshidBlue)
                                        Text(topic.name).foregroundStyle(.primary)
                                        Spacer()
                                        Text("\(topic.count)").font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }

                        Stepper("عدد الأسئلة: \(count)", value: $count, in: 5...50, step: 5)

                        Picker("نوع السؤال", selection: $type) {
                            Text("الكل").tag("all")
                            Text("اختيارات").tag("mcq")
                            Text("صح / خطأ").tag("true_false")
                            Text("نصي").tag("text")
                            Text("أكمل").tag("fill")
                            Text("معاني").tag("meaning")
                        }

                        Picker("حالة السؤال", selection: $state) {
                            Text("الكل").tag("all")
                            Text("لم أجب").tag("unanswered")
                            Text("أخطأت سابقًا").tag("wrong")
                            Text("أجبت صحيحًا").tag("correct")
                            Text("المفضلة").tag("favorite")
                        }

                        Picker("المؤقت", selection: $minutes) {
                            Text("بدون مؤقت").tag(0)
                            Text("15 د").tag(15)
                            Text("30 د").tag(30)
                            Text("45 د").tag(45)
                            Text("60 د").tag(60)
                        }
                        .pickerStyle(.segmented)
                    }
                }

                if !error.isEmpty {
                    V3InlineMessage(text: error, icon: "exclamationmark.triangle.fill", tone: .warning)
                }

                Button {
                    Task { await create() }
                } label: {
                    HStack {
                        if loading { ProgressView().tint(.white) }
                        Image(systemName: "play.fill")
                        Text(loading ? "جاري الإنشاء…" : "ابدأ الاختبار")
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(loading || subjectID == 0)
            }
            .padding(16)
            .padding(.bottom, 28)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("اختبار مخصص")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if subjectID == 0, let first = app.subjects.first {
                subjectID = first.id
                Task { await loadTopics() }
            }
        }
        .navigationDestination(isPresented: $goExam) {
            V40CustomExamSessionView(sessionID: sessionID).environmentObject(app)
        }
    }

    private func loadTopics() async {
        guard subjectID > 0 else { return }
        do {
            let d = try await APIClient.shared.request(
                "mobile/topics.php",
                query: [URLQueryItem(name: "subject_id", value: "\(subjectID)")]
            )
            let loaded = jArray(d["topics"]).map(Topic.init)
            await MainActor.run { topics = loaded; selectedTopics.removeAll(); error = "" }
        } catch {
            await MainActor.run { self.error = error.localizedDescription }
        }
    }

    private func create() async {
        await MainActor.run { loading = true; error = "" }
        do {
            let d = try await APIClient.shared.request(
                "mobile/practice.php",
                method: "POST",
                body: [
                    "csrf": app.csrf,
                    "action": "create",
                    "subject_id": subjectID,
                    "chapter_ids": Array(selectedTopics),
                    "count": count,
                    "type": type,
                    "state": state,
                    "minutes": minutes
                ]
            )
            await MainActor.run {
                sessionID = jInt(d["session_id"])
                loading = false
                goExam = sessionID > 0
                haptic()
            }
        } catch {
            await MainActor.run {
                loading = false
                self.error = error.localizedDescription
                haptic(.error)
            }
        }
    }
}

private struct V40PracticeQuestion: Identifiable {
    let id: Int
    let text: String
    let options: [QuestionOption]
    let year: String
    let round: String

    init(_ j: JSON) {
        id = jInt(j["id"])
        text = jString(j["text"])
        options = jArray(j["options"]).map(QuestionOption.init)
        year = jString(j["exam_year"])
        round = jString(j["exam_round"])
    }
}

struct V40CustomExamSessionView: View {
    @EnvironmentObject var app: AppSession
    let sessionID: Int
    @State private var title = "اختبار مخصص"
    @State private var questions: [V40PracticeQuestion] = []
    @State private var current = 0
    @State private var answers: [Int: String] = [:]
    @State private var loading = true
    @State private var submitting = false
    @State private var error = ""
    @State private var result: JSON?
    @State private var remaining = 0
    @State private var timerEnabled = false
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let result {
                resultView(result)
            } else if questions.isEmpty {
                EmptyStateView(systemImage: "doc.questionmark", title: "الاختبار فارغ", message: error.isEmpty ? "لا توجد أسئلة مناسبة لهذه الإعدادات." : error)
            } else {
                examView
            }
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .task { await load() }
        .onReceive(timer) { _ in
            guard timerEnabled, result == nil, !loading, remaining > 0 else { return }
            remaining -= 1
            if remaining == 0 { Task { await finish() } }
        }
    }

    private var examView: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 15) {
                    MurshidCard {
                        HStack {
                            Text("\(current + 1) / \(questions.count)")
                                .font(.headline.monospacedDigit())
                            Spacer()
                            if timerEnabled {
                                Label(clock, systemImage: "timer")
                                    .font(.subheadline.monospacedDigit())
                                    .foregroundStyle(remaining < 60 ? .red : .secondary)
                            }
                        }
                    }

                    MurshidCard {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("سؤال \(current + 1)")
                                .font(.caption.bold())
                                .foregroundStyle(Color.murshidBlue)
                            Text(questions[current].text)
                                .font(.title3.weight(.semibold))
                                .lineSpacing(5)
                                .fixedSize(horizontal: false, vertical: true)
                            let meta = [questions[current].year, questions[current].round].filter { !$0.isEmpty }
                            if !meta.isEmpty {
                                Text(meta.joined(separator: " • "))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }

                    answerCard(questions[current])

                    if !error.isEmpty {
                        V3InlineMessage(text: error, icon: "exclamationmark.triangle.fill", tone: .warning)
                    }
                }
                .padding(16)
            }

            Divider()

            HStack(spacing: 12) {
                Button("السابق") {
                    if current > 0 { current -= 1 }
                }
                .buttonStyle(.bordered)
                .disabled(current == 0 || submitting)

                Spacer()

                if current == questions.count - 1 {
                    Button(submitting ? "جاري التصحيح…" : "إنهاء الاختبار") {
                        Task { await finish() }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.murshidBlue)
                    .disabled(submitting || !hasCurrentAnswer)
                } else {
                    Button("التالي") {
                        guard hasCurrentAnswer else { return }
                        current += 1
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.murshidBlue)
                    .disabled(!hasCurrentAnswer || submitting)
                }
            }
            .padding(16)
            .background(.ultraThinMaterial)
        }
    }

    @ViewBuilder
    private func answerCard(_ q: V40PracticeQuestion) -> some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("إجابتك").font(.headline)
                if !q.options.isEmpty {
                    ForEach(Array(q.options.enumerated()), id: \.element.id) { index, option in
                        let selected = answers[q.id] == option.text
                        Button {
                            answers[q.id] = option.text
                            selectionHaptic()
                        } label: {
                            HStack {
                                Text(optionLetter(index))
                                    .font(.subheadline.bold())
                                    .frame(width: 32, height: 32)
                                    .background(selected ? Color.murshidBlue : Color.murshidBlue.opacity(0.09), in: Circle())
                                    .foregroundStyle(selected ? .white : Color.murshidBlue)
                                Text(option.text).foregroundStyle(.primary)
                                Spacer()
                                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(selected ? Color.murshidBlue : .secondary)
                            }
                            .padding(10)
                            .background(
                                selected ? Color.murshidBlue.opacity(0.08) : Color.primary.opacity(0.025),
                                in: RoundedRectangle(cornerRadius: 13)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                } else {
                    TextEditor(text: Binding(
                        get: { answers[q.id] ?? "" },
                        set: { answers[q.id] = $0 }
                    ))
                    .frame(minHeight: 130)
                    .padding(8)
                    .scrollContentBackground(.hidden)
                    .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 13))
                }
            }
        }
    }

    private var hasCurrentAnswer: Bool {
        guard !questions.isEmpty else { return false }
        return !(answers[questions[current].id] ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .isEmpty
    }

    private var clock: String {
        String(format: "%02d:%02d", remaining / 60, remaining % 60)
    }

    private func optionLetter(_ index: Int) -> String {
        let letters = ["أ", "ب", "ج", "د", "هـ", "و"]
        return index < letters.count ? letters[index] : "\(index + 1)"
    }

    private func load() async {
        do {
            let d = try await APIClient.shared.request(
                "mobile/practice.php",
                query: [URLQueryItem(name: "session_id", value: "\(sessionID)")]
            )
            let session = d["session"] as? JSON ?? [:]
            let loaded = jArray(d["questions"]).map(V40PracticeQuestion.init)
            let duration = jInt(session["duration_seconds"])
            await MainActor.run {
                title = jString(session["title"], default: "اختبار مخصص")
                questions = loaded
                remaining = duration
                timerEnabled = duration > 0
                loading = false
                error = ""
            }
        } catch {
            await MainActor.run { loading = false; self.self.error = error.localizedDescription }
        }
    }

    private func finish() async {
        guard !submitting, !questions.isEmpty else { return }
        await MainActor.run { submitting = true; error = "" }
        do {
            var payload: JSON = [:]
            for (id, value) in answers { payload[String(id)] = value }
            let d = try await APIClient.shared.request(
                "mobile/practice.php",
                method: "POST",
                body: [
                    "csrf": app.csrf,
                    "action": "finish",
                    "session_id": sessionID,
                    "answers": payload
                ]
            )
            await MainActor.run {
                result = d
                submitting = false
                app.freeUsed += jInt(d["answered"])
                haptic(jDouble(d["score"]) >= 70 ? .success : .warning)
            }
        } catch {
            await MainActor.run {
                submitting = false
                self.self.error = error.localizedDescription
                haptic(.error)
            }
        }
    }

    private func resultView(_ result: JSON) -> some View {
        ScrollView {
            LazyVStack(spacing: 15) {
                MurshidCard {
                    VStack(spacing: 10) {
                        Text("\(Int(jDouble(result["score"]).rounded()))%")
                            .font(.system(size: 54, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.murshidBlue)
                        Text("\(jInt(result["correct"])) صحيحة من \(jInt(result["total"]))")
                            .font(.headline)
                        Text("التصحيح ظهر بعد إنهاء الاختبار.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                }

                ForEach(Array(jArray(result["review"]).enumerated()), id: \.offset) { _, row in
                    if !jBool(row["is_correct"]) {
                        MurshidCard {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(jString(row["question_text"])).font(.headline)
                                Text("إجابتك: " + jString(row["submitted_answer"], default: "—"))
                                    .foregroundStyle(.secondary)
                                Text("الصحيح: " + jString(row["correct_answer"]))
                                    .foregroundStyle(.green)
                                    .fontWeight(.semibold)
                                if !jString(row["explanation"]).isEmpty {
                                    Text(jString(row["explanation"]))
                                        .font(.footnote)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
            .padding(16)
            .padding(.bottom, 28)
        }
    }
}

struct V40AttemptHistoryView: View {
    @State private var rows: [JSON] = []
    @State private var loading = true
    @State private var error = ""

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if !error.isEmpty {
                ErrorStateView(message: error, retry: { Task { await load() } })
            } else if rows.isEmpty {
                EmptyStateView(
                    systemImage: "clock.arrow.circlepath",
                    title: "لا توجد محاولات",
                    message: "اختباراتك المخصصة المكتملة ستظهر هنا."
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                            MurshidCard {
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(jString(row["title"])).font(.headline)
                                        Text(jString(row["subject_name"]) + " • " + jString(row["completed_at"]))
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    VStack {
                                        Text("\(Int(jDouble(row["score"]).rounded()))%")
                                            .font(.title3.bold())
                                            .foregroundStyle(Color.murshidBlue)
                                        Text("\(jInt(row["correct"]))/\(jInt(row["total_questions"]))")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }
                    .padding(16)
                }
                .background(Color.murshidBackground)
            }
        }
        .navigationTitle("سجل الاختبارات")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        do {
            let d = try await APIClient.shared.request(
                "mobile/practice.php",
                query: [URLQueryItem(name: "history", value: "1")]
            )
            await MainActor.run { rows = jArray(d["history"]); loading = false; error = "" }
        } catch {
            await MainActor.run { loading = false; self.self.error = error.localizedDescription }
        }
    }
}

struct V40WhatsNewView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                V3IntroCard(
                    eyebrow: "الإصدار 4.0",
                    title: "ما الجديد؟",
                    text: "تحديث تجربة الطالب وبنك الأسئلة.",
                    icon: "sparkles"
                )
                feature("line.3.horizontal.decrease.circle.fill", "مرشحات متقدمة", "فلترة حسب النوع والسنة والدور والصعوبة وحالتك السابقة.")
                feature("slider.horizontal.3", "اختبار مخصص", "حدد المادة والمواضيع وعدد الأسئلة والمؤقت.")
                feature("plus.bubble.fill", "اقترح سؤالًا", "أرسل سؤالًا مع الإجابة والمصدر وتابع حالته.")
                feature("note.text", "ملاحظات خاصة", "احفظ ملاحظة خاصة على أي سؤال.")
                feature("shield.checkered", "سلامة أعلى", "فحص نوع السؤال وخياراته وإجابته ومساره قبل عرضه.")
            }
            .padding(16)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("ما الجديد")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func feature(_ icon: String, _ title: String, _ text: String) -> some View {
        MurshidCard {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundStyle(Color.murshidBlue)
                    .frame(width: 40)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline)
                    Text(text).font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer()
            }
        }
    }
}


struct V40QuestionSearchView: View {
    @State private var query = ""
    @State private var results: [JSON] = []
    @State private var loading = false
    @State private var message = ""

    var body: some View {
        List {
            Section {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("ابحث في الأسئلة والمواضيع", text: $query)
                        .textInputAutocapitalization(.never)
                        .submitLabel(.search)
                        .onSubmit { Task { await search() } }
                }
            }
            if loading {
                Section { HStack { Spacer(); ProgressView(); Spacer() } }
            } else if !message.isEmpty {
                Section { Text(message).foregroundStyle(.secondary) }
            } else if results.isEmpty && query.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 {
                Section { Text("لا توجد نتائج مطابقة.").foregroundStyle(.secondary) }
            } else {
                ForEach(Array(results.enumerated()), id: \.offset) { _, row in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(jString(row["type"]))
                                .font(.caption.bold())
                                .foregroundStyle(Color.murshidBlue)
                            Spacer()
                        }
                        Text(murshidQuestionDisplayText(jString(row["title"])))
                            .font(.subheadline.weight(.semibold))
                            .fixedSize(horizontal: false, vertical: true)
                        let meta = jString(row["meta"])
                        if !meta.isEmpty {
                            Text(meta).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 5)
                }
            }
        }
        .navigationTitle("البحث في الأسئلة")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: query) { value in
            if value.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 {
                results = []
                message = ""
            }
        }
    }

    private func search() async {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard q.count >= 2 else {
            await MainActor.run { results = []; message = "اكتب حرفين على الأقل."; loading = false }
            return
        }
        await MainActor.run { loading = true; message = "" }
        do {
            let d = try await APIClient.shared.request("api/search.php", query: [URLQueryItem(name: "q", value: q)])
            await MainActor.run { results = jArray(d["results"]); loading = false }
        } catch {
            await MainActor.run { loading = false; message = error.localizedDescription }
        }
    }
}
