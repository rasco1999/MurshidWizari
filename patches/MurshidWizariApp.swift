import SwiftUI

@main
struct MurshidWizariApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var app = AppSession.shared
    @StateObject private var device = DeviceServices.shared
    @AppStorage("appearance") private var appearance = "system"
    @State private var showAcademicWelcome = true
    // A native iOS app often resumes instead of relaunching when its icon is tapped.
    // Remember genuine background visits so the greeting is visible on both paths.
    @State private var backgroundedAt: Date?
    @State private var pendingWelcomeAfterUnlock = false
    @State private var welcomePlaybackInProgress = false
    @State private var completedInitialWelcome = false
    @State private var bootstrapNeedsNetworkRetry = false

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
                    .environment(\.locale, Locale(identifier: "en_US_POSIX"))
                    .preferredColorScheme(colorScheme)
                    .opacity(showAcademicWelcome ? 0 : 1)

                if showAcademicWelcome {
                    V45WelcomeView()
                        .environmentObject(app)
                        .transition(.opacity.combined(with: .scale(scale: 1.015)))
                        .zIndex(20)
                }

                if device.isLocked {
                    LockShieldView()
                        .transition(.opacity)
                        .zIndex(30)
                }
            }
            .task {
                // Cold start: show the branded transition long enough to be noticed,
                // while restoring authentication in parallel.
                guard !completedInitialWelcome else { return }
                // Restore the account independently: never make the splash longer
                // than three seconds because of a slow network call.
                Task { await restoreSession() }
                try? await Task.sleep(nanoseconds: 2_700_000_000)
                withAnimation(.easeInOut(duration: 0.30)) {
                    showAcademicWelcome = false
                }
                completedInitialWelcome = true
            }
            .onChange(of: scenePhase) { phase in
                switch phase {
                case .background:
                    backgroundedAt = Date()
                    device.didEnterBackground()
                case .active:
                    Task { @MainActor in
                        if bootstrapNeedsNetworkRetry { await retryBootstrapAfterReconnect() }
                        await device.didBecomeActive()
                        if app.v3Authenticated { Task { try? await APIClient.shared.heartbeat() } }
                        // A brief system interruption should not replay the greeting.
                        guard completedInitialWelcome,
                              let left = backgroundedAt else { return }
                        backgroundedAt = nil
                        guard Date().timeIntervalSince(left) >= 3 else { return }
                        if device.isLocked {
                            // Preserve Face ID privacy: wait until the student unlocks.
                            pendingWelcomeAfterUnlock = true
                        } else {
                            await replayWelcome()
                        }
                    }
                default: break
                }
            }
            .onChange(of: device.isOnline) { online in
                guard online, bootstrapNeedsNetworkRetry else { return }
                Task { @MainActor in await retryBootstrapAfterReconnect() }
            }
            .onChange(of: device.isLocked) { locked in
                guard !locked, pendingWelcomeAfterUnlock, scenePhase == .active else { return }
                pendingWelcomeAfterUnlock = false
                Task { @MainActor in await replayWelcome() }
            }
        }
    }

    @MainActor
    private func replayWelcome() async {
        guard completedInitialWelcome,
              !device.isLocked,
              !showAcademicWelcome,
              !welcomePlaybackInProgress,
              scenePhase == .active else { return }

        welcomePlaybackInProgress = true
        withAnimation(.spring(response: 0.55, dampingFraction: 0.86)) {
            showAcademicWelcome = true
        }
        // Resume greeting is lightweight; it never waits for network access.
        try? await Task.sleep(nanoseconds: 2_700_000_000)
        withAnimation(.easeInOut(duration: 0.30)) {
            showAcademicWelcome = false
        }
        welcomePlaybackInProgress = false
    }

    @MainActor
    private func restoreSession() async {
        defer { app.bootstrapping = false }
        do {
            app.applyBootstrap(try await APIClient.shared.bootstrap())
            app.bootstrapNetworkFailed = false
            bootstrapNeedsNetworkRetry = false
        } catch let failure as APIError where failure.status == 0 {
            // A dropped network connection is not an invalid login session.
            // Keep cookies and retry when iOS reports connectivity again.
            app.bootstrapNetworkFailed = true
            bootstrapNeedsNetworkRetry = true
        } catch {
            bootstrapNeedsNetworkRetry = false
            app.reset()
        }
    }

    @MainActor
    private func retryBootstrapAfterReconnect() async {
        guard bootstrapNeedsNetworkRetry, !app.bootstrapping else { return }
        bootstrapNeedsNetworkRetry = false
        app.bootstrapping = true
        await restoreSession()
    }
}

struct RootView: View {
    @EnvironmentObject var app: AppSession
    var body: some View {
        Group {
            if app.bootstrapping { LoadingView(text: "جاري تجهيز حسابك…") }
            else if app.bootstrapNetworkFailed {
                VStack(spacing: 18) {
                    Image(systemName: "wifi.exclamationmark")
                        .font(.system(size: 52, weight: .semibold))
                        .foregroundStyle(Color.murshidBlue)
                    Text("تعذّر تحميل بيانات الحساب")
                        .font(.title3.bold())
                    Text("تعذّر الوصول إلى خادم المنصة عبر هذه الشبكة. حسابك محفوظ، ويمكنك إعادة المحاولة عند تحسن الاتصال.")
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
                    Button {
                        Task { @MainActor in
                            guard !app.bootstrapping else { return }
                            app.bootstrapping = true
                            defer { app.bootstrapping = false }
                            do {
                                app.applyBootstrap(try await APIClient.shared.bootstrap())
                            } catch let failure as APIError where failure.status == 0 {
                                app.bootstrapNetworkFailed = true
                            } catch {
                                app.reset()
                            }
                        }
                    } label: {
                        Label("إعادة المحاولة", systemImage: "arrow.clockwise")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(14)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.murshidBlue)
                }
                .frame(maxWidth: 420)
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
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
                Text("استخدم Face ID للعودة إلى حسابك.").multilineTextAlignment(.center).foregroundStyle(.secondary)
                Button("فتح باستخدام Face ID") { Task { await device.unlockWithFaceID(force: true) } }.buttonStyle(PrimaryButtonStyle()).frame(maxWidth: 260)
            }.padding(28)
        }
    }
}
