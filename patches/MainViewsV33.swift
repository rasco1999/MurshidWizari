import SwiftUI

struct MainTabView: View {
    @EnvironmentObject var app: AppSession
    var body: some View {
        TabView {
            NavigationStack { HomeView() }
                .tabItem { Label("الرئيسية", systemImage: "house.fill") }
            NavigationStack { SubjectsView() }
                .tabItem { Label("المواد", systemImage: "books.vertical.fill") }
            NavigationStack { ProgressHubView() }
                .tabItem { Label("تقدمي", systemImage: "chart.line.uptrend.xyaxis") }
            NavigationStack { AccountView() }
                .tabItem { Label("حسابي", systemImage: "person.crop.circle.fill") }
        }
        .tint(.murshidBlue)
        .environment(\.layoutDirection, .rightToLeft)
    }
}

struct HomeView: View {
    @EnvironmentObject var app: AppSession
    @EnvironmentObject var device: DeviceServices
    @State private var error = ""

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                welcomeCard
                if !device.isOnline { offlineBanner }
                statsGrid
                if !app.subscribed { freeCard }
                quickActions
                sectionTitle("ابدأ المراجعة", icon: "books.vertical")
                ForEach(app.subjects) { subject in
                    NavigationLink(value: subject) { SubjectCard(subject: subject) }.buttonStyle(.plain)
                }
                if !error.isEmpty { Text(error).font(.footnote).foregroundStyle(.red).padding(.top, 6) }
            }
            .padding(16)
        }
        .background(Color.murshidBackground)
        .navigationTitle("المرشد الوزاري")
        .navigationDestination(for: Subject.self) { TopicsView(subject: $0) }
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                NavigationLink(destination: NotificationsView()) {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "bell.fill")
                        if app.unreadNotifications > 0 {
                            Text("\(min(app.unreadNotifications, 99))").font(.caption2.bold()).foregroundStyle(.white).padding(4).background(.red).clipShape(Circle()).offset(x: 9, y: -8)
                        }
                    }
                }
                .accessibilityLabel("الإشعارات")
            }
        }
        .refreshable { await refresh() }
        .task { await loadUnread() }
    }

    private var welcomeCard: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [.murshidNavy, .murshidBlue], startPoint: .topTrailing, endPoint: .bottomLeading)
            Circle().fill(.white.opacity(0.06)).frame(width: 180, height: 180).offset(x: -85, y: -75)
            VStack(alignment: .leading, spacing: 8) {
                Text("أهلاً، \(firstName(app.user?.name))").font(.title2.bold()).foregroundStyle(.white)
                Text(app.user?.grade ?? "").font(.subheadline).foregroundStyle(.white.opacity(0.82))
                Text(nextMotivation).font(.footnote.weight(.medium)).foregroundStyle(.white.opacity(0.88))
            }.padding(20)
        }
        .frame(minHeight: 150)
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private var offlineBanner: some View {
        Label("أنت غير متصل الآن. سنعرض البيانات المحفوظة عند توفرها.", systemImage: "wifi.slash")
            .font(.footnote.weight(.medium)).foregroundStyle(.orange)
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.orange.opacity(0.10), in: RoundedRectangle(cornerRadius: 14))
    }

    private var statsGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            StatCard(title: "الإجابات", value: "\(jInt(app.stats["total_answers"]))", icon: "checkmark.circle")
            StatCard(title: "الدقة", value: "\(app.accuracy)%", icon: "scope")
            StatCard(title: "XP", value: "\(jInt(app.stats["xp"]))", icon: "bolt.fill")
            StatCard(title: "المستوى", value: "\(jInt(app.stats["level"], default: 1))", icon: "chart.bar.fill")
        }
    }

    private var freeCard: some View {
        MurshidCard {
            HStack(spacing: 14) {
                Image(systemName: "gift.fill").font(.system(size: 31)).foregroundStyle(Color.murshidGold)
                VStack(alignment: .leading, spacing: 5) {
                    Text("رصيدك المجاني").font(.headline)
                    Text("متبقي \(app.freeRemaining ?? 0) من \(app.freeLimit) سؤال").foregroundStyle(.secondary)
                    ProgressView(value: Double(app.freeUsed), total: Double(max(1, app.freeLimit))).tint(.murshidGold)
                }
                Spacer()
                NavigationLink(destination: SubscriptionView()) { Image(systemName: "chevron.left").font(.headline) }
            }
        }
    }

    private var quickActions: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            NavigationLink(destination: ReviewView()) { ToolTile(title: "مراجعة ذكية", subtitle: "ركز على نقاط ضعفك", icon: "brain.head.profile") }
            NavigationLink(destination: StudyPlanView()) { ToolTile(title: "خطة الدراسة", subtitle: "هدف يومي واضح", icon: "calendar.badge.clock") }
            NavigationLink(destination: FavoritesView()) { ToolTile(title: "المفضلة", subtitle: "أسئلتك المحفوظة", icon: "heart.fill") }
            NavigationLink(destination: AchievementsView()) { ToolTile(title: "الإنجازات", subtitle: "تابع تقدمك", icon: "trophy.fill") }
        }.buttonStyle(.plain)
    }

    private func sectionTitle(_ title: String, icon: String) -> some View {
        HStack { Label(title, systemImage: icon).font(.title3.bold()); Spacer() }.padding(.top, 8)
    }

    private var nextMotivation: String {
        if app.accuracy >= 85 { return "ممتاز — حافظ على هذا المستوى وراجع الأخطاء القليلة." }
        if jInt(app.stats["total_answers"]) > 0 { return "كل سؤال تحله يقرّبك خطوة من الدرجة التي تريدها." }
        return "ابدأ بأول مادة، وسنحفظ تقدمك تلقائيًا."
    }

    private func firstName(_ name: String?) -> String {
        guard let name, let first = name.split(separator: " ").first else { return "طالبنا" }
        return String(first)
    }

    private func refresh() async {
        do {
            let json = try await APIClient.shared.bootstrap()
            await MainActor.run { app.applyBootstrap(json); error = ""; selectionHaptic() }
            await loadUnread()
        } catch { await MainActor.run { self.error = error.localizedDescription } }
    }

    private func loadUnread() async {
        do {
            let json = try await APIClient.shared.request("mobile/notifications.php")
            await MainActor.run { app.unreadNotifications = jInt(json["unread"]) }
        } catch { }
    }
}

struct StatCard: View {
    let title: String; let value: String; let icon: String
    var body: some View {
        MurshidCard {
            HStack(spacing: 10) {
                ZStack { Circle().fill(Color.murshidBlue.opacity(0.11)).frame(width: 42, height: 42); Image(systemName: icon).foregroundStyle(Color.murshidBlue) }
                VStack(alignment: .leading, spacing: 2) { Text(value).font(.title2.bold()); Text(title).font(.caption).foregroundStyle(.secondary) }
                Spacer(minLength: 0)
            }
        }
    }
}

struct SubjectCard: View {
    let subject: Subject
    var body: some View {
        MurshidCard {
            HStack(spacing: 14) {
                ZStack { RoundedRectangle(cornerRadius: 14).fill(Color.murshidBlue.opacity(0.11)).frame(width: 48, height: 48); Image(systemName: "book.closed.fill").foregroundStyle(Color.murshidBlue) }
                Text(subject.name).font(.headline).foregroundStyle(.primary)
                Spacer(); Image(systemName: "chevron.left").foregroundStyle(.secondary)
            }
        }
    }
}

struct ToolTile: View {
    let title: String; let subtitle: String; let icon: String
    var body: some View {
        MurshidCard {
            VStack(alignment: .leading, spacing: 9) {
                Image(systemName: icon).font(.title2).foregroundStyle(Color.murshidBlue)
                Text(title).font(.headline).foregroundStyle(.primary)
                Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
            }.frame(maxWidth: .infinity, minHeight: 92, alignment: .leading)
        }
    }
}

struct SubjectsView: View {
    @EnvironmentObject var app: AppSession
    var body: some View {
        List(app.subjects) { subject in
            NavigationLink(value: subject) {
                HStack(spacing: 12) { Image(systemName: "book.closed.fill").foregroundStyle(Color.murshidBlue); Text(subject.name).font(.headline) }.padding(.vertical, 5)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("المواد الدراسية")
        .navigationDestination(for: Subject.self) { TopicsView(subject: $0) }
    }
}

struct TopicsView: View {
    let subject: Subject
    @State private var topics: [Topic] = []
    @State private var loading = true
    @State private var error = ""
    var body: some View {
        Group {
            if loading { LoadingView(text: "جاري تحميل المواضيع…") }
            else if !error.isEmpty { ErrorStateView(message: error, retry: { Task { await load() } }) }
            else if topics.isEmpty { EmptyStateView(systemImage: "tray", title: "لا توجد مواضيع", message: "لا توجد مواضيع متاحة لهذه المادة حاليًا.") }
            else {
                List(topics) { topic in
                    NavigationLink(destination: ExamView(topic: topic, subject: subject)) {
                        HStack(spacing: 12) {
                            Image(systemName: "doc.text.fill").foregroundStyle(Color.murshidBlue)
                            VStack(alignment: .leading, spacing: 4) { Text(topic.name).font(.headline); Text("\(topic.count) سؤال").font(.caption).foregroundStyle(.secondary) }
                        }.padding(.vertical, 5)
                    }
                }.listStyle(.insetGrouped)
            }
        }
        .navigationTitle(subject.name)
        .task { await load() }
    }
    private func load() async {
        await MainActor.run { loading = true; error = "" }
        do {
            let data = try await APIClient.shared.request("mobile/topics.php", query: [URLQueryItem(name: "subject_id", value: "\(subject.id)")], cacheKey: "topics-\(subject.id)")
            await MainActor.run { topics = jArray(data["topics"]).map(Topic.init); loading = false }
        } catch { await MainActor.run { loading = false; self.error = error.localizedDescription } }
    }
}

struct ProgressHubView: View {
    @EnvironmentObject var app: AppSession
    var body: some View {
        List {
            Section {
                HStack {
                    VStack(alignment: .leading, spacing: 6) { Text("دقة إجاباتك").font(.headline); Text("\(app.accuracy)%").font(.system(size: 34, weight: .bold, design: .rounded)).foregroundStyle(Color.murshidBlue) }
                    Spacer()
                    ProgressView(value: Double(app.accuracy), total: 100).progressViewStyle(.circular).scaleEffect(1.35).tint(.murshidBlue)
                }.padding(.vertical, 10)
            }
            Section("تحسين مستواك") {
                NavigationLink(destination: ReviewView()) { Label("المراجعة الذكية", systemImage: "brain.head.profile") }
                NavigationLink(destination: StudyPlanView()) { Label("خطة الدراسة", systemImage: "calendar") }
                NavigationLink(destination: FavoritesView()) { Label("الأسئلة المفضلة", systemImage: "heart.fill") }
                NavigationLink(destination: AchievementsView()) { Label("الإنجازات", systemImage: "trophy.fill") }
            }
            Section("إحصائيات") {
                LabeledContent("إجمالي الإجابات", value: "\(jInt(app.stats["total_answers"]))")
                LabeledContent("الإجابات الصحيحة", value: "\(jInt(app.stats["correct_answers"]))")
                LabeledContent("الاختبارات المكتملة", value: "\(jInt(app.stats["exams_completed"]))")
                LabeledContent("سلسلة النشاط", value: "\(jInt(app.stats["streak"])) يوم")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("تقدمي")
    }
}

struct ReviewView: View {
    @EnvironmentObject private var app: AppSession
    @State private var weak: [JSON] = []
    @State private var mistakes: [JSON] = []
    @State private var loading = true
    @State private var error = ""
    var body: some View {
        Group {
            if loading { LoadingView(text: "نبني مراجعتك الذكية…") }
            else if !error.isEmpty { ErrorStateView(message: error, retry: { Task { await load() } }) }
            else {
                List {
                    Section {
                        VStack(alignment: .leading, spacing: 12) {
                            Label("تحليل مبني على إجاباتك الفعلية", systemImage: "chart.bar.xaxis")
                                .font(.headline)
                                .foregroundStyle(Color.murshidBlue)
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("\(app.accuracy)%").font(.title.bold()).monospacedDigit()
                                    Text("دقة إجاباتك").font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("\(weak.filter { jInt($0["rate"]) < 70 }.count)").font(.title.bold()).monospacedDigit()
                                    Text("موضوع يحتاج مراجعة").font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            Text("المؤشرات تتغير مع إجاباتك. قلة الإجابات لا تكفي للحكم على مستواك النهائي.")
                                .font(.caption).foregroundStyle(.secondary)
                        }.padding(.vertical, 6)
                    }
                    if !weak.isEmpty {
                        Section("المواضيع التي تحتاج تركيزًا") {
                            ForEach(Array(weak.enumerated()), id: \.offset) { _, item in
                                VStack(alignment: .leading, spacing: 9) {
                                    HStack {
                                        Text(jString(item["chapter_name"])).font(.headline)
                                        Spacer()
                                        Text("\(jInt(item["rate"]))%")
                                            .font(.headline.monospacedDigit())
                                            .foregroundStyle(jInt(item["rate"]) >= 70 ? Color.green : Color.orange)
                                    }
                                    Text(jString(item["subject_name"]) + " • " + "\(jInt(item["wrong"])) أخطاء من \(jInt(item["total"])) إجابة")
                                        .font(.caption).foregroundStyle(.secondary)
                                    ProgressView(value: Double(max(0, min(100, jInt(item["rate"])))), total: 100)
                                        .tint(jInt(item["rate"]) >= 70 ? .green : .orange)
                                    if let subject = subjectFor(item) {
                                        HStack(spacing: 12) {
                                            NavigationLink(destination: V31StudySubjectView(subject: subject, focusedTopicID: jInt(item["chapter_id"]))) {
                                                Label("شرح المادة", systemImage: "play.rectangle")
                                            }
                                            if app.featureEnabled("custom_exam") {
                                                NavigationLink(destination: V40CustomExamBuilderView(initialSubjectID: subject.id, mistakesOnly: true)) {
                                                    Label("اختبر أخطاءك", systemImage: "arrow.clockwise")
                                                }
                                            }
                                        }
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(Color.murshidBlue)
                                    }
                                }.padding(.vertical, 7)
                            }
                        }
                    }
                    Section("دفتر أخطائي السابقة") {
                        if mistakes.isEmpty { Text("لا توجد أخطاء سابقة مسجلة حتى الآن. 👏").foregroundStyle(.secondary) }
                        ForEach(Array(mistakes.enumerated()), id: \.offset) { _, item in
                            VStack(alignment: .leading, spacing: 9) {
                                Text("من بنك الأسئلة")
                                    .font(.caption.bold())
                                    .foregroundStyle(Color.murshidBlue)
                                    .padding(.horizontal, 8).padding(.vertical, 4)
                                    .background(Color.murshidBlue.opacity(0.10), in: Capsule())
                                Text(murshidQuestionDisplayText(jString(item["question_text"]))).font(.body.weight(.semibold))
                                Text("الإجابة الصحيحة: \(jString(item["correct_answer"]))")
                                    .font(.subheadline.weight(.semibold)).foregroundStyle(.green)
                                let explanation = murshidUsefulExplanation(jString(item["explanation"]))
                                if !explanation.isEmpty {
                                    Text(explanation).font(.footnote).foregroundStyle(.secondary)
                                } else {
                                    Text("الشرح التفصيلي غير متاح بعد؛ تستطيع مراجعة فيديوهات المادة إن وُجدت.")
                                        .font(.footnote).foregroundStyle(.secondary)
                                }
                                Text(jString(item["subject_name"]) + " • " + jString(item["chapter_name"]))
                                    .font(.caption2).foregroundStyle(.secondary)
                                if let subject = subjectFor(item) {
                                    NavigationLink(destination: V31StudySubjectView(subject: subject, focusedTopicID: jInt(item["chapter_id"]))) {
                                        Label("انتقل إلى ادرس", systemImage: "play.rectangle.fill")
                                            .font(.subheadline.weight(.medium))
                                    }
                                }
                            }.padding(.vertical, 6)
                        }
                    }
                }.listStyle(.insetGrouped)
            }
        }
        .navigationTitle("المراجعة الذكية")
        .task { await load() }
        .refreshable { await load() }
    }
    private func subjectFor(_ row: JSON) -> Subject? {
        let id = jInt(row["subject_id"])
        return app.subjects.first(where: { $0.id == id })
    }
    private func load() async {
        await MainActor.run { loading = true }
        do {
            let data = try await APIClient.shared.request("mobile/review.php")
            await MainActor.run { weak = jArray(data["weak"]); mistakes = jArray(data["mistakes"]); loading = false; error = "" }
        } catch { await MainActor.run { loading = false; self.error = error.localizedDescription } }
    }
}

struct NotificationsView: View {
    @EnvironmentObject var app: AppSession
    @State private var items: [NotificationItem] = []
    @State private var loading = true
    var body: some View {
        Group {
            if loading { LoadingView() }
            else if items.isEmpty { EmptyStateView(systemImage: "bell.slash", title: "لا توجد إشعارات", message: "ستظهر تنبيهات المنصة هنا.") }
            else {
                List(items) { item in
                    VStack(alignment: .leading, spacing: 5) {
                        HStack { Text(item.title).font(.headline); if !item.isRead { Circle().fill(Color.murshidBlue).frame(width: 8, height: 8) } }
                        Text(item.body).foregroundStyle(.secondary)
                        Text(item.createdAt).font(.caption2).foregroundStyle(.tertiary)
                    }.padding(.vertical, 5)
                }.listStyle(.insetGrouped)
            }
        }
        .navigationTitle("الإشعارات")
        .task { await load(markAll: false) }
        .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("قراءة الكل") { Task { await load(markAll: true) } } } }
    }
    private func load(markAll: Bool) async {
        do {
            let data = try await APIClient.shared.request("mobile/notifications.php", method: markAll ? "POST" : "GET", body: markAll ? ["csrf": app.csrf, "read_all": true] : nil)
            await MainActor.run { items = jArray(data["items"]).map(NotificationItem.init); app.unreadNotifications = jInt(data["unread"]); loading = false }
        } catch { await MainActor.run { loading = false } }
    }
}

struct FavoritesView: View {
    @State private var items: [JSON] = []
    @State private var loading = true
    @State private var error = ""
    var body: some View {
        Group {
            if loading { LoadingView() }
            else if !error.isEmpty { ErrorStateView(message: error, retry: { Task { await load() } }) }
            else if items.isEmpty { EmptyStateView(systemImage: "heart", title: "المفضلة فارغة", message: "أضف أي سؤال للمفضلة من شاشة الاختبار.") }
            else {
                List(Array(items.enumerated()), id: \.offset) { _, q in
                    VStack(alignment: .leading, spacing: 7) {
                        Text("وزاري")
                            .font(.caption.bold())
                            .foregroundStyle(Color.murshidBlue)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Color.murshidBlue.opacity(0.10), in: Capsule())
                        Text(murshidQuestionDisplayText(jString(q["question_text"]))).font(.headline)
                        Text("الإجابة: \(jString(q["correct_answer"]))").foregroundStyle(.green)
                        if !jString(q["explanation"]).isEmpty { Text(jString(q["explanation"])).font(.footnote).foregroundStyle(.secondary) }
                        Text(jString(q["subject_name"]) + " • " + jString(q["chapter_name"])).font(.caption2).foregroundStyle(.secondary)
                    }.padding(.vertical, 5)
                }.listStyle(.insetGrouped)
            }
        }
        .navigationTitle("المفضلة")
        .task { await load() }
    }
    private func load() async {
        do { let data = try await APIClient.shared.request("mobile/favorites.php"); await MainActor.run { items = jArray(data["items"]); loading = false; error = "" } }
        catch { await MainActor.run { loading = false; self.error = error.localizedDescription } }
    }
}

struct StudyPlanView: View {
    @EnvironmentObject var app: AppSession
    @State private var plans: [StudyPlanItem] = []
    @State private var title = "خطتي الوزارية"
    @State private var days = 30
    @State private var target = 20
    @State private var loading = true
    @State private var message = ""
    var body: some View {
        Form {
            Section("خطة جديدة") {
                TextField("اسم الخطة", text: $title)
                Stepper("المدة: \(days) يوم", value: $days, in: 1...365)
                Stepper("الهدف اليومي: \(target) سؤال", value: $target, in: 5...500, step: 5)
                Button("إنشاء الخطة") { Task { await create() } }
                if !message.isEmpty { Text(message).font(.footnote).foregroundStyle(.green) }
            }
            Section("خططي") {
                if loading { ProgressView() }
                ForEach(plans) { p in
                    VStack(alignment: .leading) { Text(p.title).font(.headline); Text("\(p.startDate) ← \(p.endDate) • \(p.dailyTarget) سؤال/يوم").font(.caption).foregroundStyle(.secondary) }
                }
            }
        }
        .navigationTitle("خطة الدراسة")
        .task { await load() }
    }
    private func load() async {
        do { let d = try await APIClient.shared.request("mobile/study-plan.php"); await MainActor.run { plans = jArray(d["plans"]).map(StudyPlanItem.init); loading = false } }
        catch { await MainActor.run { loading = false } }
    }
    private func create() async {
        do {
            let d = try await APIClient.shared.request("mobile/study-plan.php", method: "POST", body: ["csrf": app.csrf, "title": title, "days": days, "target": target])
            await MainActor.run { plans = jArray(d["plans"]).map(StudyPlanItem.init); message = "تم إنشاء الخطة."; haptic() }
        } catch { await MainActor.run { message = error.localizedDescription } }
    }
}

struct AchievementsView: View {
    @State private var achievements: [JSON] = []
    @State private var stats: JSON = [:]
    @State private var loading = true
    var body: some View {
        Group {
            if loading { LoadingView() }
            else {
                List {
                    Section("مستواك") {
                        LabeledContent("XP", value: "\(jInt(stats["xp"]))")
                        LabeledContent("المستوى", value: "\(jInt(stats["level"], default: 1))")
                    }
                    Section("الإنجازات") {
                        ForEach(Array(achievements.enumerated()), id: \.offset) { _, a in
                            HStack {
                                Image(systemName: jString(a["earned_at"]).isEmpty ? "lock.fill" : "trophy.fill")
                                    .foregroundStyle(jString(a["earned_at"]).isEmpty ? Color.secondary : Color.murshidGold)
                                VStack(alignment: .leading) { Text(jString(a["title"])).font(.headline); Text(jString(a["description"])).font(.caption).foregroundStyle(.secondary) }
                                Spacer()
                            }
                        }
                    }
                }.listStyle(.insetGrouped)
            }
        }
        .navigationTitle("الإنجازات")
        .task { await load() }
    }
    private func load() async {
        do {
            let d = try await APIClient.shared.request("mobile/achievements.php")
            await MainActor.run { achievements = jArray(d["achievements"]); stats = d["stats"] as? JSON ?? [:]; loading = false }
        } catch { await MainActor.run { loading = false } }
    }
}
