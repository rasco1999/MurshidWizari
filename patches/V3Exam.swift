import SwiftUI

struct V3ExamView: View {
    let topic: Topic
    let subject: Subject

    @EnvironmentObject var app: AppSession
    @Environment(\.dismiss) private var dismiss

    @State private var questions: [Question] = []
    @State private var current = 0
    @State private var loading = true
    @State private var submitting = false
    @State private var error = ""
    @State private var answers: [Int: String] = [:]
    @State private var interactive: [Int: [String: String]] = [:]
    @State private var matches: [Int: [Int: MatchDraft]] = [:]
    @State private var english: [Int: [String: String]] = [:]
    @State private var grades: [Int: JSON] = [:]
    @State private var showSubscription = false
    @State private var alertMessage = ""
    @State private var showAlert = false
    @State private var showResult = false
    @State private var startedAt = Date()
    @State private var showReport = false
    @State private var reportReason = "wrong_answer"
    @State private var reportMessage = ""
    @State private var reporting = false

    var body: some View {
        Group {
            if loading {
                ScrollView {
                    LazyVStack(spacing: 14) {
                        V3SkeletonCard(height: 72)
                        V3SkeletonCard(height: 155)
                        V3SkeletonCard(height: 230)
                    }.padding(16)
                }.background(Color.murshidBackground)
            } else if !error.isEmpty && questions.isEmpty {
                ErrorStateView(message: error, retry: { Task { await load() } })
            } else if questions.isEmpty {
                EmptyStateView(
                    systemImage: "doc.questionmark",
                    title: "لا توجد أسئلة متاحة",
                    message: app.subscribed ? "لا توجد أسئلة منشورة في هذا الموضوع حاليًا." : "قد تكون استهلكت رصيدك المجاني. يمكنك الاشتراك لفتح بقية الأسئلة."
                )
            } else if showResult {
                V3ExamResultView(
                    topic: topic,
                    subject: subject,
                    answered: answeredCount,
                    correct: correctCount,
                    total: questions.count,
                    duration: Date().timeIntervalSince(startedAt),
                    onReview: { showResult = false; current = firstWrongIndex ?? 0 },
                    onDone: { dismiss() }
                )
            } else {
                examContent
            }
        }
        .navigationTitle(showResult ? "نتيجة الاختبار" : topic.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !questions.isEmpty && !showResult {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button(action: toggleFavorite) {
                        Image(systemName: questions[current].favorite ? "heart.fill" : "heart")
                            .foregroundStyle(questions[current].favorite ? .red : Color.murshidBlue)
                    }
                    .accessibilityLabel(questions[current].favorite ? "إزالة من المفضلة" : "إضافة إلى المفضلة")

                    Button { showReport = true } label: {
                        Image(systemName: "exclamationmark.bubble")
                    }
                    .accessibilityLabel("الإبلاغ عن السؤال")
                }
            }
        }
        .task { await load() }
        .alert("المرشد الوزاري", isPresented: $showAlert) {
            Button("حسنًا", role: .cancel) {}
        } message: { Text(alertMessage) }
        .sheet(isPresented: $showSubscription) {
            NavigationStack {
                V3SubscriptionView()
                    .toolbar { ToolbarItem(placement: .topBarLeading) { Button("إغلاق") { showSubscription = false } } }
            }
            .environmentObject(app)
        }
        .sheet(isPresented: $showReport) { reportSheet }
    }

    private var examContent: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 15) {
                        progressHeader.id("top")
                        questionCard(questions[current])
                        answerArea(questions[current])
                        feedbackArea(questions[current])
                        if !error.isEmpty {
                            V3InlineMessage(text: error, icon: "exclamationmark.triangle.fill", tone: .warning)
                        }
                    }
                    .padding(16)
                }
                .onChange(of: current) { _ in
                    withAnimation(.easeOut(duration: 0.22)) { proxy.scrollTo("top", anchor: .top) }
                }
            }
            Divider()
            bottomBar
        }
        .background(Color.murshidBackground)
    }

    private var progressHeader: some View {
        MurshidCard {
            VStack(spacing: 10) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(subject.name).font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                        Text(topic.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                    }
                    Spacer()
                    Text("\(current + 1) / \(questions.count)")
                        .font(.subheadline.bold().monospacedDigit())
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(Color.murshidBlue.opacity(0.09), in: Capsule())
                }

                ProgressView(value: Double(current + 1), total: Double(max(1, questions.count)))
                    .tint(.murshidBlue)

                HStack {
                    Label("تمت الإجابة: \(answeredCount)", systemImage: "checkmark.circle")
                    Spacer()
                    if !app.subscribed {
                        Button { showSubscription = true } label: {
                            Label("متبقي \(app.freeRemaining ?? 0)", systemImage: "gift.fill")
                        }
                        .foregroundStyle(Color.murshidGold)
                    } else {
                        Label("مشترك", systemImage: "checkmark.seal.fill").foregroundStyle(.green)
                    }
                }
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
            }
        }
    }

    private func questionCard(_ q: Question) -> some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 13) {
                HStack(spacing: 8) {
                    Text("سؤال \(current + 1)")
                        .font(.caption.bold())
                        .foregroundStyle(Color.murshidBlue)
                    if !q.examYear.isEmpty {
                        Label(q.examYear, systemImage: "calendar")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    if !q.examRound.isEmpty {
                        Text(q.examRound).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                }
                if !q.meaningWord.isEmpty {
                    Text(q.meaningWord)
                        .font(.headline)
                        .foregroundStyle(Color.murshidGold)
                }
                Text(q.text)
                    .font(.title3.weight(.semibold))
                    .lineSpacing(5)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
            }
        }
    }

    @ViewBuilder
    private func answerArea(_ q: Question) -> some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 13) {
                HStack {
                    Label("إجابتك", systemImage: "pencil.and.list.clipboard")
                        .font(.headline)
                    Spacer()
                    if isAnswered(q) {
                        Text("تم الحفظ").font(.caption.bold()).foregroundStyle(.green)
                    }
                }

                if isAnswered(q) {
                    Text(answerDisplay(q))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .textSelection(.enabled)
                } else if let em = q.englishMatch {
                    EnglishMatchInput(definition: em, values: bindingEnglish(q.id))
                } else if !q.options.isEmpty {
                    VStack(spacing: 10) {
                        ForEach(Array(q.options.enumerated()), id: \.element.id) { index, option in
                            optionButton(q: q, option: option, index: index)
                        }
                    }
                } else if q.type == "match" && !q.matchItems.isEmpty {
                    MatchInput(items: q.matchItems, drafts: bindingMatches(q.id))
                } else if !q.stages.isEmpty {
                    InteractiveInput(stages: q.stages, values: bindingInteractive(q.id))
                } else {
                    TextEditor(text: bindingAnswer(q.id))
                        .frame(minHeight: 120)
                        .padding(10)
                        .scrollContentBackground(.hidden)
                        .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }

                if !isAnswered(q) {
                    Button(action: submitCurrent) {
                        HStack(spacing: 9) {
                            if submitting { ProgressView().tint(.white) }
                            Image(systemName: submitting ? "hourglass" : "paperplane.fill")
                            Text(submitting ? "جاري حفظ الإجابة…" : "إرسال الإجابة")
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(submitting || !hasAnswer(q))
                    .opacity(hasAnswer(q) ? 1 : 0.55)

                    Text("لا يُخصم من رصيدك المجاني إلا بعد إرسال الإجابة.")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            }
        }
    }

    private func optionButton(q: Question, option: QuestionOption, index: Int) -> some View {
        let selected = answers[q.id] == option.text
        return Button {
            answers[q.id] = option.text
            selectionHaptic()
        } label: {
            HStack(spacing: 12) {
                Text(optionLetter(index))
                    .font(.subheadline.bold())
                    .frame(width: 34, height: 34)
                    .foregroundStyle(selected ? .white : Color.murshidBlue)
                    .background(selected ? Color.murshidBlue : Color.murshidBlue.opacity(0.10), in: Circle())
                Text(option.text)
                    .font(.body.weight(.medium))
                    .multilineTextAlignment(.leading)
                    .foregroundStyle(.primary)
                Spacer(minLength: 0)
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(selected ? Color.murshidBlue : Color.secondary)
            }
            .padding(12)
            .background(selected ? Color.murshidBlue.opacity(0.08) : Color.primary.opacity(0.025), in: RoundedRectangle(cornerRadius: 15, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous).stroke(selected ? Color.murshidBlue.opacity(0.30) : Color.primary.opacity(0.05), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func feedbackArea(_ q: Question) -> some View {
        if isAnswered(q) {
            let correct = correctness(q)
            MurshidCard {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Label(correct == true ? "إجابة صحيحة" : "راجع الإجابة", systemImage: correct == true ? "checkmark.seal.fill" : "xmark.octagon.fill")
                            .font(.headline)
                            .foregroundStyle(correct == true ? .green : .orange)
                        Spacer()
                        if correct == true {
                            Text("أحسنت").font(.caption.bold()).foregroundStyle(.green)
                        }
                    }
                    if !q.correctAnswer.isEmpty && correct != true {
                        Text("الإجابة الصحيحة")
                            .font(.caption.bold()).foregroundStyle(.secondary)
                        Text(q.correctAnswer)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.green)
                    }
                    if !q.explanation.isEmpty {
                        Divider()
                        Text("التوضيح").font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                        Text(q.explanation)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineSpacing(4)
                    }
                    if let grade = grades[q.id], !jString(grade["message"]).isEmpty {
                        Text(jString(grade["message"]))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var bottomBar: some View {
        HStack(spacing: 12) {
            Button {
                guard current > 0 else { return }
                current -= 1
                selectionHaptic()
            } label: {
                Label("السابق", systemImage: "chevron.right")
                    .frame(minWidth: 80)
            }
            .buttonStyle(.bordered)
            .disabled(current == 0)

            Spacer()

            if current == questions.count - 1 {
                Button {
                    showResult = true
                    haptic()
                } label: {
                    Label("النتيجة", systemImage: "chart.bar.fill")
                        .font(.headline)
                }
                .buttonStyle(.borderedProminent)
                .tint(.murshidBlue)
                .disabled(answeredCount == 0)
            } else {
                Button {
                    current += 1
                    selectionHaptic()
                } label: {
                    Label("التالي", systemImage: "chevron.left")
                        .frame(minWidth: 80)
                }
                .buttonStyle(.borderedProminent)
                .tint(.murshidBlue)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .background(.ultraThinMaterial)
    }

    private var reportSheet: some View {
        NavigationStack {
            Form {
                Section("سبب البلاغ") {
                    Picker("السبب", selection: $reportReason) {
                        Text("الإجابة غير صحيحة").tag("wrong_answer")
                        Text("السؤال غير واضح").tag("unclear")
                        Text("السؤال مكرر").tag("duplicate")
                        Text("مشكلة في المصدر").tag("source")
                        Text("سبب آخر").tag("other")
                    }
                }
                Section("ملاحظة اختيارية") {
                    TextField("اكتب ملاحظتك للمراجعة", text: $reportMessage, axis: .vertical)
                        .lineLimit(2...6)
                }
                Section {
                    Button {
                        reportCurrent()
                    } label: {
                        HStack {
                            if reporting { ProgressView() }
                            Text(reporting ? "جاري الإرسال…" : "إرسال البلاغ")
                        }
                    }
                    .disabled(reporting)
                }
            }
            .navigationTitle("الإبلاغ عن السؤال")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("إغلاق") { showReport = false } }
            }
        }
    }

    private var answeredCount: Int {
        questions.filter { isAnswered($0) }.count
    }

    private var correctCount: Int {
        questions.filter { correctness($0) == true }.count
    }

    private var firstWrongIndex: Int? {
        questions.firstIndex(where: { isAnswered($0) && correctness($0) == false })
    }

    private func isAnswered(_ q: Question) -> Bool {
        q.submitted || grades[q.id] != nil
    }

    private func correctness(_ q: Question) -> Bool? {
        if let grade = grades[q.id] { return jBool(grade["is_correct"]) }
        return q.storedCorrect
    }

    private func optionLetter(_ index: Int) -> String {
        let letters = ["أ", "ب", "ج", "د", "هـ", "و"]
        return index < letters.count ? letters[index] : "\(index + 1)"
    }

    private func load() async {
        await MainActor.run { loading = true; error = ""; startedAt = Date() }
        do {
            let d = try await APIClient.shared.request("mobile/exam.php", query: [URLQueryItem(name: "chapter_id", value: "\(topic.id)")])
            let qs = jArray(d["questions"]).map(Question.init)
            await MainActor.run {
                questions = qs
                for q in qs where !q.storedAnswer.isEmpty { answers[q.id] = q.storedAnswer }
                app.subscribed = jBool(d["subscribed"])
                app.freeUsed = jInt(d["free_used"])
                app.freeRemaining = d["free_remaining"] is NSNull ? nil : jInt(d["free_remaining"])
                app.csrf = jString(d["csrf"], default: app.csrf)
                loading = false
            }
        } catch {
            await MainActor.run { loading = false; self.error = error.localizedDescription }
        }
    }

    private func bindingAnswer(_ id: Int) -> Binding<String> {
        Binding(get: { answers[id] ?? "" }, set: { answers[id] = $0 })
    }

    private func bindingInteractive(_ id: Int) -> Binding<[String: String]> {
        Binding(get: { interactive[id] ?? [:] }, set: { interactive[id] = $0 })
    }

    private func bindingMatches(_ id: Int) -> Binding<[Int: MatchDraft]> {
        Binding(get: { matches[id] ?? [:] }, set: { matches[id] = $0 })
    }

    private func bindingEnglish(_ id: Int) -> Binding<[String: String]> {
        Binding(get: { english[id] ?? [:] }, set: { english[id] = $0 })
    }

    private func hasAnswer(_ q: Question) -> Bool {
        if q.englishMatch != nil { return !(english[q.id] ?? [:]).isEmpty }
        if !q.options.isEmpty { return !(answers[q.id] ?? "").isEmpty }
        if q.type == "match" {
            return q.matchItems.allSatisfy {
                let d = matches[q.id]?[$0.id]
                return !(d?.type ?? "").isEmpty && !(d?.reason ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            }
        }
        if !q.stages.isEmpty {
            let editable = q.stages.filter { $0.kind != "info" }
            return editable.allSatisfy { !(interactive[q.id]?[$0.key] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        }
        return !(answers[q.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func encodedAnswer(_ q: Question) -> String {
        if let em = q.englishMatch {
            let left = (em["left"] as? JSON ?? [:]).keys.sorted { (Int($0) ?? 0) < (Int($1) ?? 0) }
            return left.compactMap { key in
                guard let val = english[q.id]?[key], !val.isEmpty else { return nil }
                return "\(key)-\(val)"
            }.joined(separator: ", ")
        }
        if q.type == "match" {
            var items: JSON = [:]
            for item in q.matchItems {
                let d = matches[q.id]?[item.id] ?? MatchDraft()
                items[String(item.id)] = ["type": d.type, "reason": d.reason]
            }
            if let data = try? JSONSerialization.data(withJSONObject: ["items": items]),
               let text = String(data: data, encoding: .utf8) { return text }
        }
        if !q.stages.isEmpty {
            let stages = interactive[q.id] ?? [:]
            if let data = try? JSONSerialization.data(withJSONObject: ["stages": stages]),
               let text = String(data: data, encoding: .utf8) { return text }
        }
        return answers[q.id] ?? ""
    }

    private func answerDisplay(_ q: Question) -> String {
        if !q.storedAnswer.isEmpty { return q.storedAnswer }
        return encodedAnswer(q)
    }

    private func submitCurrent() {
        guard !questions.isEmpty else { return }
        let q = questions[current]
        let answer = encodedAnswer(q).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !answer.isEmpty else { return }
        submitting = true
        error = ""
        let payloadAnswers: JSON = [String(q.id): ["text": answer, "selected_text": answer, "submitted": true]]

        Task {
            do {
                let d = try await APIClient.shared.request(
                    "api/progress.php",
                    method: "POST",
                    body: [
                        "csrf": app.csrf,
                        "chapter_id": topic.id,
                        "question_index": current + 1,
                        "answers": payloadAnswers,
                        "completed": current == questions.count - 1
                    ]
                )
                var grade: JSON? = nil
                if let g = d["grades"] as? JSON { grade = g[String(q.id)] as? JSON }
                await MainActor.run {
                    if let grade { grades[q.id] = grade }
                    app.subscribed = jBool(d["subscribed"], default: app.subscribed)
                    app.freeUsed = jInt(d["free_used"], default: app.freeUsed)
                    app.freeRemaining = d["free_remaining"] is NSNull ? nil : jInt(d["free_remaining"], default: app.freeRemaining ?? 0)
                    submitting = false
                    if jBool(grade?["is_correct"]) { haptic(.success) } else { haptic(.warning) }
                }
            } catch let e as APIError {
                await MainActor.run {
                    submitting = false
                    if e.status == 402 {
                        alertMessage = e.localizedDescription
                        showAlert = true
                        showSubscription = true
                    } else {
                        error = e.localizedDescription
                        haptic(.error)
                    }
                }
            } catch {
                await MainActor.run { submitting = false; self.error = error.localizedDescription; haptic(.error) }
            }
        }
    }

    private func toggleFavorite() {
        guard !questions.isEmpty else { return }
        let idx = current
        let q = questions[idx]
        let action = q.favorite ? "unfavorite" : "favorite"
        Task {
            do {
                _ = try await APIClient.shared.request("api/question-tools.php", method: "POST", body: ["csrf": app.csrf, "question_id": q.id, "action": action])
                await MainActor.run { questions[idx].favorite.toggle(); selectionHaptic() }
            } catch {
                await MainActor.run { alertMessage = error.localizedDescription; showAlert = true; haptic(.error) }
            }
        }
    }

    private func reportCurrent() {
        guard !questions.isEmpty else { return }
        reporting = true
        let q = questions[current]
        Task {
            do {
                let d = try await APIClient.shared.request("api/question-tools.php", method: "POST", body: [
                    "csrf": app.csrf,
                    "question_id": q.id,
                    "action": "report",
                    "reason": reportReason,
                    "message": reportMessage.trimmingCharacters(in: .whitespacesAndNewlines)
                ])
                await MainActor.run {
                    reporting = false
                    showReport = false
                    reportMessage = ""
                    alertMessage = jString(d["message"], default: "تم إرسال البلاغ للمراجعة.")
                    showAlert = true
                    haptic()
                }
            } catch {
                await MainActor.run {
                    reporting = false
                    showReport = false
                    alertMessage = error.localizedDescription
                    showAlert = true
                    haptic(.error)
                }
            }
        }
    }
}

struct V3ExamResultView: View {
    let topic: Topic
    let subject: Subject
    let answered: Int
    let correct: Int
    let total: Int
    let duration: TimeInterval
    let onReview: () -> Void
    let onDone: () -> Void

    private var wrong: Int { max(0, answered - correct) }
    private var percent: Int { answered > 0 ? Int((Double(correct) / Double(answered) * 100).rounded()) : 0 }
    private var durationText: String {
        let minutes = max(0, Int(duration) / 60)
        let seconds = max(0, Int(duration) % 60)
        return String(format: "%02d:%02d", minutes, seconds)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                MurshidCard {
                    VStack(spacing: 18) {
                        ZStack {
                            Circle().fill(resultColor.opacity(0.10)).frame(width: 116, height: 116)
                            Image(systemName: resultIcon).font(.system(size: 46, weight: .semibold)).foregroundStyle(resultColor)
                        }
                        Text(resultTitle).font(.title2.bold())
                        Text("\(subject.name) • \(topic.name)").font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        Text("\(percent)%").font(.system(size: 52, weight: .heavy, design: .rounded)).foregroundStyle(resultColor).monospacedDigit()
                    }
                    .frame(maxWidth: .infinity)
                }

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    V3MetricCard(title: "الصحيحة", value: "\(correct)", icon: "checkmark.circle.fill")
                    V3MetricCard(title: "الخاطئة", value: "\(wrong)", icon: "xmark.circle.fill")
                    V3MetricCard(title: "تمت الإجابة", value: "\(answered)/\(total)", icon: "list.number")
                    V3MetricCard(title: "الوقت", value: durationText, icon: "clock.fill")
                }

                MurshidCard {
                    VStack(alignment: .leading, spacing: 11) {
                        Label("خطوتك التالية", systemImage: "sparkles").font(.headline).foregroundStyle(Color.murshidBlue)
                        Text(nextAdvice).font(.subheadline).foregroundStyle(.secondary).lineSpacing(4)
                    }
                }

                if wrong > 0 {
                    Button(action: onReview) {
                        Label("راجع الأسئلة التي تحتاج انتباهًا", systemImage: "arrow.counterclockwise.circle.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                }

                NavigationLink(destination: ReviewView()) {
                    Text("فتح المراجعة الذكية")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .foregroundStyle(Color.murshidBlue)
                        .background(Color.murshidBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 17, style: .continuous))
                }
                .buttonStyle(.plain)

                Button("العودة إلى المواضيع", action: onDone)
                    .font(.headline)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)
            }
            .padding(16)
            .padding(.bottom, 28)
        }
        .background(Color.murshidBackground)
    }

    private var resultColor: Color {
        if percent >= 85 { return .green }
        if percent >= 60 { return .murshidBlue }
        return .orange
    }

    private var resultIcon: String {
        if percent >= 85 { return "trophy.fill" }
        if percent >= 60 { return "checkmark.seal.fill" }
        return "book.fill"
    }

    private var resultTitle: String {
        if percent >= 85 { return "أداء ممتاز" }
        if percent >= 60 { return "تقدم جيد" }
        return "راجع الفكرة وحاول مرة أخرى"
    }

    private var nextAdvice: String {
        if percent >= 85 { return "انتقل إلى الموضوع التالي، وارجع إلى أخطائك القليلة في المراجعة الذكية لاحقًا." }
        if percent >= 60 { return "راجع الإجابات غير الصحيحة ثم أكمل الموضوع التالي حتى تثبت الفكرة." }
        return "الأفضل مراجعة الأخطاء الآن ثم العودة لنفس الموضوع بعد فترة قصيرة."
    }
}