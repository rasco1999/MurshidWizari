import SwiftUI
import AVFoundation
import LocalAuthentication

// MARK: - iPhone-only v4.9: previously graded questions, never an offline paid exam

// Offline access is not derived from a remembered web session or subscription.
enum V49OfflineLaunchRules {
    static func showLocalCoach(networkUnavailable: Bool, hasVerifiedArchive: Bool) -> Bool {
        networkUnavailable && hasVerifiedArchive
    }

    static func showPublicGuest(networkUnavailable: Bool, hasVerifiedArchive: Bool) -> Bool {
        networkUnavailable && !hasVerifiedArchive
    }
}


struct V49StudyRecord: Identifiable, Codable, Hashable {
    let id: Int
    let subjectID: Int
    let subjectName: String
    let topicID: Int
    let topicName: String
    let question: String
    let correctAnswer: String
    let explanation: String
    let examYear: String
    let studentAnswer: String
    let wasCorrect: Bool
    var reviewedAt: Date
    var dueAt: Date
    var lastPracticedAt: Date?
    var successfulReviews: Int
    var lapses: Int
}

struct V49StudyArchive: Codable {
    let userID: Int
    var name: String
    var grade: String
    var records: [V49StudyRecord]
    var studyDays: [String]
}

// Validate only the offline review collection. The official exam retains the
// server's original question order/index and its authoritative quota checks.
enum V49QuestionQuality {
    static func normalized(_ value: String) -> String {
        let folded = value.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "ar"))
            .replacingOccurrences(of: "أ", with: "ا")
            .replacingOccurrences(of: "إ", with: "ا")
            .replacingOccurrences(of: "آ", with: "ا")
            .replacingOccurrences(of: "ى", with: "ي")
        return folded.unicodeScalars
            .filter { CharacterSet.alphanumerics.contains($0) }
            .map { String($0) }
            .joined()
    }

    static func validForReview(_ q: Question) -> Bool {
        q.id > 0 && !normalized(q.text + q.meaningWord).isEmpty &&
            !normalized(q.correctAnswer).isEmpty
    }

    static func qualitySummary(_ questions: [Question]) -> (invalid: Int, duplicate: Int) {
        var seenIDs = Set<Int>()
        var seenTexts = Set<String>()
        var invalid = 0
        var duplicate = 0
        for q in questions {
            let choices = q.options.map(\.text).joined(separator: " ")
            let matchWords = q.matchItems.map(\.word).joined(separator: " ")
            let key = normalized(q.text + " " + q.meaningWord + " " + choices + " " + matchWords)
            if q.id <= 0 || key.isEmpty { invalid += 1; continue }
            if !seenIDs.insert(q.id).inserted || !seenTexts.insert(key).inserted { duplicate += 1 }
        }
        return (invalid, duplicate)
    }
}

@MainActor
final class V49StudyStore: ObservableObject {
    static let shared = V49StudyStore()

    @Published private(set) var archive: V49StudyArchive?
    private let recentUserKey = "murshid_v49_last_offline_user"
    private let maxRecords = 800

    private init() {}

    private func fileURL(for userID: Int) -> URL? {
        guard userID > 0,
              let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else { return nil }
        return base.appendingPathComponent("MurshidOfflineReview", isDirectory: true)
            .appendingPathComponent("review-\(userID).json")
    }

    private func read(userID: Int) -> V49StudyArchive? {
        guard let url = fileURL(for: userID),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode(V49StudyArchive.self, from: data),
              decoded.userID == userID else { return nil }
        return decoded
    }

    func bind(to user: UserSummary) {
        guard user.id > 0 else { return }
        if archive?.userID != user.id {
            archive = read(userID: user.id) ?? V49StudyArchive(
                userID: user.id, name: user.name, grade: user.grade, records: [], studyDays: []
            )
        }
        archive?.name = user.name
        archive?.grade = user.grade
        UserDefaults.standard.set(user.id, forKey: recentUserKey)
        save()
    }

    // Identity comes from a successful online bootstrap, independently of
    // the "remember me" setting. Zero saved questions must not block launch.
    func hasOfflineAccount() -> Bool {
        let userID = UserDefaults.standard.integer(forKey: recentUserKey)
        return userID > 0 && read(userID: userID) != nil
    }

    func hasOfflineReview() -> Bool {
        let userID = UserDefaults.standard.integer(forKey: recentUserKey)
        return userID > 0 && !(read(userID: userID)?.records.isEmpty ?? true)
    }

    // Called only after iOS device-owner authentication.
    @discardableResult func openOfflineReview() -> Bool {
        let userID = UserDefaults.standard.integer(forKey: recentUserKey)
        guard userID > 0, let saved = read(userID: userID) else { return false }
        archive = saved
        return true
    }

    func eraseOnLogout(userID: Int?) {
        // A password reset can occur with no active server session. Explicit
        // revocation must then still clear the last locally verified account.
        let storedID = UserDefaults.standard.integer(forKey: recentUserKey)
        let targetID = userID.flatMap { $0 > 0 ? $0 : nil } ?? storedID
        if targetID > 0, let url = fileURL(for: targetID) {
            try? FileManager.default.removeItem(at: url)
        }
        if storedID == targetID {
            UserDefaults.standard.removeObject(forKey: recentUserKey)
        }
        archive = nil
    }

    func rememberGraded(_ q: Question, subject: Subject, topic: Topic, answer: String,
                       correct: Bool, correctAnswer: String? = nil, explanation: String? = nil,
                       persistImmediately: Bool = true) {
        let verifiedAnswer = correctAnswer ?? q.correctAnswer
        guard q.id > 0, !V49QuestionQuality.normalized(q.text + q.meaningWord).isEmpty,
              !V49QuestionQuality.normalized(verifiedAnswer).isEmpty, var archive,
              !answer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        let key = V49QuestionQuality.normalized(q.text + " " + q.meaningWord)
        // Short common stems like "choose the correct answer" can legitimately
        // repeat with different choices, so never collapse those MCQs.
        if q.options.isEmpty && q.matchItems.isEmpty && key.count > 30 {
            guard !archive.records.contains(where: {
                $0.id != q.id && $0.topicID == topic.id &&
                V49QuestionQuality.normalized($0.question) == key &&
                V49QuestionQuality.normalized($0.correctAnswer) == V49QuestionQuality.normalized(verifiedAnswer)
            }) else { return }
        }
        let now = Date()
        let old = archive.records.first(where: { $0.id == q.id })
        let record = V49StudyRecord(
            id: q.id, subjectID: subject.id, subjectName: subject.name,
            topicID: topic.id, topicName: topic.name,
            question: String((q.text.isEmpty ? q.meaningWord : q.text).prefix(6000)),
            correctAnswer: String(verifiedAnswer.prefix(3000)),
            explanation: String((explanation ?? q.explanation).prefix(6000)), examYear: q.examYear,
            studentAnswer: String(answer.prefix(3000)), wasCorrect: correct,
            reviewedAt: now,
            dueAt: old?.dueAt ?? (correct ? now.addingTimeInterval(2 * 86_400) : now),
            lastPracticedAt: old?.lastPracticedAt,
            successfulReviews: old?.successfulReviews ?? 0,
            lapses: old?.lapses ?? 0
        )
        archive.records.removeAll(where: { $0.id == q.id })
        archive.records.insert(record, at: 0)
        archive.records = Array(archive.records.prefix(maxRecords))
        let day = Self.dayKey(now)
        if !archive.studyDays.contains(day) { archive.studyDays.append(day) }
        self.archive = archive
        if persistImmediately { save() }
    }

    func rememberPreviouslyGraded(_ questions: [Question], subject: Subject, topic: Topic) {
        let existingIDs = Set(records.map(\.id))
        var added = false
        for q in questions where q.submitted && q.storedCorrect != nil && !existingIDs.contains(q.id) {
            rememberGraded(q, subject: subject, topic: topic, answer: q.storedAnswer,
                           correct: q.storedCorrect == true, persistImmediately: false)
            added = true
        }
        if added { save() }
    }

    func markRecall(questionID: Int, remembered: Bool) {
        guard var archive, let i = archive.records.firstIndex(where: { $0.id == questionID }) else { return }
        let now = Date()
        archive.records[i].lastPracticedAt = now
        if remembered {
            archive.records[i].successfulReviews += 1
            let n = min(4, archive.records[i].successfulReviews)
            archive.records[i].dueAt = Calendar.current.date(byAdding: .day, value: 1 << n, to: now) ?? now
        } else {
            archive.records[i].lapses += 1
            archive.records[i].successfulReviews = 0
            archive.records[i].dueAt = now
        }
        let day = Self.dayKey(now)
        if !archive.studyDays.contains(day) { archive.studyDays.append(day) }
        self.archive = archive
        save()
    }

    var records: [V49StudyRecord] { archive?.records ?? [] }
    var due: [V49StudyRecord] {
        records.filter { $0.dueAt <= Date() }
            .sorted { priority($0) > priority($1) }
    }
    var mistakes: [V49StudyRecord] {
        records.filter { !$0.wasCorrect || $0.lapses > 0 }
            .sorted { priority($0) > priority($1) }
    }
    var todayReviews: Int {
        records.filter { $0.lastPracticedAt.map(Self.dayKey) == Self.dayKey(Date()) }.count
    }
    var streak: Int {
        let days = Set(archive?.studyDays ?? [])
        let today = Calendar.current.startOfDay(for: Date())
        var cursor = days.contains(Self.dayKey(today)) ? today : (Calendar.current.date(byAdding: .day, value: -1, to: today) ?? today)
        var count = 0
        while count < 365 && days.contains(Self.dayKey(cursor)) {
            count += 1
            cursor = Calendar.current.date(byAdding: .day, value: -1, to: cursor) ?? cursor.addingTimeInterval(-86_400)
        }
        return count
    }

    private func priority(_ q: V49StudyRecord) -> Int {
        (q.wasCorrect ? 0 : 100) + min(q.lapses * 15, 150) + (q.dueAt <= Date() ? 50 : 0)
    }

    private static func dayKey(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    private func save() {
        guard let archive, let url = fileURL(for: archive.userID),
              let data = try? JSONEncoder().encode(archive) else { return }
        do {
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        } catch {
            // Local review is optional; a storage failure must not block paid exams.
            NSLog("Offline review cache unavailable: %@", String(describing: error))
        }
    }
}

// AVSpeechSynthesizer and the shared audio-session lifecycle live in
// V498QuestionSpeech.swift. Both online explanations and offline recall use it.

// MARK: - Smart review home (does not claim server rankings or academic grades)

struct V49StudyHubView: View {
    var offlineOnly = false
    @ObservedObject private var store = V49StudyStore.shared
    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 15) {
                VStack(alignment: .leading, spacing: 9) {
                    Label("مدربك الذكي", systemImage: "brain.head.profile")
                        .font(.title2.bold()).foregroundStyle(.white)
                    Text("راجع الأسئلة المصححة سابقًا حسب أخطائك ومواعيد المراجعة. يعمل هذا التدريب دون إنترنت.")
                        .font(.subheadline).foregroundStyle(.white.opacity(0.87))
                    if offlineOnly {
                        Label("وضع دون إنترنت · لا تُرسل إجابات ولا تُستهلك أسئلة مجانية", systemImage: "wifi.slash")
                            .font(.caption.bold()).foregroundStyle(Color.murshidGold)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
                .background(LinearGradient(colors: [.murshidNavy, .murshidBlue], startPoint: .topTrailing, endPoint: .bottomLeading), in: RoundedRectangle(cornerRadius: 22))

                LazyVGrid(columns: columns, spacing: 12) {
                    V3MetricCard(title: "محفوظ للمراجعة", value: "\(store.records.count)", icon: "books.vertical")
                    V3MetricCard(title: "حان وقتها", value: "\(store.due.count)", icon: "alarm.fill")
                    V3MetricCard(title: "أيام المواظبة", value: "\(store.streak)", icon: "flame.fill")
                    V3MetricCard(title: "تدريب اليوم", value: "\(store.todayReviews)/10", icon: "target")
                }

                MurshidCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Label("تحدي 10 مراجعات اليوم", systemImage: "sparkles")
                            .font(.headline)
                        ProgressView(value: Double(min(store.todayReviews, 10)), total: 10).tint(.murshidBlue)
                        Text(store.todayReviews >= 10 ? "أتممت تحدي اليوم! 🎉" : "تبقّى \(10 - min(store.todayReviews, 10)) أسئلة للوصول إلى هدفك اليومي.")
                            .font(.subheadline).foregroundStyle(.secondary)
                        if store.todayReviews >= 10 {
                            ShareLink(item: "أكملت تحدي 10 مراجعات اليوم في منصة المرشد الوزاري 📚🔥") {
                                Label("شارك إنجازك", systemImage: "square.and.arrow.up")
                            }
                        }
                    }
                }

                if store.records.isEmpty {
                    MurshidCard {
                        VStack(spacing: 12) {
                            Image(systemName: "book.closed").font(.largeTitle).foregroundStyle(Color.murshidBlue)
                            Text("دفتر مراجعتك ما زال فارغًا").font(.headline)
                            Text("اتصل بالإنترنت وحل سؤالًا في الاختبار الرسمي حتى تُصحح إجابتك. عندها يمكن مراجعته هنا لاحقًا دون اتصال.")
                                .font(.subheadline).multilineTextAlignment(.center).foregroundStyle(.secondary)
                        }.frame(maxWidth: .infinity).padding(12)
                    }
                } else {
                    reviewLink("ابدأ مراجعتك الموصى بها", subtitle: "الأخطاء أولًا ثم الأسئلة المستحقة", icon: "sparkles", records: store.due)
                    reviewLink("دفتر الأخطاء", subtitle: "\(store.mistakes.count) سؤالًا يحتاج انتباهك", icon: "exclamationmark.circle", records: store.mistakes)
                    reviewLink("كل الأسئلة المحفوظة", subtitle: "مراجعة حرة لما تعلمته", icon: "rectangle.stack", records: store.records)

                    if let first = store.due.first ?? store.mistakes.first {
                        MurshidCard {
                            VStack(alignment: .leading, spacing: 7) {
                                Label("اقتراح المدرب", systemImage: "lightbulb.fill").font(.headline).foregroundStyle(Color.murshidBlue)
                                Text(first.subjectName + " · " + first.topicName).font(.subheadline.bold())
                                Text(first.wasCorrect ? "حان وقت تثبيت هذه المعلومة قبل نسيانها." : "لنبدأ بسؤال أخطأت فيه سابقًا حتى تثبت الإجابة.")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                Text("المدرب والمكافآت هنا تدريب محلي؛ لا يغيران درجاتك أو ترتيب تحدي المليون أو اشتراكك الرسمي.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            .padding(16)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("مدربي الذكي")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func reviewLink(_ title: String, subtitle: String, icon: String, records: [V49StudyRecord]) -> some View {
        NavigationLink(destination: V49RecallView(questionIDs: records.map(\.id))) {
            MurshidCard {
                HStack(spacing: 12) {
                    Image(systemName: icon).foregroundStyle(Color.murshidBlue).font(.title2)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(title).font(.headline).foregroundStyle(.primary)
                        Text(subtitle).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.left").foregroundStyle(.secondary)
                }
            }
        }
        .disabled(records.isEmpty)
        .opacity(records.isEmpty ? 0.55 : 1)
        .buttonStyle(.plain)
    }
}

struct V49RecallView: View {
    let questionIDs: [Int]
    @ObservedObject private var store = V49StudyStore.shared
    @State private var index = 0
    @State private var reveal = false
    @State private var remembered = 0
    @State private var needsReview = 0

    private var record: V49StudyRecord? {
        guard questionIDs.indices.contains(index) else { return nil }
        return store.records.first(where: { $0.id == questionIDs[index] })
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let q = record {
                    HStack {
                        Text("\(index + 1) / \(questionIDs.count)")
                            .font(.subheadline.bold().monospacedDigit())
                        Spacer()
                        Text(q.subjectName).font(.subheadline).foregroundStyle(.secondary)
                    }
                    ProgressView(value: Double(index + 1), total: Double(max(questionIDs.count, 1))).tint(.murshidBlue)
                    MurshidCard {
                        VStack(alignment: .leading, spacing: 15) {
                            Text(q.topicName).font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                            Text(q.question).font(.title3.bold()).lineSpacing(5).textSelection(.enabled)
                            if reveal {
                                Divider()
                                Text("الإجابة النموذجية").font(.caption.bold()).foregroundStyle(.secondary)
                                Text(q.correctAnswer).font(.body.bold()).foregroundStyle(.green)
                                if !q.explanation.isEmpty {
                                    Text("الشرح").font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                                    Text(q.explanation).font(.subheadline).lineSpacing(4)
                                } else {
                                    Text("لا يتوفر شرح معتمد لهذا السؤال حتى الآن.").font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    if reveal {
                        Text("قيّم تذكرك بصدق؛ هذا تدريب شخصي لا يُرسل إلى الخادم.")
                            .font(.caption).foregroundStyle(.secondary)
                        HStack(spacing: 12) {
                            Button { finish(q, remembered: false) } label: {
                                Label("أحتاج مراجعة", systemImage: "arrow.counterclockwise")
                                    .frame(maxWidth: .infinity)
                            }.buttonStyle(.bordered)
                            Button { finish(q, remembered: true) } label: {
                                Label("تذكّرت", systemImage: "checkmark.circle.fill")
                                    .frame(maxWidth: .infinity)
                            }.buttonStyle(.borderedProminent).tint(.murshidBlue)
                        }
                    } else {
                        Button { reveal = true; selectionHaptic() } label: {
                            Label("اكشف الإجابة", systemImage: "eye.fill")
                                .font(.headline).frame(maxWidth: .infinity)
                        }.buttonStyle(.borderedProminent).tint(.murshidBlue)
                        Text("حاول الإجابة من ذاكرتك قبل الكشف.").font(.caption).foregroundStyle(.secondary)
                    }
                } else {
                    MurshidCard {
                        VStack(spacing: 13) {
                            Image(systemName: "checkmark.seal.fill").font(.system(size: 46)).foregroundStyle(.green)
                            Text(questionIDs.isEmpty ? "لا توجد مراجعات مستحقة" : "أحسنت، انتهت جلسة المراجعة!").font(.title3.bold())
                            if !questionIDs.isEmpty {
                                Text("تذكّرت \(remembered) · يحتاج مراجعة \(needsReview)")
                                    .font(.subheadline).foregroundStyle(.secondary)
                            }
                        }.frame(maxWidth: .infinity).padding(18)
                    }
                }
            }.padding(16)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("مراجعة نشطة")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func finish(_ q: V49StudyRecord, remembered didRemember: Bool) {
        store.markRecall(questionID: q.id, remembered: didRemember)
        if didRemember { remembered += 1; haptic(.success) }
        else { needsReview += 1; haptic(.warning) }
        reveal = false
        index += 1
    }
}

// The standard bootstrap never grants a paid entitlement when disconnected.
// Only pre-graded, on-device cards become accessible after device authentication.
struct V49OfflineAccessView: View {
    @State private var unlocked = false
    @State private var unlocking = false
    @State private var message = ""
    @ObservedObject private var store = V49StudyStore.shared

    var body: some View {
        Group {
            if unlocked {
                NavigationStack { V49StudyHubView(offlineOnly: true) }
            } else {
                VStack(spacing: 16) {
                    Image(systemName: "books.vertical.circle.fill")
                        .font(.system(size: 50)).foregroundStyle(Color.murshidBlue)
                    Text("مراجعاتك محفوظة على الآيفون").font(.title3.bold())
                    Text("افتح مدربي الذكي ومراجعاتك على هذا الجهاز بعد التحقق ببصمة الوجه أو رمز قفل الجهاز. لا يشترط تفعيل «تذكّرني».")
                        .font(.subheadline).multilineTextAlignment(.center).foregroundStyle(.secondary)
                    Button { authenticate() } label: {
                        HStack {
                            if unlocking { ProgressView().tint(.white) }
                            Label("فتح المراجعة دون إنترنت", systemImage: "lock.open.fill")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent).tint(.murshidBlue).disabled(unlocking)
                    if !message.isEmpty { Text(message).font(.caption).foregroundStyle(.red) }
                }
                .frame(maxWidth: 430).padding(24)
            }
        }
    }

    private func authenticate() {
        unlocking = true
        Task { @MainActor in
            do {
                let context = LAContext()
                let allowed = try await context.evaluatePolicy(
                    .deviceOwnerAuthentication,
                    localizedReason: "افتح مراجعاتك المحفوظة في المرشد الوزاري"
                )
                if allowed && store.openOfflineReview() { unlocked = true }
                else { message = "لم نجد مراجعات محفوظة لهذا الحساب." }
            } catch {
                message = "تعذّر فتح المراجعة. تحقق من قفل الجهاز وأعد المحاولة."
            }
            unlocking = false
        }
    }
}

