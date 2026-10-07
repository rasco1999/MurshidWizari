import SwiftUI

@main
struct MurshidWizariApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var app = AppSession.shared
    @StateObject private var device = DeviceServices.shared
    @AppStorage("appearance") private var appearance = "system"
    @State private var showAcademicWelcome = true

    private var colorScheme: ColorScheme? {
        appearance == "dark" ? .dark : (appearance == "light" ? .light : nil)
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                RootView()
                    .environmentObject(app)
                    .environmentObject(device)
                    .environment(\.layoutDirection, .rightToLeft)
                    .preferredColorScheme(colorScheme)
                    .opacity(showAcademicWelcome ? 0 : 1)

                if showAcademicWelcome {
                    AcademicWelcomeView()
                        .transition(.opacity)
                        .zIndex(20)
                }

                if device.isLocked {
                    LockShieldView()
                        .transition(.opacity)
                        .zIndex(30)
                }
            }
            .task {
                async let restore: Void = restoreSession()
                try? await Task.sleep(nanoseconds: 1_250_000_000)
                await restore
                withAnimation(.easeOut(duration: 0.28)) { showAcademicWelcome = false }
            }
            .onChange(of: scenePhase) { phase in
                switch phase {
                case .background:
                    device.didEnterBackground()
                case .active:
                    Task {
                        await device.didBecomeActive()
                        if app.authenticated { try? await APIClient.shared.heartbeat() }
                    }
                default: break
                }
            }
        }
    }

    @MainActor
    private func restoreSession() async {
        defer { app.bootstrapping = false }
        do { app.applyBootstrap(try await APIClient.shared.bootstrap()) }
        catch { app.reset() }
    }
}

struct RootView: View {
    @EnvironmentObject var app: AppSession
    var body: some View {
        Group {
            if app.bootstrapping { LoadingView(text: "جاري تجهيز حسابك…") }
            else if app.authenticated { MainV3TabView() }
            else { AuthFlowView() }
        }
        .animation(.easeInOut(duration: 0.22), value: app.authenticated)
        .alert("انتهت الجلسة", isPresented: Binding(get: { app.sessionExpired }, set: { app.sessionExpired = $0 })) {
            Button("تسجيل الدخول", role: .cancel) { app.sessionExpired = false }
        } message: {
            Text("لحماية حسابك، انتهت الجلسة. سجّل الدخول مجددًا للمتابعة.")
        }
    }
}

struct AcademicWelcomeView: View {
    @State private var textOpacity = 0.0
    @State private var offset: CGFloat = 10
    var body: some View {
        ZStack {
            LinearGradient(colors: [.murshidNavy, Color(red: 0.04, green: 0.18, blue: 0.38), .murshidBlue], startPoint: .topTrailing, endPoint: .bottomLeading)
                .ignoresSafeArea()
            Circle().fill(.white.opacity(0.05)).frame(width: 360, height: 360).blur(radius: 2).offset(y: -110)
            VStack(spacing: 22) {
                Spacer()
                AcademicWelcomeMark()
                VStack(spacing: 8) {
                    Text("منصة المرشد الوزاري")
                        .font(.system(.largeTitle, design: .rounded, weight: .bold))
                        .foregroundStyle(.white)
                    Text("ابدأ بثقة • تعلّم بذكاء • تقدّم كل يوم")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white.opacity(0.82))
                }
                .opacity(textOpacity)
                .offset(y: offset)
                Spacer()
                Label("منهجك الوزاري في مكان واحد", systemImage: "checkmark.seal.fill")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.76))
                    .padding(.bottom, 30)
                    .opacity(textOpacity)
            }
            .padding(.horizontal, 26)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.55).delay(0.25)) { textOpacity = 1; offset = 0 }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("منصة المرشد الوزاري")
    }
}

struct LockShieldView: View {
    @EnvironmentObject var device: DeviceServices
    var body: some View {
        ZStack {
            Color(uiColor: .systemBackground).ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: "lock.shield.fill").font(.system(size: 48)).foregroundStyle(Color.murshidBlue)
                Text("التطبيق مقفل").font(.title2.bold())
                Text("استخدم Face ID أو رمز الجهاز للعودة إلى حسابك.").multilineTextAlignment(.center).foregroundStyle(.secondary)
                Button("فتح") { Task { await device.didBecomeActive() } }.buttonStyle(PrimaryButtonStyle()).frame(maxWidth: 260)
            }.padding(28)
        }
    }
}