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
    let submitted: Bool
    let serverIndex: Int
}

struct V50OfflineTopic: Codable, Identifiable {
    let id: Int
    let name: String
    let savedAt: Date
    let questions: [V50OfflineQuestion]
}

struct V50OfflineAnswer: Codable, Identifiable {
    var id: Int { questionID }
    let chapterID: Int
    let questionID: Int
    let serverIndex: Int
    let text: String
    let optionID: Int
    let createdAt: Date
}

struct V50OfflineVault: Codable {
    let userID: Int
    var verifiedAt: Date
    var expiresAt: Date
    var topics: [V50OfflineTopic]
    var pending: [V50OfflineAnswer]
}

enum V50OfflineRules {
    static func supports(_ type: String, optionCount: Int) -> Bool {
        switch type {
        case "mcq": return optionCount >= 2
        case "true_false", "text", "fill", "meaning": return true
        default: return false
        }
    }
}

@MainActor
final class V50OfflineExamBank: ObservableObject {
    static let shared = V50OfflineExamBank()
    @Published private(set) var vault: V50OfflineVault?
    private var syncing = false
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
        return SecItemAdd(insert as CFDictionary, nil) == errSecSuccess ? generated : nil
    }

    private func read() -> V50OfflineVault? {
        guard let url, let encrypted = try? Data(contentsOf: url),
              let secret(), let sealed = try? AES.GCM.SealedBox(combined: encrypted),
              let decoded = try? AES.GCM.open(sealed, using: secret),
              let vault = try? JSONDecoder().decode(V50OfflineVault.self, from: decoded)
        else { return nil }
        return vault
    }

    private func persist() -> Bool {
        guard let vault, let url, let secret(),
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
        return vault?.topics.filter { Date().timeIntervalSince($0.savedAt) < maxAge } ?? []
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
                  jInt(raw["id"]) > 0, !jString(raw["text"]).isEmpty else { continue }
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
        vault?.topics.removeAll { $0.id == chapterID }
        vault?.topics.insert(topic, at: 0)
        vault?.topics = Array((vault?.topics ?? []).prefix(12))
        return persist()
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
        vault?.pending.append(V50OfflineAnswer(chapterID: chapterID, questionID: questionID,
            serverIndex: question.serverIndex, text: answer, optionID: optionID,
            createdAt: Date()))
        return persist()
    }

    func clear() {
        vault = nil
        if let url { try? FileManager.default.removeItem(at: url) }
    }

    /// Idempotence: fetch server submission state before every deferred POST.
    /// Ambiguous outcomes remain in the encrypted queue until next reconciliation.
    func syncPending(userID: Int, csrf: String) async {
        guard !syncing, !csrf.isEmpty, vault?.userID == userID else { return }
        syncing = true
        defer { syncing = false }
        for entry in pending(userID: userID) {
            guard vault?.userID == userID, AppSession.shared.user?.id == userID else { break }
            do {
                let response = try await APIClient.shared.request("mobile/exam.php",
                    query: [URLQueryItem(name: "chapter_id", value: String(entry.chapterID))])
                guard let server = jArray(response["questions"]).first(where: {
                    jInt($0["id"]) == entry.questionID
                }) else { break }
                if !jBool(server["submitted"]) {
                    var answer: JSON = ["text": entry.text, "selected_text": entry.text, "submitted": true]
                    if entry.optionID > 0 { answer["selected"] = entry.optionID }
                    let body: JSON = [
                        "csrf": csrf, "chapter_id": entry.chapterID,
                        "question_index": entry.serverIndex,
                        "completed": false, "answers": [String(entry.questionID): answer]
                    ]
                    let grade = try await APIClient.shared.request("api/progress.php",
                        method: "POST", body: body)
                    guard let grades = grade["grades"] as? JSON,
                          grades[String(entry.questionID)] != nil else { break }
                }
                vault?.pending.removeAll { $0.questionID == entry.questionID }
                guard persist() else { break }
            } catch { break }
        }
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
                    }.buttonStyle(.borderedProminent).tint(.murshidBlue)
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
                                if let pending { Text("إجابتك: \(pending.text)") }
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
