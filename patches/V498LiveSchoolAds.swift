import SwiftUI
import CryptoKit

struct V498SchoolAd: Identifiable, Equatable {
    let id: Int
    let school: String
    let text: String

    init?(_ row: JSON) {
        let id = jInt(row["id"])
        let school = jString(row["school"]).trimmingCharacters(in: .whitespacesAndNewlines)
        let text = jString(row["text"]).trimmingCharacters(in: .whitespacesAndNewlines)
        guard id > 0, !school.isEmpty, !text.isEmpty,
              jBool(row["active"], default: true), jBool(row["is_enabled"], default: true) else { return nil }
        self.id = id
        self.school = school
        self.text = text
    }

    // Stable across launches, unlike Swift's randomized hashValue. Editing a
    // published ad produces a new revision without relying on client clocks.
    var revision: String {
        let data = Data("\(id)\u{0}\(school)\u{0}\(text)".utf8)
        return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    static func published(_ rows: [JSON]) -> [Self] {
        var ids = Set<Int>()
        return rows.compactMap(Self.init).filter { ids.insert($0.id).inserted }
    }
}

struct V498AdReadState {
    private(set) var seen: Set<String>
    init(seen: [String] = []) { self.seen = Set(seen) }
    func unread(in ads: [V498SchoolAd]) -> Int {
        ads.filter { !seen.contains($0.revision) }.count
    }
    mutating func markRead(_ ads: [V498SchoolAd]) {
        seen.formUnion(ads.map(\.revision))
    }
}

@MainActor
final class V498SchoolAdsStore: ObservableObject {
    static let shared = V498SchoolAdsStore()
    @Published private(set) var ads: [V498SchoolAd] = []
    @Published private(set) var unreadCount = 0
    @Published private(set) var isLoading = false
    @Published private(set) var hasLoaded = false
    @Published private(set) var error = ""
    private(set) var userID = 0
    private var generation = 0
    private var viewingAds = false
    private var readState = V498AdReadState()
    private let defaults: UserDefaults
    private let fetchAds: @MainActor () async throws -> JSON

    init(defaults: UserDefaults = .standard,
         fetchAds: @escaping @MainActor () async throws -> JSON = {
             try await APIClient.shared.request("mobile/bootstrap.php", fresh: true)
         }) {
        self.defaults = defaults
        self.fetchAds = fetchAds
    }
    private var persistenceKey: String { "murshid_school_ads_seen_v498_\(userID)" }

    func bind(userID: Int) {
        guard self.userID != userID else { return }
        generation += 1
        self.userID = max(0, userID)
        ads = []
        unreadCount = 0
        isLoading = false
        hasLoaded = false
        error = ""
        viewingAds = false
        readState = V498AdReadState(seen: userID > 0 ? defaults.stringArray(forKey: persistenceKey) ?? [] : [])
    }

    func ingest(_ rows: [JSON], for userID: Int) {
        guard userID > 0, self.userID == userID else { return }
        // Bootstrap is the canonical full list, filtered for start/end date,
        // enabled state and impression cap by the existing server. Do not use
        // popup.php's claim-filtered list: claiming a popup isn't reading a tab.
        ads = V498SchoolAd.published(rows)
        hasLoaded = true
        error = ""
        if viewingAds { markDisplayedRead() }
        else { unreadCount = readState.unread(in: ads) }
    }

    func setViewingAds(_ visible: Bool) {
        viewingAds = visible
        if visible { markDisplayedRead() }
    }

    private func markDisplayedRead() {
        guard userID > 0, hasLoaded else { return }
        readState.markRead(ads)
        defaults.set(Array(readState.seen), forKey: persistenceKey)
        unreadCount = 0
    }

    func refresh() async {
        let expectedUserID = userID
        let expectedGeneration = generation
        guard expectedUserID > 0, !isLoading else { return }
        isLoading = true
        defer { if generation == expectedGeneration { isLoading = false } }
        do {
            let response = try await fetchAds()
            guard !Task.isCancelled, generation == expectedGeneration, userID == expectedUserID else { return }
            let responseUser = response["user"] as? JSON ?? [:]
            guard jInt(responseUser["id"]) == expectedUserID,
                  let rows = response["ads"] as? [JSON] else {
                error = "تعذر التحقق من قائمة الإعلانات. اسحب للتحديث."
                return
            }
            ingest(rows, for: expectedUserID)
        } catch {
            guard !Task.isCancelled, generation == expectedGeneration else { return }
            // Preserve both the last successful list and the unread indicator.
            self.error = "تعذر تحديث الإعلانات. ستتم إعادة المحاولة تلقائيًا عند الاتصال."
        }
    }

    func monitor(userID: Int) async {
        guard userID > 0, self.userID == userID else { return }
        while !Task.isCancelled && self.userID == userID {
            await refresh()
            do { try await Task.sleep(nanoseconds: 15_000_000_000) }
            catch { return }
        }
    }
}

struct V498UnreadAdBadge: View {
    let count: Int
    var body: some View {
        if count > 0 {
            Text(count > 99 ? "99+" : "\(count)")
                .font(.system(size: 10, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .padding(.horizontal, 4)
                .frame(minWidth: 17, minHeight: 17)
                .background(Color.red, in: Capsule())
                .overlay(Capsule().stroke(Color(uiColor: .systemBackground), lineWidth: 1.5))
                .fixedSize()
                .accessibilityLabel("\(count) إعلانات جديدة")
                .accessibilityIdentifier("school-ads-unread-badge")
        }
    }
}
