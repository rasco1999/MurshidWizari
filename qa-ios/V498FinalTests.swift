import XCTest
@testable import MurshidWizari

final class V498FinalTests: XCTestCase {
    private func row(_ id: Int, text: String = "إعلان جديد") -> JSON {
        ["id": id, "school": "مدرسة الأمل", "text": text]
    }
    @MainActor
    func testOfflineLaunchDoesNotRequireRememberedSession() {
        // Only the previous online verification and protected local archive
        // matter: neither a cookie nor an active plan is an input.
        XCTAssertTrue(V49OfflineLaunchRules.showLocalCoach(
            networkUnavailable: true, hasVerifiedArchive: true))
        XCTAssertFalse(V49OfflineLaunchRules.showLocalCoach(
            networkUnavailable: false, hasVerifiedArchive: true))
        XCTAssertFalse(V49OfflineLaunchRules.showLocalCoach(
            networkUnavailable: true, hasVerifiedArchive: false))
    }

    @MainActor
    func testFirstInstallOpensPublicGuestWithoutInternet() {
        XCTAssertTrue(V49OfflineLaunchRules.showPublicGuest(
            networkUnavailable: true, hasVerifiedArchive: false))
        XCTAssertFalse(V49OfflineLaunchRules.showPublicGuest(
            networkUnavailable: true, hasVerifiedArchive: true))
        XCTAssertFalse(V49OfflineLaunchRules.showPublicGuest(
            networkUnavailable: false, hasVerifiedArchive: false))
    }

    @MainActor
    func testSessionExpiryDoesNotEraseLocallySavedStudentWhenRememberOff() {
        // Simulates a student who previously authenticated but did not
        // persist a web login session: local study is independent of cookies.
        let id = 98765012
        let user = UserSummary(id: id, name: "طالب اختبار محلي", grade: "الثالث المتوسط", gradeID: 1)
        let store = V49StudyStore.shared
        store.eraseOnLogout(userID: id)
        defer { store.eraseOnLogout(userID: id) }
        store.bind(to: user)
        XCTAssertTrue(store.hasOfflineAccount())

        let session = AppSession.shared
        session.user = user
        session.reset(expired: true)
        XCTAssertTrue(store.hasOfflineAccount(), "A server cookie expiring must not delete cached review")

        // The previous reset clears session.user. An explicit reset or sign-out
        // must still find and revoke the last locally verified user.
        session.reset(eraseOfflineStudy: true)
        XCTAssertFalse(store.hasOfflineAccount(), "Explicit sign-out must revoke the cached notebook")
    }

    func testInvalidAndDuplicateAdsAreExcluded() {
        let rows = [row(1), row(1), row(0), ["id": 2, "school": "", "text": "A"],
                    ["id": 3, "school": "S", "text": "A", "active": false]]
        XCTAssertEqual(V498SchoolAd.published(rows).map(\.id), [1])
    }
    func testRevisionsIncludeChangesToPublishedContent() {
        XCTAssertEqual(V498SchoolAd(row(1))?.revision, V498SchoolAd(row(1))?.revision)
        XCTAssertNotEqual(V498SchoolAd(row(1))?.revision, V498SchoolAd(row(1, text: "تعديل"))?.revision)
        XCTAssertNotEqual(V498SchoolAd(row(1))?.revision, V498SchoolAd(row(2))?.revision)
    }
    func testReadingAndWithdrawalUpdateUnreadCount() {
        let first = V498SchoolAd(row(1))!
        let second = V498SchoolAd(row(2))!
        var state = V498AdReadState()
        XCTAssertEqual(state.unread(in: [first]), 1)
        state.markRead([first])
        XCTAssertEqual(state.unread(in: [first, second]), 1)
        XCTAssertEqual(state.unread(in: [first]), 0)
        XCTAssertEqual(state.unread(in: []), 0)
        XCTAssertEqual(V498AdReadState(seen: Array(state.seen)).unread(in: [first, second]), 1)
    }
    @MainActor
    func testBadgesPersistAndAccountsAreIsolated() {
        let name = "V498-QA-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let store = V498SchoolAdsStore(defaults: defaults)
        store.bind(userID: 1)
        store.ingest([row(1)], for: 1)
        XCTAssertEqual(store.unreadCount, 1)
        store.setViewingAds(true)
        XCTAssertEqual(store.unreadCount, 0)
        store.setViewingAds(false)
        store.ingest([row(1), row(2)], for: 1)
        XCTAssertEqual(store.unreadCount, 1)
        let restored = V498SchoolAdsStore(defaults: defaults)
        restored.bind(userID: 1)
        restored.ingest([row(1), row(2)], for: 1)
        XCTAssertEqual(restored.unreadCount, 1)
        restored.bind(userID: 2)
        XCTAssertTrue(restored.ads.isEmpty)
        restored.ingest([row(1)], for: 2)
        XCTAssertEqual(restored.unreadCount, 1)
        restored.ingest([row(3)], for: 1) // stale account response
        XCTAssertEqual(restored.ads.map(\.id), [1])
        restored.bind(userID: 0)
        XCTAssertEqual(restored.unreadCount, 0)
        XCTAssertTrue(restored.ads.isEmpty)
    }
    @MainActor
    func testArrivalWhileAdsOpenIsReadWithoutStaleBadge() {
        let name = "V498-QA-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let store = V498SchoolAdsStore(defaults: defaults)
        store.bind(userID: 1)
        store.setViewingAds(true)
        store.ingest([row(1)], for: 1)
        XCTAssertEqual(store.unreadCount, 0)
        XCTAssertEqual(store.ads.count, 1)
        store.setViewingAds(false)
        store.ingest([row(1, text: "إعلان محدّث")], for: 1)
        XCTAssertEqual(store.unreadCount, 1)
    }
    @MainActor
    func testNetworkFailureDoesNotClearExistingUnreadAd() async {
        let name = "V498-QA-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let store = V498SchoolAdsStore(defaults: defaults, fetchAds: { throw URLError(.notConnectedToInternet) })
        store.bind(userID: 1)
        store.ingest([row(1)], for: 1)
        await store.refresh()
        XCTAssertEqual(store.unreadCount, 1)
        XCTAssertEqual(store.ads.count, 1)
        XCTAssertFalse(store.error.isEmpty)
        XCTAssertFalse(store.isLoading)
    }
    @MainActor
    func testAccountChangedDuringRequestRejectsOldResponse() async {
        let name = "V498-QA-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let store = V498SchoolAdsStore(defaults: defaults, fetchAds: {
            try await Task.sleep(nanoseconds: 40_000_000)
            return ["user": ["id": 1], "ads": [["id": 1, "school": "A", "text": "B"]]]
        })
        store.bind(userID: 1)
        let task = Task { await store.refresh() }
        await Task.yield()
        store.bind(userID: 2)
        await task.value
        XCTAssertTrue(store.ads.isEmpty)
        XCTAssertEqual(store.unreadCount, 0)
    }
}
