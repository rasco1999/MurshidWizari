import SwiftUI

struct MatchDraft: Hashable {
    var type: String = ""
    var reason: String = ""
}

struct ExamView: View {
    let topic: Topic
    let subject: Subject
    @EnvironmentObject var app: AppSession
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

    var body: some View {
        Group {
            if loading { LoadingView(text: "جاري تجهيز الاختبار…") }
            else if !error.isEmpty && questions.isEmpty { ErrorStateView(message: error, retry: { Task { await load() } }) }
            else if questions.isEmpty { EmptyStateView(systemImage: "doc.questionmark", title: "لا توجد أسئلة متاحة", message: app.subscribed ? "لا توجد أسئلة منشورة في هذا الموضوع." : "قد تكون أكملت رصيد الأسئلة المجانية. افتح الاشتراك للمتابعة.") }
            else { examContent }
        }
        .navigationTitle(topic.name)
        .murshidNavigation()
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if !questions.isEmpty {
                    Button(action: toggleFavorite) { Image(systemName: questions[current].favorite ? "heart.fill" : "heart").foregroundColor(questions[current].favorite ? .red : .murshidBlue) }
                }
            }
        }
        .task { await load() }
        .alert("المرشد الوزاري", isPresented: $showAlert) { Button("حسنًا", role: .cancel) {} } message: { Text(alertMessage) }
        .sheet(isPresented: $showSubscription) { NavigationView { SubscriptionView().toolbar { ToolbarItem(placement: .navigationBarLeading) { Button("إغلاق") { showSubscription = false } } } }.navigationViewStyle(.stack).environmentObject(app) }
    }

    private var examContent: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 16) {
                    progressHeader
                    questionCard(questions[current])
                    answerArea(questions[current])
                    feedbackArea(questions[current])
                    if !error.isEmpty { Text(error).font(.footnote).foregroundColor(.red).frame(maxWidth: .infinity, alignment: .leading) }
                }.padding(16)
            }
            Divider()
            bottomBar
        }.background(Color(uiColor: .systemGroupedBackground))
    }

    private var progressHeader: some View {
        VStack(spacing: 8) {
            HStack { Text(subject.name).font(.caption).foregroundStyle(.secondary); Spacer(); Text("السؤال \(current + 1) من \(questions.count)").font(.caption.bold()) }
            ProgressView(value: Double(current + 1), total: Double(max(1, questions.count))).tint(.murshidBlue)
            if !app.subscribed { HStack { Image(systemName: "gift.fill"); Text("المتبقي مجانًا: \(app.freeRemaining ?? 0)"); Spacer(); Button("اشترك") { showSubscription = true } }.font(.caption).foregroundColor(.murshidGold) }
        }
    }

    private func questionCard(_ q: Question) -> some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 11) {
                HStack {
                    Text("وزاري")
                        .font(.caption.bold())
                        .foregroundStyle(Color.murshidBlue)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Color.murshidBlue.opacity(0.10), in: Capsule())
                    Spacer()
                }
                if !q.meaningWord.isEmpty { Text(q.meaningWord).font(.headline).foregroundColor(.murshidGold) }
                Text(murshidQuestionDisplayText(q.text)).font(.title3.weight(.semibold)).fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    @ViewBuilder
    private func answerArea(_ q: Question) -> some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("إجابتك").font(.headline)
                if q.submitted || grades[q.id] != nil {
                    Text(answerDisplay(q)).foregroundStyle(.secondary).textSelection(.enabled)
                } else if let em = q.englishMatch {
                    EnglishMatchInput(definition: em, values: bindingEnglish(q.id))
                } else if !q.options.isEmpty {
                    ForEach(q.options) { option in
                        Button {
                            guard !q.submitted, grades[q.id] == nil, !submitting else { return }
                            answers[q.id] = option.text
                            UISelectionFeedbackGenerator().selectionChanged()
                            submitCurrent(answerOverride: option.text)
                        } label: {
                            HStack { Image(systemName: answers[q.id] == option.text ? "checkmark.circle.fill" : "circle"); Text(option.text).multilineTextAlignment(.leading); Spacer() }
                                .padding(12).foregroundColor(.primary)
                                .background(answers[q.id] == option.text ? Color.murshidBlue.opacity(0.14) : Color(uiColor: .tertiarySystemFill))
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                        }.buttonStyle(.plain)
                    }
                } else if q.type == "match" && !q.matchItems.isEmpty {
                    MatchInput(items: q.matchItems, drafts: bindingMatches(q.id))
                } else if !q.stages.isEmpty {
                    InteractiveInput(stages: q.stages, values: bindingInteractive(q.id))
                } else {
                    TextEditor(text: bindingAnswer(q.id)).frame(minHeight: 110).padding(8).scrollContentBackground(.hidden).background(Color(uiColor: .tertiarySystemFill)).clipShape(RoundedRectangle(cornerRadius: 14))
                }
                if !q.submitted && grades[q.id] == nil && q.options.isEmpty {
                    Button(action: { submitCurrent() }) { HStack { if submitting { ProgressView().tint(.white) }; Text(submitting ? "جاري تصحيح الإجابة…" : "إرسال الإجابة") } }.buttonStyle(PrimaryButtonStyle()).disabled(submitting || !hasAnswer(q))
                }
            }
        }
    }

    @ViewBuilder
    private func feedbackArea(_ q: Question) -> some View {
        let grade = grades[q.id]
        let serverCorrect = grade.map { jBool($0["is_correct"]) } ?? q.storedCorrect
        if q.submitted || grade != nil {
            MurshidCard {
                VStack(alignment: .leading, spacing: 9) {
                    Label(serverCorrect == true ? "إجابة صحيحة" : "راجع الإجابة", systemImage: serverCorrect == true ? "checkmark.seal.fill" : "xmark.octagon.fill")
                        .font(.headline).foregroundColor(serverCorrect == true ? .green : .orange)
                    let serverCorrectAnswer = jString(grade?["correct_answer"], default: q.correctAnswer)
                    let serverExplanation = jString(grade?["explanation"], default: q.explanation)
                    if !serverCorrectAnswer.isEmpty { Text("الإجابة النموذجية: \(serverCorrectAnswer)").font(.subheadline.weight(.semibold)) }
                    Divider()
                    Text("شرح الإجابة").font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                    if !serverExplanation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Text(serverExplanation).font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var bottomBar: some View {
        HStack(spacing: 12) {
            Button { if current > 0 { current -= 1; UISelectionFeedbackGenerator().selectionChanged() } } label: { Label("السابق", systemImage: "chevron.right") }.disabled(current == 0)
            Spacer()
            Text("\(current + 1)/\(questions.count)").font(.subheadline.monospacedDigit()).foregroundStyle(.secondary)
            Spacer()
            Button {
                guard current < questions.count - 1, isAnswered(questions[current]), !submitting else { return }
                current += 1; UISelectionFeedbackGenerator().selectionChanged()
            } label: { Label(current == questions.count - 1 ? "النهاية" : "التالي", systemImage: "chevron.left") }
            .disabled(current == questions.count - 1 || !isAnswered(questions[current]) || submitting)
        }.padding(.horizontal, 16).padding(.vertical, 12).background(.ultraThinMaterial)
    }

    private func load() async {
        await MainActor.run { loading = true; error = "" }
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
        } catch { await MainActor.run { loading = false; self.error = error.localizedDescription } }
    }

    private func bindingAnswer(_ id: Int) -> Binding<String> { Binding(get: { answers[id] ?? "" }, set: { answers[id] = $0 }) }
    private func bindingInteractive(_ id: Int) -> Binding<[String:String]> { Binding(get: { interactive[id] ?? [:] }, set: { interactive[id] = $0 }) }
    private func bindingMatches(_ id: Int) -> Binding<[Int:MatchDraft]> { Binding(get: { matches[id] ?? [:] }, set: { matches[id] = $0 }) }
    private func bindingEnglish(_ id: Int) -> Binding<[String:String]> { Binding(get: { english[id] ?? [:] }, set: { english[id] = $0 }) }

    private func hasAnswer(_ q: Question) -> Bool {
        if q.englishMatch != nil { return !(english[q.id] ?? [:]).isEmpty }
        if !q.options.isEmpty { return !(answers[q.id] ?? "").isEmpty }
        if q.type == "match" { return q.matchItems.allSatisfy { let d = matches[q.id]?[$0.id]; return !(d?.type ?? "").isEmpty && !(d?.reason ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty } }
        if !q.stages.isEmpty {
            let editable = q.stages.filter { $0.kind != "info" }
            return editable.allSatisfy { !(interactive[q.id]?[$0.key] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        }
        return !(answers[q.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func encodedAnswer(_ q: Question) -> String {
        if let em = q.englishMatch {
            let left = (em["left"] as? JSON ?? [:]).keys.sorted { (Int($0) ?? 0) < (Int($1) ?? 0) }
            return left.compactMap { key in guard let val = english[q.id]?[key], !val.isEmpty else { return nil }; return "\(key)-\(val)" }.joined(separator: ", ")
        }
        if q.type == "match" {
            var items: JSON = [:]
            for item in q.matchItems { let d = matches[q.id]?[item.id] ?? MatchDraft(); items[String(item.id)] = ["type": d.type, "reason": d.reason] }
            if let data = try? JSONSerialization.data(withJSONObject: ["items": items]), let text = String(data: data, encoding: .utf8) { return text }
        }
        if !q.stages.isEmpty {
            let stages = interactive[q.id] ?? [:]
            if let data = try? JSONSerialization.data(withJSONObject: ["stages": stages]), let text = String(data: data, encoding: .utf8) { return text }
        }
        return answers[q.id] ?? ""
    }

    private func answerDisplay(_ q: Question) -> String {
        if !q.storedAnswer.isEmpty { return q.storedAnswer }
        return encodedAnswer(q)
    }

    private func isAnswered(_ q: Question) -> Bool { q.submitted || grades[q.id] != nil }

    private func submitCurrent(answerOverride: String? = nil) {
        guard !questions.isEmpty else { return }
        let q = questions[current]
        let answer = (answerOverride ?? encodedAnswer(q)).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !answer.isEmpty else { return }
        if let answerOverride { answers[q.id] = answerOverride }
        submitting = true; error = ""
        let payloadAnswers: JSON = [String(q.id): ["text": answer, "selected_text": answer, "submitted": true]]
        Task {
            do {
                let d = try await APIClient.shared.request("api/progress.php", method: "POST", body: ["csrf": app.csrf, "chapter_id": topic.id, "question_index": current + 1, "answers": payloadAnswers, "completed": current == questions.count - 1])
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
                    if e.status == 402 { alertMessage = e.localizedDescription; showAlert = true; showSubscription = true }
                    else { error = e.localizedDescription; haptic(.error) }
                }
            } catch { await MainActor.run { submitting = false; self.error = error.localizedDescription; haptic(.error) } }
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
                await MainActor.run { questions[idx].favorite.toggle(); UISelectionFeedbackGenerator().selectionChanged() }
            } catch { await MainActor.run { alertMessage = error.localizedDescription; showAlert = true } }
        }
    }
}

struct InteractiveInput: View {
    let stages: [InteractiveStage]
    @Binding var values: [String: String]
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(stages) { stage in
                if stage.kind == "info" {
                    VStack(alignment: .leading, spacing: 5) { Text(stage.label).font(.subheadline.bold()); Text(stage.value).font(.system(.body, design: .monospaced)).foregroundStyle(.secondary) }.frame(maxWidth: .infinity, alignment: .leading).padding(12).background(Color.murshidBlue.opacity(0.08)).clipShape(RoundedRectangle(cornerRadius: 12))
                } else if stage.kind == "choice" && !stage.options.isEmpty {
                    VStack(alignment: .leading, spacing: 6) { Text(stage.label).font(.subheadline.bold()); Picker(stage.label, selection: Binding(get: { values[stage.key] ?? "" }, set: { values[stage.key] = $0 })) { Text("اختر").tag(""); ForEach(stage.options, id: \.value) { opt in Text(opt.label).tag(opt.value) } }.pickerStyle(.menu) }
                } else {
                    VStack(alignment: .leading, spacing: 6) { Text(stage.label).font(.subheadline.bold()); HStack { TextField(stage.placeholder, text: Binding(get: { values[stage.key] ?? "" }, set: { values[stage.key] = $0 })).keyboardType(stage.kind == "number" ? .decimalPad : .default).padding(10).background(Color(uiColor: .tertiarySystemFill)).clipShape(RoundedRectangle(cornerRadius: 12)); if !stage.unit.isEmpty { Text(stage.unit).foregroundStyle(.secondary) } } }
                }
            }
        }
    }
}

struct MatchInput: View {
    let items: [MatchItem]
    @Binding var drafts: [Int: MatchDraft]
    private let types = ["مد طبيعي","مد بدل","مد متصل","مد منفصل","مد لازم","مد عارض للسكون","مد لين"]
    var body: some View {
        VStack(spacing: 14) {
            ForEach(items) { item in
                VStack(alignment: .leading, spacing: 8) {
                    Text(item.word).font(.headline).foregroundColor(.murshidBlue)
                    Picker("نوع المد", selection: Binding(get: { drafts[item.id]?.type ?? "" }, set: { var d = drafts[item.id] ?? MatchDraft(); d.type = $0; drafts[item.id] = d })) { Text("اختر نوع المد").tag(""); ForEach(types, id: \.self) { Text($0).tag($0) } }.pickerStyle(.menu)
                    TextField("اكتب السبب", text: Binding(get: { drafts[item.id]?.reason ?? "" }, set: { var d = drafts[item.id] ?? MatchDraft(); d.reason = $0; drafts[item.id] = d })).padding(10).background(Color(uiColor: .tertiarySystemFill)).clipShape(RoundedRectangle(cornerRadius: 12))
                }.padding(12).background(Color.primary.opacity(0.035)).clipShape(RoundedRectangle(cornerRadius: 14))
            }
        }
    }
}

struct EnglishMatchInput: View {
    let definition: JSON
    @Binding var values: [String: String]
    var body: some View {
        let left = definition["left"] as? JSON ?? [:]
        let right = definition["right"] as? JSON ?? [:]
        let keys = left.keys.sorted { (Int($0) ?? 0) < (Int($1) ?? 0) }
        VStack(spacing: 12) {
            ForEach(keys, id: \.self) { key in
                VStack(alignment: .leading, spacing: 7) {
                    Text("\(key). \(jString(left[key]))").font(.headline)
                    Picker("اختر المطابقة", selection: Binding(get: { values[key] ?? "" }, set: { values[key] = $0 })) {
                        Text("اختر").tag("")
                        ForEach(right.keys.sorted(), id: \.self) { rkey in Text("\(rkey) — \(jString(right[rkey]))").tag(rkey) }
                    }.pickerStyle(.menu)
                }.padding(10).background(Color.primary.opacity(0.035)).clipShape(RoundedRectangle(cornerRadius: 13))
            }
        }
    }
}