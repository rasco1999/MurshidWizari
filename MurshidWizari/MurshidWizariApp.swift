import SwiftUI

@main
struct MurshidWizariApp: App {
    @StateObject private var session = SessionStore()
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(session)
                .environment(\.layoutDirection, .rightToLeft)
        }
    }
}

@MainActor
final class SessionStore: ObservableObject {
    @Published var isLoggedIn = false
    @Published var studentName = ""
    @Published var isBusy = false
    @Published var errorMessage: String?
    let api = APIClient()
    
    func login(email: String, password: String) async {
        isBusy = true; errorMessage = nil
        defer { isBusy = false }
        do {
            let result = try await api.login(email: email, password: password)
            studentName = result.name.isEmpty ? "الطالب" : result.name
            isLoggedIn = true
        } catch { errorMessage = error.localizedDescription }
    }
    func logout() { isLoggedIn = false; studentName = "" }
}
