import SwiftUI
import Combine

// MARK: - V3 Main Navigation

struct MainV3TabView: View {
    @EnvironmentObject var app: AppSession
    @State private var selection = 0

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack { V3HomeView() }
                .tabItem { Label("الرئيسية", systemImage: "house.fill") }
                .tag(0)

            NavigationStack { V3TestsView() }
                .tabItem { Label("الاختبارات", systemImage: "checklist.checked") }
                .tag(1)

            NavigationStack { V3SuccessView() }
                .tabItem { Label("النجاح", systemImage: "chart.line.uptrend.xyaxis") }
                .tag(2)

            NavigationStack { V3AccountView() }
                .tabItem { Label("حسابي", systemImage: "person.crop.circle.fill") }
                .tag(3)
        }
        .tint(.murshidBlue)
        .environment(\.layoutDirection, .rightToLeft)
        .onOpenURL { url in
            switch (url.host ?? url.path.replacingOccurrences(of: "/", with: "")).lowercased() {
            case "tests", "exam", "subjects": selection = 1
            case "success", "progress": selection = 2
            case "account", "subscription": selection = 3
            default: selection = 0
            }
            selectionHaptic()
        }
    }
}

// MARK: - Home

struct V3HomeView: View {
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
                hero
                if !device.isOnline { offlineBanner }
                subscriptionStrip
                performanceStrip
                continueCard
                quickTools
                subjectsSection
                if !error.isEmpty { V3InlineMessage(text: error, icon: "wifi.exclamationmark", tone: .warning) }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 28)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("المرشد الوزاري")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                NavigationLink(destination: NotificationsView()) {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "bell.fill")
                            .font(.headline)
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
        .refreshable { await refreshAll() }
        .task { await refreshAll() }
    }

    private var hero: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(
                colors: [.murshidNavy, Color(red: 0.05, green: 0.23, blue: 0.52), .murshidBlue],
                startPoint: .topTrailing,
                endPoint: .bottomLeading
            )
            .overlay(alignment: .topTrailing) {
                Circle().fill(.white.opacity(0.07)).frame(width: 200, height: 200).offset(x: 60, y: -90)
            }

            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    Image(systemName: "graduationcap.fill")
                        .font(.title3)
                        .foregroundStyle(.white)
                        .frame(width: 42, height: 42)
                        .background(.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("أهلًا، \(firstName)")
                            .font(.title2.bold())
                            .foregroundStyle(.white)
                        Text(app.user?.grade ?? "")
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.78))
                    }
                    Spacer()
                    if app.subscribed {
                        Label("مشترك", systemImage: "checkmark.seal.fill")
                            .font(.caption.bold())
                            .foregroundStyle(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 7)
                            .background(.white.opacity(0.13), in: Capsule())
                    }
                }

                Text(motivation)
                    .font(.headline)
                    .foregroundStyle(.white.opacity(0.94))
                    .lineLimit(2)

                HStack(spacing: 9) {
                    V3HeroMetric(title: "الدقة", value: "\(app.accuracy)%")
                    V3HeroMetric(title: "XP", value: "\(jInt(app.stats["xp"]))")
                    V3HeroMetric(title: "السلسلة", value: "\(jInt(app.stats["streak"])) يوم")
                }
            }
            .padding(20)
        }
        .frame(minHeight: 205)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .shadow(color: Color.murshidNavy.opacity(0.15), radius: 20, y: 10)
        .padding(.top, 6)
    }

    private var offlineBanner: some View {
        V3InlineMessage(
            text: "أنت غير متصل الآن. سنعرض البيانات المحفوظة متى كانت متاحة.",
            icon: "wifi.slash",
            tone: .warning
        )
    }

    @ViewBuilder
    private var subscriptionStrip: some View {
        if app.subscribed {
            MurshidCard {
                HStack(spacing: 12) {
                    ZStack {
                        Circle().fill(Color.green.opacity(0.12)).frame(width: 45, height: 45)
                        Image(systemName: "checkmark.seal.fill").foregroundStyle(.green)
                    }
                    VStack(alignment: .leading, spacing: 3) {
                        Text("كل الأسئلة مفتوحة").font(.headline)
                        Text("اشتراكك فعّال — واصل التقدم بدون حدود الرصيد المجاني.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                }
            }
        } else {
            NavigationLink(destination: V3SubscriptionView()) {
                MurshidCard {
                    HStack(spacing: 13) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(Color.murshidGold.opacity(0.14))
                                .frame(width: 48, height: 48)
                            Image(systemName: "gift.fill").foregroundStyle(Color.murshidGold)
                        }
                        VStack(alignment: .leading, spacing: 5) {
                            Text("رصيد التجربة المجانية").font(.headline).foregroundStyle(.primary)
                            Text("متبقي \(app.freeRemaining ?? 0) من \(app.freeLimit) سؤال")
                                .font(.subheadline).foregroundStyle(.secondary)
                            ProgressView(value: Double(app.freeUsed), total: Double(max(1, app.freeLimit)))
                                .tint(.murshidGold)
                        }
                        Spacer()
                        Image(systemName: "chevron.left").foregroundStyle(.secondary)
                    }
                }
            }
            .buttonStyle(.plain)
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

    @ViewBuilder
    private var continueCard: some View {
        if loadingInsights {
            V3SkeletonCard(height: 130)
        } else if let topic = suggestedTopic {
            NavigationLink(destination: V3ExamView(topic: topic.topic, subject: topic.subject)) {
                MurshidCard {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Label("خطوتك التالية", systemImage: "play.circle.fill")
                                .font(.headline)
                                .foregroundStyle(Color.murshidBlue)
                            Spacer()
                            Text("مقترح لك").font(.caption.bold()).foregroundStyle(.secondary)
                        }
                        Text(topic.topic.name).font(.title3.bold()).foregroundStyle(.primary)
                        Text(topic.subject.name).font(.subheadline).foregroundStyle(.secondary)
                        HStack {
                            ProgressView(value: Double(topic.coverage), total: 100).tint(.murshidBlue)
                            Text("\(topic.coverage)%").font(.caption.bold().monospacedDigit()).foregroundStyle(.secondary)
                        }
                        Label("ابدأ الآن", systemImage: "arrow.left.circle.fill")
                            .font(.subheadline.bold())
                            .foregroundStyle(Color.murshidBlue)
                    }
                }
            }
            .buttonStyle(.plain)
        } else if !app.subjects.isEmpty {
            NavigationLink(destination: V3TopicsView(subject: app.subjects[0])) {
                MurshidCard {
                    HStack(spacing: 13) {
                        Image(systemName: "play.circle.fill").font(.largeTitle).foregroundStyle(Color.murshidBlue)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("ابدأ أول مراجعة").font(.headline).foregroundStyle(.primary)
                            Text("اختر موضوعك وسنحفظ تقدمك تلقائيًا.").font(.subheadline).foregroundStyle(.secondary)
                        }
                        Spacer(); Image(systemName: "chevron.left").foregroundStyle(.secondary)
                    }
                }
            }.buttonStyle(.plain)
        }
    }

    private var quickTools: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "أدواتك السريعة", subtitle: "كل ما تحتاجه خلال ثوانٍ", icon: "bolt.fill")
            LazyVGrid(columns: columns, spacing: 12) {
                NavigationLink(destination: ReviewView()) {
                    V3ToolCard(title: "راجع أخطاءك", subtitle: "مراجعة ذكية", icon: "brain.head.profile")
                }
                NavigationLink(destination: V3FocusView()) {
                    V3ToolCard(title: "جلسة تركيز", subtitle: "25 · 45 · 60 دقيقة", icon: "timer")
                }
                NavigationLink(destination: V3ContestView()) {
                    V3ToolCard(title: "تحدي المليون", subtitle: "ترتيب ونقاط", icon: "trophy.fill")
                }
                NavigationLink(destination: StoriesView()) {
                    V3ToolCard(title: "غيّر جو", subtitle: "استراحة قصيرة", icon: "sparkles")
                }
            }
            .buttonStyle(.plain)
        }
    }

    private var subjectsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "موادك", subtitle: "اختر المادة ثم الموضوع", icon: "books.vertical.fill")
            ForEach(app.subjects) { subject in
                NavigationLink(destination: V3TopicsView(subject: subject)) {
                    V3SubjectRow(subject: subject)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var firstName: String {
        guard let value = app.user?.name.split(separator: " ").first else { return "طالبنا" }
        return String(value)
    }

    private var motivation: String {
        let total = jInt(app.stats["total_answers"])
        if app.accuracy >= 85 && total >= 10 { return "مستواك ممتاز. حافظ على إيقاعك وراجع الأخطاء القليلة." }
        if total > 0 { return "كل إجابة اليوم تقرّبك أكثر من الدرجة التي تريدها." }
        return "ابدأ بخطوة صغيرة اليوم، وسنبني تقدمك معك سؤالًا بعد سؤال."
    }

    private var suggestedTopic: (topic: Topic, subject: Subject, coverage: Int)? {
        let rows = jArray(insights["daily_topics"])
        guard let row = rows.first else { return nil }
        let sid = jInt(row["subject_id"])
        let sname = jString(row["subject_name"])
        let topic = Topic([
            "id": jInt(row["id"]),
            "name": jString(row["name"], default: jString(row["display_name"])),
            "count": jInt(row["questions_count"]),
            "theme": jString(row["theme"])
        ])
        let subject = app.subjects.first(where: { $0.id == sid }) ?? Subject(["id": sid, "name": sname])
        guard topic.id > 0, subject.id > 0 else { return nil }
        return (topic, subject, jInt(row["coverage"]))
    }

    private func refreshAll() async {
        await MainActor.run { error = "" }
        do {
            async let bootstrap = APIClient.shared.bootstrap()
            async let insightResponse = APIClient.shared.request("mobile/insights.php", cacheKey: "insights")
            async let notifications = APIClient.shared.request("mobile/notifications.php")
            let (b, i, n) = try await (bootstrap, insightResponse, notifications)
            await MainActor.run {
                app.applyBootstrap(b)
                insights = i["insights"] as? JSON ?? [:]
                weekly = i["weekly"] as? JSON ?? [:]
                app.unreadNotifications = jInt(n["unread"])
                loadingInsights = false
            }
        } catch {
            do {
                let i = try await APIClient.shared.request("mobile/insights.php", cacheKey: "insights")
                await MainActor.run {
                    insights = i["insights"] as? JSON ?? [:]
                    weekly = i["weekly"] as? JSON ?? [:]
                    loadingInsights = false
                }
            } catch {
                await MainActor.run { loadingInsights = false; self.error = error.localizedDescription }
            }
        }
    }
}

// MARK: - Tests / Subjects

struct V3TestsView: View {
    @EnvironmentObject var app: AppSession
    @State private var search = ""

    private var filtered: [Subject] {
        let q = search.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return app.subjects }
        return app.subjects.filter { $0.name.localizedCaseInsensitiveContains(q) }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                V3IntroCard(
                    eyebrow: "اختباراتك",
                    title: "اختر المادة وابدأ بثقة",
                    text: "المواضيع مرتبة حسب المنهج، وتقدمك محفوظ داخل حسابك.",
                    icon: "checklist.checked"
                )

                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("ابحث عن مادة", text: $search)
                        .textInputAutocapitalization(.never)
                }
                .padding(13)
                .background(Color.murshidSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                if filtered.isEmpty {
                    EmptyStateView(systemImage: "magnifyingglass", title: "لا توجد نتيجة", message: "جرّب كتابة اسم المادة بطريقة مختلفة.")
                        .frame(minHeight: 280)
                } else {
                    ForEach(Array(filtered.enumerated()), id: \.element.id) { index, subject in
                        NavigationLink(destination: V3TopicsView(subject: subject)) {
                            V3SubjectRow(subject: subject, number: index + 1)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
            .padding(.bottom, 22)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("الاختبارات")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct V3TopicsView: View {
    let subject: Subject
    @State private var topics: [Topic] = []
    @State private var metrics: [Int: JSON] = [:]
    @State private var loading = true
    @State private var error = ""

    var body: some View {
        Group {
            if loading {
                ScrollView { LazyVStack(spacing: 13) { ForEach(0..<5, id: \.self) { _ in V3SkeletonCard(height: 105) } }.padding(16) }
                    .background(Color.murshidBackground)
            } else if !error.isEmpty {
                ErrorStateView(message: error, retry: { Task { await load() } })
            } else if topics.isEmpty {
                EmptyStateView(systemImage: "tray", title: "لا توجد مواضيع", message: "لا توجد مواضيع جاهزة للاختبار في هذه المادة حاليًا.")
            } else {
                ScrollView {
                    LazyVStack(spacing: 13) {
                        V3IntroCard(
                            eyebrow: subject.name,
                            title: "اختر الموضوع",
                            text: "شاهد تقدمك وعدد الأسئلة قبل البدء.",
                            icon: "book.pages.fill"
                        )
                        ForEach(Array(topics.enumerated()), id: \.element.id) { index, topic in
                            NavigationLink(destination: V3ExamView(topic: topic, subject: subject)) {
                                topicRow(topic, index: index)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(16)
                    .padding(.bottom, 24)
                }
                .background(Color.murshidBackground)
            }
        }
        .navigationTitle(subject.name)
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private func topicRow(_ topic: Topic, index: Int) -> some View {
        let m = metrics[topic.id] ?? [:]
        let coverage = jInt(m["coverage"])
        let accuracy = jInt(m["accuracy"])
        let completed = jBool(m["completed"])
        return MurshidCard {
            VStack(alignment: .leading, spacing: 11) {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 13, style: .continuous)
                            .fill(completed ? Color.green.opacity(0.12) : Color.murshidBlue.opacity(0.10))
                            .frame(width: 47, height: 47)
                        Image(systemName: completed ? "checkmark.seal.fill" : "doc.text.fill")
                            .foregroundStyle(completed ? .green : Color.murshidBlue)
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text(topic.name).font(.headline).foregroundStyle(.primary)
                        Text("\(topic.count) سؤال")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if completed {
                        Text("مكتمل").font(.caption.bold()).foregroundStyle(.green)
                    } else if coverage > 0 {
                        Text("قيد التقدم").font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                    } else {
                        Text("لم يبدأ").font(.caption.bold()).foregroundStyle(.secondary)
                    }
                    Image(systemName: "chevron.left").font(.caption.bold()).foregroundStyle(.tertiary)
                }
                if coverage > 0 || accuracy > 0 {
                    HStack(spacing: 10) {
                        ProgressView(value: Double(coverage), total: 100).tint(completed ? .green : .murshidBlue)
                        Text("\(coverage)%").font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        if jInt(m["attempts"]) > 0 {
                            Text("دقة \(accuracy)%").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    private func load() async {
        await MainActor.run { loading = true; error = "" }
        do {
            async let topicsRequest = APIClient.shared.request(
                "mobile/topics.php",
                query: [URLQueryItem(name: "subject_id", value: "\(subject.id)")],
                cacheKey: "topics-\(subject.id)"
            )
            async let insightRequest = APIClient.shared.request("mobile/insights.php", cacheKey: "insights")
            let (t, i) = try await (topicsRequest, insightRequest)
            let loaded = jArray(t["topics"]).map(Topic.init)
            let ins = i["insights"] as? JSON ?? [:]
            var map: [Int: JSON] = [:]
            for row in jArray(ins["chapters"]) where jInt(row["subject_id"]) == subject.id {
                map[jInt(row["id"])] = row
            }
            await MainActor.run { topics = loaded; metrics = map; loading = false }
        } catch {
            await MainActor.run { loading = false; self.error = error.localizedDescription }
        }
    }
}

// MARK: - Success Hub

struct V3SuccessView: View {
    @EnvironmentObject var app: AppSession
    @State private var insights: JSON = [:]
    @State private var weekly: JSON = [:]
    @State private var loading = true
    @State private var error = ""

    var body: some View {
        Group {
            if loading {
                ScrollView { LazyVStack(spacing: 13) { V3SkeletonCard(height: 220); V3SkeletonCard(height: 125); V3SkeletonCard(height: 180) }.padding(16) }
                    .background(Color.murshidBackground)
            } else if !error.isEmpty {
                ErrorStateView(message: error, retry: { Task { await load() } })
            } else {
                ScrollView {
                    LazyVStack(spacing: 16) {
                        successHero
                        weeklyCard
                        strengthCards
                        dailyPlan
                        improvementTools
                    }
                    .padding(16)
                    .padding(.bottom, 26)
                }
                .background(Color.murshidBackground)
            }
        }
        .navigationTitle("النجاح")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private var successHero: some View {
        MurshidCard {
            HStack(spacing: 20) {
                V3ProgressRing(value: jInt(insights["success_index"]), label: "مؤشر النجاح")
                VStack(alignment: .leading, spacing: 9) {
                    Text("صورتك الدراسية الآن").font(.title3.bold())
                    Label("الدقة \(jInt(insights["accuracy"]))%", systemImage: "scope")
                    Label("التغطية \(jInt(insights["coverage"]))%", systemImage: "books.vertical")
                    Label("\(jInt(insights["distinct_answered"])) سؤال مختلف", systemImage: "checkmark.circle")
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
                Spacer(minLength: 0)
            }
        }
    }

    private var weeklyCard: some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 12) {
                V3SectionHeader(title: "آخر 7 أيام", subtitle: "نشاطك الحديث", icon: "calendar.badge.clock")
                HStack(spacing: 10) {
                    V3MiniMetric(value: "\(jInt(weekly["answers"]))", label: "إجابة")
                    V3MiniMetric(value: "\(jInt(weekly["accuracy"]))%", label: "دقة")
                    V3MiniMetric(value: signedChange, label: "عن الأسبوع السابق")
                }
            }
        }
    }

    private var strengthCards: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "نقاط القوة والتحسين", subtitle: "اعرف أين تركز", icon: "target")
            if let strong = insights["strongest_subject"] as? JSON {
                V3InsightRow(title: "أقوى مادة", value: jString(strong["name"]), detail: "دقة \(jInt(strong["accuracy"]))%", icon: "arrow.up.right.circle.fill", positive: true)
            }
            if let weak = insights["weakest_subject"] as? JSON {
                V3InsightRow(title: "تحتاج متابعة", value: jString(weak["name"]), detail: "دقة \(jInt(weak["accuracy"]))%", icon: "scope", positive: false)
            }
        }
    }

    @ViewBuilder
    private var dailyPlan: some View {
        let topics = jArray(insights["daily_topics"])
        if !topics.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                V3SectionHeader(title: "خطة اليوم", subtitle: "\(jInt(insights["daily_questions"], default: 20)) سؤال مقترح", icon: "list.bullet.clipboard.fill")
                ForEach(Array(topics.prefix(3).enumerated()), id: \.offset) { index, row in
                    let topic = Topic(["id": jInt(row["id"]), "name": jString(row["name"]), "count": jInt(row["questions_count"]), "theme": jString(row["theme"])])
                    let sid = jInt(row["subject_id"])
                    let subject = app.subjects.first(where: { $0.id == sid }) ?? Subject(["id": sid, "name": jString(row["subject_name"])])
                    NavigationLink(destination: V3ExamView(topic: topic, subject: subject)) {
                        MurshidCard {
                            HStack(spacing: 12) {
                                Text("\(index + 1)").font(.headline.monospacedDigit()).foregroundStyle(Color.murshidBlue).frame(width: 38, height: 38).background(Color.murshidBlue.opacity(0.10), in: Circle())
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(topic.name).font(.headline).foregroundStyle(.primary)
                                    Text(subject.name + " • إنجاز \(jInt(row["coverage"]))%").font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer(); Image(systemName: "play.circle.fill").foregroundStyle(Color.murshidBlue)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var improvementTools: some View {
        VStack(alignment: .leading, spacing: 12) {
            V3SectionHeader(title: "طوّر مستواك", subtitle: "أدوات مبنية على بياناتك", icon: "wand.and.stars")
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                NavigationLink(destination: ReviewView()) { V3ToolCard(title: "المراجعة الذكية", subtitle: "آخر الأخطاء", icon: "brain.head.profile") }
                NavigationLink(destination: StudyPlanView()) { V3ToolCard(title: "خطة الدراسة", subtitle: "هدف يومي", icon: "calendar") }
                NavigationLink(destination: AchievementsView()) { V3ToolCard(title: "الإنجازات", subtitle: "XP والمستوى", icon: "medal.fill") }
                NavigationLink(destination: V3ContestView()) { V3ToolCard(title: "تحدي المليون", subtitle: "المتصدرون", icon: "trophy.fill") }
            }
            .buttonStyle(.plain)
        }
    }

    private var signedChange: String {
        let change = jInt(weekly["change"])
        if change > 0 { return "+\(change)" }
        return "\(change)"
    }

    private func load() async {
        await MainActor.run { loading = true; error = "" }
        do {
            let d = try await APIClient.shared.request("mobile/insights.php", cacheKey: "insights")
            await MainActor.run {
                insights = d["insights"] as? JSON ?? [:]
                weekly = d["weekly"] as? JSON ?? [:]
                loading = false
            }
        } catch {
            await MainActor.run { loading = false; self.error = error.localizedDescription }
        }
    }
}

// MARK: - Focus session

struct V3FocusView: View {
    @EnvironmentObject var app: AppSession
    @State private var selectedMinutes = 25
    @State private var running = false
    @State private var endAt: Date?
    @State private var remainingSeconds = 25 * 60
    @State private var message = "اختر المدة وابدأ جلسة هادئة بلا مشتتات."
    @State private var busy = false
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                V3IntroCard(eyebrow: "جلسة تركيز", title: "وقت هادئ لدراستك", text: "ابدأ جلسة، اترك الهاتف على هذه الشاشة، وسنسجل دقائقك المكتملة.", icon: "timer")

                MurshidCard {
                    VStack(spacing: 20) {
                        ZStack {
                            Circle().stroke(Color.primary.opacity(0.06), lineWidth: 14).frame(width: 205, height: 205)
                            Circle()
                                .trim(from: 0, to: progress)
                                .stroke(Color.murshidBlue, style: StrokeStyle(lineWidth: 14, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                                .frame(width: 205, height: 205)
                                .animation(.linear(duration: 0.2), value: remainingSeconds)
                            VStack(spacing: 5) {
                                Text(clockText).font(.system(size: 42, weight: .bold, design: .rounded)).monospacedDigit()
                                Text(running ? "ركّز على هدف واحد" : "جاهز للبدء").font(.caption).foregroundStyle(.secondary)
                            }
                        }

                        Picker("المدة", selection: $selectedMinutes) {
                            Text("25 دقيقة").tag(25)
                            Text("45 دقيقة").tag(45)
                            Text("60 دقيقة").tag(60)
                        }
                        .pickerStyle(.segmented)
                        .disabled(running || busy)
                        .onChange(of: selectedMinutes) { value in if !running { remainingSeconds = value * 60 } }

                        if running {
                            Button("إنهاء الجلسة وحفظها") { Task { await finish() } }
                                .buttonStyle(PrimaryButtonStyle())
                                .disabled(busy)
                        } else {
                            Button("ابدأ جلسة التركيز") { Task { await start() } }
                                .buttonStyle(PrimaryButtonStyle())
                                .disabled(busy)
                        }

                        Text(message).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    }
                }
            }
            .padding(16)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("جلسة التركيز")
        .navigationBarTitleDisplayMode(.inline)
        .onReceive(timer) { now in
            guard running, let endAt else { return }
            remainingSeconds = max(0, Int(endAt.timeIntervalSince(now).rounded(.up)))
            if remainingSeconds == 0 { Task { await finish() } }
        }
    }

    private var progress: CGFloat {
        let total = max(1, selectedMinutes * 60)
        return CGFloat(total - remainingSeconds) / CGFloat(total)
    }

    private var clockText: String {
        String(format: "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
    }

    private func start() async {
        busy = true
        do {
            _ = try await APIClient.shared.request("mobile/study-session.php", method: "POST", body: ["csrf": app.csrf, "action": "start", "planned": selectedMinutes])
            await MainActor.run {
                remainingSeconds = selectedMinutes * 60
                endAt = Date().addingTimeInterval(TimeInterval(remainingSeconds))
                running = true
                busy = false
                message = "بدأت الجلسة. أغلق المشتتات وركز على هدف واحد فقط."
                haptic()
            }
        } catch {
            await MainActor.run { busy = false; message = error.localizedDescription; haptic(.error) }
        }
    }

    private func finish() async {
        guard running else { return }
        busy = true
        do {
            let d = try await APIClient.shared.request("mobile/study-session.php", method: "POST", body: ["csrf": app.csrf, "action": "finish"])
            let minutes = jInt(d["minutes"])
            let b = try? await APIClient.shared.bootstrap()
            await MainActor.run {
                if let b { app.applyBootstrap(b) }
                running = false
                endAt = nil
                remainingSeconds = selectedMinutes * 60
                busy = false
                message = "أحسنت! تم تسجيل \(minutes) دقيقة في نشاطك الدراسي."
                haptic()
            }
        } catch {
            await MainActor.run { busy = false; message = error.localizedDescription; haptic(.error) }
        }
    }
}

// MARK: - Contest & seasonal ranking

struct V3ContestView: View {
    @State private var contest: JSON = [:]
    @State private var season: JSON = [:]
    @State private var loading = true
    @State private var error = ""

    var body: some View {
        Group {
            if loading { LoadingView(text: "جاري تحميل الترتيب…") }
            else if !error.isEmpty { ErrorStateView(message: error, retry: { Task { await load() } }) }
            else {
                ScrollView {
                    LazyVStack(spacing: 16) {
                        V3IntroCard(eyebrow: "تحدي المليون", title: "تقدمك بين الطلاب", text: "الترتيب يعتمد على نقاط المنصة والإجابات الصحيحة ضمن فترة التحدي.", icon: "trophy.fill")
                        myRankCard
                        leaderboard(title: "متصدرو تحدي المليون", rows: jArray(contest["leaderboard"]), rankKey: "rank", xpKey: "xp")
                        leaderboard(title: "ترتيب الموسم", rows: jArray(season["leaderboard"]), rankKey: "rank", xpKey: "xp")
                    }
                    .padding(16)
                    .padding(.bottom, 24)
                }
                .background(Color.murshidBackground)
            }
        }
        .navigationTitle("التحدي")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private var myRankCard: some View {
        MurshidCard {
            HStack(spacing: 14) {
                Image(systemName: "person.crop.circle.badge.checkmark").font(.largeTitle).foregroundStyle(Color.murshidBlue)
                VStack(alignment: .leading, spacing: 5) {
                    Text("مركزك الحالي").font(.subheadline).foregroundStyle(.secondary)
                    if let mine = contest["mine"] as? JSON {
                        Text("#\(jInt(mine["rank"]))").font(.largeTitle.bold()).foregroundStyle(Color.murshidBlue)
                        Text("\(jInt(mine["xp"])) XP · \(jInt(mine["correct"])) إجابة صحيحة").font(.caption).foregroundStyle(.secondary)
                    } else {
                        Text("ابدأ الإجابة لتدخل الترتيب").font(.headline)
                    }
                }
                Spacer()
                if jBool((contest["config"] as? JSON)?["active"]) {
                    Text("نشط").font(.caption.bold()).foregroundStyle(.green).padding(.horizontal, 10).padding(.vertical, 6).background(Color.green.opacity(0.10), in: Capsule())
                }
            }
        }
    }

    private func leaderboard(title: String, rows: [JSON], rankKey: String, xpKey: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            V3SectionHeader(title: title, subtitle: rows.isEmpty ? "لا توجد بيانات بعد" : "أعلى المراكز", icon: "list.number")
            if rows.isEmpty {
                MurshidCard { Text("سيظهر الترتيب هنا عند توفر البيانات.").foregroundStyle(.secondary) }
            } else {
                ForEach(Array(rows.prefix(10).enumerated()), id: \.offset) { _, row in
                    MurshidCard {
                        HStack(spacing: 12) {
                            Text("#\(jInt(row[rankKey]))").font(.headline.monospacedDigit()).foregroundStyle(Color.murshidBlue).frame(width: 42)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(jString(row["name"])).font(.headline)
                                if !jString(row["grade"]).isEmpty { Text(jString(row["grade"])).font(.caption).foregroundStyle(.secondary) }
                            }
                            Spacer()
                            Text("\(jInt(row[xpKey])) XP").font(.subheadline.bold()).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    private func load() async {
        await MainActor.run { loading = true; error = "" }
        do {
            async let c = APIClient.shared.request("mobile/contest.php")
            async let s = APIClient.shared.request("mobile/season.php")
            let (contestData, seasonData) = try await (c, s)
            await MainActor.run { contest = contestData; season = seasonData; loading = false }
        } catch {
            await MainActor.run { loading = false; self.error = error.localizedDescription }
        }
    }
}

// MARK: - V3 Visual Components

private struct V3HeroMetric: View {
    let title: String
    let value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.subheadline.bold()).foregroundStyle(.white)
            Text(title).font(.caption2).foregroundStyle(.white.opacity(0.72))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(.white.opacity(0.09), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

struct V3MetricCard: View {
    let title: String
    let value: String
    let icon: String
    var body: some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 9) {
                Image(systemName: icon)
                    .font(.headline)
                    .foregroundStyle(Color.murshidBlue)
                    .frame(width: 36, height: 36)
                    .background(Color.murshidBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                Text(value).font(.title2.bold()).foregroundStyle(.primary)
                Text(title).font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct V3ToolCard: View {
    let title: String
    let subtitle: String
    let icon: String
    var body: some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: icon).font(.title2).foregroundStyle(Color.murshidBlue)
                Text(title).font(.headline).foregroundStyle(.primary)
                Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
            }
            .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
        }
    }
}

struct V3SubjectRow: View {
    let subject: Subject
    var number: Int? = nil
    var body: some View {
        MurshidCard {
            HStack(spacing: 13) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Color.murshidBlue.opacity(0.10))
                        .frame(width: 50, height: 50)
                    if let number {
                        Text("\(number)").font(.headline.monospacedDigit()).foregroundStyle(Color.murshidBlue)
                    } else {
                        Image(systemName: subjectSymbol(subject.name)).font(.headline).foregroundStyle(Color.murshidBlue)
                    }
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(subject.name).font(.headline).foregroundStyle(.primary)
                    Text("المواضيع والاختبارات").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.left").font(.caption.bold()).foregroundStyle(.tertiary)
            }
        }
    }

    private func subjectSymbol(_ name: String) -> String {
        if name.contains("رياض") { return "function" }
        if name.contains("فيزياء") { return "atom" }
        if name.contains("كيمي") { return "flask.fill" }
        if name.contains("أحياء") { return "leaf.fill" }
        if name.contains("إنك") || name.contains("انك") { return "globe" }
        if name.contains("إسلام") || name.contains("اسلام") { return "book.closed.fill" }
        if name.contains("تاريخ") || name.contains("اجتماع") || name.contains("جغراف") { return "map.fill" }
        return "book.closed.fill"
    }
}

struct V3SectionHeader: View {
    let title: String
    let subtitle: String
    let icon: String
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).foregroundStyle(Color.murshidBlue).frame(width: 30, height: 30).background(Color.murshidBlue.opacity(0.09), in: RoundedRectangle(cornerRadius: 9))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.title3.bold())
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
        }
    }
}

struct V3IntroCard: View {
    let eyebrow: String
    let title: String
    let text: String
    let icon: String
    var body: some View {
        MurshidCard {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: icon).font(.title2).foregroundStyle(.white).frame(width: 52, height: 52).background(LinearGradient(colors: [.murshidBlue, .murshidNavy], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                VStack(alignment: .leading, spacing: 5) {
                    Text(eyebrow).font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                    Text(title).font(.title3.bold())
                    Text(text).font(.subheadline).foregroundStyle(.secondary).lineSpacing(3)
                }
                Spacer(minLength: 0)
            }
        }
    }
}

struct V3ProgressRing: View {
    let value: Int
    let label: String
    var body: some View {
        ZStack {
            Circle().stroke(Color.primary.opacity(0.06), lineWidth: 12)
            Circle()
                .trim(from: 0, to: CGFloat(max(0, min(100, value))) / 100)
                .stroke(Color.murshidBlue, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: 2) {
                Text("\(value)%").font(.title2.bold().monospacedDigit())
                Text(label).font(.caption2).foregroundStyle(.secondary)
            }
        }
        .frame(width: 126, height: 126)
    }
}

private struct V3MiniMetric: View {
    let value: String
    let label: String
