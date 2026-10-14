import Foundation
import SwiftUI
import LocalAuthentication
import Network
import UserNotifications
import Security
import UIKit

@MainActor
final class DeviceServices: ObservableObject {
    static let shared = DeviceServices()

    @Published var isOnline = true
    @Published var isLocked = false
    @Published var biometricAvailable = false

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.muriq.murshid.network")
    private var backgroundDate: Date?
    private var authenticating = false
    private var lastSuccessfulUnlock = Date.distantPast

    private init() {
        startNetworkMonitor()
        refreshBiometricAvailability()
    }

    private func refreshBiometricAvailability() {
        let context = LAContext()
        var error: NSError?
        biometricAvailable =
            context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
            && context.biometryType == .faceID
    }

    func startNetworkMonitor() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                self?.isOnline = path.status == .satisfied
            }
        }
        monitor.start(queue: queue)
    }

    func didEnterBackground() {
        // The Face ID system sheet can temporarily change app activity.
        // Never start a new lock cycle while authentication is already running.
        guard !authenticating else { return }
        backgroundDate = Date()
    }

    func didBecomeActive() async {
        guard UserDefaults.standard.bool(forKey: "biometric_lock") else {
            isLocked = false
            backgroundDate = nil
            return
        }
        guard !authenticating else { return }
        guard Date().timeIntervalSince(lastSuccessfulUnlock) > 2 else {
            backgroundDate = nil
            isLocked = false
            return
        }
        guard let leftAt = backgroundDate,
              Date().timeIntervalSince(leftAt) > 20 else { return }

        // Consume this background event before Face ID starts so the Face ID sheet
        // cannot trigger another request for the same return-to-app event.
        backgroundDate = nil
        await unlockWithFaceID(force: true)
    }

    func unlockWithFaceID(force: Bool = false) async {
        guard UserDefaults.standard.bool(forKey: "biometric_lock") || force else {
            isLocked = false
            return
        }
        guard !authenticating else { return }

        let context = LAContext()
        context.localizedCancelTitle = "إلغاء"
        context.localizedFallbackTitle = ""

        var authError: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &authError),
              context.biometryType == .faceID else {
            biometricAvailable = false
            UserDefaults.standard.set(false, forKey: "biometric_lock")
            isLocked = false
            return
        }

        biometricAvailable = true
        authenticating = true
        isLocked = true
        defer { authenticating = false }

        do {
            let ok = try await context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: "افتح المرشد الوزاري باستخدام Face ID"
            )
            if ok {
                lastSuccessfulUnlock = Date()
                backgroundDate = nil
                isLocked = false
            } else {
                isLocked = true
            }
        } catch {
            isLocked = true
        }
    }

    func requestStudyReminder(hour: Int = 19, minute: Int = 0) async {
        let center = UNUserNotificationCenter.current()
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
        center.removePendingNotificationRequests(withIdentifiers: ["daily-study"])

        var date = DateComponents()
        date.hour = hour
        date.minute = minute

        let content = UNMutableNotificationContent()
        content.title = "وقت مراجعة قصيرة 📚"
        content.body = "حتى 10 دقائق اليوم تصنع فرقًا. افتح المرشد وواصل من حيث توقفت."
        content.sound = .default

        let trigger = UNCalendarNotificationTrigger(dateMatching: date, repeats: true)
        let request = UNNotificationRequest(identifier: "daily-study", content: content, trigger: trigger)
        try? await center.add(request)
    }

    func disableStudyReminder() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["daily-study"])
    }
}

@MainActor
final class KeychainVault {
    static let shared = KeychainVault()
    private let service = "com.muriq.murshid"
    private init() {}

    func set(_ value: Data, for key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = value
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }

    func data(for key: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess else { return nil }
        return item as? Data
    }

    func delete(_ key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
    }
}

@MainActor
final class ResponseCache {
    static let shared = ResponseCache()
    private let fm = FileManager.default

    private var base: URL {
        let root = fm.urls(for: .cachesDirectory, in: .userDomainMask).first!
        let dir = root.appendingPathComponent("MurshidCache", isDirectory: true)
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func save(_ json: JSON, key: String) {
        guard JSONSerialization.isValidJSONObject(json),
              let data = try? JSONSerialization.data(withJSONObject: json) else { return }
        try? data.write(to: fileURL(key), options: .atomic)
    }

    func load(key: String) -> JSON? {
        guard let data = try? Data(contentsOf: fileURL(key)),
              let value = try? JSONSerialization.jsonObject(with: data) as? JSON else { return nil }
        return value
    }

    private func fileURL(_ key: String) -> URL {
        let safe = Data(key.utf8).base64EncodedString().replacingOccurrences(of: "/", with: "_")
        return base.appendingPathComponent(safe + ".json")
    }
}


// MARK: - Offline question recall: Keychain-backed, graded-only, account-scoped
// This is NOT an offline exam: no answer submission, free quota, subscription
// entitlement, XP or ranking is changed by local review.
struct OfflineReviewCard: Codable, Identifiable {
    let id: Int
    var question: String
    var correctAnswer: String
    var explanation: String
    var subject: String
    var level: Int
    var dueAt: Date
    var savedAt: Date
}

enum OfflineReviewSchedule {
    static let days: [TimeInterval] = [0, 1, 3, 7, 14, 30]
    static func nextLevel(_ level: Int, mastered: Bool) -> Int {
        mastered ? min(5, max(0, level) + 1) : 0
    }
    static func nextDate(now: Date, level: Int, mastered: Bool) -> Date {
        let delay = mastered ? days[nextLevel(level, mastered: true)] * 86400 : 600
        return now.addingTimeInterval(delay)
    }
}

@MainActor
final class OfflineReviewNotebook {
    static let shared = OfflineReviewNotebook()
    private let limit = 150
    private init() {}
    private func key(_ userID: Int) -> String { "offline_review_cards_v1_\(userID)" }

    func cards(for userID: Int) -> [OfflineReviewCard] {
        guard userID > 0, let data = KeychainVault.shared.data(for: key(userID)),
              let all = try? JSONDecoder().decode([OfflineReviewCard].self, from: data) else { return [] }
        return all.filter { $0.id > 0 && !$0.question.isEmpty && !$0.correctAnswer.isEmpty }
            .sorted { $0.dueAt < $1.dueAt }
    }

    @discardableResult
    func remember(userID: Int, questionID: Int, question: String, answer: String,
                  explanation: String, subject: String, now: Date = Date()) -> Bool {
        guard userID > 0, questionID > 0,
              !question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !answer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        var collection = cards(for: userID)
        let previous = collection.first { $0.id == questionID }
        collection.removeAll { $0.id == questionID }
        collection.append(OfflineReviewCard(
            id: questionID,
            question: String(question.prefix(4000)),
            correctAnswer: String(answer.prefix(1800)),
            explanation: String(explanation.prefix(3000)),
            subject: String(subject.prefix(150)),
            level: previous?.level ?? 0,
            dueAt: previous?.dueAt ?? now,
            savedAt: previous?.savedAt ?? now))
        return persist(collection, userID: userID)
    }

    @discardableResult
    func review(userID: Int, questionID: Int, mastered: Bool, now: Date = Date()) -> Bool {
        var collection = cards(for: userID)
        guard let index = collection.firstIndex(where: { $0.id == questionID }) else { return false }
        let previous = collection[index].level
        collection[index].level = OfflineReviewSchedule.nextLevel(previous, mastered: mastered)
        collection[index].dueAt = OfflineReviewSchedule.nextDate(now: now, level: previous, mastered: mastered)
        return persist(collection, userID: userID)
    }

    private func persist(_ collection: [OfflineReviewCard], userID: Int) -> Bool {
        let bounded = Array(collection.sorted { $0.savedAt > $1.savedAt }.prefix(limit))
        guard let data = try? JSONEncoder().encode(bounded) else { return false }
        KeychainVault.shared.set(data, for: key(userID))
        return true
    }
}

struct OfflineReviewStudyView: View {
    @EnvironmentObject var app: AppSession
    @State private var cards: [OfflineReviewCard] = []
    @State private var active: Int? = nil
    @State private var revealed = false
    private var userID: Int { app.user?.id ?? 0 }
    private var due: [OfflineReviewCard] { cards.filter { $0.dueAt <= Date() } }
    private var selected: OfflineReviewCard? { cards.first { $0.id == active } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("المراجعة دون إنترنت")
                    .font(.title2.bold())
                Text("تعتمد على أسئلة صحّحها الخادم سابقاً. مراجعتك هنا لا تخصم رصيداً ولا تمنح درجات أو نقاطاً.")
                    .font(.subheadline).foregroundStyle(.secondary)
                if let card = selected {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(card.subject).font(.caption).foregroundStyle(.secondary)
                        Text(card.question).font(.title3.weight(.semibold))
                        if revealed {
                            Divider()
                            Text("الإجابة النموذجية").font(.headline)
                            Text(card.correctAnswer).font(.body)
                            if !card.explanation.isEmpty { Text(card.explanation).font(.subheadline).foregroundStyle(.secondary) }
                            HStack {
                                Button("أتقنت") { assess(true) }
                                    .buttonStyle(.borderedProminent)
                                Button("أحتاج مراجعة") { assess(false) }
                                    .buttonStyle(.bordered)
                            }
                        } else {
                            Button("أظهر الإجابة") { revealed = true }
                                .buttonStyle(.borderedProminent)
                        }
                    }.padding(18)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(uiColor: .secondarySystemGroupedBackground),
                                    in: RoundedRectangle(cornerRadius: 18))
                } else if due.isEmpty {
                    Text(cards.isEmpty ? "ليس لديك أسئلة محفوظة بعد. أجب عن أسئلة ثم راجعها هنا." :
                         "أحسنت! لا توجد مراجعات مستحقة الآن.")
                        .foregroundStyle(.secondary)
                } else {
                    Text("لديك \(due.count) سؤال للمراجعة الآن").font(.headline)
                    ForEach(due) { card in
                        Button {
                            active = card.id
                            revealed = false
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(card.question).lineLimit(2)
                                    Text(card.subject).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.left")
                            }.padding(12)
                        }.buttonStyle(.plain)
                    }
                }
            }.padding(18)
        }
        .navigationTitle("مراجعة ذكية")
        .onAppear(perform: reload)
    }

    private func reload() {
        cards = OfflineReviewNotebook.shared.cards(for: userID)
    }
    private func assess(_ mastered: Bool) {
        _ = OfflineReviewNotebook.shared.review(userID: userID, questionID: active ?? 0, mastered: mastered)
        active = nil
        revealed = false
        reload()
    }
}
