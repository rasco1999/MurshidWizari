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
        case .advertise: return "أعلن هنا"
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
    @Environment(\.colorScheme) private var colorScheme
    @AppStorage("appearance") private var appearance = "system"
    @State private var insights: JSON = [:]
    @State private var weekly: JSON = [:]
    @State private var loadingInsights = true
    @State private var error = ""
    @State private var currentClock = Date()
    @State private var homeAvatarURL: URL?
    private let clockTicker = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                welcomeHeader
                if !device.isOnline { offlineBanner }
                subscriptionStrip
                V31WebsiteHeroSlider()
                releaseUpdateStrip
                performanceStrip
                if app.featureEnabled("custom_exam") { mockExamStrip }
                subjectsSection
                nextStepsSection
                quickTools
                if !error.isEmpty { V3InlineMessage(text: error, icon: "wifi.exclamationmark", tone: .warning) }
                socialLinksFooter
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
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: toggleAppearance) {
                    Image(systemName: isDarkAppearance ? "sun.max.fill" : "moon.fill")
                        .font(.headline)
                        .frame(width: 32, height: 32)
                }
                .accessibilityLabel(isDarkAppearance ? "تفعيل الوضع الفاتح" : "تفعيل الوضع الداكن")
            }
        }
        .task { await refreshAll(); await loadHomeAvatar() }
        .refreshable { await refreshAll(); await loadHomeAvatar() }
        .onReceive(clockTicker) { currentClock = $0 }
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("MurshidAvatarChanged"))) { _ in Task { await loadHomeAvatar() } }
        .modifier(V44SchoolAdPopupModifier())
    }

    private var welcomeHeader: some View {
        ZStack(alignment: .topTrailing) {
            LinearGradient(
                colors: [.murshidNavy, Color(red: 0.06, green: 0.26, blue: 0.51), .murshidBlue],
                startPoint: .topTrailing, endPoint: .bottomLeading
            )
            Circle()
                .fill(Color.white.opacity(0.07))
                .frame(width: 190, height: 190)
                .offset(x: 75, y: -84)
            Circle()
                .stroke(Color.murshidGold.opacity(0.20), lineWidth: 1.5)
                .frame(width: 185, height: 185)
                .offset(x: 66, y: -76)

            VStack(alignment: .leading, spacing: 17) {
                HStack(alignment: .center, spacing: 14) {
                    Group {
                        if let homeAvatarURL {
                            AsyncImage(url: homeAvatarURL) { phase in
                                if case let .success(image) = phase { image.resizable().scaledToFill() }
                                else { Image(systemName: "person.crop.circle.fill").resizable().scaledToFit().padding(9) }
                            }
                        } else if let cached = V43AvatarCache.image(userID: app.user?.id ?? 0) {
                            Image(uiImage: cached).resizable().scaledToFill()
                        } else {
                            Image(systemName: "person.crop.circle.fill").resizable().scaledToFit().padding(9)
                        }
                    }
                    .frame(width: 58, height: 58)
                    .background(.white.opacity(0.13), in: Circle())
                    .clipShape(Circle())
                    .overlay(Circle().stroke(.white.opacity(0.3), lineWidth: 2))
                    .foregroundStyle(.white)
                    VStack(alignment: .leading, spacing: 5) {
                        Text(timeOfDayGreeting)
                            .font(.caption.weight(.medium)).foregroundStyle(.white.opacity(0.79))
                        Text(firstName)
                            .font(.system(.title, design: .rounded, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                        Label(app.user?.grade ?? "طالب المرشد", systemImage: "books.vertical.fill")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.murshidGold)
                    }
                    Spacer(minLength: 0)
                }

                Text(motivation)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.89))
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 10) {
                    welcomeStat(
                        value: "\(jInt(app.stats["total_answers"]))",
                        caption: "إجابة",
                        icon: "checkmark.circle.fill"
                    )
                    welcomeStat(
                        value: "\(app.accuracy)%",
                        caption: "دقة الحل",
                        icon: "chart.line.uptrend.xyaxis"
                    )
                    welcomeStat(
                        value: app.subscribed ? "مفعّل" : "\(max(0,app.freeRemaining ?? app.freeLimit))",
                        caption: app.subscribed ? "الاشتراك" : "سؤال مجاني",
                        icon: app.subscribed ? "checkmark.seal.fill" : "gift.fill"
                    )
                }
            }
            .padding(19)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .clipShape(RoundedRectangle(cornerRadius: 25, style: .continuous))
        .overlay(alignment: .topTrailing) {
            VStack(spacing: 2) {
                HStack(spacing: 5) {
                    Image(systemName: "clock.fill")
                        .foregroundStyle(Color.murshidGold)
                    Text(clockTimeText).font(.system(.subheadline, design: .rounded).bold().monospacedDigit())
                }
                Text(clockDateText)
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .foregroundStyle(.white.opacity(0.82))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(.white.opacity(0.13), in: RoundedRectangle(cornerRadius: 13))
            .overlay(RoundedRectangle(cornerRadius: 13).stroke(Color.murshidGold.opacity(0.45)))
            .environment(\.layoutDirection, .leftToRight)
            .padding(.top, 14)
            .padding(.trailing, 14)
            .accessibilityLabel("الوقت \(clockTimeText)، التاريخ \(clockDateText)")
        }
        .shadow(color: Color.murshidNavy.opacity(0.20), radius: 20, y: 9)
        .padding(.top, 6)
        .accessibilityElement(children: .combine)
    }

    private func welcomeStat(value: String, caption: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Label(value, systemImage: icon)
                .font(.headline.bold())
                .foregroundStyle(Color.murshidGold)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(caption).font(.caption2.weight(.medium))
                .foregroundStyle(.white.opacity(0.82))
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(11)
        .background(.white.opacity(0.11), in: RoundedRectangle(cornerRadius: 13))
    }

    private var offlineBanner: some View {
        V3InlineMessage(text: "أنت غير متصل الآن. سنعرض البيانات المحفوظة متى كانت متاحة.", icon: "wifi.slash", tone: .warning)
    }

    // Visible first-screen call to action; entitlement remains enforced by the server.
    @ViewBuilder
    private var subscriptionStrip: some View {
        if app.subscribed {
            MurshidCard {
                Label("اشتراكك فعّال · جميع الأسئلة متاحة", systemImage: "checkmark.seal.fill")
                    .font(.headline).foregroundStyle(.green)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        } else {
            NavigationLink(destination: V31SubscriptionView()) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: app.freeRemaining == 0 ? "exclamationmark.circle.fill" : "crown.fill")
                            .font(.title2)
                            .foregroundStyle(Color.murshidGold)
                            .frame(width: 42, height: 42)
                            .background(.white.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
                        VStack(alignment: .leading, spacing: 5) {
                            Text(app.freeRemaining == 0 ? "جاهز تكمل تطوّرك؟" : "جرّب، تعلّم، ثم قرّر")
                                .font(.headline).foregroundStyle(.white)
                            Text(app.freeRemaining == 0
                                 ? "راجع أخطاءك مجانًا، واشترك لحل أسئلة جديدة والاستمرار في التدريب."
                                 : "حل \(max(0, app.freeRemaining ?? max(0, app.freeLimit - app.freeUsed))) أسئلة مجانية متبقية، واطّلع على التصحيح قبل أن تختار الاشتراك.")
                                .font(.subheadline).foregroundStyle(.white.opacity(0.82))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                    }
                    HStack(spacing: 9) {
                        Image(systemName: "creditcard.fill")
                        Text("اكتشف مزايا الاشتراك").font(.title3.bold())
                        Spacer()
                        Image(systemName: "arrow.left").font(.headline.bold())
                    }
                    .foregroundStyle(Color.murshidNavy)
                    .padding(.horizontal, 18)
                    .frame(height: 54)
                    .background(Color.murshidGold, in: RoundedRectangle(cornerRadius: 15, style: .continuous))
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    LinearGradient(colors: [.murshidNavy, .murshidBlue],
                                   startPoint: .topTrailing, endPoint: .bottomLeading),
                    in: RoundedRectangle(cornerRadius: 21, style: .continuous)
                )
                .overlay(RoundedRectangle(cornerRadius: 21).stroke(Color.murshidGold.opacity(0.55), lineWidth: 1))
                .accessibilityElement(children: .combine)
                .accessibilityLabel("اشتراك، افتح جميع الأسئلة الوزارية")
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private var releaseUpdateStrip: some View {
        let serverVersion = jString(app.releaseInfo["version"])
        let download = jString(app.releaseInfo["download_url"])
        let installedVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "4.2.0"
        let newer = isVersion(serverVersion, newerThan: installedVersion)
        if newer {
            MurshidCard {
                VStack(alignment: .leading, spacing: 10) {
                    Label("يتوفر تحديث \(serverVersion)", systemImage: "arrow.down.app.fill")
                        .font(.headline).foregroundStyle(Color.murshidBlue)
                    Text(jString(app.releaseInfo["notes"], default: "يتوفر إصدار جديد من التطبيق."))
                        .font(.subheadline).foregroundStyle(.secondary)
                    HStack(spacing: 12) {
                        Spacer()
                        if let url = URL(string: download), url.scheme?.lowercased() == "https", !download.isEmpty {
                            Link(destination: url) {
                                Label("تحديث", systemImage: "square.and.arrow.down.fill")
                                    .font(.headline)
                                    .padding(.horizontal, 20)
                                    .padding(.vertical, 11)
                                    .background(Color.murshidGold, in: Capsule())
                                    .foregroundStyle(Color.murshidNavy)
                            }
                        } else {
                            Text("رابط التحديث غير متاح حالياً")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    private func isVersion(_ candidate: String, newerThan current: String) -> Bool {
        let lhs = candidate.split(separator: ".").map { Int($0) ?? 0 }
        let rhs = current.split(separator: ".").map { Int($0) ?? 0 }
        let count = max(lhs.count, rhs.count)
        for index in 0..<count {
            let l = index < lhs.count ? lhs[index] : 0
            let r = index < rhs.count ? rhs[index] : 0
            if l != r { return l > r }
        }
        return false
    }

    private var performanceStrip: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            if loadingInsights {
                ForEach(0..<4, id: \.self) { _ in V3SkeletonCard(height: 116) }
            } else {
                V3MetricCard(title: "الإجابات", value: "\(jInt(app.stats["total_answers"]))", icon: "checkmark.circle.fill")
                V3MetricCard(title: "اختبارات مكتملة", value: "\(jInt(app.stats["exams_completed"]))", icon: "flag.checkered")
                V3MetricCard(title: "هذا الأسبوع", value: "\(jInt(weekly["answers"]))", icon: "calendar")
                V3MetricCard(title: "مؤشر المراجعة", value: "\(jInt(insights["success_index"]))%", icon: "sparkles")
            }
        }
    }

    private var learningImpactStrip: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "تعلّم من إجاباتك", subtitle: "خطوة صغيرة بعد كل اختبار", icon: "chart.xyaxis.line")
            MurshidCard {
                VStack(alignment: .leading, spacing: 12) {
                    if jInt(weekly["answers"]) > 0 && jInt(weekly["previous"]) > 0,
                       weekly["accuracy_change"] != nil {
                        let delta = jInt(weekly["accuracy_change"])
                        HStack(spacing: 9) {
                            Image(systemName: delta >= 0 ? "arrow.up.right.circle.fill" : "arrow.down.right.circle.fill")
                                .font(.title2)
                                .foregroundStyle(delta >= 0 ? Color.green : Color.orange)
                            VStack(alignment: .leading, spacing: 4) {
                                Text("دقتك هذا الأسبوع \(jInt(weekly["accuracy"]))%")
                                    .font(.headline)
                                Text(delta == 0 ? "مماثلة للأسبوع السابق" : "\(abs(delta)) نقطة مئوية \(delta > 0 ? "أعلى" : "أقل") من الأسبوع السابق")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    } else {
                        Label("كل إجابة تساهم في تحليل نقاط قوتك والمواضيع التي تحتاج مراجعة.", systemImage: "brain.head.profile")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    NavigationLink(destination: ReviewView()) {
                        HStack {
                            Label("افتح دفتر الأخطاء والتحليل", systemImage: "book.closed.fill")
                                .font(.subheadline.bold())
                            Spacer()
                            Image(systemName: "arrow.left")
                        }
                        .foregroundStyle(Color.murshidBlue)
                    }
                }
            }
        }
    }

    private var mockExamStrip: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "محاكاة الوزاري", subtitle: "تدرّب على أجواء الامتحان الوزاري", icon: "timer")
            NavigationLink(destination: V40CustomExamBuilderView(initialMode: "mock")) {
                MurshidCard {
                    HStack(spacing: 12) {
                        Image(systemName: "timer")
                            .font(.title2)
                            .foregroundStyle(Color.murshidBlue)
                        VStack(alignment: .leading, spacing: 5) {
                            Text("ابدأ محاكاة الوزاري")
                                .font(.headline)
                                .foregroundStyle(.primary)
                            Text("30 سؤالًا · 45 دقيقة")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.left")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .buttonStyle(.plain)
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
            V3SectionHeader(title: "أدواتك السريعة", subtitle: "اختبارات ومراجعة وبحث ومساهمات", icon: "bolt.fill")
            LazyVGrid(columns: columns, spacing: 12) {
                NavigationLink(destination: ReviewView()) { V3ToolCard(title: "راجع أخطاءك", subtitle: "مراجعة ذكية", icon: "brain.head.profile") }
                if app.featureEnabled("custom_exam") {
                    NavigationLink(destination: V40CustomExamBuilderView()) { V3ToolCard(title: "اختبار مخصص", subtitle: "عدد · نوع · مؤقت", icon: "slider.horizontal.3") }
                }
                if app.featureEnabled("daily_challenge") {
                    NavigationLink(destination: V40DailyChallengeView()) { V3ToolCard(title: "تحدي اليوم", subtitle: "سؤال واحد كل 24 ساعة", icon: "flame.fill") }
                }
                if app.featureEnabled("student_question_submit") {
                    NavigationLink(destination: V40StudentQuestionSubmitView()) { V3ToolCard(title: "اقترح سؤالًا", subtitle: "ساهم في البنك", icon: "plus.bubble.fill") }
                }
                NavigationLink(destination: V40MyQuestionSubmissionsView()) { V3ToolCard(title: "طلباتك", subtitle: "تابع المراجعة", icon: "tray.full.fill") }
                if app.featureEnabled("attempt_history") {
                    NavigationLink(destination: V40AttemptHistoryView()) { V3ToolCard(title: "سجل الاختبارات", subtitle: "نتائجك السابقة", icon: "clock.arrow.circlepath") }
                }
                NavigationLink(destination: V3ContestView()) { V3ToolCard(title: "تحدي المليون", subtitle: "ترتيب ونقاط", icon: "trophy.fill") }
                NavigationLink(destination: V43StoriesView()) { V3ToolCard(title: "غيّر جو", subtitle: "استراحة قصيرة", icon: "sparkles") }
            }
            .buttonStyle(.plain)
            learningImpactStrip
        }
    }

    // Official website accounts. HTTPS universal links open the installed social
    // app when supported by iOS; otherwise they remain reachable in Safari.
    // Dynamic system surface/text colors follow both manually selected themes.
    private var socialLinksFooter: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(
                title: "تابعنا على منصات التواصل",
                subtitle: "كل جديد من منصة المرشد الوزاري",
                icon: "bubble.left.and.bubble.right.fill"
            )
            MurshidCard {
                HStack(spacing: 9) {
                    socialLinkButton(
                        title: "إنستغرام",
                        icon: "camera.fill",
                        url: URL(string: "https://www.instagram.com/murshid.iq/")!,
                        color: Color(red: 0.76, green: 0.19, blue: 0.50)
                    )
                    socialLinkButton(
                        title: "فيسبوك",
                        icon: "f",
                        url: URL(string: "https://www.facebook.com/Murshidiq/")!,
                        color: Color(red: 0.12, green: 0.33, blue: 0.77)
                    )
                    socialLinkButton(
                        title: "تيليجرام",
                        icon: "paperplane.fill",
                        url: URL(string: "https://t.me/El_Murshed_iq")!,
                        color: Color(red: 0.10, green: 0.57, blue: 0.79)
                    )
                }
                .frame(maxWidth: .infinity)
                Text("اضغط على الأيقونة لزيارة حسابنا الرسمي")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 5)
            }
        }
        .padding(.top, 5)
        .accessibilityElement(children: .contain)
    }

    private func socialLinkButton(
        title: String,
        icon: String,
        url: URL,
        color: Color
    ) -> some View {
        Link(destination: url) {
            VStack(spacing: 9) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(color)
                        .frame(width: 55, height: 55)
                        .shadow(color: color.opacity(0.20), radius: 7, y: 3)

                    if icon == "f" {
                        Text("f")
                            .font(.system(size: 36, weight: .heavy, design: .rounded))
                            .foregroundStyle(.white)
                            .offset(y: 3)
                    } else {
                        Image(systemName: icon)
                            .font(.system(size: 25, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                }
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .frame(maxWidth: .infinity, minHeight: 94)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("فتح حساب منصة المرشد الوزاري على \(title)")
        .accessibilityHint("يفتح الرابط خارج التطبيق")
    }

    private var isDarkAppearance: Bool {
        appearance == "dark" || (appearance == "system" && colorScheme == .dark)
    }

    private func toggleAppearance() {
        withAnimation(.easeInOut(duration: 0.2)) {
            appearance = isDarkAppearance ? "light" : "dark"
        }
        selectionHaptic()
    }

    private var firstName: String {
        app.user?.name.split(separator: " ").first.map(String.init) ?? "طالبنا"
    }

    private var timeOfDayGreeting: String {
        // Use Baghdad civil time even when the student's device is set to
        // another timezone. The existing clock ticker refreshes this label.
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = displayTimeZone
        let hour = calendar.component(.hour, from: currentClock)
        return hour < 12 ? "صباح الخير" : "مساء الخير"
    }

    private var displayTimeZone: TimeZone {
        TimeZone(identifier: "Asia/Baghdad") ?? .current
    }

    private var motivation: String {
        let messages = ["الاستمرار اليوم يصنع تفوق الغد.", "كل خطوة صغيرة تقرّبك من حلمك.", "اجعل هذا اليوم فرصة جديدة للنجاح.", "ثق بقدرتك، وابدأ من السؤال الأول.", "لا تقارن تقدمك إلا بنفسك بالأمس.", "الاجتهاد المتكرر يصنع إنجازاً كبيراً.", "أنت أقرب إلى هدفك مما تتصور.", "بعض الصبر وكثير من العمل يصنع الفرق.", "مراجعتك اليوم ترفع ثقتك غداً.", "العلم طريقك إلى مستقبل تستحقه."]
        let day = Calendar.current.ordinality(of: .day, in: .era, for: currentClock) ?? 0
        return messages[abs(day) % messages.count]
    }

    private var clockTimeText: String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.calendar = Calendar(identifier: .gregorian)
        fmt.timeZone = displayTimeZone
        fmt.dateFormat = "HH:mm"
        return fmt.string(from: currentClock)
    }

    private var clockDateText: String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.calendar = Calendar(identifier: .gregorian)
        fmt.timeZone = displayTimeZone
        fmt.dateFormat = "dd/MM"
        return fmt.string(from: currentClock)
    }

    private func loadHomeAvatar() async {
        guard let data = try? await APIClient.shared.request("mobile/account.php") else { return }
        let url = V43AvatarCache.url(jString(data["avatar_url"]), revision: jString(data["avatar_revision"]))
        await MainActor.run { homeAvatarURL = url }
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
            let config = try? await APIClient.shared.request("mobile/app-config.php", cacheKey: "app-config")
            await MainActor.run {
                app.applyBootstrap(b)
                if let config { app.applyAppConfig(config) }
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
    @Environment(\.openURL) private var openURL
    @EnvironmentObject private var device: DeviceServices
    @State private var selection = 0
    @State private var images: [Int: UIImage] = [:]
    @State private var ready = false
    @State private var showingAdvertisePrice = false
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
                // Avoid rotating through four network images on metered 4G/5G.
                guard ready && !device.isMeteredConnection else { return }
                withAnimation(.easeInOut(duration: 0.35)) {
                    selection = (selection + 1) % V31SlideKind.allCases.count
                }
            }
            .onChange(of: selection) { value in
                if device.isMeteredConnection, let slide = V31SlideKind(rawValue: value) {
                    Task { await loadImage(for: slide) }
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
        .onChange(of: device.isMeteredConnection) { metered in
            if !metered { Task { await preloadAllImages() } }
        }
        .alert("أعلن هنا", isPresented: $showingAdvertisePrice) {
            Button("إلغاء", role: .cancel) { }
            Button("إكمال") {
                var target = URLComponents(string: "https://t.me/r_cs4")
                target?.queryItems = [URLQueryItem(name: "text", value: "السلام عليكم، أريد طلب إعلان مدرسة في منصة المرشد الوزاري لمدة 24 ساعة بسعر 5000 دينار عراقي. يرجى إكمال الحجز معي.")]
                if let url = target?.url { openURL(url) }
            }
        } message: {
            Text("الإعلان لكل ٢٤ ساعة بسعر ٥ آلاف دينار عراقي فقط. بالضغط على إكمال ستُفتح محادثة @r_cs4 في تيليجرام مع رسالة طلب إعلان جاهزة، ويمكنك إرسالها بنفسك.")
        }
        .accessibilityLabel("سلايدر منصة المرشد الوزاري")
    }

    @ViewBuilder
    private func destination(for kind: V31SlideKind) -> some View {
        if kind == .advertise {
            Button { showingAdvertisePrice = true } label: { slideArtwork(for: kind) }
                .buttonStyle(.plain)
                .accessibilityLabel(kind.title)
        } else {
            NavigationLink {
                switch kind {
                case .million: V3ContestView()
                case .advertise: EmptyView()
                case .tests: V3TestsView()
                case .success: V3SuccessView()
                }
            } label: { slideArtwork(for: kind) }
                .buttonStyle(.plain)
                .accessibilityLabel(kind.title)
        }
    }

    private func slideArtwork(for kind: V31SlideKind) -> some View {
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

    @MainActor
    private func preloadAllImages() async {
        if device.isMeteredConnection {
            // Only the visible slide is downloaded; the rest remain lightweight
            // gradients until the student actually swipes to them.
            if let current = V31SlideKind(rawValue: selection) { await loadImage(for: current) }
            return
        }
        guard images.count < V31SlideKind.allCases.count else {
            ready = true
            return
        }

        let requests: [(Int, URL)] = V31SlideKind.allCases.compactMap { kind in
            guard images[kind.rawValue] == nil, let url = kind.imageURL else { return nil }
            return (kind.rawValue, url)
        }

        let payloads: [(Int, Data)] = await withTaskGroup(of: (Int, Data?).self) { group in
            for (id, url) in requests {
                group.addTask {
                    var request = URLRequest(url: url)
                    request.cachePolicy = .returnCacheDataElseLoad
                    request.timeoutInterval = 10
                    guard let (data, response) = try? await URLSession.shared.data(for: request),
                          let http = response as? HTTPURLResponse,
                          (200...299).contains(http.statusCode) else {
                        return (id, nil)
                    }
                    return (id, data)
                }
            }

            var result: [(Int, Data)] = []
            for await item in group {
                if let data = item.1 { result.append((item.0, data)) }
            }
            return result
        }

        for (id, data) in payloads {
            if let image = UIImage(data: data) { images[id] = image }
        }
        ready = images.count == V31SlideKind.allCases.count
    }

    @MainActor
    private func loadImage(for kind: V31SlideKind) async {
        guard images[kind.rawValue] == nil, let url = kind.imageURL else { return }
        var request = URLRequest(url: url)
        request.cachePolicy = .returnCacheDataElseLoad
        request.timeoutInterval = 8
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse,
              (200...299).contains(http.statusCode),
              let image = UIImage(data: data) else { return }
        images[kind.rawValue] = image
    }
}

// MARK: - Published school advertisements

struct V31SchoolAdsView: View {
    @State private var ads: [JSON] = []
    @State private var loading = true
    @State private var error = ""

    private var tickerText: String {
        ads.map { "\(jString($0["school"])) — \(jString($0["text"]))" }
            .joined(separator: "     •     ")
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                if loading {
                    V3SkeletonCard(height: 130)
                } else if ads.isEmpty {
                    EmptyStateView(systemImage: "megaphone", title: "لا توجد إعلانات مدرسية الآن", message: "ستظهر الإعلانات هنا تلقائياً بعد نشرها من الإدارة.")
                } else {
                    V31SchoolAdTicker(text: tickerText)
                    ForEach(ads.indices, id: \.self) { index in
                        let ad = ads[index]
                        MurshidCard {
                            VStack(alignment: .leading, spacing: 10) {
                                Label(jString(ad["school"]), systemImage: "building.2.fill")
                                    .font(.headline).foregroundStyle(Color.murshidBlue)
                                Text(jString(ad["text"])).font(.body)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
                if !error.isEmpty {
                    V3InlineMessage(text: error, icon: "exclamationmark.triangle.fill", tone: .warning)
                }
            }
            .padding(16)
            .padding(.bottom, 30)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("الإعلانات")
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadAds() }
        .refreshable { await loadAds() }
    }

    private func loadAds() async {
        do {
            let response = try await APIClient.shared.request("mobile/bootstrap.php")
            await MainActor.run {
                ads = jArray(response["ads"]).filter {
                    !jString($0["school"]).isEmpty && !jString($0["text"]).isEmpty
                        && jBool($0["active"], default: true)
                }
                loading = false
                error = ""
            }
        } catch let fetchError {
            await MainActor.run {
                loading = false
                error = "تعذر تحديث الإعلانات: \(fetchError.localizedDescription)"
            }
        }
    }
}

private struct V31SchoolAdTickerWidth: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

private struct V31SchoolAdTicker: View {
    let text: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var measuredWidth: CGFloat = 0

    private var repeatedText: some View {
        Text(text)
            .font(.subheadline.bold())
            .fixedSize(horizontal: true, vertical: false)
            .environment(\.layoutDirection, .rightToLeft)
    }

    var body: some View {
        Group {
            if reduceMotion {
                repeatedText.frame(maxWidth: .infinity, alignment: .trailing)
            } else {
                TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { context in
                    GeometryReader { viewport in
                        let textWidth = max(measuredWidth, 1)
                        let distance = max(viewport.size.width, 1) + textWidth + 24
                        let travel = CGFloat((context.date.timeIntervalSinceReferenceDate * 28)
                            .truncatingRemainder(dividingBy: Double(distance)))
                        // Physical left → right motion, independent of the
                        // Arabic text's right-to-left reading direction.
                        repeatedText.background {
                            GeometryReader { geometry in
                                Color.clear.preference(key: V31SchoolAdTickerWidth.self, value: geometry.size.width)
                            }
                        }
                        .position(x: travel - textWidth / 2, y: viewport.size.height / 2)
                    }
                    .environment(\.layoutDirection, .leftToRight)
                }
                .onPreferenceChange(V31SchoolAdTickerWidth.self) { measuredWidth = $0 }
            }
        }
        .frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48)
        .background(Color.murshidBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 14))
        .clipped()
        .accessibilityLabel(text)
    }
}

// Grade-scoped study catalogue: subject -> topic -> direct YouTube videos.
// Keep this separate from both exams and predictions.
struct V31StudyView: View {
    @EnvironmentObject private var app: AppSession
    @State private var subjects: [Subject] = []
    @State private var loading = true
    @State private var error = ""

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 13) {
                V3SectionHeader(title: "المواد الدراسية", subtitle: app.user?.grade ?? "مواد صفّك", icon: "books.vertical.fill")
                if loading {
                    ForEach(0..<3, id: \.self) { _ in V3SkeletonCard(height: 90) }
                } else if subjects.isEmpty {
                    EmptyStateView(systemImage: "books.vertical", title: "لا توجد مواد لهذا الصف", message: "ستظهر مواد صفّك عند نشرها من الإدارة.")
                } else {
                    ForEach(subjects) { subject in
                        NavigationLink(destination: V31StudySubjectView(subject: subject)) {
                            MurshidCard {
                                HStack(spacing: 12) {
                                    Image(systemName: "book.closed.fill")
                                        .font(.title2).foregroundStyle(Color.murshidBlue)
                                        .frame(width: 44, height: 44)
                                        .background(Color.murshidBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
                                    Text(subject.name).font(.headline).foregroundStyle(.primary)
                                    Spacer(minLength: 4)
                                    Image(systemName: "chevron.left").font(.caption.bold()).foregroundStyle(.secondary)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                if !error.isEmpty {
                    V3InlineMessage(text: error, icon: "wifi.exclamationmark", tone: .warning)
                }
            }
            .padding(16)
            .padding(.bottom, 20)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("ادرس")
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadSubjects() }
        .refreshable { await loadSubjects() }
    }

    @MainActor
    private func loadSubjects() async {
        let ownGradeSubjects = app.subjects.filter { $0.id > 0 && !$0.name.isEmpty }
        do {
            let data = try await APIClient.shared.request("mobile/study-catalog.php")
            subjects = jArray(data["subjects"]).map(Subject.init)
                .filter { $0.id > 0 && !$0.name.isEmpty }
            error = ""
        } catch let failure as APIError where failure.status == 404 {
            // The existing grade-filtered bootstrap already provides a safe
            // fallback while the optional study endpoint is being installed.
            subjects = ownGradeSubjects
            error = ""
        } catch {
            subjects = ownGradeSubjects
            self.error = ownGradeSubjects.isEmpty ? "تعذّر جلب مواد صفّك. حاول تحديث الصفحة." : ""
        }
        loading = false
    }
}

private struct V31StudyVideo: Identifiable {
    let title: String
    let url: URL
    var id: String { url.absoluteString }

    init?(title: String, rawURL: String) {
        guard let url = URL(string: rawURL), url.scheme?.lowercased() == "https",
              let host = url.host?.lowercased(),
              ["youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtube-nocookie.com"].contains(host) else { return nil }
        self.title = title.isEmpty ? "مشاهدة فيديو الموضوع على يوتيوب" : title
        self.url = url
    }
}

private struct V31StudyChapter: Identifiable {
    let id: Int
    let name: String
    let questionCount: Int
    let videos: [V31StudyVideo]

    init(_ json: JSON) {
        id = jInt(json["id"])
        name = jString(json["name"])
        questionCount = jInt(json["count"])
        var result = jArray(json["videos"]).compactMap { row in
            V31StudyVideo(title: jString(row["title"]), rawURL: jString(row["url"]))
        }
        if result.isEmpty {
            let singleURL = jString(json["video_url"], default: jString(json["youtube_url"]))
            if let video = V31StudyVideo(title: "مشاهدة فيديو الموضوع على يوتيوب", rawURL: singleURL) { result = [video] }
        }
        videos = result
    }
}

struct V31StudySubjectView: View {
    @EnvironmentObject private var app: AppSession
    let subject: Subject
    var focusedTopicID: Int = 0
    @State private var chapters: [V31StudyChapter] = []
    @State private var loading = true
    @State private var error = ""

    var body: some View {
        ScrollViewReader { reader in
            ScrollView {
                LazyVStack(spacing: 13) {
                V3SectionHeader(title: "موضوعات \(subject.name)", subtitle: app.user?.grade ?? "صفّك", icon: "book.pages.fill")
                if loading {
                    ForEach(0..<3, id: \.self) { _ in V3SkeletonCard(height: 100) }
                } else if !error.isEmpty {
                    ErrorStateView(message: error, retry: { Task { await loadChapters() } })
                } else if chapters.isEmpty {
                    EmptyStateView(systemImage: "book.closed", title: "لا توجد موضوعات بعد", message: "ستظهر موضوعات \(subject.name) عند إضافتها من الإدارة.")
                } else {
                    ForEach(chapters) { chapter in
                        MurshidCard {
                            VStack(alignment: .leading, spacing: 10) {
                                HStack(spacing: 11) {
                                    Image(systemName: "text.book.closed.fill")
                                        .foregroundStyle(Color.murshidBlue).frame(width: 28)
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(chapter.name).font(.headline)
                                    }
                                    Spacer()
                                }
                                if chapter.videos.isEmpty {
                                    Label("سيُضاف فيديو شرح هذا الموضوع من الإدارة", systemImage: "video.badge.plus")
                                        .font(.caption).foregroundStyle(.secondary)
                                } else {
                                    ForEach(chapter.videos) { video in
                                        Link(destination: video.url) {
                                            Label(video.title, systemImage: "play.rectangle.fill")
                                                .font(.subheadline.bold())
                                                .foregroundStyle(Color.murshidBlue)
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                                .padding(11)
                                                .background(Color.murshidBlue.opacity(0.08), in: RoundedRectangle(cornerRadius: 11))
                                        }
                                    }
                                }
                            }
                        }
                        .overlay(RoundedRectangle(cornerRadius: 19).stroke(chapter.id == focusedTopicID ? Color.murshidGold : Color.clear, lineWidth: 2))
                        .id(chapter.id)
                    }
                }
                }
                .padding(16)
                .padding(.bottom, 16)
            }
            .onChange(of: chapters.count) { _ in
                guard focusedTopicID > 0, chapters.contains(where: { $0.id == focusedTopicID }) else { return }
                withAnimation(.easeInOut(duration: 0.3)) { reader.scrollTo(focusedTopicID, anchor: .top) }
            }
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle(subject.name)
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadChapters() }
        .refreshable { await loadChapters() }
    }

    private func loadChapters() async {
        let query = [URLQueryItem(name: "subject_id", value: "\(subject.id)")]
        do {
            let data: JSON
            do {
                data = try await APIClient.shared.request(
                    "mobile/study-catalog.php", query: query,
                    cacheKey: "study-chapters-\(app.user?.id ?? 0)-\(subject.id)"
                )
            } catch let failure as APIError where failure.status == 404 {
                // Grade-bound existing API, until the new study endpoint is deployed.
                data = try await APIClient.shared.request(
                    "mobile/topics.php", query: query,
                    cacheKey: "topics-\(app.user?.id ?? 0)-\(subject.id)"
                )
            }
            await MainActor.run {
                chapters = jArray(data["topics"]).map(V31StudyChapter.init).filter { $0.id > 0 && !$0.name.isEmpty }
                loading = false
                error = ""
            }
        } catch {
            await MainActor.run { loading = false; self.error = error.localizedDescription }
        }
    }
}

struct V31PredictionsLockedView: View {
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 18) {
                V3IntroCard(eyebrow: "المرشحات", title: "المرشحات الوزارية", text: "هذا القسم مستقل عن ادرس والإعلانات ومتاح للمشتركين.", icon: "scope")
                MurshidCard {
                    VStack(spacing: 12) {
                        Image(systemName: "lock.shield.fill").font(.largeTitle).foregroundStyle(Color.murshidBlue)
                        Text("المرشحات متاحة بعد الاشتراك").font(.headline)
                        NavigationLink(destination: V31SubscriptionView()) {
                            Label("عرض الاشتراكات", systemImage: "arrow.left.circle.fill")
                        }
                        .buttonStyle(PrimaryButtonStyle())
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(16)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("المرشحات")
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
                membershipHero
                if app.subscribed {
                    MurshidCard { Label("لا تحتاج إلى أي عملية دفع الآن.", systemImage: "checkmark.circle.fill").font(.headline).foregroundStyle(.green) }
                } else {
                    premiumBenefits
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

    private var membershipHero: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label("المرشد الوزاري", systemImage: "crown.fill")
                    .font(.subheadline.bold()).foregroundStyle(Color.murshidGold)
                Spacer()
                Image(systemName: app.subscribed ? "checkmark.seal.fill" : "sparkles")
                    .font(.title).foregroundStyle(Color.murshidGold)
            }
            Text(app.subscribed ? "دراستك مستمرة بلا حدّ الأسئلة المجانية" : "لا تحفظ الإجابة فقط… افهم أين تتطور")
                .font(.system(.title2, design: .rounded, weight: .bold))
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
            Text(app.subscribed
                 ? "وصولك إلى بنك أسئلة صفك فعّال وفق مدة اشتراكك."
                 : "تدرّب على أسئلة صفك، راجع أخطاءك، واستخدم المحاكاة والمؤشرات المتاحة لتعرف الخطوة التالية.")
                .font(.subheadline).foregroundStyle(.white.opacity(0.87))
                .fixedSize(horizontal: false, vertical: true)
            if !app.subscribed {
                HStack(spacing: 10) {
                    Image(systemName: "gift.fill")
                    Text("تجربة مجانية: \(app.freeLimit) سؤال · المتبقي \(max(0, app.freeRemaining ?? max(0, app.freeLimit - app.freeUsed)))")
                }
                .font(.caption.bold())
                .foregroundStyle(Color.murshidNavy)
                .padding(.horizontal, 12).padding(.vertical, 9)
                .background(Color.murshidGold, in: Capsule())
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(21)
        .background(LinearGradient(colors: [.murshidNavy, .murshidBlue], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 23))
        .overlay(RoundedRectangle(cornerRadius: 23).stroke(Color.murshidGold.opacity(0.45), lineWidth: 1))
    }

    private var premiumBenefits: some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 15) {
                Text("ماذا تكسب عند الاشتراك؟")
                    .font(.title3.bold())
                benefit("أسئلة صفّك", detail: "تابع الحل بعد انتهاء الرصيد المجاني مع إظهار الإجابة الصحيحة.", icon: "checkmark.seal.fill")
                benefit("دفتر الأخطاء", detail: "ارجع للأسئلة التي أخطأت بها والمواضيع الأضعف.", icon: "book.closed.fill")
                if app.featureEnabled("custom_exam") {
                    benefit("اختبار ومحاكاة", detail: "حدد أسئلة للتدريب أو جرّب محاكاة مؤقتة والتصحيح بعد النهاية.", icon: "timer")
                }
                benefit("ادرس حسب صفّك", detail: "افتح المادة ثم الموضوع وفيديو يوتيوب إن نشرته الإدارة.", icon: "play.rectangle.fill")
                Label("الشرح التفصيلي والمصادر يظهران فقط حيث تتوفر بيانات صحيحة لهما.", systemImage: "info.circle")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private func benefit(_ name: String, detail: String, icon: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Color.murshidBlue)
                .frame(width: 35, height: 35)
                .background(Color.murshidBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 3) {
                Text(name).font(.headline)
                Text(detail).font(.caption).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
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
                            if p.id == "month3", let monthly = app.plans.first(where: { $0.id == "month1" }), monthly.amount * 3 > p.amount {
                                Text("توفّر \((monthly.amount * 3 - p.amount).formatted()) د.ع مقارنة بثلاث باقات شهرية")
                                    .font(.caption.bold()).foregroundStyle(.green)
                            } else {
                                Text("وصول إلى المحتوى حسب مدة الباقة").font(.caption).foregroundStyle(.secondary)
                            }
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
    @State private var showLogoutConfirm = false
    @State private var avatarItem: PhotosPickerItem?
    @State private var avatarPreview: UIImage?
    @State private var uploadingAvatar = false
    @State private var faceIDMessage = ""
    @State private var avatarMessage = ""

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                profileCard
                if loading { V3SkeletonCard(height: 90) }
                subscriptionCard
                servicesSection
                securitySection
                logoutSection
                if !avatarMessage.isEmpty { V3InlineMessage(text: avatarMessage, icon: "person.crop.circle.badge.checkmark", tone: .success) }
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
        .confirmationDialog("تسجيل الخروج؟", isPresented: $showLogoutConfirm, titleVisibility: .visible) {
            Button("تسجيل الخروج", role: .destructive) { logout() }
            Button("إلغاء", role: .cancel) {}
        } message: {
            Text("سيتم إنهاء جلسة حسابك على هذا الجهاز.")
        }
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

    // Show the user's latest uploaded picture immediately and preserve a local fallback.
    @ViewBuilder private var avatarView: some View {
        let raw = jString(account["avatar_url"])
        let revision = jString(account["avatar_revision"], default: "0")
        let url = V43AvatarCache.url(raw, revision: revision)
        let fallback = jString(account["avatar_status"]) == "approved"
            ? V43AvatarCache.image(userID: app.user?.id ?? 0) : nil
        ZStack {
            if let preview = avatarPreview {
                Image(uiImage: preview).resizable().scaledToFill()
            } else if let url {
                AsyncImage(url: url) { phase in
                    if case let .success(image) = phase {
                        image.resizable().scaledToFill()
                    } else if let fallback {
                        Image(uiImage: fallback).resizable().scaledToFill()
                    } else {
                        Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(.white.opacity(0.9))
                    }
                }
                .id(url.absoluteString)
            } else if let fallback {
                Image(uiImage: fallback).resizable().scaledToFill()
            } else {
                Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(.white.opacity(0.9))
            }
        }
        .frame(width: 78, height: 78)
        .clipShape(Circle())
        .overlay(Circle().stroke(.white.opacity(0.34), lineWidth: 2))
    }

    private var avatarStatusText: String {
        switch jString(account["avatar_status"]) {
        case "approved": return "الصورة مفعلة"
        case "blocked", "rejected": return "الصورة غير مقبولة"
        default: return "اضغط لتغيير الصورة"
        }
    }

    private var subscriptionCard: some View {
        NavigationLink(destination: V31SubscriptionView()) {
            MurshidCard {
                HStack(spacing: 13) {
                    Image(systemName: app.subscribed ? "checkmark.seal.fill" : "arrow.up.forward.app.fill").font(.title2).foregroundStyle(app.subscribed ? .green : Color.murshidBlue)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(app.subscribed ? "الاشتراك" : "اشتراك").font(.headline.bold()).foregroundStyle(app.subscribed ? Color.primary : Color.murshidBlue)
                        Text(app.subscribed ? "الأيام المتبقية: \(app.subscriptionDaysRemaining.map(String.init) ?? "—") يوم" : "الدفع خارجي ولا نطلب رقمًا إضافيًا من هنا.").font(.caption).foregroundStyle(.secondary)
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
                    NavigationLink(destination: NotificationsView()) { V31AccountRowLabel(title: "الإشعارات", subtitle: app.unreadNotifications > 0 ? "لديك \(app.unreadNotifications) غير مقروء" : "لا توجد إشعارات جديدة", icon: "bell.fill") }
                    Divider().padding(.leading, 48)
                    NavigationLink(destination: SupportView()) { V31AccountRowLabel(title: "خدمة العملاء", subtitle: "محادثة مباشرة مع الدعم", icon: "message.fill") }
                    Divider().padding(.leading, 48)

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

    private var logoutSection: some View {
        MurshidCard {
            Button(role: .destructive) { showLogoutConfirm = true } label: {
                HStack { Label("تسجيل الخروج", systemImage: "rectangle.portrait.and.arrow.right"); Spacer(); if loggingOut { ProgressView() } }
                    .frame(maxWidth: .infinity, minHeight: 46, alignment: .leading)
            }.disabled(loggingOut)
        }
    }

    private var appInfo: some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack { Label("منصة المرشد الوزاري", systemImage: "graduationcap.fill").font(.headline); Spacer(); Text("\(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—") • \(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—")").font(.caption.monospacedDigit()).foregroundStyle(.secondary) }
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
        do {
            let d = try await APIClient.shared.request("mobile/account.php")
            await MainActor.run {
                account = d
                loading = false
                error = ""
                // Keep the successful image visible until the remote avatar finishes loading.
                if ["blocked", "rejected", "none"].contains(jString(d["avatar_status"])) {
                    avatarPreview = nil
                }
            }
        } catch {
            await MainActor.run { loading = false; self.error = error.localizedDescription }
        }
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
            guard let data = try await item.loadTransferable(type: Data.self),
                  let image = UIImage(data: data),
                  let jpeg = image.jpegData(compressionQuality: 0.88) else {
                throw APIError(message: "تعذر قراءة الصورة المختارة.", status: 422, paymentRequired: false)
            }
            await MainActor.run {
                avatarPreview = image
                uploadingAvatar = true
                error = ""
                avatarMessage = ""
            }
            let d = try await APIClient.shared.uploadAvatarJPEG(jpeg, csrf: app.csrf)
            // Server must return a usable avatar URL; otherwise the upload is not complete.
            let urlText = jString(d["avatar_url"])
            guard V43AvatarCache.url(urlText, revision: jString(d["avatar_revision"])) != nil else {
                throw APIError(message: "حُفظت الصورة لكن الخادم لم يُرجع رابطاً صالحاً للعرض.", status: 502, paymentRequired: false)
            }
            await MainActor.run {
                V43AvatarCache.save(jpeg, userID: app.user?.id ?? 0)
                account["avatar_url"] = urlText
                account["avatar_revision"] = jString(d["avatar_revision"])
                account["avatar_status"] = "approved"
                uploadingAvatar = false
                avatarItem = nil
                avatarMessage = "تم تغيير صورة الحساب. الصورة ظاهرة وجاري تحديث رابطها."
                NotificationCenter.default.post(name: Notification.Name("MurshidAvatarChanged"), object: nil)
                haptic(.success)
            }
            await load()
        } catch {
            await MainActor.run {
                uploadingAvatar = false
                avatarPreview = nil
                avatarItem = nil
                self.error = error.localizedDescription
                haptic(.error)
            }
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
        let (data, response) = try await session.data(for: request)
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
