// v4.4 · additions only: live school-ad popup and real 2027 predictions tab.
import SwiftUI
import Foundation
import Combine

// A foreground-only indicator for newly published school ads. It reads the
// same public ad list as the native ads tab, without claiming popup impressions.
// The read baseline is kept separately for each student on this device.
@MainActor
final class V44SchoolAdNoticeStore: ObservableObject {
    static let shared = V44SchoolAdNoticeStore()
    @Published private(set) var unreadCount = 0
    @Published private(set) var publishedRevision = 0

    private var userID = 0
    private var latestAdIDs: Set<String> = []
    private var checking = false

    private init() {}

    func refresh(for studentID: Int) async {
        guard studentID > 0 else { return }
        if userID != studentID {
            userID = studentID
            latestAdIDs = []
            unreadCount = 0
        }
        guard !checking else { return }
        checking = true
        defer { checking = false }
        do {
            // A unique URL bypasses intermediary and URLSession GET caches.
            let response = try await APIClient.shared.request(
                "mobile/bootstrap.php",
                query: [URLQueryItem(name: "_school_ads_check", value: String(Int(Date().timeIntervalSince1970)))]
            )
            guard userID == studentID else { return }
            guard response["ads"] is [JSON] else { return }
            let active = Set(jArray(response["ads"]).compactMap { ad -> String? in
                let school = jString(ad["school"]).trimmingCharacters(in: .whitespacesAndNewlines)
                let details = jString(ad["text"]).trimmingCharacters(in: .whitespacesAndNewlines)
                guard !school.isEmpty, !details.isEmpty, jBool(ad["active"], default: true) else { return nil }
                let id = jInt(ad["id"])
                return id > 0 ? "id:\(id)" : "text:\(String(school.prefix(80)))|\(String(details.prefix(160)))"
            })
            let key = readKey(for: studentID)
            if let seen = UserDefaults.standard.stringArray(forKey: key) {
                unreadCount = active.subtracting(Set(seen)).count
            } else {
                // Ads that already existed on first launch aren't a new alert.
                UserDefaults.standard.set(Array(active), forKey: key)
                unreadCount = 0
            }
            if latestAdIDs != active {
                latestAdIDs = active
                publishedRevision += 1
            }
        } catch {
            // Transient network errors retain the last known badge state.
        }
    }

    func markRead(for studentID: Int) {
        guard studentID > 0, userID == studentID else { return }
        let key = readKey(for: studentID)
        var seen = Set(UserDefaults.standard.stringArray(forKey: key) ?? [])
        seen.formUnion(latestAdIDs)
        // Bound local storage even after years of school publications.
        UserDefaults.standard.set(Array(seen.sorted().suffix(300)), forKey: key)
        unreadCount = 0
    }

    func clearForLogout() {
        userID = 0
        latestAdIDs = []
        unreadCount = 0
    }

    private func readKey(for studentID: Int) -> String {
        "murshid_school_ads_seen_v1_\(studentID)"
    }
}

private struct V44SchoolAd: Identifiable {
    let adID: Int
    let slot: Int
    let school: String
    let details: String
    var id: String { "\(adID):\(slot)" }

    init?(_ json: JSON) {
        let newID = jInt(json["id"])
        let newSlot = jInt(json["slot"], default: -1)
        let title = jString(json["school"]).trimmingCharacters(in: .whitespacesAndNewlines)
        guard newID > 0, newSlot >= 0, !title.isEmpty else { return nil }
        adID = newID
        slot = newSlot
        school = title
        details = jString(json["text"])
    }
}

struct V44SchoolAdPopupModifier: ViewModifier {
    @EnvironmentObject private var app: AppSession
    @State private var visibleAd: V44SchoolAd?
    @State private var checking = false
    // A read-only eligibility poll is safe. The server atomically claims each
    // (ad, student, 12h period) before the popup is presented.
    private let ticker = Timer.publish(every: 180, on: .main, in: .common).autoconnect()

    func body(content: Content) -> some View {
        content
            .task { await checkForNewAd() }
            .onReceive(ticker) { _ in Task { await checkForNewAd() } }
            .onChange(of: app.v3Authenticated) { value in
                if value { Task { await checkForNewAd() } }
            }
            .sheet(item: $visibleAd) { ad in
                NavigationStack {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 18) {
                            HStack(spacing: 12) {
                                Image(systemName: "building.2.crop.circle.fill")
                                    .font(.system(size: 35))
                                    .foregroundStyle(Color.murshidGold)
                                    .frame(width: 64, height: 64)
                                    .background(Color.white.opacity(0.14), in: RoundedRectangle(cornerRadius: 18))
                                VStack(alignment: .leading, spacing: 5) {
                                    Text("إعلان مدرسة").font(.caption.bold())
                                        .foregroundStyle(Color.murshidGold)
                                    Text(ad.school).font(.title2.bold()).foregroundStyle(.white)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                                Spacer()
                            }
                            .padding(20)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(
                                LinearGradient(colors: [.murshidNavy, .murshidBlue],
                                               startPoint: .topTrailing, endPoint: .bottomLeading),
                                in: RoundedRectangle(cornerRadius: 21)
                            )
                            Text(ad.details)
                                .font(.body)
                                .lineSpacing(7)
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text("يمكنك إغلاق هذا الإعلان بعلامة ×. لن يتكرر خلال فترة الـ 12 ساعة الحالية.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        .padding(19)
                    }
                    .background(Color.murshidBackground.ignoresSafeArea())
                    .navigationTitle("إعلان مدرسي")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button {
                                visibleAd = nil
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.title2)
                                    .foregroundStyle(Color.murshidBlue)
                            }
                            .accessibilityLabel("إغلاق الإعلان")
                        }
                    }
                }
                .environment(\.layoutDirection, .rightToLeft)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.hidden)
            }
    }

    @MainActor
    private func checkForNewAd() async {
        guard app.v3Authenticated, !app.csrf.isEmpty, !checking, visibleAd == nil else { return }
        checking = true
        defer { checking = false }
        do {
            // Never cache ads: unpublishing and expiry must be reflected immediately.
            let response = try await APIClient.shared.request("mobile/school-ad-popup.php")
            for row in jArray(response["ads"]) {
                guard let ad = V44SchoolAd(row) else { continue }
                let claimed = try await APIClient.shared.request(
                    "mobile/school-ad-popup.php", method: "POST",
                    body: ["csrf": app.csrf, "id": ad.adID, "slot": ad.slot]
                )
                if jBool(claimed["shown"]) {
                    visibleAd = ad
                    return
                }
            }
        } catch {
            // Ads are optional; network issues must never block studying.
        }
    }
}

struct V44PredictionsView: View {
    @EnvironmentObject private var app: AppSession
    @State private var selectedSubjectID = 0
    @State private var predictions: [JSON] = []
    @State private var loading = false
    @State private var error = ""
    @State private var expanded: Set<Int> = []

    private var subjectName: String {
        app.subjects.first(where: { $0.id == selectedSubjectID })?.name ?? ""
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 10) {
                    Label("الدور الأول • 2027", systemImage: "scope")
                        .font(.subheadline.bold()).foregroundStyle(Color.murshidGold)
                    Text("المرشحات الوزارية")
                        .font(.title.bold()).foregroundStyle(.white)
                    Text("مراجعة مرتبة اعتماداً على السنوات السابقة وتكرار الموضوع وتوثيق الأسئلة.")
                        .font(.subheadline).foregroundStyle(.white.opacity(0.87))
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(22)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    LinearGradient(colors: [.murshidNavy, .murshidBlue],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 22)
                )

                Label("المرشحات احتمالات للمراجعة وليست ضماناً لورود السؤال، ولا تغني عن المنهج.", systemImage: "info.circle.fill")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(13)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.murshidSurface, in: RoundedRectangle(cornerRadius: 14))

                Text("اختر المادة").font(.headline)
                if app.subjects.isEmpty {
                    EmptyStateView(systemImage: "books.vertical", title: "لا توجد مواد", message: "حدّث حسابك أو اتصل بالإنترنت.")
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(app.subjects) { subject in
                                Button {
                                    if selectedSubjectID != subject.id {
                                        selectedSubjectID = subject.id
                                        Task { await load() }
                                    }
                                } label: {
                                    Text(subject.name)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(selectedSubjectID == subject.id ? .white : Color.murshidBlue)
                                        .padding(.horizontal, 16)
                                        .frame(minHeight: 42)
                                        .background(
                                            selectedSubjectID == subject.id ? Color.murshidBlue : Color.murshidSurface,
                                            in: Capsule()
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }

                if loading {
                    ProgressView("جاري تحميل مرشحات \(subjectName)…")
                        .frame(maxWidth: .infinity, minHeight: 100)
                } else if !error.isEmpty {
                    V3InlineMessage(text: error, icon: "wifi.exclamationmark", tone: .warning)
                    Button("إعادة المحاولة") { Task { await load() } }
                        .font(.headline).foregroundStyle(Color.murshidBlue)
                } else if selectedSubjectID > 0 && predictions.isEmpty {
                    EmptyStateView(
                        systemImage: "doc.text.magnifyingglass",
                        title: "لا توجد مرشحات موثقة",
                        message: "تظهر هنا عند توفر أسئلة وزارية موثقة كافية في هذه المادة."
                    )
                } else {
                    Text("مرشحات \(subjectName) — \(predictions.count) سؤال")
                        .font(.headline)
                    ForEach(Array(predictions.enumerated()), id: \.offset) { index, prediction in
                        MurshidCard {
                            DisclosureGroup(
                                isExpanded: Binding(
                                    get: { expanded.contains(index) },
                                    set: { value in
                                        if value { expanded.insert(index) }
                                        else { expanded.remove(index) }
                                    }
                                )
                            ) {
                                VStack(alignment: .leading, spacing: 12) {
                                    Divider()
                                    Text("الجواب النموذجي").font(.subheadline.bold())
                                        .foregroundStyle(Color.murshidBlue)
                                    Text(jString(prediction["correct_answer"]))
                                        .font(.body).textSelection(.enabled)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    let explanation = jString(prediction["explanation"])
                                    if !explanation.isEmpty {
                                        Text("التوضيح").font(.subheadline.bold())
                                            .foregroundStyle(Color.murshidBlue)
                                        Text(explanation).font(.body).textSelection(.enabled)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                }
                                .padding(.top, 8)
                            } label: {
                                VStack(alignment: .leading, spacing: 9) {
                                    HStack {
                                        Text(jString(prediction["chapter_name"]))
                                            .font(.caption).foregroundStyle(.secondary)
                                        Spacer()
                                        Text("\(jInt(prediction["prediction_score"]))/100")
                                            .font(.caption.bold()).foregroundStyle(Color.murshidBlue)
                                    }
                                    Text("س\(index + 1): \(jString(prediction["question_text"]))")
                                        .font(.headline).foregroundStyle(.primary)
                                        .multilineTextAlignment(.leading)
                                        .fixedSize(horizontal: false, vertical: true)
                                    Text(jString(prediction["confidence"]))
                                        .font(.caption.weight(.medium))
                                        .foregroundStyle(Color.murshidBlue)
                                }
                            }
                        }
                    }
                }
            }.padding(16).padding(.bottom, 25)
        }
        .background(Color.murshidBackground.ignoresSafeArea())
        .navigationTitle("المرشحات")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if selectedSubjectID == 0, let first = app.subjects.first {
                selectedSubjectID = first.id
                await load()
            }
        }
        .refreshable { await load() }
    }

    @MainActor
    private func load() async {
        guard selectedSubjectID > 0 else { return }
        loading = true
        error = ""
        expanded = []
        let requestedID = selectedSubjectID
        do {
            let response = try await APIClient.shared.request(
                "mobile/predictions.php",
                query: [URLQueryItem(name: "subject_id", value: String(requestedID))]
            )
            if requestedID == selectedSubjectID { predictions = jArray(response["predictions"]) }
        } catch {
            if requestedID == selectedSubjectID { self.error = error.localizedDescription }
        }
        if requestedID == selectedSubjectID { loading = false }
    }
}
