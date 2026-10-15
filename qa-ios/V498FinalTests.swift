import XCTest
@testable import MurshidWizari

final class V498FinalTests: XCTestCase {
    func testCachedPromptsRemoveOnlyExplicitHintBlocks() {
        XCTAssertEqual(murshidPromptWithoutHints("احسب 2 + 3.\nتلميح: الناتج خمسة"), "احسب 2 + 3.")
        XCTAssertEqual(murshidPromptWithoutHints("Find x: x² = 4.\nHint: x = 2"), "Find x: x² = 4.")
        XCTAssertEqual(murshidPromptWithoutHints("احسب 2 + 3. (تلميح: استخدم الجمع)"), "احسب 2 + 3.")
        for text in ["ما معنى كلمة تلميح؟", "P ≠ p؛ 10^2؛ 1,5؛ عِلْم", "قال تعالى: ﴿قُلْ هُوَ اللَّهُ أَحَدٌ﴾"] {
            XCTAssertEqual(murshidPromptWithoutHints(text), text)
        }
    }

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

    func testRetiredFrenchCacheIdentifiersCannotResurfaceOffline() {
        XCTAssertTrue(V520RetiredFrenchContent.isQuestion(6278))
        XCTAssertTrue(V520RetiredFrenchContent.isQuestion(30830))
        XCTAssertTrue(V520RetiredFrenchContent.isChapter(1507))
        XCTAssertTrue(V520RetiredFrenchContent.isChapter(85))
        XCTAssertFalse(V520RetiredFrenchContent.isQuestion(6277))
        XCTAssertFalse(V520RetiredFrenchContent.isChapter(31))
    }

    @MainActor
    func testSignedCoordinatesRemainOrderedWithinArabicSentence() {
        let source = "أوجد المستقيم المار بـ(0,-4) بين (6,0) و(3,-2)."
        let shown = V515MathBidi.display(source)
        XCTAssertTrue(shown.contains("\u{2066}(0,-4)\u{2069}"))
        XCTAssertTrue(shown.contains("\u{2066}(6,0)\u{2069}"))
        XCTAssertTrue(shown.contains("\u{2066}(3,-2)\u{2069}"))
    }

    @MainActor
    func testEquationTutorIncludesMathChemistryPhysicsAndBiology() {
        let examples: [(String,String,String)] = [
            ("الرياضيات", "أوجد معادلة المستقيم العمودي على المار بالنقطتين (6,0) و(-2,-3)", "احسب"),
            ("الفيزياء", "إذا كانت F = ma احسب القوة.", "قانون"),
            ("الكيمياء", "وازن المعادلة H2 + O2 → H2O", "الكيميائية"),
            ("الأحياء", "احسب النسبة الوراثية لنتائج التزاوج", "الوراثية"),
            ("الاقتصاد", "أوجد قيمة المعادلة x = 2y", "المجهول")
        ]
        for (subject, prompt, _) in examples {
            XCTAssertTrue(V515EquationGuide.applies(question: prompt, subject: subject, kind: "text"))
            let steps = V515EquationGuide.steps(question: prompt, subject: subject, topic: "الفصل الأول")
            XCTAssertEqual(steps.count, 4)
            XCTAssertFalse(steps.joined().contains("الإجابة النموذجية"))
        }
        XCTAssertTrue(V515EquationGuide.applies(question: "مسألة حسابية", subject: "الفيزياء", kind: "calculation"))
        XCTAssertFalse(V515EquationGuide.applies(question: "متى ولد الجواهري عام 1899؟", subject: "اللغة العربية", kind: "text"))
        XCTAssertFalse(V515EquationGuide.applies(question: "How did the canary sing? Because it was hungry.", subject: "اللغة الإنكليزية", kind: "text"))
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
    @MainActor
    func testOfflineExamVaultPersistsAndIsolatesAccounts() {
        let bank = V50OfflineExamBank.shared
        bank.clear()
        defer { bank.clear() }
        let expiry = Int(Date().timeIntervalSince1970) + 3600
        bank.bindAuthenticated(userID: 900121, subscribed: true, expiresUnix: expiry)
        XCTAssertTrue(bank.allowed(900121))
        XCTAssertFalse(bank.allowed(900122))
        let source: [JSON] = [["id": 251, "type": "mcq", "text": "اختبار حفظ محلي",
            "correct_answer": "خمسة", "options": [["id": 11, "text": "خمسة"], ["id": 12, "text": "ستة"]]]]
        XCTAssertTrue(bank.rememberTopic(userID: 900121, chapterID: 31, name: "موضوع اختباري", source: source))
        XCTAssertEqual(bank.questions(userID: 900121, chapterID: 31).count, 1)
        XCTAssertTrue(bank.queue(userID: 900121, chapterID: 31, questionID: 251, answer: "خمسة", optionID: 11))
        XCTAssertTrue(bank.queue(userID: 900121, chapterID: 31, questionID: 251, answer: "ستة", optionID: 12))
        XCTAssertEqual(bank.pending(userID: 900121).count, 1)
        XCTAssertEqual(bank.pending(userID: 900121).first?.text, "خمسة")
        var edited = source
        edited[0]["text"] = "نص تغيّر بعد الإجابة"
        XCTAssertFalse(bank.rememberTopic(userID: 900121, chapterID: 31, name: "نسخة أحدث", source: edited))
        XCTAssertEqual(bank.questions(userID: 900121, chapterID: 31).first?.text, "اختبار حفظ محلي")
        for chapter in 900...912 {
            var other = source
            other[0]["id"] = 900000 + chapter
            XCTAssertTrue(bank.rememberTopic(userID: 900121, chapterID: chapter, name: "موضوع آخر", source: other))
        }
        XCTAssertEqual(bank.topics(userID: 900121).count, 12)
        XCTAssertEqual(bank.questions(userID: 900121, chapterID: 31).first?.text, "اختبار حفظ محلي")
        bank.bindAuthenticated(userID: 900121, subscribed: false, expiresUnix: 0)
        XCTAssertFalse(bank.allowed(900121))
        XCTAssertTrue(bank.topics(userID: 900121).isEmpty)
        XCTAssertEqual(bank.pending(userID: 900121).count, 1)
        bank.bindAuthenticated(userID: 900122, subscribed: true, expiresUnix: expiry)
        XCTAssertTrue(bank.pending(userID: 900121).isEmpty)
        XCTAssertTrue(bank.topics(userID: 900122).isEmpty)
    }

    func testOfflineAnswerRevisionAndConservativeFeedback() {
        let a = V50OfflineQuestion(id: 251, type: "text", text: "احسب الناتج", meaningWord: "",
            options: [], correctAnswer: "8", explanation: "", submitted: false, serverIndex: 1)
        let b = V50OfflineQuestion(id: 251, type: "text", text: "سؤال جديد", meaningWord: "",
            options: [], correctAnswer: "8", explanation: "", submitted: false, serverIndex: 1)
        XCTAssertNotEqual(V50OfflineRules.revision(a), V50OfflineRules.revision(b))
        XCTAssertEqual(V50OfflineRules.feedback(a, answer: "٨.٠"), .correct)
        XCTAssertEqual(V50OfflineRules.feedback(a, answer: "ناتج تقريبي"), .needsServer)
    }

}
