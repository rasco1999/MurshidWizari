import UIKit
import UserNotifications
import SwiftUI

extension Notification.Name {
    static let murshidSchoolAdOpened = Notification.Name("murshidSchoolAdOpened")
}

// Native APNs alerts work while iOS suspends the app. The existing red badge is retained.
@MainActor
final class MurshidSchoolAdPush: NSObject, UNUserNotificationCenterDelegate {
    static let shared = MurshidSchoolAdPush()
    private var currentToken: Data?
    private var registeredUser = 0
    private var registeredToken = ""
    private let pendingKey = "murshid_pending_school_ad_open"
    var tokenHex: String? { currentToken?.map { String(format: "%02x", $0) }.joined() }

    func configure() { UNUserNotificationCenter.current().delegate = self }

    func activate() {
        guard AppSession.shared.v3Authenticated, (AppSession.shared.user?.id ?? 0) > 0 else { return }
        configure()
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            // UserNotifications invokes this callback on its own private queue.
            // Hop to MainActor before accessing the @MainActor-isolated service
            // or UIApplication. Swift 6 otherwise traps with dispatch_assert_queue.
            let status = settings.authorizationStatus
            Task { @MainActor in
                switch status {
                case .authorized, .provisional, .ephemeral:
                    UIApplication.shared.registerForRemoteNotifications()
                case .notDetermined:
                    UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge]) { approved, _ in
                        guard approved else { return }
                        Task { @MainActor in UIApplication.shared.registerForRemoteNotifications() }
                    }
                default: break
                }
            }
        }
    }

    func receivedToken(_ token: Data) {
        currentToken = token
        Task { await sync() }
    }

    private func sync() async {
        guard let user = AppSession.shared.user,
              AppSession.shared.v3Authenticated,
              !AppSession.shared.csrf.isEmpty,
              let token = tokenHex, token.count == 64 else { return }
        if registeredUser == user.id && registeredToken == token { return }
        #if DEBUG
        let env = "sandbox"
        #else
        let env = "production"
        #endif
        do {
            _ = try await APIClient.shared.request("mobile/ios-push.php", method: "POST",
                 body: ["csrf": AppSession.shared.csrf, "token": token,
                        "environment": env, "action": "register"])
            guard AppSession.shared.user?.id == user.id else { return }
            registeredUser = user.id
            registeredToken = token
        } catch {
            // The feature is optional until server setup; retry on next activation.
        }
    }

    func resetLocalRegistration() {
        registeredUser = 0
        registeredToken = ""
    }

    func takePendingSchoolAd() -> Bool {
        let value = UserDefaults.standard.bool(forKey: pendingKey)
        if value { UserDefaults.standard.removeObject(forKey: pendingKey) }
        return value
    }

    private func openSchoolAd() {
        UserDefaults.standard.set(true, forKey: pendingKey)
        NotificationCenter.default.post(name: .murshidSchoolAdOpened, object: nil)
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        // Avoid a double alert while the foreground screen has its live badge.
        completionHandler([])
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void) {
        let schoolAd = response.notification.request.content.userInfo["type"] as? String == "school_ad"
        if schoolAd { Task { @MainActor in MurshidSchoolAdPush.shared.openSchoolAd() } }
        completionHandler()
    }
}

