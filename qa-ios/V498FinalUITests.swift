import XCTest

final class V498FinalUITests: XCTestCase {
    @MainActor
    func testLegalFooterAndAgeDetailsInLightAppearance() {
        checkLegalFooter(dark: false)
    }

    @MainActor
    func testLegalFooterAndAgeDetailsInDarkAppearance() {
        checkLegalFooter(dark: true)
    }

    @MainActor
    private func checkLegalFooter(dark: Bool) {
        let app = XCUIApplication()
        app.launchArguments = ["V507_QA_GUEST"] + (dark ? ["V507_QA_DARK"] : [])
        app.launch()
        XCTAssertTrue(app.buttons["تسجيل الدخول"].firstMatch.waitForExistence(timeout: 25), "Public entry must open directly")
        let age = app.buttons["legal-age"].firstMatch
        for _ in 0..<14 {
            if age.exists && age.isHittable { break }
            app.swipeUp(velocity: .fast)
        }
        XCTAssertTrue(age.isHittable)
        for id in ["legal-terms", "legal-privacy", "legal-guardian"] {
            XCTAssertTrue(app.descendants(matching: .any)[id].firstMatch.exists)
        }
        screenshot(dark ? "legal-footer-dark" : "legal-footer-light")
        age.tap()
        XCTAssertTrue(app.staticTexts["دراسة آمنة وواضحة للجميع"].waitForExistence(timeout: 5))
        screenshot(dark ? "legal-age-dark" : "legal-age-light")
        app.buttons["legal-close"].tap()
        XCTAssertTrue(age.waitForExistence(timeout: 5))
    }

    @MainActor
    func testLoginHasNeutralAgeChoiceAndRejectsUnderTen() {
        let app = XCUIApplication()
        app.launchArguments = ["V507_QA_GUEST"]
        app.launch()
        let login = app.buttons["تسجيل الدخول"].firstMatch
        XCTAssertTrue(login.waitForExistence(timeout: 25))
        login.tap()
        let age = app.buttons["legal-age-picker"].firstMatch
        for _ in 0..<6 {
            if age.exists && age.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(age.isHittable)
        XCTAssertTrue(age.label.contains("اختر الفئة العمرية"), "Age must not be preselected")
        age.tap()
        app.buttons["أقل من ١٠ سنوات"].tap()
        XCTAssertTrue(app.staticTexts["الخدمة مخصصة لعمر ١٠ سنوات فأكثر."].waitForExistence(timeout: 5))
        screenshot("legal-under10-block")
        age.tap()
        app.buttons["١٨ سنة فأكثر"].tap()
        XCTAssertTrue(app.staticTexts["الخدمة مخصصة لعمر ١٠ سنوات فأكثر."].exists, "Changing to an adult age must not clear the cooldown")
    }

    @MainActor func testEmailConfirmationRejectsWrongCodeAndAcceptsCorrectCode() { checkVerification("EMAIL") }
    @MainActor func testPhoneConfirmationRejectsWrongCodeAndAcceptsCorrectCode() { checkVerification("PHONE") }

    @MainActor private func checkVerification(_ kind: String) {
        let app = XCUIApplication()
        app.launch()
        let control = app.buttons["V498_QA_VERIFY_\(kind)"]
        XCTAssertTrue(control.waitForExistence(timeout: 25))
        control.tap()
        let field = app.textFields["000000"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText("000000")
        app.swipeUp()
        app.buttons["تأكيد الحساب"].tap()
        XCTAssertTrue(app.staticTexts["رمز التحقق غير صحيح."].waitForExistence(timeout: 10))
        screenshot("verification-\(kind)-wrong-code")
        app.buttons["إعادة إرسال الرمز"].tap()
        XCTAssertTrue(app.staticTexts["أرسلنا رمز تحقق جديدًا."].waitForExistence(timeout: 10))
        field.tap()
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 6) + "123456")
        app.swipeUp()
        app.buttons["تأكيد الحساب"].tap()
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: field)
        waitForExpectations(timeout: 15)
        XCTAssertTrue(app.buttons["student-tab-0"].waitForExistence(timeout: 15))
        XCTAssertFalse(field.exists, "Successful verification must finish the confirmation screen")
        screenshot("verification-\(kind)-accepted")
    }
    @MainActor
    func testForegroundAdAppearsAndClearsAfterOpeningAds() {
        let app = XCUIApplication()
        app.launch()
        let publish = app.buttons["V498_QA_PUBLISH_FIXTURE"]
        XCTAssertTrue(publish.waitForExistence(timeout: 20))
        let adsTab = app.buttons["student-tab-3"].firstMatch
        XCTAssertTrue(adsTab.waitForExistence(timeout: 20))
        publish.tap()
        let unread = NSPredicate(format: "value CONTAINS %@", "إعلانات جديدة")
        expectation(for: unread, evaluatedWith: adsTab)
        waitForExpectations(timeout: 25)
        screenshot("01-live-ad-red-badge")
        adsTab.tap()
        XCTAssertTrue(app.staticTexts["مدرسة الاختبار 98000"].waitForExistence(timeout: 8))
        XCTAssertEqual(adsTab.value as? String ?? "", "")
        screenshot("02-ad-read")
        XCTAssertEqual(app.buttons.matching(identifier: "student-tab-0").count, 1)
        XCTAssertTrue(app.buttons["student-tab-0"].isHittable)
        app.buttons["student-tab-0"].tap()
        XCTAssertEqual(app.buttons.matching(identifier: "student-tab-3").count, 1)
        XCTAssertEqual(app.buttons["student-tab-3"].firstMatch.value as? String ?? "", "")
    }

    @MainActor
    func testCompactBrandLogosAndAccessibleTouchTargets() {
        let app = XCUIApplication()
        app.launch()
        let control = app.buttons["V498_QA_SOCIAL"]
        XCTAssertTrue(control.waitForExistence(timeout: 20))
        control.tap()
        for name in ["instagram", "facebook", "telegram"] {
            let element = app.descendants(matching: .any)["social-\(name)"].firstMatch
            XCTAssertTrue(element.waitForExistence(timeout: 5))
            XCTAssertGreaterThanOrEqual(element.frame.width, 44)
            XCTAssertGreaterThanOrEqual(element.frame.height, 44)
            XCTAssertLessThanOrEqual(element.frame.width, 48)
        }
        screenshot("04-compact-social-icons")
    }

    @MainActor
    func testOfflineColdStartAfterVerifiedLoginWithoutRememberedCookie() {
        // All bootstrap responses are synthetic URLProtocol fixtures; the QA
        // user was verified but no server cookies are saved for restoration.
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["student-tab-0"].waitForExistence(timeout: 25))
        app.terminate()

        // Reopen the real native UI with the network monitor forced offline.
        app.launchArguments = ["V498_QA_FORCE_OFFLINE"]
        app.launch()
        XCTAssertTrue(app.staticTexts["وضع الدراسة دون إنترنت"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["مراجعاتك محفوظة على الآيفون"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["فتح المراجعة دون إنترنت"].exists)
        screenshot("06-cold-offline-student-without-remember")
    }

    @MainActor
    func testMatchingEditorRequiresAllPairsAndAllowsEditing() {
        let app = XCUIApplication()
        app.launchArguments = ["V53_QA_MATCHING"]
        app.launch()
        let first = app.buttons["v53-match-A-1"]
        XCTAssertTrue(first.waitForExistence(timeout: 25))
        let submit = app.buttons["v53-match-submit"]
        XCTAssertFalse(submit.isEnabled)
        screenshot("matching-01-columns")
        for (left,right) in [("1","c"),("2","a"),("3","b"),("4","f"),("5","d"),("6","e")] {
            let a = app.buttons["v53-match-A-\(left)"]
            if !a.isHittable { app.swipeUp() }
            a.tap()
            let b = app.buttons["v53-match-B-\(right)"]
            if !b.isHittable { app.swipeUp() }
            b.tap()
        }
        if !submit.isHittable { app.swipeUp() }
        XCTAssertTrue(submit.isEnabled)
        screenshot("matching-02-completed")
        app.swipeDown()
        app.buttons["v53-match-A-1"].tap()
        app.buttons["v53-match-B-a"].tap()
        XCTAssertFalse(submit.isEnabled, "Moving an occupied target must leave one source incomplete")
        screenshot("matching-03-edit-displaces-link")
    }

    @MainActor private func screenshot(_ name: String) {
        let image = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        image.name = name
        image.lifetime = .keepAlways
        add(image)
    }
}

