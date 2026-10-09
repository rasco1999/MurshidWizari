import SwiftUI
import LocalAuthentication
import WebKit

private enum AdminColors {
    static let background = Color(red: 0.035, green: 0.095, blue: 0.185)
    static let card = Color(red: 0.080, green: 0.155, blue: 0.255)
    static let line = Color(red: 0.27, green: 0.37, blue: 0.48)
    static let gold = Color(red: 0.98, green: 0.81, blue: 0.48)
    static let muted = Color(red: 0.71, green: 0.78, blue: 0.86)
}

@main
struct MurshidAdminApp: App {
    @StateObject private var deviceGate = AdminDeviceGate()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            Group {
                if deviceGate.unlocked {
                    AdminRootView(lock: { deviceGate.lock() })
                } else {
                    AdminLockView(deviceGate: deviceGate)
                }
            }
            .preferredColorScheme(.dark)
            .environment(\.layoutDirection, .rightToLeft)
            .onChange(of: scenePhase) { phase in
                if phase == .background { deviceGate.lock() }
            }
        }
    }
}

@MainActor
final class AdminDeviceGate: ObservableObject {
    @Published private(set) var unlocked = false
    @Published var evaluating = false
    @Published var errorMessage = ""

    func lock() {
        unlocked = false
        errorMessage = ""
    }

    func unlock() {
        guard !evaluating else { return }
        evaluating = true
        errorMessage = ""
        let context = LAContext()
        context.localizedCancelTitle = "إلغاء"
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            evaluating = false
            errorMessage = "فعّل رمز قفل للآيفون أولاً لحماية حساب المدير."
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "التحقق من مالك الجهاز قبل فتح تطبيق الإدارة") { [weak self] approved, failure in
            Task { @MainActor in
                guard let self else { return }
                self.evaluating = false
                self.unlocked = approved
                if !approved {
                    self.errorMessage = (failure as? LAError)?.code == .userCancel ? "" : "لم يتم التحقق من هوية مالك الجهاز. حاول مرة أخرى."
                }
            }
        }
    }
}

private struct AdminLockView: View {
    @ObservedObject var deviceGate: AdminDeviceGate

    var body: some View {
        ZStack {
            AdminColors.background.ignoresSafeArea()
            VStack(spacing: 18) {
                Image(systemName: "building.columns.fill")
                    .font(.system(size: 54, weight: .regular))
                    .foregroundColor(AdminColors.gold)
                    .frame(width: 110, height: 110)
                    .background(AdminColors.card, in: RoundedRectangle(cornerRadius: 26))
                    .overlay(RoundedRectangle(cornerRadius: 26).stroke(AdminColors.line, lineWidth: 1))
                Text("الإدارة").font(.system(size: 34, weight: .bold))
                Text("منصة المرشد الوزاري").font(.headline).foregroundColor(AdminColors.muted)
                Text("لوحة خاصة بحساب المدير. تحقّق من هويتك للوصول إلى بيانات المنصة.")
                    .font(.subheadline).foregroundColor(AdminColors.muted).multilineTextAlignment(.center)
                    .padding(.horizontal, 28)
                Button {
                    deviceGate.unlock()
                } label: {
                    HStack(spacing: 9) {
                        if deviceGate.evaluating { ProgressView().tint(AdminColors.background) }
                        Image(systemName: "faceid")
                        Text(deviceGate.evaluating ? "جاري التحقق…" : "فتح تطبيق الإدارة")
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(AdminColors.gold, in: RoundedRectangle(cornerRadius: 15))
                    .foregroundColor(AdminColors.background)
                }
                .disabled(deviceGate.evaluating)
                .padding(.horizontal, 35)
                if !deviceGate.errorMessage.isEmpty {
                    Text(deviceGate.errorMessage)
                        .font(.caption).foregroundColor(.orange).multilineTextAlignment(.center)
                }
                Text("التحقق من الجهاز لا يلغي تسجيل الدخول الإداري أو التحقق الثنائي في الموقع.")
                    .font(.caption2).foregroundColor(AdminColors.muted).multilineTextAlignment(.center)
                    .padding(.horizontal, 26)
            }
            .padding()
        }
        .onAppear { deviceGate.unlock() }
    }
}

private enum AdminSection: String, CaseIterable, Identifiable {
    case dashboard, codes, ads, more

    var id: String { rawValue }
    var title: String {
        switch self {
        case .dashboard: return "الرئيسية"
        case .codes: return "الأكواد"
        case .ads: return "الإعلانات"
        case .more: return "المزيد"
        }
    }
    var systemImage: String {
        switch self {
        case .dashboard: return "square.grid.2x2.fill"
        case .codes: return "ticket.fill"
        case .ads: return "megaphone.fill"
        case .more: return "slider.horizontal.3"
        }
    }
    var route: String {
        switch self {
        case .dashboard: return "/admin/dashboard.php"
        case .codes: return "/admin/mobile-console.php?section=codes"
        case .ads: return "/admin/mobile-console.php?section=ads"
        case .more: return "/admin/dashboard.php"
        }
    }
}

@MainActor
final class AdminBrowser: NSObject, ObservableObject, WKNavigationDelegate, WKUIDelegate {
    let webView: WKWebView
    @Published var isLoading = false
    @Published var canGoBack = false
    @Published var errorMessage = ""
    @Published var pageTitle = "لوحة الإدارة"
    private(set) var didStart = false
    private let allowedHosts: Set<String> = ["www.mur-iq.com", "mur-iq.com"]

    override init() {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init()
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.scrollView.keyboardDismissMode = .interactive
        webView.allowsBackForwardNavigationGestures = true
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 0.035, green: 0.095, blue: 0.185, alpha: 1)
    }

    func start() {
        guard !didStart else { return }
        didStart = true
        open(AdminSection.dashboard.route)
    }

    func open(_ path: String) {
        guard path.hasPrefix("/admin/"),
              let url = URL(string: "https://www.mur-iq.com" + path) else { return }
        errorMessage = ""
        webView.load(URLRequest(url: url, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 25))
    }

    func reload() {
        errorMessage = ""
        if webView.url == nil { open(AdminSection.dashboard.route) }
        else { webView.reload() }
    }

    func back() {
        if webView.canGoBack { webView.goBack() }
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.cancel)
            return
        }
        if url.scheme == "https", let host = url.host?.lowercased(), allowedHosts.contains(host) {
            decisionHandler(.allow)
            return
        }
        decisionHandler(.cancel)
        // User-tapped external links open outside the management browser.
        if navigationAction.navigationType == .linkActivated,
           ["https", "mailto", "tel"].contains(url.scheme?.lowercased() ?? "") {
            UIApplication.shared.open(url)
        }
    }

    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if navigationAction.targetFrame == nil, let url = navigationAction.request.url,
           url.scheme == "https", let host = url.host?.lowercased(), allowedHosts.contains(host) {
            webView.load(navigationAction.request)
        }
        return nil
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        isLoading = true
        errorMessage = ""
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        isLoading = false
        canGoBack = webView.canGoBack
        pageTitle = webView.title ?? "لوحة الإدارة"
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        handle(error, in: webView)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        handle(error, in: webView)
    }

    private func handle(_ error: Error, in webView: WKWebView) {
        isLoading = false
        canGoBack = webView.canGoBack
        if (error as NSError).code != NSURLErrorCancelled {
            errorMessage = "تعذّر الاتصال بإدارة الموقع. تأكد من البيانات أو Wi-Fi ثم أعد المحاولة."
        }
    }
}

private struct AdminWebView: UIViewRepresentable {
    let webView: WKWebView
    func makeUIView(context: Context) -> WKWebView { webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

private struct AdminRootView: View {
    let lock: () -> Void
    @StateObject private var browser = AdminBrowser()
    @State private var section = AdminSection.dashboard

    var body: some View {
        VStack(spacing: 0) {
            header
            if browser.isLoading {
                ProgressView().tint(AdminColors.gold).frame(maxWidth: .infinity).padding(.vertical, 2)
            }
            if !browser.errorMessage.isEmpty && section != .more {
                HStack(spacing: 8) {
                    Text(browser.errorMessage).font(.caption).foregroundColor(.orange)
                    Spacer(minLength: 4)
                    Button("إعادة") { browser.reload() }.foregroundColor(AdminColors.gold)
                }
                .padding(10).background(AdminColors.card)
            }
            if section == .more {
                moreView
            } else {
                AdminWebView(webView: browser.webView)
                    .background(AdminColors.background)
            }
            bottomBar
        }
        .background(AdminColors.background.ignoresSafeArea())
        .onAppear { browser.start() }
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: "building.columns.fill")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(AdminColors.gold)
                .frame(width: 44, height: 44)
                .background(AdminColors.card, in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 2) {
                Text("الإدارة").font(.headline.bold()).foregroundColor(.white)
                Text("المرشد الوزاري • اتصال مباشر").font(.caption2).foregroundColor(AdminColors.muted)
            }
            Spacer(minLength: 8)
            Button { browser.back() } label: {
                Image(systemName: "chevron.right").font(.headline)
                    .frame(width: 35, height: 35)
            }
            .disabled(!browser.canGoBack)
            .opacity(browser.canGoBack ? 1 : 0.35)
            Button { browser.reload() } label: {
                Image(systemName: "arrow.clockwise").font(.headline)
                    .frame(width: 35, height: 35)
            }
        }
        .foregroundColor(AdminColors.gold)
        .padding(.horizontal, 15)
        .padding(.vertical, 11)
        .background(AdminColors.background)
        .overlay(alignment: .bottom) { Rectangle().fill(AdminColors.line).frame(height: 1) }
    }

    private var bottomBar: some View {
        HStack(spacing: 0) {
            ForEach(AdminSection.allCases) { item in
                Button {
                    section = item
                    if item != .more { browser.open(item.route) }
                } label: {
                    VStack(spacing: 5) {
                        Image(systemName: item.systemImage).font(.system(size: 19, weight: .semibold))
                        Text(item.title).font(.system(size: 11, weight: section == item ? .bold : .medium))
                    }
                    .foregroundColor(section == item ? AdminColors.gold : AdminColors.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(item.title)
            }
        }
        .background(AdminColors.background)
        .overlay(alignment: .top) { Rectangle().fill(AdminColors.line).frame(height: 1) }
    }

    private var moreView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("إدارة المنصة").font(.title2.bold()).foregroundColor(AdminColors.gold)
                    .padding(.top, 12)
                Text("هذه الأدوات تفتح صفحات لوحة موقعك الأصلية، وتطبّق صلاحيات المدير المعرّفة على الخادم.")
                    .font(.subheadline).foregroundColor(AdminColors.muted)
                Group {
                    moreRow("الطلاب والحسابات", symbol: "person.2.fill", route: "/admin/users.php")
                    moreRow("الأسئلة", symbol: "doc.text.fill", route: "/admin/questions.php")
                    moreRow("المنهج والمواد", symbol: "books.vertical.fill", route: "/admin/curriculum.php")
                    moreRow("طلبات الأسئلة", symbol: "text.bubble.fill", route: "/admin/question-requests.php")
                    moreRow("الاشتراكات", symbol: "creditcard.fill", route: "/admin/subscriptions.php")
                    moreRow("إعلانات المدرسة في الموقع", symbol: "megaphone.fill", route: "/admin/school-ads.php")
                    moreRow("الدعم الفني", symbol: "bubble.left.and.bubble.right.fill", route: "/admin/support.php")
                    moreRow("التقارير", symbol: "chart.bar.fill", route: "/admin/analytics.php")
                    moreRow("إعدادات الأمان", symbol: "lock.shield.fill", route: "/admin/security.php")
                    moreRow("لوحة الموقع الكاملة", symbol: "rectangle.grid.2x2.fill", route: "/admin/dashboard.php")
                }
                Button {
                    lock()
                } label: {
                    Label("قفل تطبيق الإدارة", systemImage: "lock.fill")
                        .font(.headline).foregroundColor(.orange)
                        .frame(maxWidth: .infinity).padding(14)
                        .background(AdminColors.card, in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
                Text("قفل التطبيق لا يسجل الخروج من الموقع. استخدم تسجيل الخروج من لوحة المدير لإنهاء جلسة الخادم.")
                    .font(.caption2).foregroundColor(AdminColors.muted)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
    }

    private func moreRow(_ title: String, symbol: String, route: String) -> some View {
        Button {
            section = .dashboard
            browser.open(route)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: symbol)
                    .frame(width: 32, height: 32)
                    .foregroundColor(AdminColors.gold)
                Text(title).font(.subheadline.bold()).foregroundColor(.white)
                Spacer()
                Image(systemName: "chevron.left").foregroundColor(AdminColors.muted)
            }
            .padding(13)
            .background(AdminColors.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(AdminColors.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
