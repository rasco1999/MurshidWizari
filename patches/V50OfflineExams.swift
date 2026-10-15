import Foundation
import SwiftUI
import CryptoKit
import Security

// Private device-only cache; subscription and official grades remain server-authoritative.
struct V50OfflineQuestion: Codable, Identifiable {
    struct Option: Codable, Identifiable {
        let id: Int
        let text: String
    }
    let id: Int
    let type: String
    let text: String
    let meaningWord: String
    let options: [Option]
    let correctAnswer: String
    let explanation: String
    var submitted: Bool
    let serverIndex: Int
}

struct V50OfflineTopic: Codable, Identifiable {
    let id: Int
    let name: String
    let savedAt: Date
    var questions: [V50OfflineQuestion]
}

struct V50OfflineAnswer: Codable, Identifiable {
    var id: Int { questionID }
    let chapterID: Int
    let questionID: Int
    let serverIndex: Int
    let text: String
    let optionID: Int
    let createdAt: Date
    var revision: String? = nil
}

struct V50OfflineVault: Codable {
    let userID: Int
    var verifiedAt: Date
    var expiresAt: Date
    var topics: [V50OfflineTopic]
    var pending: [V50OfflineAnswer]
}

enum V50OfflineRules {
    enum Feedback: String { case correct, incorrect, needsServer }
    static func supports(_ type: String, optionCount: Int) -> Bool {
        switch type {
        case "mcq": return optionCount >= 2
        case "true_false", "text", "fill", "meaning": return true
        default: return false
        }
    }
    static func revision(_ question: V50OfflineQuestion) -> String {
        var fields = [String(question.id), question.type, question.text,
                      question.meaningWord, question.correctAnswer, String(question.options.count)]
        for option in question.options { fields += [String(option.id), option.text] }
        return SHA256.hash(data: Data(fields.joined(separator: "\u{001f}").utf8))
            .map { String(format: "%02x", $0) }.joined()
    }
    static func normalized(_ value: String) -> String {
        let digits = Array("٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹".unicodeScalars)
        var text = ""
        for scalar in value.precomposedStringWithCanonicalMapping.unicodeScalars {
            if let index = digits.firstIndex(of: scalar) { text += String(index % 10) }
            else if (0x064b...0x0652).contains(scalar.value) || scalar.value == 0x0670 || scalar.value == 0x0640 { continue }
            else if CharacterSet.whitespacesAndNewlines.contains(scalar) { text += " " }
            // Preserve operators and punctuation: "2+3" must never become "23".
            else { text += String(scalar).lowercased() }
        }
        var result = text.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
        while result.last == "." || result.last == " " { result.removeLast() }
        return result
    }
    static func feedback(_ question: V50OfflineQuestion, answer: String) -> Feedback {
        let given = normalized(answer), correct = normalized(question.correctAnswer)
        guard !given.isEmpty, !correct.isEmpty else { return .needsServer }
        if given == correct { return .correct }
        let choices = question.options.isEmpty && question.type == "true_false"
            ? ["صح", "خطأ"] : question.options.map(\.text)
        let labels = choices.map(normalized)
        if ["mcq", "true_false"].contains(question.type), labels.contains(given), labels.contains(correct) {
            return .incorrect
        }
        let pattern = #"^[-+]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)$"#
        if given.range(of: pattern, options: .regularExpression) != nil,
           correct.range(of: pattern, options: .regularExpression) != nil,
           let a = Decimal(string: given, locale: Locale(identifier: "en_US_POSIX")),
           let b = Decimal(string: correct, locale: Locale(identifier: "en_US_POSIX")) {
            return a == b ? .correct : .incorrect
        }
        return .needsServer
    }
}

@MainActor
final class V50OfflineExamBank: ObservableObject {
    static let shared = V50OfflineExamBank()
    @Published private(set) var vault: V50OfflineVault?
    @Published private(set) var syncing = false
    @Published private(set) var syncMessage = ""
    private let maxAge: TimeInterval = 7 * 86_400
    private let service = "com.muriq.murshid.offline.exam.secret.v1"
    private let account = "device-only"

    private init() { vault = read() }

    private var url: URL? {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)
            .first?.appendingPathComponent("MurshidOfflineExams", isDirectory: true)
            .appendingPathComponent("vault.enc")
    }

    private func secret() -> SymmetricKey? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecSuccess, let data = result as? Data, data.count == 32 {
            return SymmetricKey(data: data)
        }
        guard status == errSecItemNotFound else { return nil }
        let generated = SymmetricKey(size: .bits256)
        let data = generated.withUnsafeBytes { Data($0) }
        let insert: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: data
        ]
        let added = SecItemAdd(insert as CFDictionary, nil)
        #if DEBUG
        if added != errSecSuccess { NSLog("Offline Keychain status: %d", added) }
        #endif
        return added == errSecSuccess ? generated : nil
    }

    private func read() -> V50OfflineVault? {
        guard let url, let encrypted = try? Data(contentsOf: url),
              let secret = secret(), let sealed = try? AES.GCM.SealedBox(combined: encrypted),
              let decoded = try? AES.GCM.open(sealed, using: secret),
              let vault = try? JSONDecoder().decode(V50OfflineVault.self, from: decoded)
        else { return nil }
        return vault
    }

    private func persist() -> Bool {
        guard let vault, let url, let secret = secret(),
              let data = try? JSONEncoder().encode(vault),
              let encrypted = try? AES.GCM.seal(data, using: secret).combined else { return false }
        do {
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                    withIntermediateDirectories: true)
            try encrypted.write(to: url,
                options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            var flags = URLResourceValues()
            flags.isExcludedFromBackup = true
            var item = url
            try item.setResourceValues(flags)
            return true
        } catch { return false }
    }

    func bindAuthenticated(userID: Int, subscribed: Bool, expiresUnix: Int) {
        guard userID > 0 else { return }
        if let old = vault, old.userID != userID { clear() }
        let now = Date()
        if vault == nil {
            vault = V50OfflineVault(userID: userID, verifiedAt: .distantPast,
                                    expiresAt: .distantPast, topics: [], pending: [])
        }
        if subscribed && expiresUnix > Int(now.timeIntervalSince1970) {
            vault?.verifiedAt = now
            vault?.expiresAt = Date(timeIntervalSince1970: TimeInterval(expiresUnix))
        } else {
            // Retain pending answers until the student renews or explicitly signs out.
            vault?.verifiedAt = .distantPast
            vault?.expiresAt = .distantPast
        }
        _ = persist()
    }

    func allowed(_ userID: Int, now: Date = Date()) -> Bool {
        guard let vault, vault.userID == userID else { return false }
        return now < vault.expiresAt &&
            now.timeIntervalSince(vault.verifiedAt) < maxAge &&
            now.timeIntervalSince(vault.verifiedAt) > -300
    }

    func topics(userID: Int) -> [V50OfflineTopic] {
        guard allowed(userID) else { return [] }
        return vault?.topics.filter {
            let age = Date().timeIntervalSince($0.savedAt)
            return age >= -300 && age < maxAge
        } ?? []
    }

    func questions(userID: Int, chapterID: Int) -> [V50OfflineQuestion] {
        topics(userID: userID).first(where: { $0.id == chapterID })?.questions ?? []
    }

    func pending(userID: Int) -> [V50OfflineAnswer] {
        guard vault?.userID == userID else { return [] }
        return vault?.pending ?? []
    }

    @discardableResult
    func rememberTopic(userID: Int, chapterID: Int, name: String, source: [JSON]) -> Bool {
        guard allowed(userID), chapterID > 0, !name.isEmpty, source.count <= 250 else { return false }
        var contents: [V50OfflineQuestion] = []
        for (position, raw) in source.enumerated() {
            let options = jArray(raw["options"]).map {
                V50OfflineQuestion.Option(id: jInt($0["id"]), text: jString($0["text"]))
            }
            let type = jString(raw["type"])
            guard V50OfflineRules.supports(type, optionCount: options.count),
                  jInt(raw["id"]) > 0, !V520RetiredFrenchContent.isQuestion(jInt(raw["id"])),
                  !V520RetiredFrenchContent.isChapter(chapterID), !jString(raw["text"]).isEmpty else { continue }
            contents.append(V50OfflineQuestion(
                id: jInt(raw["id"]), type: type, text: jString(raw["text"]),
                meaningWord: jString(raw["meaning_word"]), options: options,
                correctAnswer: jString(raw["correct_answer"]),
                explanation: jString(raw["explanation"]), submitted: jBool(raw["submitted"]),
                serverIndex: position + 1))
        }
        guard !contents.isEmpty else { return false }
        let topic = V50OfflineTopic(id: chapterID, name: String(name.prefix(180)),
                                   savedAt: Date(), questions: contents)
        let previous = vault
        vault?.topics.removeAll { $0.id == chapterID }
        vault?.topics.insert(topic, at: 0)
        vault?.topics = Array((vault?.topics ?? []).prefix(12))
        if persist() { return true }
        vault = previous
        return false
    }

    @discardableResult
    func queue(userID: Int, chapterID: Int, questionID: Int, answer: String,
               optionID: Int) -> Bool {
        guard allowed(userID), answer.count <= 3000, !answer.isEmpty,
              let question = questions(userID: userID, chapterID: chapterID)
                .first(where: { $0.id == questionID }), !question.submitted
        else { return false }
        if pending(userID: userID).contains(where: { $0.questionID == questionID }) { return true }
        guard (vault?.pending.count ?? 0) < 300 else { return false }
        let previous = vault
        vault?.pending.append(V50OfflineAnswer(chapterID: chapterID, questionID: questionID,
            serverIndex: question.serverIndex, text: answer, optionID: optionID,
            createdAt: Date(), revision: V50OfflineRules.revision(question)))
        if persist() { return true }
        vault = previous
        return false
    }

    func clear() {
        vault = nil
        if let url { try? FileManager.default.removeItem(at: url) }
    }

    @discardableResult
    func discard(userID: Int, questionID: Int) -> Bool {
        guard vault?.userID == userID else { return false }
        let previous = vault
        vault?.pending.removeAll { $0.questionID == questionID }
        if persist() { return true }
        vault = previous
        return false
    }

    private func acknowledge(_ entry: V50OfflineAnswer, userID: Int) -> Bool {
        guard vault?.userID == userID else { return false }
        let previous = vault
        if let topic = vault?.topics.firstIndex(where: { $0.id == entry.chapterID }),
           let question = vault?.topics[topic].questions.firstIndex(where: { $0.id == entry.questionID }) {
            vault?.topics[topic].questions[question].submitted = true
        }
        vault?.pending.removeAll { $0.questionID == entry.questionID }
        if persist() { return true }
        vault = previous
        return false
    }

    /// Reconcile with a fresh server read before every POST; never replay an edited question.
    func syncPending(userID: Int, csrf: String) async {
        guard !syncing, allowed(userID), !csrf.isEmpty,
              AppSession.shared.user?.id == userID else { return }
        syncing = true
        defer { syncing = false }
        var confirmed = 0, revised = 0
        for entry in pending(userID: userID) {
            guard vault?.userID == userID, AppSession.shared.user?.id == userID else { break }
            do {
                let response = try await APIClient.shared.request("mobile/exam.php",
                    query: [URLQueryItem(name: "chapter_id", value: String(entry.chapterID))])
                guard vault?.userID == userID, AppSession.shared.user?.id == userID else { break }
                let live = jArray(response["questions"])
                guard let position = live.firstIndex(where: { jInt($0["id"]) == entry.questionID }) else {
                    revised += 1; continue
                }
                let server = live[position]
                if !jBool(server["submitted"]) {
                    let options = jArray(server["options"]).map {
                        V50OfflineQuestion.Option(id: jInt($0["id"]), text: jString($0["text"]))
                    }
                    let current = V50OfflineQuestion(id: entry.questionID, type: jString(server["type"]),
                        text: jString(server["text"]), meaningWord: jString(server["meaning_word"]),
                        options: options, correctAnswer: jString(server["correct_answer"]),
                        explanation: jString(server["explanation"]), submitted: false, serverIndex: position + 1)
                    let original = questions(userID: userID, chapterID: entry.chapterID)
                        .first(where: { $0.id == entry.questionID })
                    let expected = entry.revision ?? original.map(V50OfflineRules.revision) ?? ""
                    guard !expected.isEmpty, expected == V50OfflineRules.revision(current) else {
                        revised += 1; continue
                    }
                    var answer: JSON = ["text": entry.text, "selected_text": entry.text, "submitted": true]
                    if entry.optionID > 0 { answer["selected"] = entry.optionID }
                    let body: JSON = ["csrf": csrf, "chapter_id": entry.chapterID,
                        "question_index": position + 1, "completed": false,
                        "answers": [String(entry.questionID): answer]]
                    let grade = try await APIClient.shared.request("api/progress.php", method: "POST", body: body)
                    guard vault?.userID == userID, AppSession.shared.user?.id == userID,
                          let grades = grade["grades"] as? JSON,
                          grades[String(entry.questionID)] != nil else { break }
                }
                guard acknowledge(entry, userID: userID) else { break }
                confirmed += 1
            } catch { break }
        }
        syncMessage = "تم تأكيد \(confirmed) إجابة · المتبقي \(pending(userID: userID).count)"
        if revised > 0 { syncMessage += " · تغيّر \(revised) سؤال؛ احتفظنا بإجاباتك للمراجعة" }
    }

}

struct V50OfflineExamListView: View {
    @ObservedObject private var bank = V50OfflineExamBank.shared
    @ObservedObject private var store = V49StudyStore.shared
    var body: some View {
        let userID = store.archive?.userID ?? AppSession.shared.user?.id ?? 0
        let topics = bank.topics(userID: userID)
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("اختباراتي المحفوظة").font(.title2.bold())
                Text("يمكنك الإجابة عن الأسئلة المحفوظة، ثم تُصحَّح رسمياً عند مزامنتها مع الخادم. لا توجد نقاط أو درجات محلية.")
                    .font(.subheadline).foregroundStyle(.secondary)
                if !bank.syncMessage.isEmpty {
                    Text(bank.syncMessage).font(.caption).foregroundStyle(.secondary)
                }
                Text("إجابات مؤجلة: \(bank.pending(userID: userID).count)")
                    .font(.subheadline.bold()).foregroundStyle(Color.murshidBlue)
                ForEach(topics) { topic in
                    NavigationLink(destination: V50OfflineQuestionView(topicID: topic.id)) {
                        MurshidCard {
                            HStack {
                                VStack(alignment: .leading, spacing: 5) {
                                    Text(topic.name).font(.headline)
                                    Text("\(topic.questions.count) سؤال محفوظ").font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.left")
                            }
                        }
                    }.buttonStyle(.plain)
                }
                if topics.isEmpty {
                    Text("لا توجد اختبارات محفوظة ضمن صلاحية اشتراك مؤكدة. افتح الموضوع أثناء الاتصال بالإنترنت.")
                        .foregroundStyle(.secondary)
                }
                if AppSession.shared.user?.id == userID && !AppSession.shared.csrf.isEmpty &&
                    !bank.pending(userID: userID).isEmpty {
                    Button("مزامنة الإجابات الآن") {
                        Task { await bank.syncPending(userID: userID, csrf: AppSession.shared.csrf) }
                    }.buttonStyle(.borderedProminent).tint(.murshidBlue).disabled(bank.syncing)
                }
            }.padding(16)
        }
        .navigationTitle("دراسة دون إنترنت")
    }
}

struct V50OfflineQuestionView: View {
    let topicID: Int
    @ObservedObject private var bank = V50OfflineExamBank.shared
    @ObservedObject private var store = V49StudyStore.shared
    @State private var index = 0
    @State private var answer = ""
    @State private var optionID = 0
    @State private var confirmDiscard = false
    private var userID: Int { store.archive?.userID ?? AppSession.shared.user?.id ?? 0 }
    private var questions: [V50OfflineQuestion] { bank.questions(userID: userID, chapterID: topicID) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if questions.indices.contains(index) {
                    let question = questions[index]
                    let pending = bank.pending(userID: userID).first { $0.questionID == question.id }
                    Text("السؤال \(index + 1) من \(questions.count)")
                        .font(.subheadline.bold()).foregroundStyle(Color.murshidBlue)
                    ProgressView(value: Double(index + 1), total: Double(max(1, questions.count)))
                    MurshidCard { Text(question.text).font(.title3.bold()) }
                    if pending != nil || question.submitted {
                        MurshidCard {
                            VStack(alignment: .leading, spacing: 9) {
                                Text(pending == nil ? "الإجابة مسجلة سابقاً" : "الإجابة محفوظة محلياً")
                                    .font(.headline)
                                if let pending {
                                    Text("إجابتك: \(pending.text)")
                                    let feedback = V50OfflineRules.feedback(question, answer: pending.text)
                                    Text(feedback == .correct ? "نتيجة تدريبية: إجابة صحيحة ✓" :
                                         feedback == .incorrect ? "نتيجة تدريبية: راجع الإجابة النموذجية" :
                                         "تحتاج هذه الصيغة إلى تصحيح الخادم بعد الاتصال")
                                        .font(.subheadline.bold()).foregroundStyle(Color.murshidBlue)
                                    Button("حذف الإجابة المؤجلة", role: .destructive) { confirmDiscard = true }
                                        .confirmationDialog("حذف إجابة لم تُرسَل بعد؟", isPresented: $confirmDiscard) {
                                            Button("حذف الإجابة", role: .destructive) {
                                                _ = bank.discard(userID: userID, questionID: question.id)
                                            }
                                        } message: { Text("إذا تغيّر السؤال، يمكنك حذف المسودة ثم تنزيل نسخته الجديدة أثناء الاتصال.") }
                                }
                                if !question.correctAnswer.isEmpty {
                                    Text("الإجابة النموذجية: \(question.correctAnswer)")
                                    Text(question.explanation).font(.subheadline).foregroundStyle(.secondary)
                                }
                                Text("لم تُحتسب درجة جديدة دون تأكيد الخادم.")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        Button(index == questions.count - 1 ? "إنهاء المراجعة" : "التالي") {
                            index += 1; answer = ""; optionID = 0
                        }.buttonStyle(.borderedProminent).tint(.murshidBlue)
                    } else {
                        MurshidCard {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("إجابتك").font(.headline)
                                if !question.options.isEmpty || question.type == "true_false" {
                                    let choices = question.options.isEmpty
                                        ? [V50OfflineQuestion.Option(id: 0, text: "صح"),
                                           V50OfflineQuestion.Option(id: 0, text: "خطأ")]
                                        : question.options
                                    ForEach(Array(choices.enumerated()), id: \.offset) { _, option in
                                        Button {
                                            answer = option.text
                                            optionID = option.id
                                        } label: {
                                            HStack {
                                                Text(option.text).frame(maxWidth: .infinity, alignment: .leading)
                                                Image(systemName: answer == option.text ? "checkmark.circle.fill" : "circle")
                                            }
                                        }.buttonStyle(.bordered)
                                    }
                                } else {
                                    TextField("اكتب إجابتك هنا", text: $answer, axis: .vertical)
                                        .lineLimit(3...6)
                                }
                                Button("حفظ الإجابة على الجهاز") {
                                    _ = bank.queue(userID: userID, chapterID: topicID,
                                                   questionID: question.id, answer: answer,
                                                   optionID: optionID)
                                }
                                .disabled(answer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                                .buttonStyle(.borderedProminent).tint(.murshidBlue)
                            }
                        }
                    }
                } else {
                    Text("أكملت مراجعة الأسئلة المتاحة. ستُصحَّح الإجابات الجديدة بعد المزامنة.")
                        .font(.headline)
                }
            }.padding(16)
        }
        .navigationTitle("اختبار محفوظ")
    }
}
