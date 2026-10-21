import XCTest
@testable import MurshidWizari

final class V498FinalTests: XCTestCase {
    func testAgePolicyRejectsUnselectedAndUnderTenEvenWithConsent() {
        for band in [-1, 0, 1, 6] {
            XCTAssertNotNil(V507AgeRules.validationError(band: band, terms: true, guardian: true))
        }
    }

    func testMinorBandsRequireBothTermsAndGuardianAcknowledgement() {
        for band in 2...4 {
            XCTAssertNotNil(V507AgeRules.validationError(band: band, terms: true, guardian: false))
            XCTAssertNotNil(V507AgeRules.validationError(band: band, terms: false, guardian: true))
            XCTAssertNil(V507AgeRules.validationError(band: band, terms: true, guardian: true))
        }
        XCTAssertNil(V507AgeRules.validationError(band: 5, terms: true, guardian: false))
        XCTAssertNotNil(V507AgeRules.validationError(band: 5, terms: false, guardian: false))
    }

    @MainActor
    func testUnderageCooldownSurvivesChoosingAnAdultBand() {
        let defaults = UserDefaults.standard
        let previous = defaults.object(forKey: V507AgePolicy.blockKey)
        defer {
            if let previous { defaults.set(previous, forKey: V507AgePolicy.blockKey) }
            else { defaults.removeObject(forKey: V507AgePolicy.blockKey) }
        }
        V507AgePolicy.recordUnderage()
        XCTAssertTrue(V507AgePolicy.blocked)
        XCTAssertThrowsError(try V507AgePolicy.validate(band: 5, terms: true, guardian: true))
    }

    @MainActor
    func testAccountRequestsCannotSendWithoutAgeAndTerms() async {
        // The fixture transport returns success for account paths: these failures
        // must therefore happen in the real API client before a request is sent.
        do {
            _ = try await APIClient.shared.login(identifier: "age-fixture@example.invalid", password: "fixture", remember: false,
                ageBand: 0, termsAccepted: false, guardianAccepted: false)
            XCTFail("Missing age must prevent a login request")
        } catch let error as APIError { XCTAssertEqual(error.status, 422) }
        catch { XCTFail("Unexpected error: \(error)") }
        do {
            _ = try await APIClient.shared.register(["full_name": "طالب اختبار محلي"])
            XCTFail("Missing age and terms must prevent registration")
        } catch let error as APIError { XCTAssertEqual(error.status, 422) }
        catch { XCTFail("Unexpected error: \(error)") }
    }

    @MainActor
    private func assertVerificationFailure(kind: String, mode: String, code: String = "123456", status: Int, posts: Int) async {
        V498QAURLProtocol.configureVerification(kind: kind, mode: mode)
        do {
            _ = try await APIClient.shared.verifyPending(kind: kind, code: code)
            XCTFail("Rejected verification must not open an account")
        } catch let error as APIError {
            XCTAssertEqual(error.status, status)
        } catch { XCTFail("Unexpected error: \(error)") }
        let state = V498QAURLProtocol.verificationSnapshot()
        XCTAssertEqual(state.posts, posts, "An OTP POST must never silently repeat")
        XCTAssertFalse(state.calls.contains { $0.contains(" /verify-") }, "Verification must not depend on a web form")
    }

    @MainActor
    private func checkPendingVerification(_ kind: String) async throws {
        defer { V498QAURLProtocol.configureVerification(kind: nil) }
        await assertVerificationFailure(kind: kind, mode: "expired_session", status: 409, posts: 0)
        await assertVerificationFailure(kind: kind, mode: "normal", code: "000000", status: 422, posts: 1)
        await assertVerificationFailure(kind: kind, mode: "csrf_expired", status: 419, posts: 1)
        await assertVerificationFailure(kind: kind, mode: "missing_receipt", status: 503, posts: 1)
        XCTAssertEqual(V498QAURLProtocol.verificationSnapshot().bootstraps, 0)
        await assertVerificationFailure(kind: kind, mode: "bootstrap_unavailable", status: 503, posts: 1)
        XCTAssertEqual(V498QAURLProtocol.verificationSnapshot().bootstraps, 1)

        V498QAURLProtocol.configureVerification(kind: kind, mode: "cooldown")
        do { _ = try await APIClient.shared.resendPending(kind: kind); XCTFail("Cooldown must remain enforced") }
        catch let error as APIError { XCTAssertEqual(error.status, 429) }
        XCTAssertEqual(V498QAURLProtocol.verificationSnapshot().posts, 1)

        V498QAURLProtocol.configureVerification(kind: kind)
        let message = try await APIClient.shared.resendPending(kind: kind)
        XCTAssertEqual(message, "أرسلنا رمز تحقق جديدًا.")
        XCTAssertEqual(V498QAURLProtocol.verificationSnapshot().posts, 1)

        V498QAURLProtocol.configureVerification(kind: kind)
        let result = try await APIClient.shared.verifyPending(kind: kind, code: "١٢٣٤٥٦")
        XCTAssertEqual(jInt((result["user"] as? JSON)?["id"]), 987654)
        let state = V498QAURLProtocol.verificationSnapshot()
        XCTAssertEqual(state.posts, 1)
        XCTAssertEqual(state.bootstraps, 1)
        XCTAssertEqual(state.calls, ["GET /api/verify-\(kind).php", "POST /api/verify-\(kind).php"])
    }

    @MainActor func testPendingEmailJSONConfirmationAndResend() async throws { try await checkPendingVerification("email") }
    @MainActor func testPendingPhoneJSONConfirmationAndResend() async throws { try await checkPendingVerification("phone") }
    @MainActor func testMalformedVerificationNeverReachesTheNetwork() async {
        defer { V498QAURLProtocol.configureVerification(kind: nil) }
        await assertVerificationFailure(kind: "email", mode: "normal", code: "12345", status: 422, posts: 0)
        await assertVerificationFailure(kind: "invalid", mode: "normal", status: 422, posts: 0)
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

