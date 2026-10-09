import Foundation
import LocalAuthentication
import Network
import UserNotifications
import Security
import UIKit

@MainActor
final class DeviceServices: ObservableObject {
    static let shared = DeviceServices()

    @Published var isOnline = true
    // Start conservatively until NWPathMonitor identifies Wi-Fi or cellular.
    @Published var isMeteredConnection = true
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
                self?.isMeteredConnection = path.isExpensive || path.isConstrained
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
