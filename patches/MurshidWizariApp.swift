import SwiftUI

@main
struct MurshidWizariApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var app = AppSession.shared
    @StateObject private var device = DeviceServices.shared
    @AppStorage("appearance") private var appearance = "system"
    @AppStorage("student_palette") private var studentPaletteID = "royal"
    @State private var showAcademicWelcome = true
    // A native iOS app often resumes instead of relaunching when its icon is tapped.
    // Remember genuine background visits so the greeting is visible on both paths.
    @State private var backgroundedAt: Date?
    @State private var pendingWelcomeAfterUnlock = false
    @State private var welcomePlaybackInProgress = false
    @State private var completedInitialWelcome = false
    @State private var bootstrapNeedsNetworkRetry = false
    @State private var bootstrapAttempt = 0
    @State private var bootstrapTask: Task<Void, Never>?
    @State private var bootstrapTimeoutTask: Task<Void, Never>?

    private var colorScheme: ColorScheme? {
        appearance == "dark" ? .dark : (appearance == "light" ? .light : nil)
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                RootView(onRetryBootstrap: { beginBootstrap() })
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
            .tint(StudentPalette(rawValue: studentPaletteID)?.primary ?? .murshidBlue)
            #if V498_QA
            .modifier(V498QATools())
            #endif
            .task {
                // Cold start: show the branded transition long enough to be noticed,
                // while restoring authentication in parallel.
                guard !completedInitialWelcome else { return }
                // Restore the account independently: never make the splash longer
                // than three seconds because of a slow network call.
                MurshidSchoolAdPush.shared.configure()
                beginBootstrap()
                try? await Task.sleep(nanoseconds: 2_700_000_000)
                withAnimation(.easeInOut(duration: 0.30)) {
                    showAcademicWelcome = false
                }
                completedInitialWelcome = true
            }
            .onChange(of: app.v3Authenticated) { loggedIn in
                if loggedIn { MurshidSchoolAdPush.shared.activate() }
                else { MurshidSchoolAdPush.shared.resetLocalRegistration() }
            }
            .onChange(of: scenePhase) { phase in
                switch phase {
                case .background:
                    backgroundedAt = Date()
                    device.didEnterBackground()
                case .active:
                    Task { @MainActor in
                        if bootstrapNeedsNetworkRetry { beginBootstrap() }
                        await device.didBecomeActive()
                        if app.v3Authenticated {
                            MurshidSchoolAdPush.shared.activate()
                            Task { try? await APIClient.shared.heartbeat() }
                        }
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
                Task { @MainActor in
                    if !online, app.bootstrapping, !app.v3Authenticated {
                        // The network monitor can report no connection after launch.
                        bootstrapAttempt += 1
                        bootstrapTask?.cancel()
                        bootstrapTimeoutTask?.cancel()
                        bootstrapTask = nil
                        bootstrapTimeoutTask = nil
                        app.bootstrapping = false
                        app.bootstrapNetworkFailed = true
                        bootstrapNeedsNetworkRetry = true
                    } else if online, bootstrapNeedsNetworkRetry, !app.v3Authenticated {
                        beginBootstrap()
                    }
                }
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

    // Run one session bootstrap at a time. A separate deadline prevents an
    // indefinitely spinning login screen when carrier DNS or connectivity stalls.
    @MainActor
    private func beginBootstrap() {
        guard !app.bootstrapping || bootstrapTask == nil else { return }
        bootstrapAttempt += 1
        let attempt = bootstrapAttempt
        bootstrapTask?.cancel()
        bootstrapTimeoutTask?.cancel()
        // Native offline launch is independent of the "remember me" cookie.
        if !device.isOnline {
            app.bootstrapping = false
            app.bootstrapNetworkFailed = true
            bootstrapNeedsNetworkRetry = true
            return
        }
        app.bootstrapping = true
        app.bootstrapNetworkFailed = false
        bootstrapNeedsNetworkRetry = false

        bootstrapTask = Task { @MainActor in
            do {
                let response = try await APIClient.shared.bootstrap()
                guard bootstrapAttempt == attempt else { return }
                app.applyBootstrap(response)
                bootstrapNeedsNetworkRetry = false
            } catch let failure as APIError where failure.status == 0 {
                guard bootstrapAttempt == attempt else { return }
                // Network timeouts are not invalid credentials. Keep stored
                // cookies and allow retry; never reset the student account.
                app.bootstrapNetworkFailed = true
                bootstrapNeedsNetworkRetry = true
            } catch is CancellationError {
                return
            } catch {
                guard bootstrapAttempt == attempt else { return }
                // Real expired/invalid sessions still return to sign-in.
                bootstrapNeedsNetworkRetry = false
                app.reset()
            }
            guard bootstrapAttempt == attempt else { return }
            app.bootstrapping = false
            bootstrapTask = nil
            bootstrapTimeoutTask?.cancel()
            bootstrapTimeoutTask = nil
        }

        bootstrapTimeoutTask = Task { @MainActor in
            do { try await Task.sleep(nanoseconds: 20_000_000_000) }
            catch { return }
            guard bootstrapAttempt == attempt, app.bootstrapping else { return }
            // Invalidate this attempt *before* cancellation so its late
            // response cannot overwrite the next attempt or restore the spinner.
            bootstrapAttempt += 1
            bootstrapTask?.cancel()
            bootstrapTask = nil
            bootstrapTimeoutTask = nil
            app.bootstrapNetworkFailed = true
            bootstrapNeedsNetworkRetry = true
            app.bootstrapping = false
        }
    }
}

struct RootView: View {
    @EnvironmentObject var app: AppSession
    @State private var showingPublicOfflineLanding = false
    let onRetryBootstrap: () -> Void
    var body: some View {
        Group {
            if app.bootstrapping { LoadingView(text: "جاري تجهيز حسابك…") }
            else if app.bootstrapNetworkFailed {
                VStack(spacing: 0) {
                    HStack(spacing: 10) {
                        Image(systemName: "wifi.slash")
                            .foregroundStyle(Color.murshidBlue)
                        Text("وضع الدراسة دون إنترنت").font(.subheadline.bold())
                        Spacer(minLength: 8)
                        Button("إعادة الاتصال") { onRetryBootstrap() }
                            .font(.footnote.weight(.semibold))
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .background(Color.murshidBlue.opacity(0.08))

                    if V49StudyStore.shared.hasOfflineAccount() && !showingPublicOfflineLanding {
                        // Account data is only visible after iOS owner authentication.
                        // Offline review needs neither remembered cookies nor subscriptions.
                        V49OfflineAccessView()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        Button("استعراض الواجهة العامة") {
                            showingPublicOfflineLanding = true
                        }
                        .font(.footnote.weight(.semibold))
                        .padding(.vertical, 14)
                    } else {
                        // A clean install is still able to open the native guest pages.
                        AuthFlowView()
                    }
                }
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

