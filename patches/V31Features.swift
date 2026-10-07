import SwiftUI
import PhotosUI
import LocalAuthentication
import UserNotifications
import UIKit
import Combine

// MARK: - v3.1 Home: mirrors the live website slider and keeps study actions below subjects

private enum V31SlideKind: Int, CaseIterable, Identifiable {
    case million, advertise, tests, success
    var id: Int { rawValue }
    var title: String {
        switch self {
        case .million: return "تحدي المليون"
        case .advertise: return "أعلن مدرستك"
        case .tests: return "الاختبارات"
        case .success: return "النجاح"
        }
    }
    var imageURL: URL? {
        let base = "https://www.mur-iq.com/assets/img/hero/"
        switch self {
        case .million: return URL(string: base + "million.webp?v=20260928-v56")
        case .advertise: return URL(string: base + "advertise-here.webp?v=20260928-v56")
        case .tests: return URL(string: base + "tests.webp?v=20260928-v56")
        case .success: return URL(string: base + "study.webp?v=20260928-v56")
        }
    }
}

struct V31HomeView: View {
    @EnvironmentObject var app: AppSession
    @EnvironmentObject var device: DeviceServices
    @State private var insights: JSON = [:]
    @State private var weekly: JSON = [:]
    @State private var loadingInsights = true
    @State private var error = ""

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                welcomeHeader
                if !device.isOnline { offlineBanner }
                V31WebsiteHeroSlider()
                subscriptionStrip
                performanceStrip
                subjectsSection
                nextStepsSection
                quickTools
                if !error.isEmpty { V3InlineMessage(text: error, icon: "wifi.exclamationmark", tone: .warning) }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 30)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("المرشد الوزاري")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                NavigationLink(destination: NotificationsView()) {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "bell.fill").font(.headline)
                        if app.unreadNotifications > 0 {
                            Text("\(min(app.unreadNotifications, 99))")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(4)
                                .background(.red, in: Circle())
                                .offset(x: 9, y: -8)
                        }
                    }
                }
                .accessibilityLabel("الإشعارات")
            }
        }
        .task { await refreshAll() }
        .refreshable { await refreshAll() }
    }

    private var welcomeHeader: some View {
        MurshidCard {
            HStack(spacing: 13) {
                Image(systemName: "graduationcap.fill")
                    .font(.title2)
                    .foregroundStyle(Color.murshidBlue)
                    .frame(width: 48, height: 48)
                    .background(Color.murshidBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                VStack(alignment: .leading, spacing: 4) {
                    Text("أهلًا، \(firstName)").font(.title3.bold())
                    Text(app.user?.grade ?? "").font(.subheadline).foregroundStyle(.secondary)
                    Text(motivation).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
                Spacer()
                if app.subscribed {
                    Image(systemName: "checkmark.seal.fill").foregroundStyle(.green).font(.title3)
                }
            }
        }
        .padding(.top, 6)
    }

    private var offlineBanner: some View {
        V3InlineMessage(text: "أنت غير متصل الآن. سنعرض البيانات المحفوظة متى كانت متاحة.", icon: "wifi.slash", tone: .warning)
    }

    @ViewBuilder
    private var subscriptionStrip: some View {
        if app.subscribed {
            MurshidCard {
                HStack(spacing: 12) {
                    Image(systemName: "checkmark.seal.fill").foregroundStyle(.green).font(.title2)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("كل الأسئلة مفتوحة").font(.headline)
                        Text("اشتراكك فعّال — واصل التقدم بدون حدود الرصيد المجاني.").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                }
            }
        } else {
            NavigationLink(destination: V31SubscriptionView()) {
                MurshidCard {
                    HStack(spacing: 13) {
                        Image(systemName: "gift.fill")
                            .foregroundStyle(Color.murshidGold)
                            .frame(width: 46, height: 46)
                            .background(Color.murshidGold.opacity(0.13), in: RoundedRectangle(cornerRadius: 13))
                        VStack(alignment: .leading, spacing: 4) {
                            Text("رصيد التجربة المجانية").font(.headline).foregroundStyle(.primary)
                            Text("متبقي \(app.freeRemaining ?? 0) من \(app.freeLimit) سؤال").font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer(); Image(systemName: "chevron.left").foregroundStyle(.secondary)
                    }
                }
            }.buttonStyle(.plain)
        }
    }

    private var performanceStrip: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            V3MetricCard(title: "الإجابات", value: "\(jInt(app.stats["total_answers"]))", icon: "checkmark.circle.fill")
            V3MetricCard(title: "اختبارات مكتملة", value: "\(jInt(app.stats["exams_completed"]))", icon: "flag.checkered")
            V3MetricCard(title: "هذا الأسبوع", value: "\(jInt(weekly["answers"]))", icon: "calendar")
            V3MetricCard(title: "مؤشر النجاح", value: "\(jInt(insights["success_index"]))%", icon: "sparkles")
        }
    }

    private var subjectsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "موادك", subtitle: "اختر المادة ثم الموضوع", icon: "books.vertical.fill")
            ForEach(app.subjects) { subject in
                NavigationLink(destination: V3TopicsView(subject: subject)) { V3SubjectRow(subject: subject) }
                    .buttonStyle(.plain)
            }
        }
    }

    private var nextStepsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "خطواتك التالية", subtitle: "اقتراحات مرتبة حسب تقدمك", icon: "figure.walk.motion")
            if loadingInsights {
                V3SkeletonCard(height: 130)
            } else if let topic = suggestedTopic {
                NavigationLink(destination: V3ExamView(topic: topic.topic, subject: topic.subject)) {
                    MurshidCard {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Label("مقترح لك", systemImage: "play.circle.fill").font(.headline).foregroundStyle(Color.murshidBlue)
                                Spacer(); Text("\(topic.coverage)%").font(.caption.bold().monospacedDigit()).foregroundStyle(.secondary)
                            }
                            Text(topic.topic.name).font(.title3.bold()).foregroundStyle(.primary)
                            Text(topic.subject.name).font(.subheadline).foregroundStyle(.secondary)
                            ProgressView(value: Double(topic.coverage), total: 100).tint(.murshidBlue)
                        }
                    }
                }.buttonStyle(.plain)
            } else if let first = app.subjects.first {
                NavigationLink(destination: V3TopicsView(subject: first)) {
                    MurshidCard {
                        HStack(spacing: 12) {
                            Image(systemName: "play.circle.fill").font(.largeTitle).foregroundStyle(Color.murshidBlue)
                            VStack(alignment: .leading, spacing: 3) {
                                Text("ابدأ أول مراجعة").font(.headline).foregroundStyle(.primary)
                                Text("اختر موضوعك وسنحفظ تقدمك تلقائيًا.").font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer(); Image(systemName: "chevron.left").foregroundStyle(.secondary)
                        }
                    }
                }.buttonStyle(.plain)
            }
        }
    }

    private var quickTools: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "أدواتك السريعة", subtitle: "كل ما تحتاجه بعد اختيار موادك", icon: "bolt.fill")
            LazyVGrid(columns: columns, spacing: 12) {
                NavigationLink(destination: ReviewView()) { V3ToolCard(title: "راجع أخطاءك", subtitle: "مراجعة ذكية", icon: "brain.head.profile") }
                NavigationLink(destination: V3FocusView()) { V3ToolCard(title: "جلسة تركيز", subtitle: "25 · 45 · 60 دقيقة", icon: "timer") }
                NavigationLink(destination: V3ContestView()) { V3ToolCard(title: "تحدي المليون", subtitle: "ترتيب ونقاط", icon: "trophy.fill") }
                NavigationLink(destination: StoriesView()) { V3ToolCard(title: "غيّر جو", subtitle: "استراحة قصيرة", icon: "sparkles") }
            }.buttonStyle(.plain)
        }
    }

    private var firstName: String {
        app.user?.name.split(separator: " ").first.map(String.init) ?? "طالبنا"
    }

    private var motivation: String {
        let total = jInt(app.stats["total_answers"])
        if app.accuracy >= 85 && total >= 10 { return "مستواك ممتاز. حافظ على إيقاعك وراجع الأخطاء القليلة." }
        if total > 0 { return "كل إجابة اليوم تقرّبك أكثر من الدرجة التي تريدها." }
        return "ابدأ بخطوة صغيرة اليوم، وسنبني تقدمك معك سؤالًا بعد سؤال."
    }

    private var suggestedTopic: (topic: Topic, subject: Subject, coverage: Int)? {
        guard let row = jArray(insights["daily_topics"]).first else { return nil }
        let sid = jInt(row["subject_id"])
        let subject = app.subjects.first(where: { $0.id == sid }) ?? Subject(["id": sid, "name": jString(row["subject_name"])])
        let topic = Topic(["id": jInt(row["id"]), "name": jString(row["name"], default: jString(row["display_name"])), "count": jInt(row["questions_count"]), "theme": jString(row["theme"])])
        guard topic.id > 0, subject.id > 0 else { return nil }
        return (topic, subject, jInt(row["coverage"]))
    }

    private func refreshAll() async {
        await MainActor.run { error = "" }
        do {
            let b = try await APIClient.shared.bootstrap()
            let i = try await APIClient.shared.request("mobile/insights.php", cacheKey: "insights")
            let n = try await APIClient.shared.request("mobile/notifications.php")
            await MainActor.run {
                app.applyBootstrap(b)
                insights = i["insights"] as? JSON ?? [:]
                weekly = i["weekly"] as? JSON ?? [:]
                app.unreadNotifications = jInt(n["unread"])
                loadingInsights = false
            }
            await V31PushRegistrar.syncIfPossible()
        } catch {
            await MainActor.run { loadingInsights = false; self.error = error.localizedDescription }
        }
    }
}

private struct V31WebsiteHeroSlider: View {
    @State private var selection = 0
    @State private var images: [Int: UIImage] = [:]
    @State private var ready = false
    private let timer = Timer.publish(every: 5.5, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(spacing: 8) {
            TabView(selection: $selection) {
                ForEach(V31SlideKind.allCases) { kind in
                    destination(for: kind)
                        .tag(kind.rawValue)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .frame(height: 220)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(Color.primary.opacity(0.05)))
            .shadow(color: Color.black.opacity(0.08), radius: 18, y: 8)
            .onReceive(timer) { _ in
                guard ready else { return }
                withAnimation(.easeInOut(duration: 0.35)) {
                    selection = (selection + 1) % V31SlideKind.allCases.count
                }
            }

            HStack(spacing: 7) {
                ForEach(V31SlideKind.allCases) { kind in
                    Capsule()
                        .fill(kind.rawValue == selection ? Color.murshidBlue : Color.secondary.opacity(0.25))
                        .frame(width: kind.rawValue == selection ? 22 : 7, height: 7)
                        .animation(.easeInOut(duration: 0.2), value: selection)
                }
            }
        }
        .task { await preloadAllImages() }
        .accessibilityLabel("سلايدر منصة المرشد الوزاري")
    }

    @ViewBuilder
    private func destination(for kind: V31SlideKind) -> some View {
        NavigationLink {
            switch kind {
            case .million: V3ContestView()
            case .advertise: V31SchoolAdRequestView()
            case .tests: V3TestsView()
            case .success: V3SuccessView()
            }
        } label: {
            ZStack {
                LinearGradient(colors: [.murshidNavy, .murshidBlue], startPoint: .topLeading, endPoint: .bottomTrailing)
                if let image = images[kind.rawValue] {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .transition(.opacity)
                } else {
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .fill(Color.murshidBlue.opacity(0.16))
                        .overlay {
                            Image(systemName: "photo")
                                .font(.title2)
                                .foregroundStyle(.white.opacity(0.28))
                        }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(kind.title)
    }

    @MainActor
    private func preloadAllImages() async {
        guard images.count < V31SlideKind.allCases.count else { ready = true; return }
        var loaded: [Int: UIImage] = [:]
        for kind in V31SlideKind.allCases {
            guard let url = kind.imageURL else { continue }
            var request = URLRequest(url: url)
            request.cachePolicy = .returnCacheDataElseLoad
            request.timeoutInterval = 20
            if let (data, response) = try? await URLSession.shared.data(for: request),
               let http = response as? HTTPURLResponse,
               (200...299).contains(http.statusCode),
               let image = UIImage(data: data) {
                loaded[kind.rawValue] = image
            }
        }
        if !loaded.isEmpty {
            images.merge(loaded) { _, new in new }
        }
        ready = images.count == V31SlideKind.allCases.count
    }
}

// MARK: - External subscription flow: no phone field, no in-app payment web sheet

struct V31SubscriptionView: View {
    @EnvironmentObject var app: AppSession
    @Environment(\.openURL) private var openURL
    @AppStorage("last_payment_order") private var storedOrderID = 0
    @State private var plan = ""
        @State private var loading = false
    @State private var error = ""
    @State private var statusMessage = ""


    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                V3IntroCard(eyebrow: "الاشتراك", title: app.subscribed ? "اشتراكك مفعّل" : "افتح كامل الأسئلة", text: app.subscribed ? "يمكنك الوصول إلى كامل المحتوى المتاح في صفك." : "اختر الباقة فقط، ثم انتقل مباشرة إلى صفحة الدفع الخارجية الآمنة.", icon: app.subscribed ? "checkmark.seal.fill" : "arrow.up.forward.app.fill")
                if app.subscribed {
                    MurshidCard { Label("لا تحتاج إلى أي عملية دفع الآن.", systemImage: "checkmark.circle.fill").font(.headline).foregroundStyle(.green) }
                } else {
                    plansSection
                    actionSection
                }
            }.padding(16).padding(.bottom, 28)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("الاشتراك")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { if plan.isEmpty { plan = app.plans.first?.id ?? "month1" } }
    }

    private var plansSection: some View {
        VStack(alignment: .leading, spacing: 11) {
            V3SectionHeader(title: "اختر الباقة", subtitle: "يمكنك تغييرها قبل الانتقال للدفع", icon: "calendar.badge.plus")
            ForEach(app.plans) { p in
                Button {
                    plan = p.id; selectionHaptic()
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: plan == p.id ? "checkmark.circle.fill" : "circle").foregroundStyle(plan == p.id ? Color.murshidBlue : Color.secondary)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(p.label).font(.headline).foregroundStyle(.primary)
                            Text("وصول كامل حسب مدة الباقة").font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer(); Text("\(p.amount.formatted()) د.ع").font(.headline).foregroundStyle(Color.murshidBlue)
                    }
                    .padding(15)
                    .background(plan == p.id ? Color.murshidBlue.opacity(0.08) : Color.murshidSurface, in: RoundedRectangle(cornerRadius: 18))
                }.buttonStyle(.plain)
            }
        }
    }

    private var actionSection: some View {
        VStack(spacing: 12) {
            if !statusMessage.isEmpty { V3InlineMessage(text: statusMessage, icon: "checkmark.circle.fill", tone: .success) }
            if !error.isEmpty { V3InlineMessage(text: error, icon: "exclamationmark.triangle.fill", tone: .warning) }
            Button(action: startCheckout) {
                HStack(spacing: 9) {
                    if loading { ProgressView().tint(.white) }
                    Image(systemName: "safari.fill")
                    Text(loading ? "جاري تجهيز الرابط…" : "الانتقال إلى الدفع الخارجي")
                }
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(loading || plan.isEmpty)
            if storedOrderID > 0 {
                Button("تحقق من حالة آخر عملية") { Task { await checkStatus() } }.font(.headline).foregroundStyle(Color.murshidBlue).padding(.vertical, 8)
            }
            Label("بعد اختيار الباقة ستنتقل إلى صفحة الدفع الخارجية الآمنة. لا توجد طريقة دفع يختارها الطالب داخل التطبيق.", systemImage: "rectangle.and.hand.point.up.left.fill")
                .font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
    }

    private func startCheckout() {
        error = ""; statusMessage = ""; loading = true
        Task {
            do {
                let d = try await APIClient.shared.request("api/payment-checkout.php", method: "POST", body: [
                    "csrf": app.csrf,
                    "action": "checkout",
                    "plan": plan,
                    "customer_name": app.user?.name ?? "طالب مشترك",
                    "customer_phone": "07700000000"
                ])
                if jBool(d["subscribed"]) {
                    let b = try await APIClient.shared.bootstrap()
                    await MainActor.run { app.applyBootstrap(b); loading = false; statusMessage = "اشتراكك مفعّل بالفعل."; haptic() }
                    return
                }
                guard let url = URL(string: jString(d["redirect"])) else { throw APIError(message: "تعذر تجهيز رابط الدفع الخارجي.", status: 422, paymentRequired: false) }
                await MainActor.run {
                    storedOrderID = jInt(d["order_id"])
                    loading = false
                    openURL(url)
                }
            } catch {
                await MainActor.run { loading = false; self.error = error.localizedDescription; haptic(.error) }
            }
        }
    }

    private func checkStatus() async {
        guard storedOrderID > 0 else { return }
        do {
            let d = try await APIClient.shared.request("api/payment-checkout.php", method: "POST", body: ["csrf": app.csrf, "action": "status", "order_id": storedOrderID])
            let subscribed = jBool(d["subscribed"])
            if subscribed {
                let b = try await APIClient.shared.bootstrap()
                await MainActor.run { app.applyBootstrap(b); storedOrderID = 0; haptic() }
            }
            await MainActor.run { statusMessage = jString(d["message"], default: subscribed ? "تم تفعيل الاشتراك." : "الدفع قيد التحقق."); error = "" }
        } catch { await MainActor.run { self.error = error.localizedDescription; haptic(.error) } }
    }
}

private struct V31AccountRowLabel: View {
    let title: String
    let subtitle: String
    let icon: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Color.murshidBlue)
                .frame(width: 34, height: 34)
                .background(Color.murshidBlue.opacity(0.09), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.body.weight(.medium)).foregroundStyle(.primary)
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.left").font(.caption.bold()).foregroundStyle(.tertiary)
        }
        .frame(minHeight: 54)
    }
}

private struct V31AccountRow: View {
    let title: String
    let subtitle: String
    let icon: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            V31AccountRowLabel(title: title, subtitle: subtitle, icon: icon)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Account with real Face ID and PhotosPicker avatar

struct V31AccountView: View {
    @EnvironmentObject var app: AppSession
    @EnvironmentObject var device: DeviceServices
    @AppStorage("appearance") private var appearance = "system"
    @AppStorage("biometric_lock") private var biometricLock = false
    @AppStorage("study_reminder") private var studyReminder = false
    @AppStorage("study_reminder_hour") private var reminderHour = 19
    @AppStorage("study_reminder_minute") private var reminderMinute = 0

    @State private var account: JSON = [:]
    @State private var loading = true
    @State private var error = ""
    @State private var showRename = false
    @State private var newName = ""
    @State private var loggingOut = false
    @State private var avatarItem: PhotosPickerItem?
    @State private var avatarPreview: UIImage?
    @State private var uploadingAvatar = false
    @State private var faceIDMessage = ""

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                profileCard
                if loading { V3SkeletonCard(height: 90) }
                subscriptionCard
                servicesSection
                securitySection
                appearanceSection
                logoutSection
                appInfo
                if !faceIDMessage.isEmpty { V3InlineMessage(text: faceIDMessage, icon: "faceid", tone: .info) }
                if !error.isEmpty { V3InlineMessage(text: error, icon: "exclamationmark.triangle.fill", tone: .warning) }
            }.padding(16).padding(.bottom, 28)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("حسابي")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
        .onChange(of: avatarItem) { _ in Task { await processAvatarSelection() } }
        .alert("تغيير الاسم", isPresented: $showRename) {
            TextField("الاسم الثلاثي", text: $newName)
            Button("حفظ") { Task { await rename() } }
            Button("إلغاء", role: .cancel) {}
        } message: { Text("يمكن تغيير الاسم مرة واحدة فقط حسب إعدادات المنصة.") }
    }

    private var profileCard: some View {
        ZStack {
            LinearGradient(colors: [.murshidNavy, .murshidBlue], startPoint: .topTrailing, endPoint: .bottomLeading)
            HStack(spacing: 15) {
                VStack(spacing: 7) {
                    ZStack(alignment: .bottomTrailing) {
                        avatarView
                        PhotosPicker(selection: $avatarItem, matching: .images) {
                            ZStack {
                                Circle().fill(.white).frame(width: 29, height: 29)
                                Image(systemName: "camera.fill").font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                            }
                        }
                        .buttonStyle(.plain)
                        .disabled(uploadingAvatar)
                        if uploadingAvatar {
                            ProgressView()
                                .controlSize(.small)
                                .tint(.white)
                                .offset(x: -8, y: -8)
                        }
                    }
                    Text(avatarStatusText).font(.caption2.weight(.semibold)).foregroundStyle(.white.opacity(0.76))
                }
                VStack(alignment: .leading, spacing: 5) {
                    Text(app.user?.name ?? "").font(.title3.bold()).foregroundStyle(.white)
                    Text(app.user?.grade ?? "").font(.subheadline).foregroundStyle(.white.opacity(0.78))
                    if app.subscribed { Label("اشتراك فعّال", systemImage: "checkmark.seal.fill").font(.caption.bold()).foregroundStyle(.white) }
                    else { Label("\(app.freeRemaining ?? 0) سؤال مجاني متبقٍ", systemImage: "gift.fill").font(.caption.bold()).foregroundStyle(.white.opacity(0.86)) }
                }
                Spacer()
            }.padding(20)
        }
        .frame(minHeight: 154)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
    }

    @ViewBuilder private var avatarView: some View {
        if let preview = avatarPreview {
            Image(uiImage: preview).resizable().scaledToFill().frame(width: 78, height: 78).clipShape(Circle()).overlay(Circle().stroke(.white.opacity(0.34), lineWidth: 2))
        } else {
            let raw = jString(account["avatar_url"])
            if let url = URL(string: raw), !raw.isEmpty {
                AsyncImage(url: url) { phase in
                    if case let .success(image) = phase { image.resizable().scaledToFill() }
                    else { Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(.white.opacity(0.9)) }
                }.frame(width: 78, height: 78).clipShape(Circle()).overlay(Circle().stroke(.white.opacity(0.34), lineWidth: 2))
            } else {
                Image(systemName: "person.crop.circle.fill").resizable().frame(width: 78, height: 78).foregroundStyle(.white.opacity(0.92))
            }
        }
    }

    private var avatarStatusText: String {
        switch jString(account["avatar_status"]) {
        case "pending": return "الصورة قيد المراجعة"
        case "rejected": return "الصورة تحتاج تغييرًا"
        case "approved": return "الصورة معتمدة"
        default: return "اضغط لتغيير الصورة"
        }
    }

    private var subscriptionCard: some View {
        NavigationLink(destination: V31SubscriptionView()) {
            MurshidCard {
                HStack(spacing: 13) {
                    Image(systemName: app.subscribed ? "checkmark.seal.fill" : "arrow.up.forward.app.fill").font(.title2).foregroundStyle(app.subscribed ? .green : Color.murshidBlue)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(app.subscribed ? "الاشتراك" : "افتح كامل المنصة").font(.headline).foregroundStyle(.primary)
                        Text(app.subscribed ? "اشتراكك مفعّل حاليًا." : "الدفع خارجي ولا نطلب رقمًا إضافيًا من هنا.").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer(); Image(systemName: "chevron.left").foregroundStyle(.secondary)
                }
            }
        }.buttonStyle(.plain)
    }

    private var servicesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "الحساب والخدمات", subtitle: "إدارة كل ما يخص حسابك", icon: "square.grid.2x2.fill")
            MurshidCard {
                VStack(spacing: 0) {
                    if jBool(account["can_change_name"]) {
                        V31AccountRow(title: "تغيير الاسم", subtitle: "متاح مرة واحدة", icon: "pencil") { newName = app.user?.name ?? ""; showRename = true }
                        Divider().padding(.leading, 48)
                    }
                    NavigationLink(destination: V31SchoolAdRequestView()) { V31AccountRowLabel(title: "طلب إعلان مدرسة", subtitle: "أرسل الطلب مباشرة إلى الإدارة", icon: "megaphone.fill") }
                    Divider().padding(.leading, 48)
                    NavigationLink(destination: NotificationsView()) { V31AccountRowLabel(title: "الإشعارات", subtitle: app.unreadNotifications > 0 ? "لديك \(app.unreadNotifications) غير مقروء" : "لا توجد إشعارات جديدة", icon: "bell.fill") }
                    Divider().padding(.leading, 48)
                    NavigationLink(destination: SupportView()) { V31AccountRowLabel(title: "خدمة العملاء", subtitle: "محادثة مباشرة مع الدعم", icon: "message.fill") }
                    Divider().padding(.leading, 48)
                    NavigationLink(destination: StoriesView()) { V31AccountRowLabel(title: "غيّر جو", subtitle: "رسائل وقصص قصيرة", icon: "sparkles") }
                }
            }
        }
    }

    private var securitySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "الأمان والتذكير", subtitle: "خيارات خاصة بجهازك", icon: "lock.shield.fill")
            MurshidCard {
                VStack(spacing: 14) {
                    Toggle(isOn: faceIDBinding) { Label("قفل حقيقي بـ Face ID", systemImage: "faceid") }
                        .disabled(!faceIDSupported)
                    if !faceIDSupported { Text("Face ID غير متاح على هذا الجهاز أو غير مهيأ من إعدادات iPhone.").font(.caption).foregroundStyle(.secondary) }
                    Divider()
                    Toggle(isOn: Binding(get: { studyReminder }, set: { value in
                        studyReminder = value
                        Task { if value { await device.requestStudyReminder(hour: reminderHour, minute: reminderMinute) } else { device.disableStudyReminder() } }
                    })) { Label("تذكير دراسة يومي", systemImage: "bell.badge.fill") }
                    if studyReminder {
                        DatePicker("وقت التذكير", selection: reminderDateBinding, displayedComponents: .hourAndMinute)
                            .onChange(of: reminderHour) { _ in rescheduleReminder() }
                            .onChange(of: reminderMinute) { _ in rescheduleReminder() }
                    }
                    Divider()
                    NavigationLink(destination: V3PasswordRecoveryView()) {
                        HStack { Label("استعادة / تغيير كلمة المرور", systemImage: "key.fill"); Spacer(); Image(systemName: "chevron.left").font(.caption.bold()).foregroundStyle(.secondary) }.foregroundStyle(.primary)
                    }
                }
            }
        }
    }

    private var appearanceSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "المظهر", subtitle: "اختر الشكل المريح لك", icon: "circle.lefthalf.filled")
            MurshidCard {
                Picker("المظهر", selection: $appearance) {
                    Label("النظام", systemImage: "iphone").tag("system")
                    Label("فاتح", systemImage: "sun.max.fill").tag("light")
                    Label("داكن", systemImage: "moon.fill").tag("dark")
                }.pickerStyle(.segmented)
            }
        }
    }

    private var logoutSection: some View {
        MurshidCard {
            Button(role: .destructive) { logout() } label: {
                HStack { Label("تسجيل الخروج", systemImage: "rectangle.portrait.and.arrow.right"); Spacer(); if loggingOut { ProgressView() } }
                    .frame(maxWidth: .infinity, minHeight: 46, alignment: .leading)
            }.disabled(loggingOut)
        }
    }

    private var appInfo: some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack { Label("منصة المرشد الوزاري", systemImage: "graduationcap.fill").font(.headline); Spacer(); Text("3.1 • 310").font(.caption.monospacedDigit()).foregroundStyle(.secondary) }
                Text("تطبيق iPhone أصلي مرتبط مباشرة بحسابك في المنصة.").font(.footnote).foregroundStyle(.secondary)
                HStack(spacing: 14) {
                    Link("الخصوصية", destination: URL(string: "https://www.mur-iq.com/privacy.php")!)
                    Link("شروط الاستخدام", destination: URL(string: "https://www.mur-iq.com/terms.php")!)
                }.font(.caption.bold())
            }
        }
    }

    private var faceIDSupported: Bool {
        let context = LAContext(); var error: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) && context.biometryType == .faceID
    }

    private var faceIDBinding: Binding<Bool> {
        Binding(get: { biometricLock }, set: { newValue in
            if !newValue { biometricLock = false; faceIDMessage = "تم إيقاف قفل Face ID."; return }
            Task {
                let ok = await authenticateFaceID(reason: "أكد Face ID لتفعيل قفل المرشد الوزاري")
                await MainActor.run {
                    biometricLock = ok
                    faceIDMessage = ok ? "تم تفعيل Face ID فعليًا. سيُطلب بعد الرجوع للتطبيق من الخلفية." : "لم يتم تفعيل Face ID. حاول مرة أخرى."
                    if ok { haptic() } else { haptic(.error) }
                }
            }
        })
    }

    private func authenticateFaceID(reason: String) async -> Bool {
        let context = LAContext(); context.localizedCancelTitle = "إلغاء"; context.localizedFallbackTitle = ""
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error), context.biometryType == .faceID else { return false }
        do { return try await context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) }
        catch { return false }
    }

    private var reminderDateBinding: Binding<Date> {
        Binding { Calendar.current.date(from: DateComponents(hour: reminderHour, minute: reminderMinute)) ?? Date() }
        set: { date in let c = Calendar.current.dateComponents([.hour,.minute], from: date); reminderHour = c.hour ?? 19; reminderMinute = c.minute ?? 0 }
    }

    private func rescheduleReminder() { guard studyReminder else { return }; Task { await device.requestStudyReminder(hour: reminderHour, minute: reminderMinute) } }

    private func load() async {
        do { let d = try await APIClient.shared.request("mobile/account.php"); await MainActor.run { account = d; loading = false; error = "" } }
        catch { await MainActor.run { loading = false; self.error = error.localizedDescription } }
    }

    private func rename() async {
        let name = v3NormalizePersonName(newName)
        guard name.split(separator: " ").count >= 3 else { await MainActor.run { error = "اكتب الاسم الثلاثي على الأقل."; haptic(.error) }; return }
        do {
            _ = try await APIClient.shared.request("mobile/account.php", method: "POST", body: ["csrf": app.csrf, "action": "change_name", "name": name])
            let b = try await APIClient.shared.bootstrap()
            await MainActor.run { app.applyBootstrap(b); error = ""; haptic() }
            await load()
        } catch { await MainActor.run { self.error = error.localizedDescription; haptic(.error) } }
    }

    private func processAvatarSelection() async {
        guard let item = avatarItem else { return }
        do {
            guard let data = try await item.loadTransferable(type: Data.self), let image = UIImage(data: data), let jpeg = image.jpegData(compressionQuality: 0.88) else {
                throw APIError(message: "تعذر قراءة الصورة المختارة.", status: 422, paymentRequired: false)
            }
            await MainActor.run { avatarPreview = image; uploadingAvatar = true; error = "" }
            let d = try await APIClient.shared.uploadAvatarJPEG(jpeg, csrf: app.csrf)
            await MainActor.run { uploadingAvatar = false; error = jString(d["message"], default: "تم رفع الصورة للمراجعة."); haptic() }
            await load()
        } catch {
            await MainActor.run { uploadingAvatar = false; self.error = error.localizedDescription; haptic(.error) }
        }
    }

    private func logout() {
        loggingOut = true
        Task {
            do { try await APIClient.shared.logout(); await MainActor.run { loggingOut = false; haptic() } }
            catch { await MainActor.run { loggingOut = false; self.error = error.localizedDescription; haptic(.error) } }
        }
    }
}

// MARK: - Internal school advertisement request -> existing admin support inbox

struct V31SchoolAdRequestView: View {
    @EnvironmentObject var app: AppSession
    @State private var schoolName = ""
    @State private var adText = ""
    @State private var days = 1
    @State private var loading = false
    @State private var message = ""
    @State private var isError = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                V3IntroCard(eyebrow: "إعلانات المدارس", title: "أرسل طلب الإعلان إلى الإدارة", text: "الطلب يصل كرسالة داخلية إلى لوحة الإدارة، ويتضمن اسم المدرسة ونص الإعلان وعدد الأيام.", icon: "megaphone.fill")
                MurshidCard {
                    VStack(alignment: .leading, spacing: 14) {
                        Label("اسم المدرسة", systemImage: "building.2.fill").font(.subheadline.bold())
                        TextField("مثال: مدارس الأوائل", text: $schoolName)
                            .padding(12).background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 13))
                        Label("الإعلان الكامل", systemImage: "text.alignright").font(.subheadline.bold())
                        TextEditor(text: $adText)
                            .frame(minHeight: 150)
                            .padding(10)
                            .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 13))
                        Stepper(value: $days, in: 1...90) {
                            HStack { Label("مدة الإعلان", systemImage: "calendar"); Spacer(); Text("\(days) يوم").bold() }
                        }
                    }
                }
                if !message.isEmpty { V3InlineMessage(text: message, icon: isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill", tone: isError ? .warning : .success) }
                Button(action: submit) {
                    HStack { if loading { ProgressView().tint(.white) }; Image(systemName: "paperplane.fill"); Text(loading ? "جاري الإرسال…" : "إرسال الطلب إلى الإدارة") }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(loading)
            }.padding(16).padding(.bottom, 30)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("طلب إعلان مدرسة")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func submit() {
        let school = schoolName.trimmingCharacters(in: .whitespacesAndNewlines)
        let text = adText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard school.count >= 3 else { message = "اكتب اسم المدرسة."; isError = true; haptic(.error); return }
        guard text.count >= 10 else { message = "اكتب نص الإعلان الكامل."; isError = true; haptic(.error); return }
        loading = true; message = ""; isError = false
        let internalMessage = """
        طلب إعلان مدرسة — تطبيق iOS
        اسم المدرسة: \(school)
        الإعلان الكامل:
        \(text)
        عدد الأيام: \(days)
        الطالب المرسل: \(app.user?.name ?? "طالب")
        """
        Task {
            do {
                _ = try await APIClient.shared.request("mobile/support.php", method: "POST", body: ["csrf": app.csrf, "message": internalMessage])
                await MainActor.run { loading = false; message = "تم إرسال طلب الإعلان إلى الإدارة بنجاح."; isError = false; haptic(); adText = "" }
            } catch { await MainActor.run { loading = false; message = error.localizedDescription; isError = true; haptic(.error) } }
        }
    }
}

// MARK: - Avatar multipart upload

extension APIClient {
    func uploadAvatarJPEG(_ imageData: Data, csrf: String) async throws -> JSON {
        let url = baseURL.appendingPathComponent("mobile/avatar.php")
        let boundary = "MurshidBoundary-\(UUID().uuidString)"
        var body = Data()
        func append(_ string: String) { body.append(Data(string.utf8)) }
        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"csrf\"\r\n\r\n")
        append(csrf + "\r\n")
        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"avatar\"; filename=\"avatar.jpg\"\r\n")
        append("Content-Type: image/jpeg\r\n\r\n")
        body.append(imageData)
        append("\r\n--\(boundary)--\r\n")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = body
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError(message: "تعذر الاتصال بالخادم.", status: 0, paymentRequired: false) }
        guard let json = try? JSONSerialization.jsonObject(with: data) as? JSON else { throw APIError(message: "تعذر قراءة استجابة رفع الصورة.", status: http.statusCode, paymentRequired: false) }
        if !(200...299).contains(http.statusCode) || !jBool(json["ok"]) { throw APIError(message: jString(json["message"], default: "تعذر رفع الصورة."), status: http.statusCode, paymentRequired: false) }
        return json
    }
}

// MARK: - APNs client registration (server endpoint is included in the matching website patch)

final class MurshidAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        Task {
            let center = UNUserNotificationCenter.current()
            let granted = (try? await center.requestAuthorization(options: [.alert, .badge, .sound])) ?? false
            if granted { await MainActor.run { UIApplication.shared.registerForRemoteNotifications() } }
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        UserDefaults.standard.set(token, forKey: "apns_device_token")
        Task { await V31PushRegistrar.syncIfPossible() }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        UserDefaults.standard.set(error.localizedDescription, forKey: "apns_last_error")
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }
}

@MainActor
enum V31PushRegistrar {
    static func syncIfPossible() async {
        let token = UserDefaults.standard.string(forKey: "apns_device_token") ?? ""
        let csrf = AppSession.shared.csrf
        guard !token.isEmpty, !csrf.isEmpty, AppSession.shared.v3Authenticated else { return }
        do {
            _ = try await APIClient.shared.request("mobile/apns-register.php", method: "POST", body: [
                "csrf": csrf,
                "token": token,
                "bundle_id": "com.muriq.murshid",
                "platform": "ios"
            ])
        } catch { }
    }
}